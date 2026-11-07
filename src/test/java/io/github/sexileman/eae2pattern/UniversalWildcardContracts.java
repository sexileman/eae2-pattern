package io.github.sexileman.eae2pattern;

import io.github.sexileman.eae2pattern.mixin.UniversalMixinPlugin;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import javax.tools.ToolProvider;

/** Universal artifact/signature checks and isolated routing with optional providers absent/present. */
public final class UniversalWildcardContracts {
    private static int checks;
    private static final String BRIDGE = "io/github/sexileman/eae2pattern/compat/gtl/GtlWildcardBridge";
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    private static byte[] bytes(ZipFile zip, String path) throws Exception {
        var entry = zip.getEntry(path); if (entry == null) throw new AssertionError("Missing " + path);
        try (var stream = zip.getInputStream(entry)) { return stream.readAllBytes(); }
    }
    private static ClassNode node(byte[] bytes) { var n = new ClassNode(); new ClassReader(bytes).accept(n, 0); return n; }
    public static void main(String[] args) throws Exception {
        Path own = Path.of(args[0]), mods = Path.of(args[1]);
        byte[] bridge;
        try (var app = new ZipFile(own.toFile())) {
            check(Collections.list(app.entries()).stream().noneMatch(e -> e.getName().endsWith(".jar") || e.getName().startsWith("org/leodreamer/") || e.getName().startsWith("org/gtlcore/")), "universal jar embeds neither original wildcard nor GTL classes");
            bridge = bytes(app, BRIDGE + ".class");
            check(app.getEntry("io/github/sexileman/eae2pattern/api/MachineWildcardApi.class") != null, "own independent API shipped");
            String config = new String(bytes(app, "eae2_pattern.mixins.json"), java.nio.charset.StandardCharsets.UTF_8);
            check(config.contains("UniversalMixinPlugin") && config.contains("GtlWildcardActivationMixin") && config.contains("GtlWildcardExpansionMixin"), "optional GTL discovery and both adapters are configured");
            check(app.getEntry("assets/eae2_pattern/models/item/machine_wildcard_pattern.json") != null, "independent item resources shipped");
            var recipe = com.google.gson.JsonParser.parseString(new String(bytes(app, "data/eae2_pattern/recipes/machine_wildcard_pattern.json"), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            check(recipe.get("type").getAsString().equals("minecraft:crafting_shapeless"), "blank wildcard uses a vanilla universal crafting recipe");
            var ingredients = recipe.getAsJsonArray("ingredients");
            check(ingredients.size() == 1 && ingredients.get(0).getAsJsonObject().get("item").getAsString().equals("ae2:blank_pattern"), "carrier recipe needs only the mandatory AE2 blank item");
            check(recipe.getAsJsonObject("result").get("item").getAsString().equals("eae2_pattern:machine_wildcard_pattern")
                    && recipe.getAsJsonObject("result").get("count").getAsInt() == 1, "recipe yields exactly one independent blank carrier");
            check(!recipe.toString().contains("wildcard_pattern:wildcard_pattern") && !recipe.toString().contains("gtceu:"), "crafting does not require either optional mod");
            check(app.getEntry("data/eae2_pattern/advancements/recipes/misc/machine_wildcard_pattern.json") != null, "vanilla recipe-book unlock is shipped");
            var reset=com.google.gson.JsonParser.parseString(new String(bytes(app,"data/eae2_pattern/recipes/machine_wildcard_pattern_reset.json"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            check(reset.get("type").getAsString().equals("minecraft:crafting_shapeless"),"reset uses standard crafting for workbench and AE crafting grids");
            check(reset.getAsJsonArray("ingredients").size()==1&&reset.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString().equals("eae2_pattern:machine_wildcard_pattern"),"reset consumes one carrier without an NBT-specific ingredient restriction");
            var blank=reset.getAsJsonObject("result");
            check(blank.get("item").getAsString().equals("eae2_pattern:machine_wildcard_pattern")&&blank.get("count").getAsInt()==1&&blank.keySet().equals(Set.of("item","count")),"reset returns one fresh NBT-free carrier, never copies source data or multiplies items");
            check(app.getEntry("data/eae2_pattern/advancements/recipes/misc/machine_wildcard_pattern_reset.json")!=null,"reset recipe-book unlock is shipped");
            check(app.getEntry("io/github/sexileman/eae2pattern/client/OptionalPinyinSearch.class")!=null,"optional native JEC bridge is shipped");
            check(Collections.list(app.entries()).stream().noneMatch(e->e.getName().startsWith("me/towdium/")),"JEC and pinyin library are not bundled into the universal jar");
            for(String name:List.of("DynamicRule","PortableRuleQuery","RuleRecipe","ServerRecipeReader","WildcardRuntime","MaterialForms","WeightedCache")){
                var binary=bytes(app,"io/github/sexileman/eae2pattern/wildcard/"+name+".class");
                check(!new String(binary,java.nio.charset.StandardCharsets.ISO_8859_1).contains("net/minecraft/client/"),"dedicated-server dynamic core has no client class link: "+name);
            }
            check(app.getEntry("io/github/sexileman/eae2pattern/client/WildcardChoiceScreen.class")!=null,"searchable form/material/tag picker shipped");
            var writerJob=node(bytes(app,"io/github/sexileman/eae2pattern/net/server/MachineWildcardWriter$Job.class"));
            var dynamic=writerJob.methods.stream().filter(m->m.name.equals("dynamicStep")).findFirst().orElseThrow();
            boolean levelEncoding=false,playerEncoding=false;for(var insn:dynamic.instructions)if(insn instanceof MethodInsnNode call&&call.owner.equals("io/github/sexileman/eae2pattern/pattern/PatternEncoder")&&call.name.equals("encode")){levelEncoding|=call.desc.contains("/Level;");playerEncoding|=call.desc.contains("/ServerPlayer;");}
            check(levelEncoding&&!playerEncoding,"dynamic prewarming uses the same player-independent child definitions as reload resolution");
            var runtime=node(bytes(app,"io/github/sexileman/eae2pattern/wildcard/WildcardRuntime.class"));
            check(runtime.methods.stream().anyMatch(m->m.name.equals("clear"))&&runtime.methods.stream().anyMatch(m->m.name.equals("prime")),"runtime supports reload invalidation and generation prewarming");
            var items = node(bytes(app, "io/github/sexileman/eae2pattern/wildcard/WildcardItems.class"));
            check(node(bytes(app,"io/github/sexileman/eae2pattern/wildcard/WildcardItems$1.class")).methods.stream().anyMatch(m->m.name.equals("getMaxStackSize")&&m.desc.equals("(Lnet/minecraft/world/item/ItemStack;)I")),"per-stack Forge limit distinguishes blank and NBT carriers");
            check(items.methods.stream().anyMatch(m -> m.name.equals("creative")), "blank carrier has a creative-tab acquisition entry");
            var jei = node(bytes(app, "io/github/sexileman/eae2pattern/compat/jei/EAE2PatternJeiPlugin.class"));
            check(jei.methods.stream().anyMatch(m -> m.name.equals("registerRecipes")), "JEI usage information is registered");
            var writer = node(bytes(app, "io/github/sexileman/eae2pattern/net/server/MachineWildcardWriter.class"));
            var heldStart = writer.methods.stream().filter(m -> m.name.equals("startHeld")).findFirst().orElseThrow();
            boolean needsNetwork = false;
            for (var insn : heldStart.instructions) if (insn instanceof MethodInsnNode call && call.owner.endsWith("/NetworkAccess")) needsNetwork = true;
            check(!needsNetwork, "handheld configuration has no ME access precondition");
            for (String name : List.of("MachineWildcardProviderMixin", "GtlWildcardActivationMixin", "GtlWildcardExpansionMixin"))
                check(app.getEntry("io/github/sexileman/eae2pattern/mixin/" + name + ".class") != null, "universal hook exists: " + name);
            var expansion = node(bytes(app, "io/github/sexileman/eae2pattern/mixin/GtlWildcardExpansionMixin.class"));
            check(expansion.methods.stream().anyMatch(m -> m.name.equals("isWildcardPattern") && m.desc.equals("(Lnet/minecraft/world/item/ItemStack;)Z")), "recognition overwrite uses native descriptor");
            check(expansion.methods.stream().anyMatch(m -> m.name.equals("expandPatterns") && m.desc.equals("(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)Ljava/util/List;")), "expansion overwrite uses native descriptor");
        }
        Path original;
        try (var files = Files.list(mods)) { original = files.filter(p -> p.toString().contains("gtlcore-1") && p.toString().endsWith(".jar")).findFirst().orElseThrow(); }
        String prefix = "org/gtlcore/gtlcore/integration/wildcard/";
        try (var gtl = new ZipFile(original.toFile())) {
            var activation = node(bytes(gtl, prefix + "WildcardPatternCompat.class"));
            var expansion = node(bytes(gtl, prefix + "WildcardPatternCompatImpl.class"));
            launcherProbe(Map.of((prefix + "WildcardPatternCompat").replace('/', '.'), bytes(gtl, prefix + "WildcardPatternCompat.class"),
                    (prefix + "WildcardPatternCompatImpl").replace('/', '.'), bytes(gtl, prefix + "WildcardPatternCompatImpl.class")));
            check(UniversalMixinPlugin.supportedPair(activation, expansion), "unmodified pack GTL supports automatic adapters");
            check(!UniversalMixinPlugin.supportedPair(null, null), "absent GTL loads no GTL adapter");
            check(!UniversalMixinPlugin.supportedPair(activation, null), "missing expansion disables activation too");
            check(!UniversalMixinPlugin.supportedPair(null, expansion), "missing activation disables expansion too");
            var changed = node(bytes(gtl, prefix + "WildcardPatternCompatImpl.class"));
            changed.methods.removeIf(m -> m.name.equals("expandPatterns"));
            check(!UniversalMixinPlugin.supportedPair(activation, changed), "unsupported GTL API disables the adapter pair safely");
            var noAssignment = node(bytes(gtl, prefix + "WildcardPatternCompat.class"));
            noAssignment.methods.stream().filter(m -> m.name.equals("<clinit>")).forEach(m -> m.instructions.clear());
            check(!UniversalMixinPlugin.supportedPair(noAssignment, expansion), "activation requires its exact field assignment hook");
            var hiddenLink = node(bytes(gtl, prefix + "WildcardPatternCompatImpl.class"));
            var unsafe = new MethodNode(Opcodes.ACC_STATIC, "newUnadaptedLegacyDependency", "()V", null, null);
            unsafe.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "org/leodreamer/wildcard_pattern/OtherApi", "call", "()V", false));
            unsafe.instructions.add(new InsnNode(Opcodes.RETURN)); hiddenLink.methods.add(unsafe);
            check(!UniversalMixinPlugin.supportedPair(activation, hiddenLink), "unknown legacy-only dependency disables optional adapters");
            unsafe.name = "expandPatterns";
            check(!UniversalMixinPlugin.supportedPair(activation, hiddenLink), "an unadapted overload must not bypass the signature gate");
            var previous = own.getParent().resolve("gtlcore-1.2.3.0-fix9-eae2-wildcard.jar");
            if (Files.isRegularFile(previous)) try (var patched = new ZipFile(previous.toFile())) {
                check(UniversalMixinPlugin.supportedPair(node(bytes(patched, prefix + "WildcardPatternCompat.class")), node(bytes(patched, prefix + "WildcardPatternCompatImpl.class"))), "previously supplied GTL patch also accepts the universal adapters");
            }
        }
        Path base = own.getParent().resolve("universal-contracts-" + UUID.randomUUID()); Files.createDirectories(base);
        for (boolean a : new boolean[]{false, true}) for (boolean b : new boolean[]{false, true}) scenario(base, bridge, a, b);
        System.out.println("Passed " + checks + " universal packaging, optional-interface and provider-routing checks. No actual Forge world was launched.");
    }
    private static void launcherProbe(Map<String, byte[]> input) throws Exception {
        // Use the actual Mixin 0.8.5 ModLauncher provider which rejected false in the crash report.
        // Only its transformer-loader callback is supplied by the test; no game or registry starts.
        var provider = (org.spongepowered.asm.service.IClassBytecodeProvider) Class.forName("org.spongepowered.asm.launch.MixinLaunchPluginLegacy")
                .getConstructor().newInstance();
        var field = provider.getClass().getDeclaredField("transformerLoader"); field.setAccessible(true);
        Map<String, byte[]> fixtures = new HashMap<>(input); int[] calls = {0};
        Object transformer = java.lang.reflect.Proxy.newProxyInstance(field.getType().getClassLoader(), new Class<?>[]{field.getType()}, (proxy, method, args) -> {
            if (method.getName().equals("buildTransformedClassNodeFor")) { calls[0]++; return fixtures.get((String) args[0]); }
            if (method.getName().equals("toString")) return "FixtureTransformerLoader";
            return null;
        });
        field.set(provider, transformer);
        boolean reproduced = false;
        try { provider.getClassNode("org.leodreamer.wildcard_pattern.wildcard.WildcardPatternLogic", false); }
        catch (IllegalArgumentException expected) { reproduced = expected.getMessage().contains("untransformed bytecode"); }
        check(reproduced, "actual ModLauncher provider reproduces the reported 4.1.0 failure");
        var plugin = new UniversalMixinPlugin(() -> provider);
        String legacy = "org.leodreamer.wildcard_pattern.wildcard.WildcardPatternLogic";
        check(!plugin.shouldApplyMixin(legacy, "io.github.sexileman.eae2pattern.mixin.WildcardExpansionMixin"), "removed legacy mod is skipped through the actual supported launcher path");
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, legacy.replace('.', '/'), null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "decodePatterns",
                "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)Ljava/util/stream/Stream;", null, null);
        method.visitCode(); method.visitInsn(Opcodes.ACONST_NULL); method.visitInsn(Opcodes.ARETURN); method.visitMaxs(1, 2); method.visitEnd(); writer.visitEnd();
        fixtures.put(legacy, writer.toByteArray());
        check(plugin.shouldApplyMixin(legacy, "io.github.sexileman.eae2pattern.mixin.WildcardExpansionMixin"), "present legacy interface is inspected through supported launcher path");
        String activation = UniversalMixinPlugin.GTL + "WildcardPatternCompat", expansion = UniversalMixinPlugin.GTL + "WildcardPatternCompatImpl";
        check(plugin.shouldApplyMixin(activation, "GtlWildcardActivationMixin"), "GTL activation survives actual launcher bytecode retrieval");
        check(plugin.shouldApplyMixin(expansion, "GtlWildcardExpansionMixin"), "GTL expansion remains enabled as a pair");
        check(calls[0] >= 4, "real provider used the transformer-loader callback rather than failing before class lookup");
        int before = calls[0];
        check(plugin.shouldApplyMixin("appeng.helpers.patternprovider.PatternProviderLogic", "MachineWildcardProviderMixin") && calls[0] == before,
                "ordinary AE2 adapter does not request optional bytecode");
        byte[] saved = fixtures.remove(expansion);
        var missing = new UniversalMixinPlugin(() -> provider);
        check(!missing.shouldApplyMixin(activation, "GtlWildcardActivationMixin"), "missing GTL half is nonfatal in actual launcher provider");
        fixtures.put(expansion, saved);
        check(!missing.shouldApplyMixin(expansion, "GtlWildcardExpansionMixin"), "a failed GTL probe keeps both halves consistently disabled");
    }
    private static void scenario(Path base, byte[] bridge, boolean own, boolean legacy) throws Exception {
        Path dir = base.resolve("own-" + own + "-legacy-" + legacy); Files.createDirectories(dir);
        Map<String,String> sources = new LinkedHashMap<>();
        sources.put("net.minecraft.world.item.Item", "public class Item { public final int kind; public Item(int k){kind=k;} }");
        sources.put("net.minecraft.world.item.ItemStack", "public class ItemStack { public final int kind; public ItemStack(int k){kind=k;} public boolean isEmpty(){return kind<1;} public boolean m_41619_(){return isEmpty();} public boolean is(Item i){return kind==i.kind;} public boolean m_150930_(Item i){return is(i);} }");
        sources.put("net.minecraft.world.level.Level", "public class Level {}");
        sources.put("net.minecraft.world.level.ItemLike", "public interface ItemLike { net.minecraft.world.item.Item asItem(); net.minecraft.world.item.Item m_5456_(); }");
        sources.put("appeng.api.crafting.IPatternDetails", "public interface IPatternDetails {}");
        sources.put("appeng.api.crafting.FixturePattern", "public class FixturePattern implements IPatternDetails { private final String value; public FixturePattern(String v){value=v;} public String toString(){return value;} }");
        sources.put("net.minecraftforge.fml.ModList", "public class ModList { public static ModList get(){return new ModList();} public boolean isLoaded(String s){return (s.equals(\"eae2_pattern\") && " + own + ") || (s.equals(\"wildcard_pattern\") && " + legacy + ");} }");
        sources.put("org.slf4j.Logger", "public interface Logger { void error(String message, Throwable error); }");
        sources.put("com.mojang.logging.LogUtils", "public class LogUtils { public static org.slf4j.Logger getLogger(){return (m,e)->{throw new AssertionError(m,e);};} }");
        if (own) sources.put("io.github.sexileman.eae2pattern.api.MachineWildcardApi", "public class MachineWildcardApi { public static boolean isPattern(net.minecraft.world.item.ItemStack s){return s.kind==1;} public static java.util.List<appeng.api.crafting.IPatternDetails> expand(net.minecraft.world.item.ItemStack s,net.minecraft.world.level.Level l){return java.util.List.of(new appeng.api.crafting.FixturePattern(\"own\"));} }");
        if (legacy) {
            sources.put("org.leodreamer.wildcard_pattern.WildcardItems", "public class WildcardItems { public static final net.minecraft.world.level.ItemLike WILDCARD_PATTERN=new net.minecraft.world.level.ItemLike(){public net.minecraft.world.item.Item asItem(){return new net.minecraft.world.item.Item(2);} public net.minecraft.world.item.Item m_5456_(){return asItem();}}; }");
            sources.put("org.leodreamer.wildcard_pattern.wildcard.WildcardPatternLogic", "public class WildcardPatternLogic { public static java.util.stream.Stream<appeng.api.crafting.IPatternDetails> decodePatterns(net.minecraft.world.item.ItemStack s,net.minecraft.world.level.Level l){return java.util.stream.Stream.of(new appeng.api.crafting.FixturePattern(\"legacy\"));} }");
        }
        List<String> compilerArgs = new ArrayList<>(List.of("--release", "17", "-proc:none", "-classpath", dir.toString(), "-d", dir.toString()));
        for (var entry : sources.entrySet()) {
            int dot = entry.getKey().lastIndexOf('.'); Path file = dir.resolve(entry.getKey().replace('.', '/') + ".java"); Files.createDirectories(file.getParent());
            Files.writeString(file, "package " + entry.getKey().substring(0, dot) + "; " + entry.getValue()); compilerArgs.add(file.toString());
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        check(compiler != null && compiler.run(null, null, null, compilerArgs.toArray(String[]::new)) == 0, "isolated fixtures compile");
        Path helper = dir.resolve(BRIDGE + ".class"); Files.createDirectories(helper.getParent()); Files.write(helper, bridge);
        try (var loader = new URLClassLoader(new java.net.URL[]{dir.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Class<?> api = loader.loadClass(BRIDGE.replace('/', '.'));
            Class<?> stack = loader.loadClass("net.minecraft.world.item.ItemStack"), level = loader.loadClass("net.minecraft.world.level.Level");
            check((Boolean) api.getMethod("available").invoke(null) == (own || legacy), "activation handles provider presence: " + own + "/" + legacy);
            var accepts = api.getMethod("accepts", stack); var expand = api.getMethod("expand", stack, level);
            Object world = level.getConstructor().newInstance();
            for (int kind : new int[]{1,2,3}) {
                Object item = stack.getConstructor(int.class).newInstance(kind); boolean expected = kind == 1 ? own : kind == 2 && legacy;
                check((Boolean) accepts.invoke(null, item) == expected, "correct item dispatch " + own + "/" + legacy + "/" + kind);
                var result = (List<?>) expand.invoke(null, item, world);
                check(expected ? result.size() == 1 && result.get(0).toString().equals(kind == 1 ? "own" : "legacy") : result.isEmpty(), "correct expansion without absent-provider linkage");
            }
        }
    }
}
