package io.github.sexileman.eae2pattern;

import com.google.gson.JsonParser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipFile;

/** Verify the delivered, reobfuscated single-jar artifact without using the external wildcard jar. */
public final class StandaloneDistributionContracts {
    private static int checks;
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message); checks++;
    }
    private static byte[] bytes(ZipFile zip, String name) throws Exception {
        var entry = zip.getEntry(name);
        if (entry == null) throw new AssertionError("Missing entry: " + name);
        try (var stream = zip.getInputStream(entry)) { return stream.readAllBytes(); }
    }
    private static String text(ZipFile zip, String name) throws Exception { return new String(bytes(zip, name), StandardCharsets.UTF_8); }
    public static void main(String[] args) throws Exception {
        Path distribution = Path.of(args[0]), mods = Path.of(args[1]);
        Path extracted = distribution.getParent().resolve("standalone-contract/wildcard_pattern-embedded.jar");
        Files.createDirectories(extracted.getParent());
        try (var outer = new ZipFile(distribution.toFile())) {
            var metadata = JsonParser.parseString(text(outer, "META-INF/jarjar/metadata.json")).getAsJsonObject();
            var jars = metadata.getAsJsonArray("jars");
            check(jars.size() == 1, "exactly one embedded compatibility core");
            var nested = jars.get(0).getAsJsonObject();
            check(nested.getAsJsonObject("version").get("artifactVersion").getAsString().equals("0.1.2"), "pinned compatibility version");
            byte[] core = bytes(outer, nested.get("path").getAsString());
            check(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(core)).equals("d9f69e4f74249af4faef2486ef7ab31cf572699ec6000d6fda5609d99f20ec41"), "exact pack-compatible core, including its non-GT fix");
            Files.write(extracted, core);
            check(text(outer, "META-INF/MANIFEST.MF").contains("MixinConfigs: eae2_pattern.mixins.json"), "outer expansion hooks are enabled");
            check(outer.getEntry("io/github/sexileman/eae2pattern/mixin/WildcardExpansionMixin.class") != null, "bound recipe hook shipped");
            check(text(outer, "META-INF/licenses/Wildcard-Pattern-MIT.txt").contains("LeoDreamer"), "upstream attribution shipped");
        }
        Map<String, ClassNode> classes = new HashMap<>();
        try (var core = new ZipFile(extracted.toFile())) {
            check(text(core, "META-INF/mods.toml").contains("modId = \"wildcard_pattern\""), "GTL activation mod ID preserved");
            check(text(core, "META-INF/mods.toml").contains("[1.4.4,1.4.5)"), "actual GTL compatibility range preserved");
            check(text(core, "META-INF/MANIFEST.MF").contains("MixinConfigs: wildcard_pattern.mixins.json"), "native provider/material hooks enabled");
            check(core.getEntry("assets/wildcard_pattern/models/item/wildcard_pattern.json") != null, "old registry namespace model preserved");
            for (var entry : java.util.Collections.list(core.entries())) if (entry.getName().endsWith(".class")) {
                var node = new ClassNode(); new ClassReader(bytes(core, entry.getName())).accept(node, ClassReader.SKIP_DEBUG); classes.put(node.name, node);
            }
        }
        Path gtl;
        try (var files = Files.list(mods)) { gtl = files.filter(p -> p.getFileName().toString().contains("gtlcore-1") && p.toString().endsWith(".jar")).findFirst().orElseThrow(); }
        var linked = new HashSet<String>();
        String prefix = "org/leodreamer/wildcard_pattern/";
        try (var zip = new ZipFile(gtl.toFile())) {
            for (var entry : java.util.Collections.list(zip.entries())) if (entry.getName().endsWith(".class")) {
                ClassNode node = new ClassNode(); new ClassReader(bytes(zip, entry.getName())).accept(node, ClassReader.SKIP_DEBUG);
                for (var method : node.methods) for (var insn : method.instructions) {
                    if (insn instanceof MethodInsnNode call && call.owner.startsWith(prefix)) {
                        var target = classes.get(call.owner);
                        check(target != null && target.methods.stream().anyMatch(m -> m.name.equals(call.name) && m.desc.equals(call.desc)), "GTL legacy method linkage: " + call.owner + "." + call.name);
                        linked.add(call.owner + "." + call.name + call.desc);
                    } else if (insn instanceof FieldInsnNode field && field.owner.startsWith(prefix)) {
                        var target = classes.get(field.owner);
                        check(target != null && target.fields.stream().anyMatch(f -> f.name.equals(field.name) && f.desc.equals(field.desc)), "GTL legacy field linkage: " + field.owner + "." + field.name);
                        linked.add(field.owner + "." + field.name + field.desc);
                    } else if (insn instanceof TypeInsnNode type && type.desc.startsWith(prefix)) {
                        check(classes.containsKey(type.desc), "GTL legacy class linkage: " + type.desc);
                    }
                }
            }
        }
        check(!linked.isEmpty(), "GTL references were actually audited");
        // All wildcard classes for this check come from the delivered artifact, never the mods folder.
        CompatibilityContracts.main(new String[]{mods.toString(), extracted.toString()});
        System.out.println("Passed " + checks + " standalone package/linkage checks; " + linked.size() + " unique GTL links. No external Wildcard Pattern jar used; no game was launched.");
    }
}
