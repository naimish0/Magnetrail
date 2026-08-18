---
version: alpha
name: Magnetrail
description: Premium, calm mobile puzzle game built around arrows, magnetic line-of-sight, and alternating Pull/Push polarity.
colors:
  primary: "#183153"
  primary-strong: "#10223C"
  pull: "#18A7B8"
  pull-soft: "#E8F7F8"
  push: "#E79A2D"
  push-soft: "#FFF4DF"
  background: "#F4F7FB"
  surface: "#FFFFFF"
  surface-raised: "#FAFCFE"
  on-surface: "#172033"
  on-surface-muted: "#5B6574"
  border: "#D7DEE7"
  grid-line: "#C9D3DF"
  wall: "#39414D"
  success: "#267A5B"
  error: "#B84343"
  scrim: "rgba(16, 34, 60, 0.56)"
typography:
  display:
    fontFamily: Manrope
    fontSize: 34px
    fontWeight: 800
    lineHeight: 1.1
    letterSpacing: -0.03em
  headline-lg:
    fontFamily: Manrope
    fontSize: 26px
    fontWeight: 750
    lineHeight: 1.2
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Manrope
    fontSize: 20px
    fontWeight: 700
    lineHeight: 1.25
  body-lg:
    fontFamily: Manrope
    fontSize: 16px
    fontWeight: 500
    lineHeight: 1.5
  body-md:
    fontFamily: Manrope
    fontSize: 14px
    fontWeight: 500
    lineHeight: 1.45
  label-lg:
    fontFamily: Manrope
    fontSize: 15px
    fontWeight: 700
    lineHeight: 1.2
  label-md:
    fontFamily: Manrope
    fontSize: 13px
    fontWeight: 700
    lineHeight: 1.2
  label-sm:
    fontFamily: Manrope
    fontSize: 11px
    fontWeight: 750
    lineHeight: 1.2
    letterSpacing: 0.08em
rounded:
  none: 0px
  sm: 10px
  md: 16px
  lg: 24px
  xl: 30px
  full: 9999px
spacing:
  xxs: 4px
  xs: 8px
  sm: 12px
  md: 16px
  lg: 24px
  xl: 32px
  xxl: 48px
  screen-x: 20px
  screen-top: 16px
  screen-bottom: 24px
  touch-min: 48px
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.surface}"
    typography: "{typography.label-lg}"
    rounded: "{rounded.md}"
    height: 56px
    padding: 16px
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.primary}"
    typography: "{typography.label-lg}"
    rounded: "{rounded.md}"
    height: 52px
    padding: 16px
  icon-button:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.primary}"
    rounded: "{rounded.full}"
    size: 48px
  board:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.xl}"
    padding: 16px
  card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.lg}"
    padding: 20px
  polarity-pull:
    backgroundColor: "{colors.pull-soft}"
    textColor: "{colors.pull}"
    typography: "{typography.label-sm}"
    rounded: "{rounded.full}"
    height: 32px
    padding: 10px
  polarity-push:
    backgroundColor: "{colors.push-soft}"
    textColor: "{colors.push}"
    typography: "{typography.label-sm}"
    rounded: "{rounded.full}"
    height: 32px
    padding: 10px
---

# Magnetrail Design System

## Overview

Magnetrail is a portrait mobile logic game in which players clear arrows while magnets change their effective direction. The defining interaction is an automatic polarity flip: a successful magnetic move changes Pull to Push or Push to Pull, transforming the next decision.

The interface should feel **calm, tactile, intelligent, premium, and immediately readable**. It should resemble a beautifully made physical logic toy placed on a quiet digital work surface. It must not resemble a generic hyper-casual arrow clone, a casino interface, a children's learning app, or a neon science-fiction control panel.

The visual hierarchy is always:

1. Board state and arrow paths.
2. Current magnet polarity.
3. Level objective and progress.
4. Undo, restart, pause, and optional assistance.

The user should feel curiosity before acting, certainty while watching the result, and a crisp sense of closure when the board clears. Use generous negative space. The board is the hero; surrounding UI is quiet.

Target a portrait mobile canvas of **390 x 844 px**, while respecting iOS and Android safe areas. Designs must adapt cleanly from 360 px to 430 px width.

Use a temporary text-based Magnetrail wordmark only. Do not invent a complex final logo during UI exploration.

## Colors

The base palette uses cool paper-like surfaces and deep navy structure. Pull and Push have different semantic colors, but color is never the only indicator.

- **Primary Navy (`#183153`):** Brand, primary buttons, strong icons, active navigation, and key level labels.
- **Pull Cyan (`#18A7B8`):** Inward magnetic influence, Pull labels, Pull field lines, and the Pull tutorial.
- **Push Amber (`#E79A2D`):** Outward magnetic influence, Push labels, Push field lines, and the Push tutorial.
- **Background (`#F4F7FB`):** Quiet app canvas with a subtle cool tint.
- **Surface (`#FFFFFF`):** Game board, cards, sheets, and controls.
- **Ink (`#172033`):** Primary text and arrow bodies.
- **Muted Slate (`#5B6574`):** Secondary labels and metadata.
- **Wall (`#39414D`):** Permanent blockers; visibly solid and heavier than arrows.
- **Success (`#267A5B`):** Completion and confirmed progress only.
- **Error (`#B84343`):** Collision impact only; never use as a persistent punishment color.

Pull must also use **inward chevrons and converging field lines**. Push must use **outward chevrons and diverging field lines**. Every polarity indicator includes the word `PULL` or `PUSH` when space permits.

Maintain WCAG AA contrast for UI text. Decorative field lines may be lower contrast, but arrows, walls, controls, labels, and projected route segments must remain clearly distinguishable.

Do not introduce extra saturated colors into the prototype. Stars may use Push Amber. Locked or unavailable states use neutral slate, not an additional hue.

## Typography

Use **Manrope** throughout. Its geometric structure supports the logic-game identity while remaining friendly and highly legible on small screens.

- **Display:** Brand wordmark and rare celebratory headlines only.
- **Headline large:** Home greeting and completion headline.
- **Headline medium:** Level title, modal title, and settings section.
- **Body:** Tutorial explanation and supporting copy.
- **Labels:** Buttons, level numbers, polarity state, counters, and compact metadata.

Use sentence case for headings and buttons. Reserve uppercase for the very short semantic labels `PULL`, `PUSH`, and optional eyebrow labels such as `DAILY PUZZLE` in later versions.

Never use condensed fonts, outlined text, exaggerated cartoon lettering, metallic display fonts, or multiple unrelated typefaces.

## Layout

Use a strict 4 px foundation with the provided 8/12/16/24/32/48 px rhythm.

Core gameplay screen structure:

- Safe-area top inset.
- Compact 56-64 px top bar with back or pause, centered level label, and settings or sound control.
- Optional 32 px polarity legend or tutorial text.
- Flexible hero area containing a square board centered horizontally.
- 16-24 px clearance around the board so trajectories can visually exit.
- Bottom action row with Undo, Restart, and Hint; keep it above the home indicator.

The board should occupy approximately **344 x 344 px** on a 390 px-wide device. It may scale down for a 7 x 7 puzzle, but cell hit targets should remain forgiving through invisible expanded tap regions.

Home screen structure:

- Temporary Magnetrail wordmark and short tagline at the top.
- One prominent Continue card showing level progress.
- Large primary Play button.
- Secondary Level Select button.
- Small settings access in the top-right.
- No coin counter, store banner, inbox, event rail, or promotional carousel in the prototype.

Favor one main visual anchor per screen. Avoid placing several equally strong cards above the fold.

## Elevation & Depth

Use **tonal layering and restrained soft shadows**, not glassmorphism.

- App background: flat cool gray-blue.
- Board and main cards: pure white with a soft shadow around `0 10px 30px rgba(24, 49, 83, 0.10)`.
- Floating controls: white surface with `0 6px 18px rgba(24, 49, 83, 0.12)`.
- Modals and completion cards: stronger separation, maximum `0 18px 48px rgba(16, 34, 60, 0.18)`.

Do not use glossy bevels, fake metal, large blurred neon glows, thick drop shadows, frosted glass, or excessive layered cards.

Magnetic fields may use a controlled outer glow in the state color at 10-18% opacity. This glow communicates force and must not become decorative haze.

## Shapes

The shape language combines soft containers with precise game geometry.

- Cards and boards: 24-30 px corner radius.
- Buttons: 16 px corner radius, not fully pill-shaped unless they are compact chips.
- Icon buttons: circular.
- Arrow bodies: bold, simple, geometric, and easy to recognize at a glance. Avoid thin line arrows.
- Magnet node: circular core with a subtle split-ring or rail motif. Do not use emoji or a literal red-and-blue horseshoe magnet.
- Walls: dark solid rounded rectangles or dense blocks with a subtle inset line.
- Grid: thin neutral lines; avoid a heavy checkerboard.

The magnet core should be the most visually distinctive board object. Pull shows inward motion around the node; Push shows outward motion. The same core remains recognizable in both states.

## Components

### App bar

Use a quiet top bar without a filled container. Center `Level 06` or the current screen title. Use circular 48 px controls for back, pause, sound, or settings. Never show more than two utility icons at once.

### Primary button

Use Primary Navy, 56 px height, 16 px radius, white text, and a subtle pressed scale of 0.98. Button copy is short: `Play`, `Continue`, `Next level`, or `Try again`.

### Secondary button

Use a white surface with a subtle border or shadow and Primary Navy text. Avoid ghost buttons directly over visually busy board content.

### Game board

Use a clean white rounded square. Internal grid lines are subtle but visible. Allow arrow animations to pass beyond the board edge without being clipped.

Game objects must remain visually separate:

- Arrow: navy body, rounded stem, strong triangular head, minimum visible size 28 px.
- Selected arrow: slightly lifted, navy outline, and a short projected path segment.
- Pull field: cyan converging lines plus inward chevrons.
- Push field: amber diverging lines plus outward chevrons.
- Wall: dense charcoal block.
- Empty cell: no fill or only a very subtle hover/tap response.

Do not render every cell as an individual raised tile. The board should feel open, not like a match-3 grid.

### Polarity badge

Place a compact badge near the board or temporarily above the magnet during tutorial moments. Always pair color with label and directional glyph:

- Pull: `PULL  ›‹`
- Push: `PUSH  ‹›`

After players understand the rule, the on-board field should carry most of the state communication; the badge remains available for accessibility.

### Bottom controls

Use three evenly spaced actions: Undo, Restart, Hint. Icons sit inside 48 px circular surfaces with 11-13 px labels underneath. Hint is less visually prominent than Undo and Restart during the prototype.

### Tutorial coach mark

Use a focused translucent scrim while keeping the relevant arrow, magnet, and path fully bright. The explanation appears in a white rounded card with one sentence and a small `Got it` button.

Tutorial copy:

- Pull: `Pull turns an aligned arrow toward the magnet.`
- Push: `Push sends an aligned arrow away from the magnet.`
- Flip: `Every magnetic move flips the field.`
- Occlusion: `Arrows and walls can block the magnetic field.`

### Collision feedback

Show the moving arrow contacting the blocker, a compact red impact ring, and a quick rewind. Do not show a large failure modal for a single invalid launch. The board remains interactive immediately.

### Completion card

Use a centered elevated card or bottom sheet after the final arrow animation lands. Show:

- `Board cleared`
- One to three amber stars
- Compact metrics: `Overloads 0`, `Hints 0`
- Large `Next level` button
- Small `Replay` text button

Celebration is polished and brief: a magnetic ripple, a few geometric particles, and a gentle star reveal. Avoid confetti cannons, treasure chests, coin showers, or jackpot language.

### Level tile

Use a 3-column grid on level select. Each tile is a white rounded square with a clear level number and up to three small stars. Completed tiles may receive a faint cyan edge. Locked tiles use a neutral lock and must not appear punitive.

### Settings

Use a bottom sheet or full-height modal with large labeled rows. Include Sound, Music, Haptics, Reduced Motion, High-Contrast Fields, and Path Preview Assistance. Toggles must use Navy when active and neutral gray when inactive.

## Do's and Don'ts

- Do make the board the dominant visual element on every gameplay screen.
- Do show Pull and Push through color, words, and directional field geometry.
- Do keep arrows thick, simple, and easy to tap.
- Do reserve strong color for polarity, primary actions, success, and collision feedback.
- Do let trajectories animate beyond the board edge.
- Do use whitespace to create a calm, premium feel.
- Do maintain at least 48 x 48 px interaction targets.
- Do design light mode first, then derive dark mode after the light system is approved.
- Don't copy the black-on-white visual style, hearts system, dense arrow artwork, or screen composition of existing Arrow Out games.
- Don't use emoji magnets, red/blue horseshoes, faux physics diagrams, or realistic metal textures.
- Don't add coins, ads, store promotions, daily streaks, leaderboards, lives, or event banners to prototype screens.
- Don't use glassmorphism, neon cyberpunk styling, glossy 3D buttons, or exaggerated gradients.
- Don't hide polarity behind color alone.
- Don't place tutorial text directly over the board without a scrim and focus treatment.
- Don't show a blocking full-screen failure state after an invalid arrow launch.

## Iconography

Use a coherent rounded-outline icon family with approximately 2 px strokes at 24 px. Icons should feel geometric rather than hand-drawn. Prefer simple symbols for back, pause, undo, restart, hint, sound, settings, replay, lock, and accessibility.

The arrow pieces on the board are game objects, not standard UI icons; they should be heavier and more dimensional than navigation icons.

## Motion

Design static screens so motion intent is obvious in annotations and state variants.

- Tap acknowledgment: under 50 ms through a small scale/compression response.
- Arrow traversal: 70-110 ms per cell, with slight acceleration over long empty routes.
- Pull capture: curved ease-in toward the magnet, brief cyan compression, then absorption.
- Push exit: decisive outward acceleration with an amber trail that dissipates at the edge.
- Polarity flip: 220-320 ms core rotation/compression, then field lines reverse.
- Collision: 250-400 ms impact and rewind.
- Completion: 450-700 ms after the final arrow leaves or is captured.

Reduced Motion replaces curves, particles, bounce, and rotation with short fades, direct translation, and an instant polarity icon swap.

## Voice and Copy

Voice is concise, encouraging, and intelligent. Never claim to test IQ or make the player smarter. Avoid urgency, guilt, exaggerated rewards, and slang.

Preferred vocabulary:

- `Board cleared`
- `Find the sequence`
- `The field flipped`
- `Path blocked`
- `Try another arrow`
- `Clean solve`

Avoid:

- `Amazing!!!`
- `Genius`
- `Brain test`
- `You failed`
- `Only 1% can solve this`
- `Claim reward`

## Screen Blueprint

Create these screens as one coherent mobile product family:

1. **Splash:** Background canvas, temporary Magnetrail wordmark, subtle cyan-to-amber magnetic rail motif, no loading clutter.
2. **Home:** Wordmark, `Bend the path. Clear the board.`, Continue card for Level 06, primary Play button, Level Select button, settings icon.
3. **Level Select:** Header, chapter label `Field Basics`, 3-column level grid for Levels 1-12, star progress `21 / 36`, no monetization UI.
4. **Gameplay - normal:** Level 02 with a 4 x 4 board, two navy arrows, no magnet, Undo/Restart/Hint controls.
5. **Gameplay - Pull:** Level 06 state with a cyan Pull magnet, wall, two arrows, visible converging field, `PULL ›‹` badge.
6. **Gameplay - Push:** Same layout after one successful move, magnet changed to amber Push, outward field geometry, `PUSH ‹›` badge.
7. **Tutorial overlay:** Level 05 with focused arrow and magnet, dimmed surrounding UI, copy `Every magnetic move flips the field.`
8. **Collision state:** Arrow touching a wall, small impact ring, `Path blocked` inline message, no modal.
9. **Pause/settings sheet:** Resume, Restart level, Sound, Music, Haptics, Reduced Motion, High-Contrast Fields.
10. **Completion:** `Board cleared`, three stars, `Overloads 0`, `Hints 0`, Next Level primary button, Replay secondary action.

For board examples, use the authored Level 06 state:

- Board: 5 x 5.
- Pull magnet at row 3, column 3.
- Arrow A at row 3, column 1, printed north.
- Arrow B at row 3, column 4, printed south.
- Wall at row 3, column 5.
- Intended sequence: launch B while Pull is active, magnet flips to Push, then launch A away from the magnet.

## Prototype Flow

Connect the following interactions in Stitch's playable prototype:

- Splash -> Home.
- Home `Play` -> Gameplay Pull.
- Home `Level Select` -> Level Select.
- Level tile 06 -> Gameplay Pull.
- Tap Arrow B -> Gameplay Push state.
- Tap Arrow A -> Completion.
- Gameplay pause icon -> Pause/settings sheet.
- Pause `Resume` -> prior gameplay state.
- Completion `Next level` -> Gameplay normal or a Level 07 placeholder.
- Completion `Replay` -> Gameplay Pull initial state.

The prototype should communicate state change through screen variants even though production gameplay animation will be implemented separately.

## Stitch Generation Brief

Design a high-fidelity portrait mobile UI for **Magnetrail**, a premium deterministic arrow-and-magnet logic puzzle. Use this DESIGN.md as the authoritative visual system. Generate the ten screens in the Screen Blueprint and connect the Prototype Flow.

Prioritize the game board, visible magnetic polarity, and one-thumb clarity. The board should look like a precision logic toy on a calm cool-white surface. Pull is cyan with inward geometry; Push is amber with outward geometry. Both states use explicit text labels as well as color. Arrows are thick navy game pieces. Walls are dense charcoal blocks. Use generous negative space, Manrope typography, quiet white surfaces, and restrained shadows.

Do not add coins, hearts, ads, promotional banners, streaks, shop UI, leaderboards, 3D treasure, emoji magnets, neon cyberpunk effects, glassmorphism, or generic hyper-casual clutter. Do not imitate the composition or artwork of existing Arrow Out games.

Start by placing the Home, Gameplay Pull, and Gameplay Push screens side by side so the brand and polarity transition can be reviewed together. Then generate the remaining screens using the approved component language. Keep all interaction targets at least 48 x 48 px and preserve safe-area spacing.

## Review Checklist

Before treating the design as final, confirm:

- A new viewer notices the board before any surrounding UI.
- Pull and Push remain distinguishable in grayscale through words and field direction.
- The magnet is visually unique without using a literal horseshoe magnet.
- Arrow pieces remain readable on 5 x 5 and 7 x 7 boards.
- All gameplay actions are reachable and at least 48 x 48 px.
- The board has enough outer clearance for exit animation.
- Home contains one obvious primary action.
- Collision feedback does not block continued play.
- Completion feels rewarding without economy or casino language.
- Reduced Motion and High-Contrast Fields have visible design states.
- Screen components, spacing, radii, colors, and typography match the tokens above.
- No screen introduces a feature outside the prototype scope.

## Approval Sequence

Finalize the design in four controlled passes:

1. **Direction:** Approve Home plus Pull and Push gameplay as a visual family.
2. **System:** Approve color contrast, typography, board geometry, arrow shape, magnet shape, controls, and accessibility variants.
3. **Flow:** Approve Level Select, tutorial, collision, pause, and completion screens as one clickable prototype.
4. **Handoff:** Export final screen designs, DESIGN.md, component definitions, spacing measurements, interaction notes, and light-mode assets. Begin dark mode only after light mode is signed off.
