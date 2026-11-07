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
        Path wildcard = args.length > 1 ? Path.of(args[1])
                : jar(mods, n -> n.toLowerCase().contains("wildcard_pattern") && n.endsWith(".jar"));
        String logic = "org/leodreamer/wildcard_pattern/wildcard/WildcardPatternLogic";
        var node = read(wildcard, logic);
        check(node.methods.stream().anyMatch(m -> m.name.equals("decodePatterns") && m.desc.equals("(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)Ljava/util/stream/Stream;") && (m.access & 8) != 0), "native static wildcard expansion descriptor changed");
        check(calls(read(wildcard, "org/leodreamer/wildcard_pattern/mixin/PatternProviderLogicMixin"), logic, "decodePatterns"), "ordinary AE2 provider must call hooked expansion");
        Path gtl = jar(mods, n -> n.contains("gtlcore-1") && n.endsWith(".jar"));
        check(calls(read(gtl, "org/gtlcore/gtlcore/integration/wildcard/WildcardPatternCompatImpl"), logic, "decodePatterns"), "GTL wildcard buffer must call the same expansion");
        Path gt = jar(mods, n -> n.startsWith("gtceu-") && n.endsWith(".jar"));
        check(read(gt, "com/gregtechceu/gtceu/api/recipe/GTRecipeSerializer").fields.stream()
                .anyMatch(f -> f.name.equals("CODEC") && f.desc.equals("Lcom/mojang/serialization/Codec;") && (f.access & 9) == 9),
                "GT serializer must expose a public codec for stable restart fingerprints");
        Path extended = jar(mods, n -> n.startsWith("ExtendedAE-") && n.endsWith(".jar"));
        for (String name : new String[]{"common/tileentities/TileExPatternProvider", "common/parts/PartExPatternProvider"}) {
            var provider = read(extended, "com/glodblock/github/extendedae/" + name);
            boolean baseLogic = provider.methods.stream().anyMatch(m -> {
                for (var insn : m.instructions) if (insn instanceof TypeInsnNode type && type.desc.equals("appeng/helpers/patternprovider/PatternProviderLogic")) return true;
                return false;
            });
            check(baseLogic, "ExtendedAE provider must use standard AE2 provider logic: " + name);
        }
        System.out.println("Passed " + checks + " installed-mod bytecode contracts (not a game runtime test)");
    }
}
