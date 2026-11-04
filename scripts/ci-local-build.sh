#!/usr/bin/env bash
#
# Local CI-parity build.
#
# Runs exactly the task list from .github/workflows/build.yml so a change can
# be verified locally before pushing:
#
#   clean assembleDebug assembleRelease testDebugUnitTest lintDebug lintRelease
#
# Setup it does for you (mirroring the workflow):
#   - Android SDK: platform-tools, platforms;android-35, build-tools
#   - JDK 17 (already required by the build)
#   - ~/.android/debug.keystore, generated exactly like the workflow does
#   - a LOCAL release keystore for assembleRelease (see the note below)
#
# The local release key is a throwaway test key. The official release key
# lives in the RELEASE_KEYSTORE_* repository secrets and is only available to
# CI, so a locally built release APK is signed with a different key: Android
# refuses to install it over the released app (INSTALL_FAILED_UPDATE_
# INCOMPATIBLE) — uninstall the released app first, which clears its data.
#
# Usage:
#   scripts/ci-local-build.sh                 # full CI task list
#   scripts/ci-local-build.sh assembleDebug   # a single task
#
# Env:
#   ANDROID_HOME / ANDROID_SDK_ROOT   SDK location (default ~/.android-sdk)
#   LOCAL_RELEASE_KEYSTORE            path for the local release keystore
#   GRADLE_HEAP                       heap for the Gradle daemon
#   GRADLE_HEAP_SMALL                 heap for the lighter stages
#   CI_LOCAL_STAGED=1                 run each task in its own Gradle invocation
#   CI_LOCAL_CPUS=0,1                 pin the build to a few cores
#   CI_LOCAL_CLEAN=1                  run "clean" before the other tasks
#   CI_LOCAL_LOG_DIR                  where the per-stage logs are written
#
# On an ARM64 Linux host the x86-64-only build tools (aapt2, zipalign) are run
# through qemu-user with a small amd64 sysroot. AGP is pointed at the wrapped
# aapt2 with android.aapt2FromMavenOverride, so it does not extract its own
# copy of aapt2 (that copy would be a raw x86-64 binary again).
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/.android-sdk}}"
SDK_TOOLS_VERSION=11076708   # commandlinetools-linux-11076708_latest.zip
BUILD_TOOLS_VERSION=35.0.0
PLATFORM=android-35
GRADLE_HEAP="${GRADLE_HEAP:-1400m}"
GRADLE_HEAP_SMALL="${GRADLE_HEAP_SMALL:-1100m}"
LOG_DIR="${CI_LOCAL_LOG_DIR:-/tmp/opencode/ci-local-logs}"

TASKS_EXPLICIT=0
TASKS=("$@")
if [ ${#TASKS[@]} -eq 0 ]; then
  TASKS=(clean assembleDebug assembleRelease testDebugUnitTest lintDebug lintRelease)
else
  TASKS_EXPLICIT=1
fi

# Logs go to stderr so that a function whose output is captured (the setup
# helpers) returns only its result.
log() { printf '\n== %s\n' "$*" >&2; }
fail() { echo "ERROR: $*" >&2; exit 1; }

# --- Android SDK ------------------------------------------------------------
install_sdk_tools() {
  local zip="$SDK_ROOT/cmdline-tools.zip"
  log "installing Android command-line tools into $SDK_ROOT"
  mkdir -p "$SDK_ROOT/cmdline-tools/latest"
  curl -sSL -o "$zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-${SDK_TOOLS_VERSION}_latest.zip"
  rm -rf "$SDK_ROOT/cmdline-tools/latest"
  mkdir -p "$SDK_ROOT/cmdline-tools/latest" "$SDK_ROOT/.extract"
  unzip -q -o "$zip" -d "$SDK_ROOT/.extract"
  mv "$SDK_ROOT/.extract/cmdline-tools/"* "$SDK_ROOT/cmdline-tools/latest/"
  rm -rf "$SDK_ROOT/.extract" "$zip"
}

if [ ! -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
  install_sdk_tools
fi

export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export PATH="$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$PATH"

log "accepting SDK licenses"
yes | sdkmanager --licenses >/dev/null 2>&1 || true

log "installing SDK packages (platform-tools, $PLATFORM, build-tools;$BUILD_TOOLS_VERSION)"
sdkmanager --install "platform-tools" "platforms;$PLATFORM" "build-tools;$BUILD_TOOLS_VERSION" >/dev/null

# --- x86_64 tools on an ARM64 host ------------------------------------------
# Google's Linux build tools (aapt2, zipalign, adb) are x86-64-only. On an
# ARM64 Linux host they cannot run, so they are executed through qemu-user
# with a minimal amd64 sysroot (glibc loader + libgcc_s).
X86_SYSROOT="${X86_SYSROOT:-$SDK_ROOT/x86_64-sysroot}"
X86_TOOLS="$SDK_ROOT/x86-tools"
QEMU=qemu-x86_64-static
USE_QEMU=0
if [ "$(uname -m)" != "x86_64" ]; then
  USE_QEMU=1
fi

# True for an x86-64 ELF binary. The ELF header is read directly, so the
# script does not need the "file" command.
is_x86_64() {
  local machine
  machine=$(od -An -tx1 -j18 -N2 "$1" 2>/dev/null | tr -d ' \n')
  [ "$machine" = "3e00" ]
}

# Replaces a binary with a qemu wrapper. Returns 0 when it wrapped something.
wrap_binary() {
  local bin="$1"
  if [ "$USE_QEMU" != "1" ]; then return 1; fi
  if [ ! -f "$bin" ] || [ ! -x "$bin" ]; then return 1; fi
  if [ -f "$bin.real" ]; then return 1; fi
  if ! is_x86_64 "$bin"; then return 1; fi
  mv "$bin" "$bin.real"
  cat > "$bin" <<WRAPPER
#!/bin/sh
exec env LD_LIBRARY_PATH="$X86_SYSROOT/usr/lib/x86_64-linux-gnu" \\
  "$QEMU" -L "$X86_SYSROOT" "$bin.real" "\$@"
WRAPPER
  chmod +x "$bin"
  return 0
}

# The amd64 sysroot: just the dynamic loader, glibc and libgcc_s.
build_sysroot() {
  log "building amd64 sysroot for qemu (glibc + libgcc_s)"
  mkdir -p "$X86_SYSROOT"
  local tmp
  tmp="$(mktemp -d)"
  local glibc_url libgcc_url
  glibc_url=$(curl -s --max-time 60 "http://archive.ubuntu.com/ubuntu/pool/main/g/glibc/" \
    | grep -oE 'libc6_2\.39[^"]*_amd64\.deb' | sort -u | tail -1)
  libgcc_url=$(curl -s --max-time 60 "http://archive.ubuntu.com/ubuntu/pool/main/g/gcc-14/" \
    | grep -oE 'libgcc-s1_14[^"]*_amd64\.deb' | sort -u | tail -1)
  [ -n "$glibc_url" ] || fail "could not find the amd64 glibc package"
  [ -n "$libgcc_url" ] || fail "could not find the amd64 libgcc_s package"
  curl -sSL --max-time 300 -o "$tmp/libc6.deb" \
    "http://archive.ubuntu.com/ubuntu/pool/main/g/glibc/$glibc_url"
  curl -sSL --max-time 300 -o "$tmp/libgcc.deb" \
    "http://archive.ubuntu.com/ubuntu/pool/main/g/gcc-14/$libgcc_url"
  dpkg-deb -x "$tmp/libc6.deb" "$X86_SYSROOT"
  dpkg-deb -x "$tmp/libgcc.deb" "$X86_SYSROOT"
  rm -rf "$tmp"
  ln -sfn usr/lib64 "$X86_SYSROOT/lib64"
  ln -sfn usr/lib "$X86_SYSROOT/lib"
}

sysroot_ok() {
  [ -f "$X86_SYSROOT/usr/lib/x86_64-linux-gnu/libc.so.6" ] &&
    [ -e "$X86_SYSROOT/lib64/ld-linux-x86-64.so.2" ]
}

# The aapt2 that matches the Android Gradle Plugin of this checkout. It is
# the same artifact AGP would download itself, so the daemon protocol matches.
aapt2_artifact_version() {
  local agp_version="$1"
  local gradle_home="${GRADLE_USER_HOME:-$HOME/.gradle}"
  local dir version
  for dir in "$gradle_home/caches/modules-2/files-2.1/com.android.tools.build/aapt2"/*/; do
    [ -d "$dir" ] || continue
    version="$(basename "$dir")"
    case "$version" in
      "$agp_version"-*) echo "$version"; return 0 ;;
    esac
  done
  # Not in the cache yet: ask Google's Maven repository.
  curl -s --max-time 60 \
    "https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/maven-metadata.xml" \
    | grep -oE "<version>${agp_version}-[0-9]+</version>" \
    | sed -E 's|</?version>||g' | tail -1
}

# Writes a qemu wrapper named "aapt2" (the name AGP expects) and returns its
# path. The wrapper is used through android.aapt2FromMavenOverride.
setup_aapt2_override() {
  local agp_version
  agp_version=$(sed -n 's/^agp = "\(.*\)"/\1/p' "$REPO_ROOT/gradle/libs.versions.toml" | head -1)
  [ -n "$agp_version" ] || fail "could not read the AGP version from gradle/libs.versions.toml"

  local version jar
  version="$(aapt2_artifact_version "$agp_version")"
  [ -n "$version" ] || fail "could not find an aapt2 artifact for AGP $agp_version"

  jar="$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/com.android.tools.build/aapt2/$version" \
    -name "aapt2-$version-linux.jar" 2>/dev/null | head -1)"
  if [ -z "$jar" ]; then
    log "downloading aapt2 $version"
    jar="$X86_TOOLS/aapt2-$version-linux.jar"
    mkdir -p "$X86_TOOLS"
    curl -sSL --max-time 300 -o "$jar" \
      "https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/$version/aapt2-$version-linux.jar"
  fi
  [ -s "$jar" ] || fail "the aapt2 artifact ($jar) is missing or empty"

  mkdir -p "$X86_TOOLS"
  unzip -p "$jar" aapt2 > "$X86_TOOLS/aapt2.real"
  [ -s "$X86_TOOLS/aapt2.real" ] || fail "could not extract aapt2 from $jar"
  chmod +x "$X86_TOOLS/aapt2.real"

  cat > "$X86_TOOLS/aapt2" <<WRAPPER
#!/bin/sh
exec env LD_LIBRARY_PATH="$X86_SYSROOT/usr/lib/x86_64-linux-gnu" \\
  "$QEMU" -L "$X86_SYSROOT" "$X86_TOOLS/aapt2.real" "\$@"
WRAPPER
  chmod +x "$X86_TOOLS/aapt2"
  echo "$X86_TOOLS/aapt2"
}

# The wrapper only works when the sysroot is complete, so check it for real.
aapt2_override_works() {
  printf 'start\n' | timeout 30 "$1" daemon 2>/dev/null | grep -q Ready
}

setup_x86_emulation() {
  command -v "$QEMU" >/dev/null 2>&1 || fail "$QEMU not found (apt install qemu-user-static)"
  if ! sysroot_ok; then
    build_sysroot
  fi
  sysroot_ok || fail "the amd64 sysroot at $X86_SYSROOT is incomplete"

  log "setting up the wrapped aapt2 for AGP"
  local override
  override="$(setup_aapt2_override)"
  if ! aapt2_override_works "$override"; then
    log "the wrapped aapt2 does not start, rebuilding the sysroot"
    rm -rf "$X86_SYSROOT"
    build_sysroot
    override="$(setup_aapt2_override)"
    aapt2_override_works "$override" || fail "the wrapped aapt2 still does not start"
  fi
  AAPT2_OVERRIDE="$override"

  log "wrapping the remaining x86_64 SDK binaries for qemu"
  local d b
  for d in "$SDK_ROOT"/build-tools/*/; do
    for b in "$d"aapt2 "$d"zipalign "$d"aidl "$d"split-select "$d"dexdump; do
      wrap_binary "$b" || true
    done
  done
  wrap_binary "$SDK_ROOT/platform-tools/adb" || true
}

AAPT2_OVERRIDE=""
if [ "$USE_QEMU" = "1" ]; then
  setup_x86_emulation
fi

# --- Keystores --------------------------------------------------------------
# The debug key: the same shape the workflow generates, so the debug variant
# behaves the same locally.
if [ ! -f "$HOME/.android/debug.keystore" ]; then
  log "generating debug keystore"
  mkdir -p "$HOME/.android"
  keytool -genkeypair -keystore "$HOME/.android/debug.keystore" \
    -storepass android -keypass android -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10950 \
    -dname "CN=Android Debug,O=Android,C=US"
fi

# The release key: a LOCAL test key. The upload key is a CI secret.
LOCAL_KEYSTORE="${LOCAL_RELEASE_KEYSTORE:-$HOME/.android/singlenote-local-release.keystore}"
if [ ! -f "$LOCAL_KEYSTORE" ]; then
  log "generating local release keystore (test key, not the upload key)"
  mkdir -p "$(dirname "$LOCAL_KEYSTORE")"
  keytool -genkeypair -keystore "$LOCAL_KEYSTORE" \
    -storepass singlenote -keypass singlenote -alias singlenote \
    -keyalg RSA -keysize 2048 -validity 10950 \
    -dname "CN=Singlenote Local,O=Singlenote,C=US"
fi
export RELEASE_KEYSTORE_FILE="$LOCAL_KEYSTORE"
export RELEASE_KEYSTORE_PASSWORD=singlenote
export RELEASE_KEY_ALIAS=singlenote
export RELEASE_KEY_PASSWORD=singlenote

# --- Build ------------------------------------------------------------------
cd "$REPO_ROOT"
log "java: $(java -version 2>&1 | head -1)"
log "sdk:   $SDK_ROOT"

# AGP starts one aapt2 daemon per thread (8 by default). On a small machine
# those daemons do not fit next to the Gradle daemon, so the pool is limited.
GRADLE_CMD=(./gradlew)
if [ -n "${CI_LOCAL_CPUS:-}" ] && command -v taskset >/dev/null 2>&1; then
  if taskset -c "$CI_LOCAL_CPUS" true 2>/dev/null; then
    log "pinning the build to CPUs $CI_LOCAL_CPUS"
    GRADLE_CMD=(taskset -c "$CI_LOCAL_CPUS" ./gradlew)
  else
    # The container may not allow every CPU of the list.
    log "CPUs $CI_LOCAL_CPUS are not available here, running without pinning"
  fi
fi

gradle_run() {
  local heap="$1"; shift
  local extra=()
  if [ -n "$AAPT2_OVERRIDE" ]; then
    extra+=("-Pandroid.aapt2FromMavenOverride=$AAPT2_OVERRIDE")
  fi
  # --no-daemon releases the heap between stages, --no-configuration-cache and
  # --no-watch-fs keep Gradle's caches out of a small machine's RAM.
  "${GRADLE_CMD[@]}" --no-daemon --no-configuration-cache --no-watch-fs \
    "-Dorg.gradle.jvmargs=-Xmx${heap} -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8" \
    "-Dorg.gradle.workers.max=1" \
    "-Pandroid.aapt2ThreadPoolSize=1" \
    "-Pandroid.r8.maxWorkers=1" \
    "-Pandroid.sync.suppressAgpWarnings=UNSUPPORTED_PROJECT_OPTION_USE" \
    "-Pkotlin.compiler.execution.strategy=in-process" \
    "${extra[@]}" \
    "$@"
}

run_stage() {
  local heap="$1"; shift
  local tasks=("$@")
  local name="${tasks[0]}"
  log "stage: ${tasks[*]} (heap ${heap})"
  mkdir -p "$LOG_DIR"
  local stage_log="$LOG_DIR/${name}.log"

  if gradle_run "$heap" "${tasks[@]}" 2>&1 | tee "$stage_log"; then
    return 0
  fi

  if grep -q "Daemon startup failed" "$stage_log" 2>/dev/null; then
    log "the aapt2 daemon failed to start; retrying the stage once"
    if [ -n "$AAPT2_OVERRIDE" ] && ! aapt2_override_works "$AAPT2_OVERRIDE"; then
      log "rebuilding the wrapped aapt2"
      setup_x86_emulation
    fi
    if gradle_run "$heap" "${tasks[@]}" 2>&1 | tee "$stage_log.retry"; then
      return 0
    fi
  fi
  return 1
}

# The repo asks for a 2 GB daemon heap, which does not fit on a small machine.
# CI_LOCAL_STAGED=1 (default on hosts with less than 4 GB of RAM) runs each
# task in its own Gradle invocation with a modest heap, so memory is released
# between stages instead of being held by one long-lived daemon.
if [ "${CI_LOCAL_STAGED:-auto}" = "1" ] || { [ "${CI_LOCAL_STAGED:-auto}" = "auto" ] && [ "$(awk '/MemTotal/ {print int($2/1024/1024)}' /proc/meminfo)" -lt 4 ]; }; then
  if [ "$TASKS_EXPLICIT" = "1" ]; then
    for t in "${TASKS[@]}"; do
      run_stage "$GRADLE_HEAP" "$t"
    done
  else
    if [ "${CI_LOCAL_CLEAN:-0}" = "1" ]; then
      run_stage "$GRADLE_HEAP_SMALL" clean
    fi
    # The release build comes first: it is the one a device test needs, and it
    # is the heaviest step (R8). The rest of the list follows the workflow order.
    # The debug build gets the full heap as well: merging the debug dex files
    # without shrinking needs more memory than the unit tests.
    run_stage "$GRADLE_HEAP" assembleRelease
    run_stage "$GRADLE_HEAP_SMALL" testDebugUnitTest
    run_stage "$GRADLE_HEAP" lintDebug
    run_stage "$GRADLE_HEAP" lintRelease
    run_stage "$GRADLE_HEAP" assembleDebug
  fi
else
  log "gradle tasks: ${TASKS[*]}"
  exec "${GRADLE_CMD[@]}" --no-daemon \
    "-Dorg.gradle.jvmargs=-Xmx${GRADLE_HEAP} -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8" \
    "-Pandroid.aapt2ThreadPoolSize=1" \
    "-Pandroid.r8.maxWorkers=1" \
    "-Pandroid.sync.suppressAgpWarnings=UNSUPPORTED_PROJECT_OPTION_USE" \
    "-Pkotlin.compiler.execution.strategy=in-process" \
    "${TASKS[@]}"
fi
