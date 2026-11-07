package io.github.sexileman.eae2pattern.net.server;

import io.github.sexileman.eae2pattern.EAE2Pattern;
import io.github.sexileman.eae2pattern.pattern.*;
import io.github.sexileman.eae2pattern.wildcard.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Prepare incrementally without spending blanks; then reuse the transactional network writer. */
@Mod.EventBusSubscriber(modid = EAE2Pattern.MOD_ID)
public final class MachineWildcardWriter {
    private static final ArrayDeque<Job> JOBS = new ArrayDeque<>();
    private MachineWildcardWriter() {}
    public static boolean busy(UUID id) { return JOBS.stream().anyMatch(j -> j.player.getUUID().equals(id)); }
    public static void forget(UUID id) { JOBS.removeIf(j -> j.player.getUUID().equals(id)); }
    public static void start(ServerPlayer player, List<PatternSpec> specs, Consumer<GenerationReport> callback) {
        start(player, specs, new CompoundTag(), callback);
    }
    public static void start(ServerPlayer player, List<PatternSpec> specs, CompoundTag rules, Consumer<GenerationReport> callback) {
        if (!MachineWildcard.available()) { callback.accept(failed(specs.size(), "Machine wildcard item is unavailable")); return; }
        if (specs.isEmpty()) { callback.accept(GenerationReport.nothingToDo()); return; }
        NetworkAccess network = NetworkAccess.resolve(player);
        if (network == null) { callback.accept(failed(specs.size(), "ME network unavailable")); return; }
        JOBS.add(new Job(player, List.copyOf(specs), network, WildcardEditorState.clean(rules), callback));
    }
    public static void startHeld(ServerPlayer player, List<PatternSpec> specs, CompoundTag rules,
                                 net.minecraft.world.InteractionHand hand, Consumer<GenerationReport> callback) {
        if (specs.isEmpty()) { callback.accept(GenerationReport.nothingToDo()); return; }
        if (specs.size() > MachineWildcardData.MAX_RECIPES) {
            callback.accept(failed(specs.size(), "Held wildcard supports at most 2048 recipes; use network batch generation for more")); return;
        }
        CompoundTag clean = WildcardEditorState.clean(rules);
        ItemStack held = player.getItemInHand(hand);
        if (!MachineWildcard.isOwnItem(held) || held.getCount() != 1 || clean.getByteArray("held_fingerprint").length != 32
                || !java.security.MessageDigest.isEqual(clean.getByteArray("held_fingerprint"), HeldWildcardFingerprint.visible(held))) {
            callback.accept(failed(specs.size(), "Held wildcard changed; reopen its editor")); return;
        }
        Job job = new Job(player, List.copyOf(specs), null, clean, callback);
        job.hand = hand; job.slot = hand == net.minecraft.world.InteractionHand.MAIN_HAND ? player.getInventory().selected : -1;
        job.expected = HeldWildcardFingerprint.visible(held);
        job.rules.remove("held_fingerprint"); JOBS.add(job);
    }
    private static GenerationReport failed(int n, String message) {
        return new GenerationReport(n, 0, 0, 0, 0, GenerationReport.StopReason.ENCODE_FAILED, List.of(message));
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long deadline = System.nanoTime() + 2_000_000L;
        int work = 0;
        while (!JOBS.isEmpty() && work++ < 32 && System.nanoTime() < deadline) {
            Job job = JOBS.removeFirst();
            if (job.player.hasDisconnected()) continue;
            try { if (!job.step()) JOBS.addLast(job); }
            catch (Exception e) {
                EAE2Pattern.LOGGER.warn("Machine wildcard preparation rejected: {}", e.getMessage());
                job.callback.accept(failed(job.specs.size(), String.valueOf(e.getMessage())));
            }
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { JOBS.clear(); }
    private static final class Job {
        final ServerPlayer player;
        final List<PatternSpec> specs;
        final NetworkAccess network;
        final Consumer<GenerationReport> callback;
        final List<ItemStack> prepared = new ArrayList<>();
        final List<CompoundTag> rows = new ArrayList<>();
        final CompoundTag rules;
        net.minecraft.world.InteractionHand hand;
        int slot;
        byte[] expected;
        ResourceLocation machine;
        int cursor, bytes, totalBytes;
        Job(ServerPlayer player, List<PatternSpec> specs, NetworkAccess network, CompoundTag rules, Consumer<GenerationReport> callback) {
            this.player = player; this.specs = specs; this.network = network; this.rules = rules.copy(); this.rules.remove("held_fingerprint"); this.callback = callback;
        }
        void flush() {
            if (rows.isEmpty()) return;
            emit(rows);
            rows.clear(); bytes = 0;
        }
        void emit(List<CompoundTag> entries) {
            try {
                var item = MachineWildcard.item(machine, entries, player.getGameProfile().getName());
                item.getOrCreateTag().put("editor", rules.copy()); prepared.add(item);
            } catch (MachineWildcardData.CapacityException tooLarge) {
                if (entries.size() < 2) throw tooLarge;
                int middle = entries.size() / 2;
                emit(entries.subList(0, middle)); emit(entries.subList(middle, entries.size()));
            }
        }
        boolean step() throws Exception {
            if (hand == null) {
                NetworkAccess current = NetworkAccess.resolve(player);
                if (current == null || !network.sameNetwork(current)) throw new IllegalArgumentException("ME network changed; no blanks consumed");
            } else verifyHeld(false);
            PatternSpec spec = specs.get(cursor++);
            if (spec.kind() != PatternKind.PROCESSING) throw new IllegalArgumentException("Machine wildcard supports processing recipes only");
            var recipe = player.serverLevel().getRecipeManager().byKey(spec.recipeId())
                    .orElseThrow(() -> new IllegalArgumentException("Source recipe is not registered on server: " + spec.recipeId()));
            ResourceLocation type = MachineWildcard.type(recipe);
            if (type == null) throw new IllegalArgumentException("Unregistered machine recipe type");
            CompoundTag row = new CompoundTag();
            row.putString("recipe", spec.recipeId().toString()); row.putByteArray("fingerprint", MachineWildcard.fingerprint(recipe));
            row.put("pattern", PatternEncoder.encode(player, spec).save(new CompoundTag()));
            int size = MachineWildcardData.bytes(row);
            if (size > MachineWildcardData.MAX_RAW_BYTES - 4096) throw new IllegalArgumentException("A source pattern is too large for a wildcard: " + spec.recipeId());
            totalBytes += size;
            if (totalBytes > 16_000_000) throw new IllegalArgumentException("Machine wildcard preparation exceeds 16 MB");
            if (!type.equals(machine) || rows.size() == MachineWildcardData.MAX_RECIPES || bytes + size > MachineWildcardData.MAX_RAW_BYTES - 4096) flush();
            machine = type; rows.add(row); bytes += size;
            if (cursor < specs.size()) return false;
            flush();
            if (hand == null) PatternWriter.startPrepared(player, prepared, network, callback);
            else {
                if (prepared.size() != 1) throw new IllegalArgumentException("Selection needs multiple wildcard items; reduce it or use network batch generation");
                verifyHeld(true);
                var updated = player.getItemInHand(hand).copy();
                updated.setTag(HeldWildcardFingerprint.mergeEncodingTags(updated.getOrCreateTag(), prepared.get(0).getOrCreateTag()));
                player.setItemInHand(hand, updated); player.getInventory().setChanged();
                player.inventoryMenu.broadcastChanges();
                if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
                callback.accept(new GenerationReport(1, 1, 1, 0, 0, GenerationReport.StopReason.COMPLETED, List.of()));
            }
            return true;
        }
        void verifyHeld(boolean checkData) {
            ItemStack current = player.getItemInHand(hand);
            if (!HeldWildcardFingerprint.positionMatches(hand == net.minecraft.world.InteractionHand.MAIN_HAND, slot,
                    player.getInventory().selected, MachineWildcard.isOwnItem(current), current.getCount())
                    || (checkData && !java.security.MessageDigest.isEqual(expected, HeldWildcardFingerprint.visible(current))))
                throw new IllegalArgumentException("Held wildcard changed; no item was modified");
        }
    }
}
