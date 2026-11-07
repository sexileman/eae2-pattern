package io.github.sexileman.eae2pattern;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Reproducible, hash-pinned surgery on the user's GTL jar. All unrelated class/resource bytes survive. */
public final class GtlCorePatcher {
    public static final String INPUT_SHA = "81b52d5e0621c8eaa0dc55460d8c9be51619889ba6d29ea8bdbda92af96dea3c";
    public static final String PREFIX = "org/gtlcore/gtlcore/integration/wildcard/";
    public static final String BRIDGE = PREFIX + "eae2/GtlWildcardBridge";
    private static final String STACK = "Lnet/minecraft/world/item/ItemStack;", LEVEL = "Lnet/minecraft/world/level/Level;";
    private GtlCorePatcher() {}
    private static byte[] patch(byte[] input, boolean wrapper) {
        ClassNode node = new ClassNode(); new ClassReader(input).accept(node, 0); int changes = 0;
        for (var method : node.methods) {
            boolean init = wrapper && method.name.equals("<clinit>");
            boolean accepts = !wrapper && method.name.equals("isWildcardPattern") && method.desc.equals("(" + STACK + ")Z");
            boolean expand = !wrapper && method.name.equals("expandPatterns") && method.desc.equals("(" + STACK + LEVEL + ")Ljava/util/List;");
            if (!init && !accepts && !expand) continue;
            method.instructions.clear(); method.tryCatchBlocks.clear();
            if (method.localVariables != null) method.localVariables.clear();
            if (method.visibleLocalVariableAnnotations != null) method.visibleLocalVariableAnnotations.clear();
            if (method.invisibleLocalVariableAnnotations != null) method.invisibleLocalVariableAnnotations.clear();
            if (init) {
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "available", "()Z", false));
                method.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, node.name, "IS_LOADED", "Z"));
                method.instructions.add(new InsnNode(Opcodes.RETURN));
            } else {
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                if (expand) method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, accepts ? "accepts" : "expand", method.desc, false));
                method.instructions.add(new InsnNode(accepts ? Opcodes.IRETURN : Opcodes.ARETURN));
            }
            changes++;
        }
        if (changes != (wrapper ? 1 : 2)) throw new IllegalArgumentException("Unsupported GTL method contract: " + node.name);
        var out = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(out); return out.toByteArray();
    }
    public static void main(String[] args) throws Exception {
        Path mods = Path.of(args[0]), support = Path.of(args[1]), output = Path.of(args[2]), root = Path.of(args[3]);
        Path input;
        try (var files = Files.list(mods)) { input = files.filter(p -> p.getFileName().toString().contains("gtlcore-1") && p.toString().endsWith(".jar")).findFirst().orElseThrow(); }
        if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input))).equals(INPUT_SHA))
            throw new IllegalArgumentException("GTL input differs from the audited 1.2.3.0-fix9 pack jar; refusing to patch");
        Map<String, byte[]> replacement = new LinkedHashMap<>();
        try (var zip = new ZipFile(input.toFile()); var helper = new ZipFile(support.toFile())) {
            for (String name : List.of("WildcardPatternCompat", "WildcardPatternCompatImpl")) {
                String path = PREFIX + name + ".class";
                replacement.put(path, patch(zip.getInputStream(zip.getEntry(path)).readAllBytes(), name.equals("WildcardPatternCompat")));
            }
            String bridge = BRIDGE + ".class";
            replacement.put(bridge, helper.getInputStream(helper.getEntry(bridge)).readAllBytes());
            String toml = new String(zip.getInputStream(zip.getEntry("META-INF/mods.toml")).readAllBytes(), StandardCharsets.UTF_8);
            toml += "\n[[dependencies.gtlcore]]\nmodId=\"eae2_pattern\"\nmandatory=false\nversionRange=\"[4.0.0,)\"\nordering=\"AFTER\"\nside=\"BOTH\"\n";
            replacement.put("META-INF/mods.toml", toml.getBytes(StandardCharsets.UTF_8));
            String dir = "META-INF/eae2-wildcard-patch/";
            for (String source : List.of("src/test/java/io/github/sexileman/eae2pattern/GtlCorePatcher.java",
                    "src/main/java/" + BRIDGE + ".java", "docs/gtlcore-patch.md", "docs/licenses/LGPL-3.0.txt", "docs/licenses/GPL-3.0.txt"))
                replacement.put(dir + Path.of(source).getFileName(), Files.readAllBytes(root.resolve(source)));
            replacement.put(dir + "input.sha256", (INPUT_SHA + "\n").getBytes(StandardCharsets.UTF_8));
            Files.createDirectories(output.getParent());
            try (var out = new ZipOutputStream(Files.newOutputStream(output))) {
                for (var entry : Collections.list(zip.entries())) {
                    if (replacement.containsKey(entry.getName())) continue;
                    if (entry.getName().matches("(?i)META-INF/.*\\.(SF|RSA|DSA)")) throw new IllegalArgumentException("Signed input is not supported");
                    var copy = new ZipEntry(entry.getName()); copy.setTime(entry.getTime()); out.putNextEntry(copy);
                    if (!entry.isDirectory()) try (var stream = zip.getInputStream(entry)) { stream.transferTo(out); }
                    out.closeEntry();
                }
                for (var entry : replacement.entrySet()) {
                    var added = new ZipEntry(entry.getKey()); added.setTime(Math.max(0, zip.getEntry("META-INF/mods.toml").getTime()));
                    out.putNextEntry(added); out.write(entry.getValue()); out.closeEntry();
                }
            }
        }
        System.out.println("Patched only 2 GTL classes and optional-dependency metadata; added isolated dual-provider bridge: " + output);
    }
}
