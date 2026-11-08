# EAE2 Pattern artwork

Original 32px pixel-grid card artwork, licensed under the repository MIT license. No AE2 texture is copied.

- `machine_wildcard_pattern.svg`: blank card source.
- `machine_wildcard_pattern_encoded.svg`: neutral frame and muted rose wildcard mark for encoded cards.
- `icon-preview.png`: blank and encoded slanted-card preview, enlarged with nearest-neighbor sampling.

Game textures are 32×32 RGBA PNGs in `src/main/resources/assets/eae2_pattern/textures/item/`. The 256×256 mod logo remains a separate branding asset. The JEI launcher uses the actual blank wildcard item, and the item model switches to the encoded artwork through the client-only `eae2_pattern:encoded` predicate.

The slanted thin-card silhouette follows the visual language of the AE2 blank pattern. These SVG coordinates and pixels are newly authored; no AE2 texture is embedded. The mod logo is a separate branding asset.

Version 6.5.6 uses a 16px logical grid exported at 2× nearest-neighbor scale. Both states share the neutral casing; state color is confined to the small inlay.
