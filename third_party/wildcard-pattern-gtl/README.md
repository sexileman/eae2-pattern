# Wildcard Pattern GTL compatibility core

The `standalone-gtl` distribution embeds this core through Forge Jar-in-Jar. It does not require an external Wildcard Pattern jar. The standard distribution remains unchanged and keeps this dependency optional.

- Upstream: https://github.com/LeoDreamer2004/Wildcard-Pattern
- Author: LeoDreamer; license: MIT (see LICENSE.txt).
- Compatibility baseline: GTL branch, version 0.1.2, GTCEu 1.4.4.
- Input supplied with the user's pack: `[新版GTLcore前置模组通配符样板]wildcard_pattern-0.1.2-gtl-fix-non-gt-material.jar`.
- SHA-256: `d9f69e4f74249af4faef2486ef7ab31cf572699ec6000d6fda5609d99f20ec41`.

The input binary is preserved byte-for-byte, including the pack's non-GT-material compatibility patch. Its patch source was not supplied; this is not described as a reproducible build of that patch. EAE2's new machine binding, generation UI, source validation and expansion override are in this repository's Java sources.

GTLCore 1.2.3.0-fix9 checks `ModList.isLoaded("wildcard_pattern")` and directly links `org.leodreamer.wildcard_pattern.WildcardItems.WILDCARD_PATTERN` and `WildcardPatternLogic.decodePatterns`. Removing that ID or those classes would disable the existing machine or break linking. The internal module intentionally preserves the ID, registry entries, Java interfaces, mixins, assets and old NBT support. Forge's mod list will still show the compatibility module; removing the separate jar does not remove its required compatibility identity.

New bound patterns use EAE2's expansion override. Legacy unbound patterns retain their old behavior; they cannot gain a machine whitelist without regenerating from a real machine category. This artifact is for the supplied GTL/GTCEu 1.4.4 pack. Its original GTCEu version requirement remains intact.

Do not install the external Wildcard Pattern jar alongside the standalone artifact. Do not install EAE2's standard and standalone variants together. Use the standard artifact for non-GT packs.
