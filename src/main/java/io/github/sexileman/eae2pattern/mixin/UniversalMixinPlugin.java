package io.github.sexileman.eae2pattern.mixin;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import java.util.List;
import java.util.Set;

/** Probe bytecode only: Forge's ModList and registries are not ready during mixin discovery. */
public final class UniversalMixinPlugin implements IMixinConfigPlugin {
    public static final String GTL = "org.gtlcore.gtlcore.integration.wildcard.";
    private final java.util.function.Supplier<IClassBytecodeProvider> bytecode;
    private Boolean gtlSupported, cyclesSupported, growthSupported;
    public UniversalMixinPlugin() { this(() -> MixinService.getService().getBytecodeProvider()); }
    public UniversalMixinPlugin(java.util.function.Supplier<IClassBytecodeProvider> bytecode) { this.bytecode = bytecode; }
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
    public static boolean supportedCycles(ClassNode node,ClassNode process,ClassNode calculation) {
        if(node==null||process==null||calculation==null)return false;
        if(!method(node,"buildChildPatterns","()V")||!method(process,"notRecursive","(Lappeng/api/crafting/IPatternDetails;)Z")
                ||!method(calculation,"run","()Lappeng/api/networking/crafting/ICraftingPlan;"))return false;
        if(!field(node,"what","Lappeng/api/stacks/AEKey;")||!field(node,"parent","Lappeng/crafting/CraftingTreeProcess;")||!field(node,"job","Lappeng/crafting/CraftingCalculation;")
                ||!field(process,"parent","Lappeng/crafting/CraftingTreeNode;")||!field(process,"details","Lappeng/api/crafting/IPatternDetails;")
                ||!field(calculation,"networkInv","Lappeng/crafting/inv/NetworkCraftingSimulationState;")||!field(calculation,"level","Lnet/minecraft/world/level/Level;")||!field(calculation,"simRequester","Lappeng/api/networking/crafting/ICraftingSimulationRequester;"))return false;
        int calls=0;
        for(var m:node.methods)if(m.name.equals("buildChildPatterns"))for(var insn:m.instructions)
            if(insn instanceof MethodInsnNode call&&call.owner.equals("appeng/crafting/CraftingTreeProcess")&&call.name.equals("notRecursive")&&call.desc.equals("(Lappeng/api/crafting/IPatternDetails;)Z"))calls++;
        return calls==1;
    }
    private static boolean method(ClassNode n,String name,String desc){return n.methods.stream().anyMatch(m->m.name.equals(name)&&m.desc.equals(desc));}
    private static boolean field(ClassNode n,String name,String desc){return n.fields.stream().anyMatch(f->f.name.equals(name)&&f.desc.equals(desc));}
    private static boolean has(ClassNode node, String name, String descriptor) {
        return node.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(descriptor) && (m.access & Opcodes.ACC_STATIC) != 0);
    }
    public static boolean supportedGrowth(ClassNode cpu,ClassNode job,ClassNode progress,ClassNode tracker){
        if(cpu==null||job==null||progress==null||tracker==null)return false;
        return field(cpu,"inventory","Lappeng/crafting/inv/ListCraftingInventory;")&&field(cpu,"job","Lappeng/crafting/execution/ExecutingCraftingJob;")&&field(cpu,"cluster","Lappeng/me/cluster/implementations/CraftingCPUCluster;")
            &&method(cpu,"executeCrafting","(ILappeng/me/service/CraftingService;Lappeng/api/networking/energy/IEnergyService;Lnet/minecraft/world/level/Level;)I")
            &&method(cpu,"insert","(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;)J")&&method(cpu,"finishJob","(Z)V")
            &&method(cpu,"trySubmitJob","(Lappeng/api/networking/IGrid;Lappeng/api/networking/crafting/ICraftingPlan;Lappeng/api/networking/security/IActionSource;Lappeng/api/networking/crafting/ICraftingRequester;)Lappeng/api/networking/crafting/ICraftingSubmitResult;")
            &&method(cpu,"writeToNBT","(Lnet/minecraft/nbt/CompoundTag;)V")&&method(cpu,"readFromNBT","(Lnet/minecraft/nbt/CompoundTag;)V")
            &&field(job,"waitingFor","Lappeng/crafting/inv/ListCraftingInventory;")&&field(job,"tasks","Ljava/util/Map;")&&field(job,"timeTracker","Lappeng/crafting/execution/ElapsedTimeTracker;")
            &&field(job,"link","Lappeng/crafting/CraftingLink;")&&field(job,"finalOutput","Lappeng/api/stacks/GenericStack;")&&field(job,"remainingAmount","J")
            &&field(progress,"value","J")&&method(tracker,"decrementItems","(JLappeng/api/stacks/AEKeyType;)V");
    }
    public static boolean supportedGrowthPlan(ClassNode plan){
        return plan!=null&&method(plan,"<init>","(Lappeng/api/stacks/GenericStack;JZZLappeng/api/stacks/KeyCounter;Lappeng/api/stacks/KeyCounter;Lappeng/api/stacks/KeyCounter;Ljava/util/Map;)V")
            &&method(plan,"finalOutput","()Lappeng/api/stacks/GenericStack;")&&method(plan,"patternTimes","()Ljava/util/Map;");
    }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if(mixin.endsWith("GrowthPlanMixin")||mixin.endsWith("GrowthCpuMixin")||mixin.endsWith("GrowthJobAccessor")||mixin.endsWith("GrowthProgressAccessor")||mixin.endsWith("GrowthTrackerInvoker")){
            try{
                if(growthSupported==null){var provider=bytecode.get();growthSupported=supportedGrowthPlan(provider.getClassNode("appeng.crafting.CraftingPlan"))&&supportedGrowth(provider.getClassNode("appeng.crafting.execution.CraftingCpuLogic"),provider.getClassNode("appeng.crafting.execution.ExecutingCraftingJob"),provider.getClassNode("appeng.crafting.execution.ExecutingCraftingJob$TaskProgress"),provider.getClassNode("appeng.crafting.execution.ElapsedTimeTracker"));
                    io.github.sexileman.eae2pattern.cycle.CycleRuntimeGate.supported=growthSupported;
                    if(!growthSupported)com.mojang.logging.LogUtils.getLogger().warn("Experimental cycle execution disabled together: unsupported AE2 CPU shape");
                }return growthSupported;
            }catch(ClassNotFoundException|java.io.IOException|RuntimeException|LinkageError unsupported){growthSupported=false;io.github.sexileman.eae2pattern.cycle.CycleRuntimeGate.supported=false;return false;}
        }
        if(mixin.endsWith("CraftingCycleNodeMixin")||mixin.endsWith("CraftingCycleCalculationMixin")||mixin.endsWith("CycleProcessAccessor")) {
            try {
                if(cyclesSupported==null){var provider=bytecode.get();cyclesSupported=supportedCycles(provider.getClassNode("appeng.crafting.CraftingTreeNode"),provider.getClassNode("appeng.crafting.CraftingTreeProcess"),provider.getClassNode("appeng.crafting.CraftingCalculation"));
                    if(!cyclesSupported)com.mojang.logging.LogUtils.getLogger().warn("EAE2 cycle diagnostics disabled together: unsupported AE2 planner interfaces");}
                return cyclesSupported;
            }catch(ClassNotFoundException|java.io.IOException|RuntimeException|LinkageError unsupported){cyclesSupported=false;com.mojang.logging.LogUtils.getLogger().warn("EAE2 cycle diagnostics unavailable",unsupported);return false;}
        }
        if (!target.startsWith(GTL) && !target.startsWith("org.leodreamer.wildcard_pattern.")) return true;
        try {
            // ModLauncher's provider rejects runTransformers=false before checking whether a class
            // exists. The supported single-argument overload requests its normal transformed view.
            var provider = bytecode.get();
            if (target.startsWith(GTL)) {
                if (gtlSupported == null) {
                    gtlSupported = supportedPair(provider.getClassNode(GTL + "WildcardPatternCompat"),
                            provider.getClassNode(GTL + "WildcardPatternCompatImpl"));
                    if (!gtlSupported) com.mojang.logging.LogUtils.getLogger().warn("EAE2 GTL adapters skipped together: unsupported integration interfaces");
                }
                return gtlSupported;
            }
            ClassNode node = provider.getClassNode(target);
            boolean apply = supported(mixin, node);
            if (!apply) com.mojang.logging.LogUtils.getLogger().warn("EAE2 optional adapter skipped: unsupported interface {}", target);
            return apply;
        } catch (ClassNotFoundException absent) {
            if (target.startsWith(GTL)) gtlSupported = false;
            return false;
        } catch (java.io.IOException | IllegalArgumentException | IllegalStateException | LinkageError unavailable) {
            if (target.startsWith(GTL)) gtlSupported = false;
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
