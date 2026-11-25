#!/usr/bin/env python3
"""Rebuild the Singlenote showcase assets from a folder of fresh screenshots.

Usage:
    python3 docs/showcase/build.py [source-directory]

With no argument, the source directory is docs/showcase/source. It must hold
exactly one image per slot (any .jpg/.jpeg/.png extension):

    editor           note editor             -> hero, social preview,
                                                feature graphic, 01-editor
    archive          archive screen          -> features, 04-archive
    settings         Settings main screen    -> features "Make it yours",
                                                05-settings
    language         Language screen         -> features third phone
    about            About app screen        -> features fourth phone, 06-privacy
    widget-settings  widget settings screen  -> 03-widget
    lock             lock screen with pin    -> notification, 02-pinned
    shade            notification shade      -> notification shade card
    widget-src       square home-screen widget source (>500px wide)

Only screen content changes. Typography, copy, layout, frames, corner radii,
rims and shadows all come from the committed assets, which are read from git
HEAD (never the working tree), so a rebuild cannot compound an earlier pass.
After approving a rebuild, commit the assets to make them the new baseline.
Change the scene design only when explicitly asked.

Requires Pillow, numpy and scipy.
"""
import io
import os
import subprocess
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy import ndimage

ROOT = subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True).strip()
OUT = os.path.join(ROOT, "docs", "showcase")
DEFAULT_SOURCE = os.path.join(OUT, "source")

SLOTS = (
    "editor", "archive", "settings", "language", "about",
    "widget-settings", "lock", "shade", "widget-src",
)

# Baseline resolutions of the supplied screenshots; crop/mask coordinates are
# scaled to whatever resolution the new batch arrives in.
BASE_PHONE = (900, 2000)
BASE_WIDGET = (667, 667)

WIDGET_CROP = (59, 151, 603, 521)   # exact widget surface in BASE_WIDGET
SHADE_CROP = (33, 220, 867, 631)    # exact notification card in BASE_PHONE
CARRIER_MASK = (0, 0, 400, 80)      # top-left carrier label in BASE_PHONE

EXPECTED = {
    "hero.png": (3200, 1800, "RGB"),
    "features.png": (3200, 1800, "RGB"),
    "notification.png": (3200, 1520, "RGB"),
    "social-preview.png": (1280, 640, "RGB"),
    "store/01-editor.png": (1080, 1920, "RGB"),
    "store/02-pinned.png": (1080, 1920, "RGB"),
    "store/03-widget.png": (1080, 1920, "RGB"),
    "store/04-archive.png": (1080, 1920, "RGB"),
    "store/05-settings.png": (1080, 1920, "RGB"),
    "store/06-privacy.png": (1080, 1920, "RGB"),
    "store/feature-graphic.png": (1024, 500, "RGB"),
}


def template(name):
    """Read an approved asset from git HEAD, never from the working tree."""
    data = subprocess.check_output(["git", "show", f"HEAD:docs/showcase/{name}"])
    return Image.open(io.BytesIO(data)).convert("RGB")


def load_screens(source_dir):
    if not os.path.isdir(source_dir):
        raise SystemExit(f"source directory not found: {source_dir}")
    screens = {}
    for slot in SLOTS:
        matches = [
            os.path.join(source_dir, f)
            for f in os.listdir(source_dir)
            if os.path.splitext(f)[0] == slot
            and os.path.splitext(f)[1].lower() in (".jpg", ".jpeg", ".png")
        ]
        if len(matches) != 1:
            raise SystemExit(f"expected exactly one '{slot}' image, found {matches}")
        image = Image.open(matches[0]).convert("RGB")
        if slot != "widget-src":
            ratio = image.width / image.height
            if abs(ratio - 9 / 20) > 0.02:
                print(f"warning: {slot} is {image.size}, not 9:20; it will be fitted")
        screens[slot.replace("-", "_")] = image
    return screens


def scaled_box(box, image, baseline):
    sx, sy = image.width / baseline[0], image.height / baseline[1]
    return tuple(round(v * (sx if i % 2 == 0 else sy)) for i, v in enumerate(box))


def save(im, name):
    path = os.path.join(OUT, name)
    im.save(path, "PNG", optimize=True)
    w, h, mode = EXPECTED[name]
    ok = im.size == (w, h) and im.mode == mode and os.path.getsize(path) < 1024 * 1024
    print(f"{'ok  ' if ok else 'BAD '} {name:28s} {im.size} {im.mode} "
          f"{os.path.getsize(path) // 1024} KB")
    if not ok:
        raise SystemExit(f"unexpected export: {name}")


def surface_mask(image, color, tol=8):
    """Exact mask of a rounded surface and its contents, from its own colour."""
    a = np.array(image).astype(int)
    m = np.abs(a - np.array(color)).max(axis=2) <= tol
    m = ndimage.binary_fill_holes(m)
    lab, n = ndimage.label(m)
    if n:
        sizes = ndimage.sum(m, lab, range(1, n + 1))
        m = lab == (np.argmax(sizes) + 1)
    return Image.fromarray((m * 255).astype("uint8"), "L")


def clean_surface(image, color, tol=8):
    """Fill everything outside a rounded surface with that surface's colour."""
    out = Image.new("RGB", image.size, color)
    out.paste(image, (0, 0), surface_mask(image, color, tol))
    return out


def rounded_mask(size, radius):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, size[0] - 1, size[1] - 1],
                                        radius=radius, fill=255)
    return m


def paste_screen(canvas, box, src, radius, mask=None):
    x0, y0, x1, y1 = box
    resized = src.resize((x1 - x0, y1 - y0), Image.LANCZOS)
    canvas.paste(resized, (x0, y0), mask or rounded_mask(resized.size, radius))


def paste_9_20(canvas, box, src, radius):
    x0, y0 = box[0], box[1]
    w = box[2] - box[0]
    resized = src.resize((w, round(w * 20 / 9)), Image.LANCZOS)
    canvas.paste(resized, (x0, y0), rounded_mask(resized.size, radius))


def floating_widget(canvas, template_image, frame_box, bg, inner_box, screen_box):
    """Place the widget from the template over a new screen, shadow and all."""
    px0, py0, px1, py1 = screen_box
    old = np.array(template_image.crop(screen_box)).astype(float)
    new = np.array(canvas.crop(screen_box)).astype(float)
    dark = np.clip(1 - (old / np.array(bg, float)).min(axis=2), 0, 1)
    fx0, fy0, fx1, fy1 = frame_box
    dark[fy0 - py0:fy1 - py0, fx0 - px0:fx1 - px0] = 0  # frame covers its own area
    diff = np.abs(new - np.array(bg, float)).max(axis=2)
    eligible = np.clip((8 - diff) / 6, 0, 1)  # 1 on flat background, 0 on new UI
    shadow = Image.new("RGBA", (px1 - px0, py1 - py0), (0, 0, 0, 0))
    shadow.putalpha(Image.fromarray((dark * eligible * 255).astype("uint8"), "L"))
    canvas.paste(shadow, (px0, py0), shadow)

    # Alpha and artwork from the template: real corner radius, rim and edge.
    frame_patch = np.array(template_image.crop(frame_box)).astype(int)
    edge = np.abs(frame_patch - np.array(bg)).max(axis=2)
    layer = template_image.crop(frame_box).convert("RGBA")
    layer.putalpha(Image.fromarray(np.clip(edge * 85, 0, 255).astype("uint8"), "L"))
    canvas.paste(layer, (fx0, fy0), layer)

    return surface_mask(template_image.crop(inner_box), (12, 12, 14), 5)


def card_rgba(crop, color, tol=8):
    """Crop a notification card with its exact rounded corners as alpha."""
    alpha = surface_mask(crop, color, tol).filter(ImageFilter.GaussianBlur(0.6))
    rgba = crop.convert("RGBA")
    rgba.putalpha(alpha)
    return rgba


def build(s):
    # Mask the carrier label with the status bar's pure black.
    lock = s["lock"].copy()
    mask_box = scaled_box(CARRIER_MASK, s["lock"], BASE_PHONE)
    ImageDraw.Draw(lock).rectangle(mask_box, fill=(0, 0, 0))
    s["lock"] = lock

    # Crop the two overlapping cards at their exact bounds.
    widget = clean_surface(s["widget_src"].crop(
        scaled_box(WIDGET_CROP, s["widget_src"], BASE_WIDGET)), (12, 12, 14), 5)
    shade_src = s["shade"].crop(scaled_box(SHADE_CROP, s["shade"], BASE_PHONE))

    # README hero — editor + widget.
    name = "hero.png"
    im = template(name)
    paste_9_20(im, (2268, 168, 2953, 1688), s["editor"], 55)
    box = (1800, 1000, 2459, 1450)
    mask = floating_widget(im, template(name), (1789, 990, 2471, 1462),
                           (20, 17, 24), box, (2268, 168, 2953, 1688))
    paste_screen(im, box, widget, 36, mask=mask)
    save(im, name)

    # README features — archive / settings / language / about.
    im = template("features.png")
    for box, key in [
        ((204, 592, 848, 1800), "archive"),
        ((920, 592, 1564, 1800), "settings"),   # "Make it yours"
        ((1636, 592, 2280, 1800), "language"),
        ((2352, 592, 2996, 1800), "about"),
    ]:
        paste_9_20(im, box, s[key], 51)
    save(im, "features.png")

    # README notification — lock screen + true crop of the notification card.
    name = "notification.png"
    im = template(name)
    paste_9_20(im, (2168, 168, 2924, 1520), s["lock"], 62)
    card = card_rgba(shade_src, (32, 32, 32), 8)
    card = card.resize((840, round(card.height * 840 / card.width)), Image.LANCZOS)
    shadow = Image.new("RGBA", card.size, (0, 0, 0, 0))
    shadow.putalpha(card.getchannel("A").filter(ImageFilter.GaussianBlur(12))
                    .point(lambda a: int(a * 0.55)))
    im.paste(shadow, (1740, 1008), shadow)
    im.paste(card, (1740, 1000), card)
    save(im, name)

    # Social preview — editor + widget.
    name = "social-preview.png"
    im = template(name)
    paste_9_20(im, (964, 36, 1206, 574), s["editor"], 17)
    box = (780, 364, 1000, 514)
    mask = floating_widget(im, template(name), (775, 359, 1005, 519),
                           (20, 17, 24), box, (964, 36, 1206, 574))
    paste_screen(im, box, widget, 12, mask=mask)
    save(im, name)

    # Store phone screenshots.
    for name, key in [
        ("01-editor.png", "editor"),
        ("02-pinned.png", "lock"),
        ("03-widget.png", "widget_settings"),
        ("04-archive.png", "archive"),
        ("05-settings.png", "settings"),
        ("06-privacy.png", "about"),
    ]:
        im = template(f"store/{name}")
        paste_screen(im, (278, 616, 802, 1780), s[key], 40)
        save(im, f"store/{name}")

    # Store feature graphic — editor + widget.
    name = "store/feature-graphic.png"
    im = template(name)
    paste_9_20(im, (764, 52, 1006, 500), s["editor"], 17)
    box = (620, 330, 830, 473)
    mask = floating_widget(im, template(name), (614, 324, 836, 479),
                           (20, 17, 24), box, (764, 52, 1006, 500))
    paste_screen(im, box, widget, 12, mask=mask)
    save(im, name)


def main():
    if len(sys.argv) > 2:
        raise SystemExit("usage: python3 docs/showcase/build.py [source-directory]")
    source = sys.argv[1] if len(sys.argv) == 2 else DEFAULT_SOURCE
    build(load_screens(source))


if __name__ == "__main__":
    main()
