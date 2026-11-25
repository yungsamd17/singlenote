# Showcase image guidelines

Rules for producing and reviewing Singlenote marketing images. They come from
the approved set in this directory and the feedback that shaped it. When in
doubt, match the existing files and make the composition quieter.

Read [`README.md`](README.md) for the asset map, sizes and upload steps.

## The product is the design

- Use real screenshots from the current build inside a dark, rounded phone
  frame. Never draw a fake screen, invent UI, or use a stock phone mockup.
- One idea per image. The note is the subject; the headline, phone and widget
  support it.
- Show the app, not a metaphor. No floating 3D devices, glass panels, device
  piles or stock photography added to the composition. A wallpaper inside a
  real screenshot is part of the screenshot.
- Repeat the same frame, type and glow treatment across the set. The images
  should read as one campaign, not six unrelated posters.

## Visual system

- Background: `#0D0B10` (near-black AMOLED). Depth comes from a single soft
  magenta glow, never from a lighter page background.
- Ink: `#F3EDF7`; secondary `#CAC4D0`; quiet `#938F99`; faint `#7E7488`.
- Accent: `#F48FB1`; soft `#F8BBD0`; deep `#880E4F`.
- Surfaces: `#211F26`, `#2B2930`; frame `#1B1920`; shade card `#202020`.
- Type: Google Sans Flex. Headlines use 600 weight and tight tracking
  (about `-0.04em`), sentence case, never all-caps headlines.
- Headline pattern: first line in ink, second line in accent, with the
  punctuation the sentence needs. One-line section titles stay all ink.
- Body copy is `#CAC4D0`; the single trust sentence is `#938F99`. Do not make
  paragraphs pink.
- Glows are soft, blurred radials behind the device. No neon outlines, no
  rainbow gradients, no glows behind body copy.
- Empty space is part of the design. Do not fill it with badges or extra copy.

## Phones and cards

- Phone frame: 4px `#1B1920` bezel, a 1px white rim at 11–13% opacity, and a
  circular corner radius computed from the frame width (`width * 0.092`).
  A percentage radius resolves separately per axis and makes tall phones look
  square-ish; never use one.
- The visible screen keeps a 9:20 aspect ratio inside the bezel. The inner image
  radius is the frame radius minus 4px.
- Every visible corner must be genuinely round. Do not clip a corner by accident.
  If a phone bleeds off an edge, do it deliberately and keep the other corners
  intact. Store screenshots show the whole phone, including the bottom corners.
- Widget: crop the card at its exact surface bounds in whatever resolution the
  source arrives (the build script scales the approved crop coordinates).
  Keep the 4px rim and drop shadow from the template so the card floats above
  the editor. No black margin between the image and the rim, no double outline.
- The settings screen is the Play Store "Make it yours." shot. The README
  gallery may keep the accent-color dialog because that image is about seeing
  the actual colors.
- Gallery captions sit above the phones in accent pink, sentence case, two to
  four words. No uppercase eyebrows.
- One quiet trust sentence may sit under the copy
  ("Offline-first, with no account and no tracking."). Never turn those claims
  into pills, chips, badges or a row of icons.

## Crops and masking

- Lock screen: the carrier label at the left of the status bar is not part of
  the product. Mask it with pure `#000000` matching the status bar and keep the
  right-side status icons. Leave no visible rectangle seam.
- Notification shade: crop the notification card at its exact bounds and use
  its own rounded corners as the alpha mask; float it over the lock screen
  with a soft drop shadow. No white hairline around the crop.
- Never paint over a UI control, label or icon to fix a crop. Recrop, or choose
  a different screenshot.

## Rebuilding from new screenshots

**Routine refresh:** drop the new screenshots into `docs/showcase/source/`
using the slot names in [README.md](README.md) and run
`python3 docs/showcase/build.py` with no arguments. No design decisions are
needed. Commit the approved results so they become the next baseline. Do not
touch styling: headlines, captions, copy, colours, frames, corner radii, rims,
shadows and scene layout stay as they are. Change them only when the developer
explicitly asks for a visual update.

The committed PNGs are the approved scenes. `build.py` reads templates from git
HEAD, never from the working tree, so it cannot compound an earlier pass. Do
not regenerate by hand from a file you just wrote.

- Paste screenshots into the **inner screen** only, through a rounded mask. The
  inner radius is `frame width × 0.092` minus the bezel/rim inset. A plain
  rectangular paste leaves square corners over the template's rounded screen.
- A card that overlaps a phone owns its frame, rim and soft drop shadow — the
  widget in hero, social preview and the feature graphic, the shade card in the
  notification image. After pasting the new phone screen, recover the shadow as
  a black alpha layer from how much the template darkens the flat screen
  background, and apply it only where the new screenshot is still plain
  background. Paste the template's own frame/rim artwork on top. Neither the
  shadow nor its mask may have an artificial rectangular boundary: derive them
  from the template's actual pixels and let them fade out naturally. Include
  the 1px light rim **outside** the dark bezel in the frame crop — a box tight
  to the bezel drops that rim and leaves the card looking half-outlined.
  Take the frame layer's alpha from the template pixels too (difference from
  the screen background), not from a computed radius: `width * 0.092` rounds
  the outer corners slightly too tight and makes them visibly differ.
- Change an overlapping widget's content *inside its inner screen box*. Do not
  fill or redraw the whole widget: that erases its rounded corners and shadow.
- The notification card is a **true crop of the shade screenshot**: crop it at
  its exact bounds, use its rounded corners as the alpha mask, and float it over
  the new lock screen with a drop shadow. Do not stretch new card content into
  the old card's mask — that misaligns the card background with its corners.
- Keep the widget's single 4px frame and shadow from the template; paste only
  the cropped card surface, with no black margin on any edge.
- Widget content is fitted inside the existing inner box; the notification
  card keeps its own aspect ratio when scaled. Never stretch a frame or move a
  card.
- `build.py` checks every export's dimensions, mode and size. For the overlap,
  sample the card's bezel, rim and shadow pixels against the committed file —
  they must match exactly; a missing rim is easy to miss on a small screen.

## Copy

Short, literal and human. Say what the app does.

Approved examples:

- One note. Everywhere.
- The note comes first.
- Pin it. It stays.
- Nothing lost.
- Make it yours.
- Your note, at a glance.
- Offline by design.
- Your note on the home screen, the lock screen and the shade.
- Offline-first, with no account and no tracking.

Avoid:

- Forced symmetry that means nothing. "Small app. Big care." is the canonical
  anti-example: do not write a second sentence just to balance the first.
- Generic SaaS/AI phrasing: "Everything you need, nothing you don't",
  "Beautifully simple", "Supercharged", "Meet your new...", "Say goodbye to...",
  "Built for the way you...", "Designed to keep up".
- Exclamation marks, emoji, arrows, checkmark bullets, "FREE", "NEW", version
  numbers or ratings in a headline.
- Claims the app does not support. The notification has **Open** and **New
  note**, not snooze. The language setting translates the app, not the note.
  Do not imply a lock-screen action bypasses the lock.
- An em dash at the start of a wrapped line. Rewrite, or let the break fall
  naturally.

## Don't

- No "01 / 02" indices, page numbers or corner metadata.
- No eyebrow label above a headline.
- No small pink divider rule above a headline.
- No footer strip: no "Free · Open source · Android 10+", URL, copyright or
  version line.
- No pill rows for offline / accounts / tracking.
- No fake UI, fake notifications or reassembled screenshots.
- No stock phone mockups or recolored phone bodies.
- No busy backgrounds: grids, dot patterns, noise, bevels or circuit lines.
- No drop shadows on text.
- No white hairline around a cropped card; use the card's own surface color.
  The widget's 4px frame is the exception: it deliberately borrows the phone
  frame language so the widget reads as a separate layer.
- No app-store badges, QR codes, ratings or "as seen on" marks.
- No AI-generated people, hands, devices or decorative 3D objects.

## Per-asset layout

- **README hero** — 3200×1800. Brand top-left, headline block on the left, the
  editor phone on the right, the widget overlapping its lower-left edge with a
  small "Home screen widget" caption.
- **README features** — 3200×1800. One-line title, one-line subline, four phones
  in a row, pink captions above each. The phones may bleed off the bottom edge.
- **README notification** — 3200×1520. Headline and copy on the left, lock-screen
  phone on the right, shade card overlapping its lower-left. No footer.
- **Social preview** — 1280×640. Brand, headline, subline and trust sentence on
  the left; editor phone and widget on the right. Keep 64px side margins and at
  least 70px between the phone and the right edge so X's 1.91:1 crop is safe.
- **Store previews** — 1080×1920 (9:16). Brand top-left, two-line headline,
  subline, one fully visible phone centered below. 135px side gutters at a
  540×960 design scale.
- **Feature graphic** — 1024×500. Brand and headline left, phone and widget
  right, no footer strip.
- **Play icon** — 512×512, opaque, the launcher glyph on its dark background.

## Export and verify

- Capture at 2× and downscale with Lanczos. Never upscale a screenshot.
- Store screenshots and the feature graphic: 24-bit PNG, sRGB, no alpha.
- Play icon: 512×512 RGBA PNG, opaque background, under 1 MB.
- Social preview: 1280×640 PNG, under 1 MB.
- Keep filenames stable; Play Store order is the filename order.
- Check dimensions, mode and file size before replacing a file:

  ```sh
  python3 - <<'PY'
  from PIL import Image
  im = Image.open("docs/showcase/social-preview.png")
  print(im.size, im.mode)
  PY
  ```

## Review checklist

- Every screen is a current, real screenshot.
- Every visible phone corner is round; no elliptical or square corners.
- The widget crop is exact, the rim is a single clean 4px, and it reads above
  the phone.
- Every overlapping card still has its bezel, rim and drop shadow, with round
  corners (widget in hero/social/feature graphic, shade in notification).
- No hard seam or misaligned background is visible where an overlapping card
  meets the phone screenshot; the shadow fades smoothly into the new screen.
- The lock-screen carrier area is pure black with no seam.
- The shade crop is matted in its own surface color with no white ring.
- The headline and subline are true, literal and free of banned patterns.
- No indices, eyebrows, pills, divider rule, footer or badge crept back in.
- The dimensions are exact and the files are reasonably sized.
- The image still looks intentional at 400px wide (Discord) and in a 1.91:1
  crop (X/Twitter).
