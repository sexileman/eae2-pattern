package io.github.sexileman.eae2pattern;
import com.mojang.brigadier.*;
import com.mojang.brigadier.tree.*;
import com.mojang.brigadier.arguments.*;
import net.minecraft.commands.CommandSourceStack;
import io.github.sexileman.eae2pattern.command.EAE2PatternCommands;
import io.github.sexileman.eae2pattern.config.EAE2PatternConfig;
public final class CommandChecks {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static int run() throws Exception {
        var dispatcher=new CommandDispatcher<CommandSourceStack>();EAE2PatternCommands.register(dispatcher);
        var root=dispatcher.getRoot().getChild("eae2_pattern");
        check(root.getChild("cycles").getChild("report")!=null&&dispatcher.getRoot().getChild("eae2cycles")==null,"cycle reporting shares the mod command root");
        var set=root.getChild("config").getChild("set");
        for(String key:new String[]{"cycles.experimental_execution","cycles.chat_diagnostics","encoding.allow_fluid_substitutes"}){
            var node=(ArgumentCommandNode<CommandSourceStack,?>)set.getChild(key).getChild("value");
            check(node.getType() instanceof BoolArgumentType,"boolean config has a typed command argument");
            for(String value:new String[]{"true","false"})check(node.getType().parse(new StringReader(value)) instanceof Boolean,"boolean commands accept native true/false");
            for(String value:new String[]{"yes","1","TRUE","truejunk"}){
                boolean rejected=false;try{node.getType().parse(new StringReader(value));}catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){rejected=true;}
                check(rejected,"invalid boolean rejected at parsing, before config mutation");
            }
            var context=new com.mojang.brigadier.context.CommandContextBuilder<CommandSourceStack>(dispatcher,null,dispatcher.getRoot(),0).build("");
            var suggestions=node.getType().listSuggestions(context,new com.mojang.brigadier.suggestion.SuggestionsBuilder("",0)).get();
            check(suggestions.getList().stream().map(s->s.getText()).toList().containsAll(java.util.List.of("true","false")),"boolean arguments suggest true and false");
        }
        var limit=(IntegerArgumentType)((ArgumentCommandNode<CommandSourceStack,?>)set.getChild("limits.max_patterns_per_request").getChild("value")).getType();
        check(limit.parse(new StringReader("65536"))==65536,"integer upper boundary accepted");
        for(String invalid:new String[]{"0","65537","-1","x"}){
            boolean rejected=false;try{limit.parse(new StringReader(invalid));}catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){rejected=true;}
            check(rejected,"integer limit rejects invalid/out-of-range values before mutation");
        }
        check(!EAE2PatternConfig.CYCLE_CHAT_DIAGNOSTICS.getDefault(),"automatic cycle chat is disabled by default");
        return checks;
    }
}
