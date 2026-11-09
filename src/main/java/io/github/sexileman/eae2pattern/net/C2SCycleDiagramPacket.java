package io.github.sexileman.eae2pattern.net;
import appeng.menu.me.crafting.CraftConfirmMenu;
import io.github.sexileman.eae2pattern.cycle.*;
import io.github.sexileman.eae2pattern.mixin.CycleConfirmMenuAccessor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;
public record C2SCycleDiagramPacket(int menu,int request) {
    private static final Map<UUID,Long> LAST=new LinkedHashMap<>();
    public void encode(FriendlyByteBuf buf){buf.writeVarInt(menu);buf.writeVarInt(request);}
    public static C2SCycleDiagramPacket decode(FriendlyByteBuf buf){return new C2SCycleDiagramPacket(buf.readVarInt(),buf.readVarInt());}
    public void handle(Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ctx.enqueueWork(()->{
        var player=ctx.getSender();if(player==null||request<0||player.containerMenu.containerId!=menu||!(player.containerMenu instanceof CraftConfirmMenu confirm))return;
        long now=System.nanoTime();long previous=LAST.getOrDefault(player.getUUID(),0L);if(now-previous<500_000_000L)return;
        LAST.remove(player.getUUID());LAST.put(player.getUUID(),now);while(LAST.size()>128)LAST.remove(LAST.keySet().iterator().next());
        var data=CycleDiagramData.status("unsupported");
        if(confirm instanceof CycleConfirmMenuAccessor accessor){
            var plan=accessor.eae2$currentPlan();
            data=plan==null?CycleDiagramData.status("loading"):(Object)plan instanceof GrowthPlanView view&&view.eae2$diagram()!=null?view.eae2$diagram():CycleDiagramData.status("none");
        }
        PacketHandler.sendToPlayer(player,new S2CCycleDiagramPacket(menu,request,data));
    });ctx.setPacketHandled(true);}
}
