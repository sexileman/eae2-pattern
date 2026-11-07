package io.github.sexileman.eae2pattern.mixin;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;
import java.util.List;
import java.util.Set;

/** Probe bytecode only: Forge's ModList and registries are not ready during mixin discovery. */
public final class UniversalMixinPlugin implements IMixinConfigPlugin {
    public static final String GTL = "org.gtlcore.gtlcore.integration.wildcard.";
    private Boolean gtlSupported;
    public static boolean supportedPair(ClassNode activation, ClassNode expansion) {
        return supported("GtlWildcardActivationMixin", activation) && supported("GtlWildcardExpansionMixin", expansion)
                && noUnadaptedLegacyLinks(activation, Set.of())
                && noUnadaptedLegacyLinks(expansion, Set.of("isWildcardPattern(Lnet/minecraft/world/item/ItemStack;)Z",
                    "expandPatterns(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)Ljava/util/List;"));
    }
    private static boolean noUnadaptedLegacyLinks(ClassNode node, Set<String> replaced) {
        for (var field : node.fields) if (field.desc.contains("org/leodreamer/")) return false;
        for (var method : node.methods) {
            if (replaced.contains(method.name + method.desc)) continue;
            if (method.desc.contains("org/leodreamer/")) return false;
            for (var insn : method.instructions) {
                if (insn instanceof MethodInsnNode call && call.owner.startsWith("org/leodreamer/")) return false;
                if (insn instanceof FieldInsnNode field && field.owner.startsWith("org/leodreamer/")) return false;
                if (insn instanceof TypeInsnNode type && type.desc.startsWith("org/leodreamer/")) return false;
            }
        }
        return true;
    }
    public static boolean supported(String mixin, ClassNode node) {
        if (node == null) return false;
        if (mixin.endsWith("GtlWildcardActivationMixin")) {
            return node.fields.stream().anyMatch(f -> f.name.equals("IS_LOADED") && f.desc.equals("Z")
                    && (f.access & (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)) == (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL))
                    && node.methods.stream().filter(m -> m.name.equals("<clinit>")).anyMatch(m -> {
                        for (var instruction : m.instructions)
                            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTSTATIC
                                    && field.owner.equals(node.name) && field.name.equals("IS_LOADED") && field.desc.equals("Z")) return true;
                        return false;
                    });
        }
        if (mixin.endsWith("GtlWildcardExpansionMixin"))
            return has(node, "isWildcardPattern", "(Lnet/minecraft/world/item/ItemStack;)Z")
                    && has(node, "expandPatterns", "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)Ljava/util/List;");
        if (mixin.endsWith(".WildcardExpansionMixin"))
            return has(node, "decodePatterns", "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)Ljava/util/stream/Stream;");
        return true;
    }
    private static boolean has(ClassNode node, String name, String descriptor) {
        return node.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(descriptor) && (m.access & Opcodes.ACC_STATIC) != 0);
    }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (!target.startsWith(GTL) && !target.startsWith("org.leodreamer.wildcard_pattern.")) return true;
        try {
            if (target.startsWith(GTL)) {
                if (gtlSupported == null) {
                    var provider = MixinService.getService().getBytecodeProvider();
                    gtlSupported = supportedPair(provider.getClassNode(GTL + "WildcardPatternCompat", false),
                            provider.getClassNode(GTL + "WildcardPatternCompatImpl", false));
                    if (!gtlSupported) com.mojang.logging.LogUtils.getLogger().warn("EAE2 GTL adapters skipped together: unsupported integration interfaces");
                }
                return gtlSupported;
            }
            ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(target, false);
            boolean apply = supported(mixin, node);
            if (!apply) com.mojang.logging.LogUtils.getLogger().warn("EAE2 optional adapter skipped: unsupported interface {}", target);
            return apply;
        } catch (ClassNotFoundException absent) { return false; }
        catch (java.io.IOException unavailable) {
            com.mojang.logging.LogUtils.getLogger().warn("EAE2 optional adapter unavailable: {}", target, unavailable); return false;
        }
    }
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> other) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
}
