# EAE2 Pattern artwork

Original 32px pixel-grid card artwork, licensed under the repository MIT license. No AE2 texture is copied.

- `machine_wildcard_pattern.svg`: blank card source.
- `machine_wildcard_pattern_encoded.svg`: lit core and branching traces for encoded cards.
- `icon-preview.png`: blank, encoded and mod-logo preview, enlarged with nearest-neighbor sampling.

Game textures are 32×32 RGBA PNGs in `src/main/resources/assets/eae2_pattern/textures/item/`. The 256×256 mod logo places the encoded icon on a muted purple badge. The JEI launcher uses the actual blank wildcard item, and the item model switches to the encoded artwork through the client-only `eae2_pattern:encoded` predicate.
