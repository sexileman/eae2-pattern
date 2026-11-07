package io.github.sexileman.eae2pattern;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import javax.tools.ToolProvider;

/** Audit shipped jars and exercise the real SRG bridge with isolated optional-provider fixtures. */
public final class IndependentWildcardContracts {
    private static int checks;
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    private static byte[] bytes(ZipFile zip, String path) throws Exception {
        var entry = zip.getEntry(path); if (entry == null) throw new AssertionError("Missing " + path);
        try (var stream = zip.getInputStream(entry)) { return stream.readAllBytes(); }
    }
    private static ClassNode node(byte[] bytes) { var n = new ClassNode(); new ClassReader(bytes).accept(n, 0); return n; }
    public static void main(String[] args) throws Exception {
        Path own = Path.of(args[0]), patched = Path.of(args[1]), mods = Path.of(args[2]);
        Path original;
        try (var files = Files.list(mods)) { original = files.filter(p -> p.toString().contains("gtlcore-1") && p.toString().endsWith(".jar")).findFirst().orElseThrow(); }
        byte[] bridge;
        try (var app = new ZipFile(own.toFile()); var out = new ZipFile(patched.toFile()); var in = new ZipFile(original.toFile())) {
            check(Collections.list(app.entries()).stream().noneMatch(e -> e.getName().endsWith(".jar") || e.getName().startsWith("org/leodreamer/")), "independent jar embeds no original wildcard classes or module");
            check(app.getEntry(GtlCorePatcher.BRIDGE + ".class") == null, "GTL bridge is not duplicated in EAE2 module");
            check(app.getEntry("io/github/sexileman/eae2pattern/api/MachineWildcardApi.class") != null, "independent integration API shipped");
            check(app.getEntry("io/github/sexileman/eae2pattern/mixin/MachineWildcardProviderMixin.class") != null, "independent AE2 provider expansion hook shipped");
            check(app.getEntry("assets/eae2_pattern/models/item/machine_wildcard_pattern.json") != null, "own item model namespace shipped");
            Set<String> allowed = Set.of(GtlCorePatcher.PREFIX + "WildcardPatternCompat.class", GtlCorePatcher.PREFIX + "WildcardPatternCompatImpl.class", "META-INF/mods.toml");
            int unchanged = 0;
            for (var entry : Collections.list(in.entries())) if (!entry.isDirectory() && !allowed.contains(entry.getName())) {
                if (!Arrays.equals(bytes(in, entry.getName()), bytes(out, entry.getName()))) throw new AssertionError("Unrelated GTL data changed: " + entry.getName());
                unchanged++;
            }
            check(unchanged > 100, "all unrelated GTL class/resource bytes preserved (" + unchanged + ")");
            for (String name : allowed) check(!Arrays.equals(bytes(in, name), bytes(out, name)), "intended GTL edit exists: " + name);
            bridge = bytes(out, GtlCorePatcher.BRIDGE + ".class");
            for (String path : List.of(GtlCorePatcher.PREFIX + "WildcardPatternCompat.class", GtlCorePatcher.PREFIX + "WildcardPatternCompatImpl.class", GtlCorePatcher.BRIDGE + ".class")) {
                var cls = node(bytes(out, path));
                for (var method : cls.methods) for (var insn : method.instructions) {
                    if (insn instanceof MethodInsnNode call) check(!call.owner.startsWith("org/leodreamer/"), "no hard link to removed legacy method");
                    if (insn instanceof FieldInsnNode field) check(!field.owner.startsWith("org/leodreamer/"), "no hard link to removed legacy field");
                }
            }
            check(out.getEntry("META-INF/eae2-wildcard-patch/GtlCorePatcher.java") != null, "reproducible patch source included");
        }
        Path base = own.getParent().resolve("independent-contracts-" + UUID.randomUUID()); Files.createDirectories(base);
        for (boolean a : new boolean[]{false, true}) for (boolean b : new boolean[]{false, true}) scenario(base, bridge, a, b);
        System.out.println("Passed " + checks + " independent artifact, GTL preservation and four-provider-presence checks. Fixtures are not a Minecraft world test.");
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
        Path helper = dir.resolve(GtlCorePatcher.BRIDGE + ".class"); Files.createDirectories(helper.getParent()); Files.write(helper, bridge);
        try (var loader = new URLClassLoader(new java.net.URL[]{dir.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Class<?> api = loader.loadClass(GtlCorePatcher.BRIDGE.replace('/', '.'));
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
