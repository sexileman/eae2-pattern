# 历史归档：4.0.0 的 GTLCore 二进制补丁

**4.1.0 起无需执行本文步骤或替换 GTLCore。请使用通用 eae2-pattern.jar，适配代码已移入该 jar。**

以下保留 4.0.0 的实现记录；相关构建工具已从当前版本移除。旧源码可在提交 [efd175e](https://github.com/sexileman/eae2-pattern/tree/efd175e) 查看。

This patch targets the user's supplied GTLCore 1.2.3.0-fix9 jar, SHA-256 `81b52d5e0621c8eaa0dc55460d8c9be51619889ba6d29ea8bdbda92af96dea3c`. It preserves existing pack-specific fixes by modifying only two integration classes and optional dependency metadata, adding a small isolated bridge. Unsupported input hashes are rejected.

Original metadata points to the deleted `https://github.com/nutant233/GTLCore`; the maintained source reference is https://github.com/GNSW/GTLCore. This is a reproducible binary patch, not a claim that the provided custom binary was rebuilt from that repository. The input declares LGPLv3; its existing license/resources are retained, and this patch includes LGPLv3 text and the complete patch/bridge source.

Changes:

1. `WildcardPatternCompat.IS_LOADED` becomes true if either `eae2_pattern` or `wildcard_pattern` is installed.
2. `WildcardPatternCompatImpl.isWildcardPattern` and `expandPatterns` delegate to the isolated `GtlWildcardBridge`.
3. The bridge uses EAE2's public `MachineWildcardApi` for the independent item, and the original mod's existing APIs for its own item. Both integrations are discovered lazily; neither optional implementation is directly linked in bytecode.
4. Optional EAE2 dependency metadata requests version 4.0.0 or newer. Existing machine IDs, registries, native legacy behavior and all unrelated class/resource contents stay unchanged.

Build: `gradle --offline -I .tools/local-cache.gradle build independentWildcardTest`. The patch source in `src/test/java/io/github/sexileman/eae2pattern/GtlCorePatcher.java` accepts an input mods directory, the reobfuscated support jar, an output path and this project root. The exact input jar is supplied locally and is never overwritten. Output: `build/libs/gtlcore-1.2.3.0-fix9-eae2-wildcard.jar`.

Replace the GTLCore jar in client and server with this output; never install both original and patched GTLCore together. Original Wildcard Pattern may be present or absent. To keep original generic patterns unchanged, retain that mod; the new independent item does not require it.

Runtime validation is still required in the actual pack. In particular test EAE2 only, original only, both, and neither provider; check registration, existing placed wildcard buffers, save/restart, advertised recipes and actual item/fluid dispatch.
