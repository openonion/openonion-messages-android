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

The lavender onion in
`app/src/main/res/drawable-nodpi/openonion_mark_transparent.png` is the shared
OpenOnion master mark and the sole approved product mark. It must not be
redrawn, recolored, rotated, cropped, outlined, stretched, given a drop shadow,
or combined with a second SMS-specific logo.

- Minimum digital size: 32 dp; preferred app-bar size: 44 dp.
- Clear space: at least one quarter of the mark width on every side.
- Preferred background: Warm Paper or white. On dark surfaces, retain the
  original mark and confirm sufficient separation visually.
- The in-app lockup places the mark left of the two-line `OPENONION` / `Messages`
  wordmark. Do not reproduce the wordmark inside the image asset.
- Notification and action icons are functional symbols, never substitute logos.

The Apache-2.0 license covers this repository's code and assets but does not
grant permission to imply that a modified build is an official OpenOnion
release. Forks should use a distinct application ID and presentation when
needed to avoid user confusion.

## Color

These hexadecimal values are canonical. Brand lavender identifies OpenOnion;
signal orange is a restrained accent, not a large background color.

| Token | Hex | Use |
|---|---:|---|
| Ink | `#1B1820` | Primary text and high-contrast content |
| Warm Paper | `#FBF9F5` | Default light background |
| Paper Surface | `#FFFFFF` | Cards and dialogs |
| Onion Lavender | `#B9A6E9` | Master mark, supporting highlights |
| Lavender Soft | `#F0EBFA` | Setup indicators and quiet surfaces |
| Onion Purple | `#674F9C` | Primary actions and links |
| Purple Deep | `#35274B` | Active encrypted-inbox surface |
| Signal Orange | `#E95B2A` | Unread and exceptional attention cues |
| Secure Green | `#267454` | Confirmed private/complete state only |
| Hairline | `#E8E2EA` | Borders and dividers |
| Muted Ink | `#69636F` | Secondary text |

Dark mode uses `#151219` for the background, `#211C28` for surfaces,
`#2A2432` for raised surfaces, and `#F8F4FA` for primary content. Color must
never be the only way an error, completion, or privacy state is communicated.

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
- The active encrypted state uses Purple Deep, a lock symbol, explicit text,
  and a green `PRIVATE` label. Green never promises that the device itself is
  uncompromised.
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
