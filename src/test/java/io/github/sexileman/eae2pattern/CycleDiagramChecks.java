package io.github.sexileman.eae2pattern;
import io.github.sexileman.eae2pattern.cycle.*;
import io.github.sexileman.eae2pattern.net.*;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import java.util.*;
public final class CycleDiagramChecks {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static int run(){
        var pattern=(IPatternDetails)java.lang.reflect.Proxy.newProxyInstance(CycleDiagramChecks.class.getClassLoader(),new Class[]{IPatternDetails.class},(p,m,a)->switch(m.getName()){
            case "getInputs"->new IPatternDetails.IInput[0];case "getOutputs"->new GenericStack[0];case "hashCode"->1;case "equals"->p==a[0];default->null;
        });
        var metadata=new GrowthCraftingPlan(null,1234,new KeyCounter(),Map.of(pattern,10000L),List.of(pattern),List.of(new TwoRecipeGrowth.Stage(0,10000)));
        var data=CycleDiagramData.build(metadata,null);
        check(data.getString("status").equals("planned")&&data.getLong("bytes")==1234,"diagram distinguishes executable metadata from a mere candidate");
        check(data.getList("phases",10).getCompound(0).getLong("times")==10000,"diagram preserves complete batch quantities without per-item expansion");
        var buf=new FriendlyByteBuf(Unpooled.buffer());
        try{
            new S2CCycleDiagramPacket(7,99,data).encode(buf);var copy=S2CCycleDiagramPacket.decode(buf);
            check(copy.menu()==7&&copy.request()==99&&copy.data().equals(data),"response retains menu binding, request identity and phase snapshot");
            buf.clear();var huge=CycleDiagramData.status("planned");huge.putByteArray("payload",new byte[CycleDiagramData.MAX_BYTES+1]);
            new S2CCycleDiagramPacket(7,100,huge).encode(buf);
            check(S2CCycleDiagramPacket.decode(buf).data().getString("status").equals("too_large"),"oversized plans return a bounded failure state instead of a huge packet");
            buf.clear();buf.writeVarInt(7);buf.writeVarInt(100);buf.writeVarInt(CycleDiagramData.MAX_BYTES+1);
            boolean refused=false;try{S2CCycleDiagramPacket.decode(buf);}catch(RuntimeException expected){refused=true;}
            check(refused,"oversized incoming diagram data is rejected before allocation");
            buf.clear();new C2SCycleDiagramPacket(7,123).encode(buf);var request=C2SCycleDiagramPacket.decode(buf);
            check(request.menu()==7&&request.request()==123,"query preserves exact menu and request identity");
        }finally{buf.release();}
        return checks;
    }
}
