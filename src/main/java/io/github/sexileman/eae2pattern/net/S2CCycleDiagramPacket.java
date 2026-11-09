package io.github.sexileman.eae2pattern.net;
import io.github.sexileman.eae2pattern.cycle.CycleDiagramData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import io.netty.buffer.Unpooled;
import java.util.function.Supplier;
public record S2CCycleDiagramPacket(int menu,int request,CompoundTag data) {
    public void encode(FriendlyByteBuf buf){
        buf.writeVarInt(menu);buf.writeVarInt(request);
        var encoded=new FriendlyByteBuf(Unpooled.buffer());
        try{encoded.writeNbt(data.sizeInBytes()>CycleDiagramData.MAX_BYTES?CycleDiagramData.status("too_large"):data);if(encoded.readableBytes()>CycleDiagramData.MAX_BYTES){encoded.clear();encoded.writeNbt(CycleDiagramData.status("too_large"));}
            byte[] bytes=new byte[encoded.readableBytes()];encoded.readBytes(bytes);buf.writeByteArray(bytes);
        }finally{encoded.release();}
    }
    public static S2CCycleDiagramPacket decode(FriendlyByteBuf buf){
        int menu=buf.readVarInt(),request=buf.readVarInt();var encoded=new FriendlyByteBuf(Unpooled.wrappedBuffer(buf.readByteArray(CycleDiagramData.MAX_BYTES)));
        try{var tag=encoded.readNbt();if(tag==null||encoded.isReadable())throw new IllegalArgumentException("Invalid cycle diagram");return new S2CCycleDiagramPacket(menu,request,tag);}finally{encoded.release();}
    }
    public void handle(Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ctx.enqueueWork(()->io.github.sexileman.eae2pattern.client.CyclePlanOverlay.accept(this));ctx.setPacketHandled(true);}
}
