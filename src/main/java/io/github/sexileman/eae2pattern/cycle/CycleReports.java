package io.github.sexileman.eae2pattern.cycle;
import appeng.api.networking.crafting.*;
import appeng.api.stacks.*;
import io.github.sexileman.eae2pattern.EAE2Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
/** Server-thread presentation/storage; bounded diagnostics, never a replacement crafting plan. */
@Mod.EventBusSubscriber(modid=EAE2Pattern.MOD_ID)
public final class CycleReports {
    private record DisplayWitness(Component path,List<Component> patterns){}
    private record Report(Component target,long amount,boolean missing,List<DisplayWitness> witnesses,boolean limited,long time){}
    private static final Map<UUID,Report> LAST=new LinkedHashMap<>();
    private static final Map<UUID,Long> NOTIFIED=new LinkedHashMap<>();
    private static final long TTL=600_000,COOLDOWN=5000;
    private CycleReports(){}
    public static void publish(Level level,ICraftingSimulationRequester requester,ICraftingPlan plan,CycleCapture capture){
        if(level.isClientSide||plan==null||level.getServer()==null)return;
        var witnesses=capture==null?List.<CycleCapture.Witness>of():capture.witnesses();
        var actor=requester.getActionSource().player().orElse(null);
        if(!(actor instanceof ServerPlayer player)){
            if(!witnesses.isEmpty())EAE2Pattern.LOGGER.debug("Crafting cycle candidates for {}: {}",plan.finalOutput().what().getId(),witnesses.size());
            return;
        }
        UUID id=player.getUUID();var server=level.getServer();
        var display=new ArrayList<DisplayWitness>();boolean limited=capture!=null&&capture.truncated();int weight=0;
        for(var witness:witnesses){
            var patterns=new ArrayList<Component>();
            for(var pattern:witness.patterns().stream().limit(4).toList()){
                var line=Component.translatable("eae2_pattern.cycle.pattern",stacks(pattern.inputs()),stacks(pattern.outputs()));
                if(!pattern.recipeTypes().isEmpty())line.append(" ["+String.join(", ",pattern.recipeTypes())+"]");
                patterns.add(line);
                if(pattern.inputs().size()>12||pattern.outputs().size()>12)limited=true;
            }
            if(witness.patterns().size()>4||witness.path().size()>9)limited=true;
            var path=chain(witness.path());weight+=Component.Serializer.toJson(path).length()*2;
            for(var pattern:patterns)weight+=Component.Serializer.toJson(pattern).length()*2;
            if(weight>64_000){limited=true;break;}
            display.add(new DisplayWitness(path,List.copyOf(patterns)));
        }
        var report=new Report(name(plan.finalOutput().what()),plan.finalOutput().amount(),plan.simulation(),List.copyOf(display),limited,System.currentTimeMillis());
        server.execute(()->{
            LAST.entrySet().removeIf(e->System.currentTimeMillis()-e.getValue().time()>TTL);
            LAST.remove(id);LAST.put(id,report);
            while(LAST.size()>64)LAST.remove(LAST.keySet().iterator().next());
            var online=server.getPlayerList().getPlayer(id);if(online==null||report.witnesses().isEmpty())return;
            long now=System.currentTimeMillis();long previous=NOTIFIED.getOrDefault(id,0L);
            if(now-previous<COOLDOWN)return;
            NOTIFIED.remove(id);NOTIFIED.put(id,now);while(NOTIFIED.size()>128)NOTIFIED.remove(NOTIFIED.keySet().iterator().next());
            online.displayClientMessage(Component.translatable("eae2_pattern.cycle.detected",report.witnesses().get(0).path(),report.witnesses().size(),Component.translatable(report.missing()?"eae2_pattern.cycle.missing":"eae2_pattern.cycle.planned")),false);
        });
    }
    private static Component name(AEKey key){
        var name=key.getDisplayName().copy();
        return Component.Serializer.toJson(name).length()<=256?name:Component.literal(key.getId().toString());
    }
    private static Component chain(List<AEKey> path){
        var text=Component.empty();int limit=Math.min(path.size(),9);
        for(int i=0;i<limit;i++){if(i>0)text.append(" → ");text.append(name(path.get(i)));}
        if(path.size()>limit)text.append(" …");return text;
    }
    private static Component stacks(List<GenericStack> slots){
        var text=Component.empty();int limit=Math.min(slots.size(),12);
        for(int i=0;i<limit;i++){if(i>0)text.append(" + ");var stack=slots.get(i);text.append(stack.what().formatAmount(stack.amount(),AmountFormat.FULL)+" × ").append(name(stack.what()));}
        if(slots.size()>limit)text.append(" …");return text;
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("eae2cycles").executes(ctx->{
            var player=ctx.getSource().getPlayerOrException();var report=LAST.get(player.getUUID());
            if(report==null||System.currentTimeMillis()-report.time()>TTL){ctx.getSource().sendSuccess(()->Component.translatable("eae2_pattern.cycle.none"),false);return 0;}
            ctx.getSource().sendSuccess(()->Component.translatable("eae2_pattern.cycle.header",report.target(),report.amount(),Component.translatable(report.missing()?"eae2_pattern.cycle.missing":"eae2_pattern.cycle.planned")),false);
            for(var witness:report.witnesses()){
                ctx.getSource().sendSuccess(()->witness.path(),false);
                for(var pattern:witness.patterns())ctx.getSource().sendSuccess(()->pattern,false);
            }
            if(report.witnesses().isEmpty())ctx.getSource().sendSuccess(()->Component.translatable("eae2_pattern.cycle.none"),false);
            if(report.limited())ctx.getSource().sendSuccess(()->Component.translatable("eae2_pattern.cycle.limited"),false);
            ctx.getSource().sendSuccess(()->Component.translatable("eae2_pattern.cycle.scope"),false);return report.witnesses().size();
        }));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){LAST.remove(event.getEntity().getUUID());NOTIFIED.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void stop(ServerStoppedEvent event){LAST.clear();NOTIFIED.clear();}
}
