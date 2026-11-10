package io.github.sexileman.eae2pattern;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

/** Inspect optional jars without initializing Minecraft, GT, or their registries. Not an in-game test. */
public final class CompatibilityContracts {
    private static int checks;
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message); checks++;
    }
    private static Path jar(Path directory, java.util.function.Predicate<String> name) throws Exception {
        try (var files = Files.list(directory)) {
            return files.filter(p -> name.test(p.getFileName().toString())).findFirst().orElseThrow();
        }
    }
    private static ClassNode read(Path jar, String owner) throws Exception {
        try (var zip = new ZipFile(jar.toFile()); var stream = zip.getInputStream(zip.getEntry(owner + ".class"))) {
            ClassNode node = new ClassNode(); new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG); return node;
        }
    }
    private static boolean calls(ClassNode node, String owner, String name) {
        return node.methods.stream().anyMatch(m -> {
            for (var insn : m.instructions) if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return true;
            return false;
        });
    }
    public static void main(String[] args) throws Exception {
        Path mods = Path.of(args[0]);
        Path treeJar=jar(mods,n->n.startsWith("ae2ct-")&&n.endsWith(".jar"));
        var summary=read(treeJar,"com/neuvillette/ae2ct/mixin/AE2CraftingPlanSummary");
        boolean concreteCast=false;for(var method:summary.methods)for(var instruction:method.instructions)
            if(instruction instanceof TypeInsnNode type&&type.getOpcode()==org.objectweb.asm.Opcodes.CHECKCAST&&type.desc.equals("appeng/crafting/CraftingPlan"))concreteCast=true;
        check(concreteCast,"installed AE2CT requires a concrete native plan in its preview summary");
        try(var treeLoader=new java.net.URLClassLoader(new java.net.URL[]{treeJar.toUri().toURL()},CompatibilityContracts.class.getClassLoader())){
            var helper=treeLoader.loadClass("com.neuvillette.ae2ct.api.RecipeHelper");
            var metadata=new io.github.sexileman.eae2pattern.cycle.GrowthCraftingPlan(null,42,new appeng.api.stacks.KeyCounter(),java.util.Map.of(),java.util.List.of(),java.util.List.of());
            Object display=helper.getMethod("fromCraftingPlan",appeng.crafting.CraftingPlan.class).invoke(null,metadata.nativePlan());
            check(display!=null&&helper.isInstance(display),"real installed AE2CT summary builder accepts the native experimental plan without ClassCastException");
        }
        Path wildcard = args.length > 1 ? Path.of(args[1])
                : jar(mods, n -> n.toLowerCase().contains("wildcard_pattern") && n.endsWith(".jar"));
        String logic = "org/leodreamer/wildcard_pattern/wildcard/WildcardPatternLogic";
        var node = read(wildcard, logic);
        check(node.methods.stream().anyMatch(m -> m.name.equals("decodePatterns") && m.desc.equals("(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)Ljava/util/stream/Stream;") && (m.access & 8) != 0), "native static wildcard expansion descriptor changed");
        check(calls(read(wildcard, "org/leodreamer/wildcard_pattern/mixin/PatternProviderLogicMixin"), logic, "decodePatterns"), "ordinary AE2 provider must call hooked expansion");
        Path gtl = jar(mods, n -> n.contains("gtlcore-1") && n.endsWith(".jar"));
        check(calls(read(gtl, "org/gtlcore/gtlcore/integration/wildcard/WildcardPatternCompatImpl"), logic, "decodePatterns"), "GTL wildcard buffer must call the same expansion");
        var bufferBase=read(gtl,"org/gtlcore/gtlcore/common/machine/multiblock/part/ae/MEPatternBufferPartMachineBase");
        var bufferPush=bufferBase.methods.stream().filter(m->m.name.equals("pushPattern")&&m.desc.equals("(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z")).findFirst().orElseThrow();
        boolean originalLookup=false,bulkInput=false;
        for(var insn:bufferPush.instructions)if(insn instanceof MethodInsnNode call){
            originalLookup|=call.name.equals("getSlotIndexForPattern");
            bulkInput|=call.name.equals("pushPattern")&&call.desc.equals("([Lappeng/api/stacks/KeyCounter;)V");
        }
        check(originalLookup&&bulkInput,"installed GTL buffer looks up original pattern then accepts complete bulk counters");
        for(String type:java.util.List.of("org/gtlcore/gtlcore/common/machine/multiblock/part/ae/MEPatternBufferPartMachine","org/gtlcore/gtlcore/integration/wildcard/MEWildcardPatternBufferPartMachine")){
            var concrete=read(gtl,type);
            check(concrete.methods.stream().noneMatch(m->m.name.equals("pushPattern")),"GTL normal/wildcard buffers inherit the inspected bulk transport: "+type);
        }
        var nativeCpu=read(gtl,"org/gtlcore/gtlcore/mixin/ae2/logic/CraftingCpuLogicMixin");
        check(calls(nativeCpu,"org/gtlcore/gtlcore/integration/ae2/AEUtils","extractForProcessingPattern")&&calls(nativeCpu,"org/gtlcore/gtlcore/integration/ae2/crafting/CraftingPatternPower","forCpu"),"native GTL CPU uses bulk extraction and its dedicated energy rule");
        try(var gtlLoader=new java.net.URLClassLoader(new java.net.URL[]{gtl.toUri().toURL()},CompatibilityContracts.class.getClassLoader())){
            var power=gtlLoader.loadClass("org.gtlcore.gtlcore.integration.ae2.crafting.CraftingPatternPower").getMethod("forCpu",double.class,boolean.class,long.class);
            check((Double)power.invoke(null,10000d,true,10000L)==1d,"real GTL bulk energy does not collapse 10000 affordable crafts to single craft");
            check((Double)power.invoke(null,10000d,false,10000L)==10000d,"GTL energy rule leaves ordinary providers unchanged");
        }
        Path gt = jar(mods, n -> n.startsWith("gtceu-") && n.endsWith(".jar"));
        check(read(gt, "com/gregtechceu/gtceu/api/recipe/GTRecipeSerializer").fields.stream()
                .anyMatch(f -> f.name.equals("CODEC") && f.desc.equals("Lcom/mojang/serialization/Codec;") && (f.access & 9) == 9),
                "GT serializer must expose a public codec for stable restart fingerprints");
        var gtRecipe=read(gt,"com/gregtechceu/gtceu/api/recipe/GTRecipe");
        for(String field:new String[]{"inputs","outputs","tickInputs","tickOutputs"})check(gtRecipe.fields.stream().anyMatch(f->f.name.equals(field)&&(f.access&1)!=0),"dynamic GT source map is public: "+field);
        var content=read(gt,"com/gregtechceu/gtceu/api/recipe/content/Content");
        for(String field:new String[]{"content","chance","maxChance"})check(content.fields.stream().anyMatch(f->f.name.equals(field)&&(f.access&1)!=0),"dynamic GT content contract: "+field);
        check(read(gt,"com/gregtechceu/gtceu/api/recipe/ingredient/SizedIngredient").methods.stream().anyMatch(m->m.name.equals("getAmount")&&m.desc.equals("()I")),"dynamic sized item amount accessor exists");
        check(read(gt,"com/gregtechceu/gtceu/api/recipe/ingredient/FluidIngredient").methods.stream().anyMatch(m->m.name.equals("getStacks")&&m.desc.equals("()[Lcom/lowdragmc/lowdraglib/side/fluid/FluidStack;")),"dynamic fluid alternatives accessor exists");
        var chemical=read(gt,"com/gregtechceu/gtceu/api/data/chemical/ChemicalHelper");
        check(chemical.methods.stream().anyMatch(m->m.name.equals("getPrefix")&&m.desc.equals("(Lnet/minecraft/world/level/ItemLike;)Lcom/gregtechceu/gtceu/api/data/tag/TagPrefix;")),"GT form recognition uses the public unification prefix API");
        check(chemical.methods.stream().anyMatch(m->m.name.equals("getMaterial")&&m.desc.equals("(Lnet/minecraft/world/item/ItemStack;)Lcom/gregtechceu/gtceu/api/data/chemical/material/stack/MaterialStack;")),"GT material picker uses public stack material API");
        check(read(gt,"com/gregtechceu/gtceu/api/data/tag/TagPrefix").methods.stream().anyMatch(m->m.name.equals("name")&&m.desc.equals("()Ljava/lang/String;")),"GT prefix identity accessor exists");
        var material=read(gt,"com/gregtechceu/gtceu/api/data/chemical/material/Material");
        check(material.methods.stream().anyMatch(m->m.name.equals("getResourceLocation")&&m.desc.equals("()Lnet/minecraft/resources/ResourceLocation;")),"material picker uses the actual namespaced GT material identity");
        check(material.methods.stream().anyMatch(m->m.name.equals("getLocalizedName")&&m.desc.equals("()Lnet/minecraft/network/chat/MutableComponent;")),"material picker uses GT localized names across namespaces");
        var gtlBuffer=read(gtl,"org/gtlcore/gtlcore/integration/wildcard/MEWildcardPatternBufferPartMachine");
        var update=gtlBuffer.methods.stream().filter(m->m.name.equals("update")&&m.desc.equals("()V")).findFirst().orElseThrow();
        boolean hotExpansion=false;for(var insn:update.instructions)if(insn instanceof MethodInsnNode call&&call.name.equals("expandPatterns"))hotExpansion=true;
        check(!hotExpansion,"installed GTL buffer does not call wildcard expansion directly from its per-tick update");
        Path extended = jar(mods, n -> n.startsWith("ExtendedAE-") && n.endsWith(".jar"));
        for (String name : new String[]{"common/tileentities/TileExPatternProvider", "common/parts/PartExPatternProvider"}) {
            var provider = read(extended, "com/glodblock/github/extendedae/" + name);
            boolean baseLogic = provider.methods.stream().anyMatch(m -> {
                for (var insn : m.instructions) if (insn instanceof TypeInsnNode type && type.desc.equals("appeng/helpers/patternprovider/PatternProviderLogic")) return true;
                return false;
            });
            check(baseLogic, "ExtendedAE provider must use standard AE2 provider logic: " + name);
        }
        Path jei=jar(mods,n->n.startsWith("jei-")&&n.endsWith(".jar"));
        var guiHandlers=read(jei,"mezz/jei/api/registration/IGuiHandlerRegistration");
        check(guiHandlers.methods.stream().anyMatch(m->m.name.equals("addGuiScreenHandler")&&m.desc.equals("(Ljava/lang/Class;Lmezz/jei/api/gui/handlers/IScreenHandler;)V")),"installed JEI supports custom screen footprints");
        check(guiHandlers.methods.stream().anyMatch(m->m.name.equals("addGhostIngredientHandler")&&m.desc.equals("(Ljava/lang/Class;Lmezz/jei/api/gui/handlers/IGhostIngredientHandler;)V")),"installed JEI supports ghost ingredient targets");
        check(read(jei,"mezz/jei/api/gui/handlers/IGlobalGuiHandler").methods.stream().anyMatch(m->m.name.equals("getClickableIngredientUnderMouse")&&m.desc.equals("(DD)Ljava/util/Optional;")),"installed JEI supports tool ingredient bookmark/focus keys");
        check(read(jei,"mezz/jei/api/runtime/IIngredientManager").methods.stream().anyMatch(m->m.name.equals("createClickableIngredient")&&m.desc.equals("(Lmezz/jei/api/ingredients/IIngredientType;Ljava/lang/Object;Lnet/minecraft/client/renderer/Rect2i;Z)Ljava/util/Optional;")),"installed JEI accepts typed item and fluid ingredient regions");
        var wrapper=read(jei,"mezz/jei/gui/input/GuiContainerWrapper");boolean preservesClick=false;
        for(var method:wrapper.methods)for(var insn:method.instructions)if(insn instanceof MethodInsnNode call&&call.owner.equals("mezz/jei/gui/input/ClickableIngredientInternal")&&call.name.equals("<init>")){
            var last=insn.getPrevious();while(last!=null&&last.getOpcode()<0)last=last.getPrevious();
            var previous=last==null?null:last.getPrevious();while(previous!=null&&previous.getOpcode()<0)previous=previous.getPrevious();
            preservesClick=last!=null&&previous!=null&&last.getOpcode()==org.objectweb.asm.Opcodes.ICONST_0&&previous.getOpcode()==org.objectweb.asm.Opcodes.ICONST_0;
        }
        check(preservesClick,"native JEI plugin ingredients preserve the tool's normal left-click behavior");
        Path ae=jar(mods,n->n.toLowerCase().startsWith("appliedenergistics2")&&n.endsWith(".jar"));
        try(var aeZip=new ZipFile(ae.toFile())){check(aeZip.getEntry("assets/ae2/textures/guis/background.png")!=null,"installed AE2 provides the native panel texture");}
        Path jec;
        try(var files=Files.list(mods)){jec=files.filter(p->p.getFileName().toString().contains("jecharacters-")&&p.toString().endsWith(".jar")).findFirst().orElse(null);}
        if(jec!=null)try(var loader=new java.net.URLClassLoader(new java.net.URL[]{jec.toUri().toURL()},CompatibilityContracts.class.getClassLoader())){
            var nativeMatch=read(jec,"me/towdium/jecharacters/utils/Match");
            check(nativeMatch.methods.stream().anyMatch(m->m.name.equals("contains")&&m.desc.equals("(Ljava/lang/String;Ljava/lang/CharSequence;)Z")&&(m.access&9)==9),"optional JEC exposes its native shared-context match API");
            var search=io.github.sexileman.eae2pattern.client.OptionalPinyinSearch.bind(loader);
            check(search.test("铁锭","tieding"),"actual installed JEC accepts full pinyin");
            check(search.test("铁锭","td"),"actual installed JEC accepts pinyin initials");
            check(search.test("卷板机","juanbanji"),"actual installed JEC matches machine names");
            check(search.test("卷板机","jbj"),"actual installed JEC matches machine initials");
            check(search.test("铁板","TB"),"actual JEC bridge accepts uppercase initials");
            check(!search.test("铁板","tongding"),"JEC bridge rejects unrelated pinyin");
            check(search.test("铁板","铁板"),"literal Chinese remains valid with JEC");
        }
        System.out.println("Passed " + checks + " installed-mod interface and optional JEC matching checks (not a game runtime test)");
    }
}
