package io.github.sexileman.eae2pattern.net.server;

import appeng.api.stacks.AEItemKey;
import io.github.sexileman.eae2pattern.EAE2Pattern;
import io.github.sexileman.eae2pattern.net.PacketHandler;
import io.github.sexileman.eae2pattern.net.S2CInspectionPacket;
import io.github.sexileman.eae2pattern.pattern.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Read-only explicit checks of stored pattern signatures and currently published AE2 definitions. */
@Mod.EventBusSubscriber(modid = EAE2Pattern.MOD_ID)
public final class PatternInspection {
    private static final ArrayDeque<Job> JOBS = new ArrayDeque<>();
    private PatternInspection() {}
    public static boolean busy(UUID id) { return JOBS.stream().anyMatch(j -> j.operation == 1 && j.player.getUUID().equals(id)); }
    public static void forget(UUID id) { JOBS.removeIf(j -> j.player.getUUID().equals(id)); }
    public static void cancel(UUID id, int requestId) { JOBS.removeIf(j -> j.player.getUUID().equals(id) && j.requestId == requestId); }
    public static void start(ServerPlayer player, List<PatternSpec> specs, int requestId, int operation) {
        if (operation == 2 && specs.size() != 1) return;
        // Keep at most one pending read-only preview per player, replacing stale mouse selections.
        JOBS.removeIf(j -> j.operation == 2 && j.player.getUUID().equals(player.getUUID()));
        JOBS.add(new Job(player, specs, requestId, operation));
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long deadline = System.nanoTime() + 2_000_000L;
        int work = 0;
        while (!JOBS.isEmpty() && work++ < 32 && System.nanoTime() < deadline) {
            Job job = JOBS.removeFirst();
            if (job.player.hasDisconnected()) continue;
            try { if (!job.step()) JOBS.addLast(job); }
            catch (RuntimeException e) {
                EAE2Pattern.LOGGER.warn("Read-only pattern inspection failed for {}", job.player.getUUID(), e);
                job.details.clear(); job.details.add("Inspection failed; see server log"); job.finish(false);
            }
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { JOBS.clear(); }
    private static final class Job {
        final ServerPlayer player;
        final List<PatternSpec> specs;
        final int requestId, operation;
        final NetworkAccess network;
        final List<ResourceLocation> existing = new ArrayList<>();
        final List<PatternSpec> preview = new ArrayList<>();
        final List<String> details = new ArrayList<>();
        int cursor, failed,inventoryCursor;
        List<AEItemKey> inventory=List.of();
        final Set<String> storedSignatures=new HashSet<>();
        boolean limitedReported,checkingPublished;
        List<appeng.api.crafting.IPatternDetails> published=List.of();
        int publishedCursor;
        String pendingSignature;
        ResourceLocation pendingId;
        Job(ServerPlayer player, List<PatternSpec> specs, int id, int operation) {
            this.player = player; this.specs = specs; requestId = id; this.operation = operation;
            network = operation == 1 ? NetworkAccess.resolve(player) : null;
            if(network!=null){var snapshot=network.patternInventory();inventory=snapshot.keys();if(snapshot.limited())limited();}
        }
        boolean step() {
            if (operation == 1) {
                var current = NetworkAccess.resolve(player);
                if (network == null || current == null || !network.sameNetwork(current)) {
                    details.add("ME network unavailable or changed"); finish(false); return true;
                }
            }
            if(operation==1&&inventoryCursor<inventory.size()){
                storedSignatures.add(PatternSignature.of(inventory.get(inventoryCursor++)));return false;
            }
            if(!inventory.isEmpty()&&inventoryCursor==inventory.size()){inventory=List.of();inventoryCursor=0;}
            if(checkingPublished)return checkPublished();
            PatternSpec spec = specs.get(cursor++);
            try {
                var encoded = PatternEncoder.encode(player, spec);
                if (operation == 2) preview.add(EncodedPatternView.decode(player.serverLevel(), spec, encoded));
                else {
                    String signature=PatternSignature.of(AEItemKey.of(encoded));
                    boolean found=storedSignatures.contains(signature);
                    if(!found){
                        var decoded=appeng.api.crafting.PatternDetailsHelper.decodePattern(encoded,player.serverLevel());
                        if(decoded!=null){
                            published=network.craftingFor(decoded.getPrimaryOutput().what()).stream().limit(513).toList();
                            if(published.size()>512){limited();published=published.subList(0,512);}
                            if(!published.isEmpty()){
                                pendingSignature=signature;pendingId=spec.recipeId();publishedCursor=0;checkingPublished=true;return false;
                            }
                        }
                    }
                    if(found)existing.add(spec.recipeId());
                }
            } catch (Exception e) {
                failed++;
                if (details.size() < 4) {
                    String detail = spec.recipeId() + ": " + e.getMessage();
                    details.add(detail.substring(0, Math.min(detail.length(), 512)));
                }
            }
            if (cursor < specs.size()) return false;
            finish(true); return true;
        }
        boolean checkPublished(){
            boolean found=false;
            try{found=pendingSignature.equals(PatternSignature.of(published.get(publishedCursor++).getDefinition()));}
            catch(RuntimeException unsupported){limited();}
            if(found)existing.add(pendingId);
            if(!found&&publishedCursor<published.size())return false;
            checkingPublished=false;published=List.of();pendingSignature=null;pendingId=null;
            if(cursor<specs.size())return false;finish(true);return true;
        }
        void limited(){if(!limitedReported){limitedReported=true;details.add("Pattern inspection capacity reached; results are partial");}}
        void finish(boolean success) {
            PacketHandler.sendToPlayer(player, new S2CInspectionPacket(requestId, operation, success,
                    cursor, failed, existing, preview, details));
        }
    }
}
