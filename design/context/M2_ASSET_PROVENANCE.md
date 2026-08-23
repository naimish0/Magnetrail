# Magnetrail M2 asset provenance

## Typography

No Manrope font file or redistribution license is present in this repository.
M2 therefore uses Android's local system sans-serif family and does not fetch a
runtime font. A future bundled Manrope asset must include its license before the
theme is changed.

## Board artwork

The Rail Dart, magnet split-ring core, directional field rings, trails, impact
ring, magnetic completion ripple, and geometric particles are original shapes
drawn in project-owned Compose Canvas code. They do not incorporate competitor
artwork, third-party vectors, emoji, or Material arrow icons.

## Sound

M2 sound effects are original procedural PCM cues defined locally in
`AndroidFeedback.kt`. Each short cue is synthesized from a frequency sweep,
envelope, and optional harmonic at playback time. There are no downloaded,
third-party, or remotely loaded audio files. Music is not shipped, so no Music
toggle is shown.
