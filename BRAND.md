# OpenOnion Messages brand system

OpenOnion Messages is the private SMS endpoint in the OpenOnion product family:
an inbox that a person can read and, after explicit pairing, a chosen Agent can
read through end-to-end encryption. Every product decision should communicate
owner control, legible privacy, and operational reliability.

## Name and positioning

Use **OpenOnion Messages** on first mention. Use **Messages** only when the
OpenOnion context is already visible, such as the second line of the in-app
lockup. The one-line descriptor is:

> Encrypted SMS for you and your AI agents.

Do not position the app as an OTP extractor, surveillance tool, invisible
forwarder, or authentication provider. Prefer “private Agent inbox,” “encrypted
copy,” “connected Agent,” and “owner-controlled.” Never claim the service
cannot see metadata; it receives the routing metadata documented in PRIVACY.md.

## Master mark and lockup

The green onion in
`app/src/main/res/drawable-nodpi/openonion_mark_transparent.png` is the shared
OpenOnion master mark and the sole approved product mark. It must not be
redrawn, rotated, cropped, outlined, stretched, given a drop shadow,
or combined with a second SMS-specific logo.

- Minimum digital size: 32 dp; preferred app-bar size: 36 dp.
- Clear space: at least one quarter of the mark width on every side.
- Preferred background: white or Paper. On dark surfaces, retain the
  original mark and confirm sufficient separation visually.
- The in-app lockup places the mark left of the two-line `OpenOnion` / `MESSAGES`
  wordmark. Do not reproduce the wordmark inside the image asset.
- Notification and action icons are functional symbols, never substitute logos.

The Apache-2.0 license covers this repository's code and assets but does not
grant permission to imply that a modified build is an official OpenOnion
release. Forks should use a distinct application ID and presentation when
needed to avoid user confusion.

## Color

OpenOnion uses one chromatic family: green. White and black create the product's
structure; green signals identity, action, privacy, and live state. Neutral
grays may be made only by mixing black and white. Purple, lavender, orange,
blue, and unrelated semantic hues are not part of this product.

| Token | Hex | Use |
|---|---:|---|
| OpenOnion Green | `#087443` | Primary actions, brand mark, live state |
| Bright Green | `#19B86B` | Small highlights on black surfaces |
| Soft Green | `#E8F7EF` | Quiet state containers and setup steps |
| OpenOnion Black | `#101411` | Primary text and connected-state surface |
| OpenOnion White | `#FFFFFF` | Cards, dialogs, and content on black |
| Paper | `#F7F9F7` | Default light background |
| Muted Ink | `#5D665F` | Secondary text |
| Hairline | `#DCE3DE` | Borders and dividers |

Dark mode uses `#090B0A` for the background, `#131715` for surfaces,
`#1B211D` for raised surfaces, and `#F7FAF8` for primary content. Error and
destructive states use explicit words and confirmation—not an off-brand red.
Color must never be the only way a state is communicated.

## Typography

Use the Android system sans-serif family to avoid an external font dependency
and preserve high-quality native rendering across supported devices. The type
scale is implemented in `ui/theme/Theme.kt`:

- 28/34 sp semibold for screen-level statements;
- 22/28 sp semibold for section and product titles;
- 17/24 sp semibold for card titles;
- 16/24 sp regular for primary reading text;
- 14/21 sp regular for explanatory text; and
- 12/16 sp medium for metadata and uppercase overlines.

Use sentence case for actions and headings. Uppercase is reserved for the
`OPENONION` lockup and short metadata labels, with deliberate letter spacing.

## Layout and components

Use an 8 dp grid, allowing 4 dp only for optical alignment. Screen gutters are
20 dp. Card padding is 24 dp for primary state cards and 16–18 dp for message
cards. The standard corner radii are 8, 12, 18, 24, and 30 dp.

- Show exactly one primary setup action at a time.
- Primary buttons are full width and at least 52 dp high.
- All interactive targets are at least 48×48 dp.
- The active encrypted state uses OpenOnion Black, a green lock, explicit text,
  and an outlined green `ON` state. Green never promises that the device itself
  is uncompromised.
- Destructive actions require a confirmation that names both deletion targets:
  the phone and Agent inbox.
- Borders and small elevation separate surfaces; avoid decorative gradients,
  glass effects, excessive shadows, and generic AI sparkle imagery.

## Voice and trust

Explain what happens before asking for permission. Name the actor (“this
phone,” “connected Agent,” or “oo-api”) and the data form (“SMS” or “encrypted
copy”). Avoid vague assurances such as “100% secure.” Error messages should say
what did not finish and preserve a safe retry path. SMS content is untrusted
input and must never be presented as authorization for an Agent action.

## Accessibility review

Every release must check light and dark contrast, large-font reflow, meaningful
content descriptions, keyboard/switch navigation, and 48 dp touch targets.
Status must remain understandable without color. Screenshots and marketing
assets must use synthetic messages and non-routable example identifiers.
