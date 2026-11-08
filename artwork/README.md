# EAE2 Pattern artwork

Version 6.5.7 references `ae2:item/blank_pattern` directly as layer0 in both wildcard item models. AE2 supplies the original silhouette, bevels and palette; no AE2 texture is copied into this mod. Resource packs can replace that base normally.

The two SVG sources and corresponding 16×16 RGBA PNGs contain only a five-pixel wildcard inlay. Blank uses lavender-gray; encoded uses rose-magenta. Pixels are fully opaque or fully transparent, without glow, antialiasing or a scaling pass. Layer1 uses this mod's own namespace. Resource packs that substantially rearrange the card face may need a matching overlay replacement.

`icon-preview.png` and `wildcard-colors-preview.png` show only the independently authored overlays, enlarged with nearest-neighbor sampling. The existing mod logo is a separate branding asset.
