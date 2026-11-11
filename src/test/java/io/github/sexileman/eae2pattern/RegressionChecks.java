package io.github.sexileman.eae2pattern;

import io.github.sexileman.eae2pattern.client.Ingredient;
import io.github.sexileman.eae2pattern.client.ItemSearchQuery;
import io.github.sexileman.eae2pattern.client.RecipePlan;
import io.github.sexileman.eae2pattern.client.RecipeFilters;
import io.github.sexileman.eae2pattern.client.ProcessingMultiplier;
import io.github.sexileman.eae2pattern.client.RecipeGroups;
import io.github.sexileman.eae2pattern.client.RecipeDiagnostics;
import io.github.sexileman.eae2pattern.client.IngredientPresentation;
import io.github.sexileman.eae2pattern.client.DetailRowLayout;
import io.github.sexileman.eae2pattern.net.S2CInspectionPacket;
import io.github.sexileman.eae2pattern.net.C2SGeneratePatternsPacket;
import io.github.sexileman.eae2pattern.compat.jei.GtChanceReader;
import io.github.sexileman.eae2pattern.pattern.*;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/** Boundary and semantic regressions; run with gradlew regressionTest. */
public final class RegressionChecks {
    private static int checks;
    public static void main(String[] args) throws Exception {
        circuitConditions(); policies(); wireFormat(); searches(); gtVersions(); layoutAndGrid(); manualMultiplier(); grouping(); inspectionPackets(); slotPresentation(); patternMetadata(); amountLabels(); machineWildcards(); heldWildcardWrites(); dynamicRules(); stackedCarriers(); wildcardTooltip(); materialPicker(); aeToolScreens(); encodingMessages(); recipeStripColumns(); searchDebounce(); wildcardStockStatus(); guidePages(); moreMenuLayout(); cyclePaths(); generationSafety();
        checks+=CycleAlphaChecks.run();
        checks+=CommandChecks.run();
        checks+=CycleDiagramChecks.run();
        checks+=io.github.sexileman.eae2pattern.cycle.CycleOptimizationChecks.run();
        System.out.println("Passed " + checks + " regression checks");
    }
    private static void circuitConditions() throws Exception {
        var id=new ResourceLocation("gtceu:programmed_circuit");var tag=new CompoundTag();tag.putInt("Configuration",24);
        var circuit=new StackSpec(StackType.ITEM,id,tag,1);
        var selected=new io.github.sexileman.eae2pattern.wildcard.CircuitCondition(2,java.util.Set.of(24,1));
        check(io.github.sexileman.eae2pattern.wildcard.CircuitCondition.configuration(circuit)==24,"circuit uses full configuration NBT");
        check(io.github.sexileman.eae2pattern.wildcard.CircuitCondition.configuration(new StackSpec(StackType.ITEM,new ResourceLocation("other:tool"),tag,1))==-1,"unrelated Configuration tag is not a GT circuit");
        var input=List.of(new io.github.sexileman.eae2pattern.wildcard.RuleRecipe.Slot(circuit,0));
        check(io.github.sexileman.eae2pattern.wildcard.CircuitCondition.configuration(new StackSpec(StackType.ITEM,id,null,1))==0,"GTCEu missing configuration defaults to zero, distinct from no circuit");
        check(selected.matches(input),"non consumed circuit participates in dedicated matching");
        check(!new io.github.sexileman.eae2pattern.wildcard.CircuitCondition(1,java.util.Set.of()).matches(input),"no circuit excludes circuit recipes");
        check(!selected.matches(List.<io.github.sexileman.eae2pattern.wildcard.RuleRecipe.Slot>of()),"selected configurations exclude recipes without circuit");
        var state=new CompoundTag();selected.write(state);var clean=io.github.sexileman.eae2pattern.wildcard.WildcardEditorState.clean(state);
        check(io.github.sexileman.eae2pattern.wildcard.CircuitCondition.read(clean).equals(selected),"wildcard cleaner preserves circuit selection");
        var recipe=new io.github.sexileman.eae2pattern.wildcard.RuleRecipe(new ResourceLocation("test:recipe"),input,List.of());
        check(new io.github.sexileman.eae2pattern.wildcard.DynamicRule(clean).matches(recipe),"server dynamic resolver matches selected circuit");
        new io.github.sexileman.eae2pattern.wildcard.CircuitCondition(2,java.util.Set.of(2)).write(state);
        check(!new io.github.sexileman.eae2pattern.wildcard.DynamicRule(state).matches(recipe),"server rejects different circuit even when tool search is disabled");
        check(new io.github.sexileman.eae2pattern.wildcard.DynamicRule(new CompoundTag()).matches(recipe),"legacy wildcard without circuit fields retains all configurations");
        var bad=new CompoundTag();bad.putInt("circuit_mode",2);bad.putIntArray("circuit_configs",new int[]{-1});boolean rejected=false;
        try{io.github.sexileman.eae2pattern.wildcard.WildcardEditorState.clean(bad);}catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"server rejects malformed circuit configuration");
        bad.putIntArray("circuit_configs",new int[257]);rejected=false;try{io.github.sexileman.eae2pattern.wildcard.WildcardEditorState.clean(bad);}catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"circuit rule payload is bounded");
        var all=new CompoundTag();io.github.sexileman.eae2pattern.wildcard.CircuitCondition.ALL.write(all);
        check(all.isEmpty(),"default circuit mode does not change legacy rule fingerprints");
        var metal=new StackSpec(StackType.ITEM,new ResourceLocation("test:metal"),null,1);
        var tool=new StackSpec(StackType.ITEM,new ResourceLocation("test:mould"),null,1);
        var normal=new RecipeFilters(ItemSearchQuery.empty(),ItemSearchQuery.empty(),3,0,false);
        var never=new RecipeFilters(ItemSearchQuery.empty(),ItemSearchQuery.empty(),1,0,false);
        var chance=new RecipeFilters(ItemSearchQuery.empty(),ItemSearchQuery.empty(),2,0,false);
        var withCircuit=entry(List.of(Ingredient.consumed(metal),Ingredient.notConsumed(circuit)),List.of(),List.of());
        check(!normal.rejects(withCircuit)&&never.rejects(withCircuit),"circuit does not classify ordinary materials as non-consumed");
        var withAuxCircuit=entry(List.of(Ingredient.consumed(metal)),List.of(Ingredient.notConsumed(circuit)),List.of());
        check(!normal.rejects(withAuxCircuit)&&never.rejects(withAuxCircuit),"separate catalyst circuit also remains independent");
        var withTool=entry(List.of(Ingredient.consumed(metal)),List.of(Ingredient.notConsumed(circuit),Ingredient.notConsumed(tool)),List.of());
        check(normal.rejects(withTool)&&!never.rejects(withTool),"non-circuit mould still participates in consumption filtering");
        var withChance=entry(List.of(Ingredient.consumed(metal,.1f)),List.of(Ingredient.notConsumed(circuit)),List.of());
        check(normal.rejects(withChance)&&!chance.rejects(withChance),"chance consumption remains independent of circuit selection");
        var ordinary=entry(List.of(Ingredient.consumed(metal)),List.of(),List.of());
        check(!normal.rejects(ordinary)&&never.rejects(ordinary),"ordinary recipes without GT retain original material filter behavior");
        check(withAuxCircuit.hasNeverConsumedInput(),"original diagnostics and encoding still recognize the required circuit");
        var clazz=Class.forName("io.github.sexileman.eae2pattern.client.CircuitCatalog");var ctor=clazz.getDeclaredConstructor(RecipePlan.class);ctor.setAccessible(true);
        var available=clazz.getDeclaredMethod("available");available.setAccessible(true);
        var noGt=new RecipePlan(new ResourceLocation("minecraft:crafting"),Component.literal("crafting"),PatternKind.CRAFTING,List.of());
        check(!(boolean)available.invoke(ctor.newInstance(noGt)),"no GT circuits hides the optional filter");
        var entry=new RecipePlan.Entry(PatternKind.PROCESSING,new ResourceLocation("test:circuits"),Component.literal("circuit"),null,List.of(),List.of(new Ingredient(circuit,0,false)),List.of(),true);
        var plan=new RecipePlan(new ResourceLocation("gtceu:bender"),Component.literal("bender"),PatternKind.PROCESSING,List.of(entry));
        var catalog=ctor.newInstance(plan);check((boolean)available.invoke(catalog),"original non-consumed catalyst exposes circuit selection");
        var matches=clazz.getDeclaredMethod("matches",RecipePlan.Entry.class,io.github.sexileman.eae2pattern.wildcard.CircuitCondition.class);matches.setAccessible(true);
        check((boolean)matches.invoke(catalog,entry,selected),"cached client circuit condition matches authoritative rule");
    }
    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    private static void generationSafety() throws Exception {
        var values=io.github.sexileman.eae2pattern.pattern.CompleteIngredients.map(List.of("A","B"),String::toLowerCase);
        check(values.equals(List.of("a","b")),"complete ingredient conversion preserves every slot and order");
        boolean refused=false;try{io.github.sexileman.eae2pattern.pattern.CompleteIngredients.map(List.of("A","B"),x->x.equals("B")?null:x);}catch(IllegalArgumentException expected){refused=expected.getMessage().contains("slot 2");}
        check(refused,"one unsupported material rejects the entire conversion instead of silently dropping B");
        var batch=new io.github.sexileman.eae2pattern.pattern.BatchPreflight<>(List.of("ok1","bad","ok2"),x->x);
        check(!batch.enough(99),"no batch may write before all validation is complete");
        batch.step(v->{});check(!batch.enough(99),"a valid prefix cannot spend blanks early");
        batch.step(v->{throw new IllegalArgumentException("unsupported");});batch.step(v->{});
        check(batch.done()&&batch.required()==2&&batch.rejected()==1,"required blanks count valid results, not submitted recipes");
        check(batch.enough(2)&&!batch.enough(1),"exact available blank count gates the prepared valid batch");
        check(batch.valid().equals(List.of("ok1","ok2"))&&batch.errors().size()==1,"valid source references and diagnostics are retained without encoded stacks");
        var many=new io.github.sexileman.eae2pattern.pattern.BatchPreflight<>(java.util.Collections.nCopies(50,"bad"),x->x);
        while(!many.done())many.step(v->{throw new Exception("x".repeat(1000));});
        check(many.required()==0&&many.errors().size()==5&&many.errors().stream().allMatch(e->e.length()<=512),"failed preparation diagnostics have bounded size");
        var id=new ResourceLocation("ae2:processing_pattern");var tag=new CompoundTag();tag.putString("encodePlayer","one");tag.putString("input","same");
        String signature=io.github.sexileman.eae2pattern.pattern.PatternSignature.of(id,tag);
        var renamed=tag.copy();renamed.putString("encodePlayer","two");var display=new CompoundTag();display.putString("Name","renamed");renamed.put("display",display);
        check(signature.equals(io.github.sexileman.eae2pattern.pattern.PatternSignature.of(id,renamed)),"display name and encoder identity do not prevent semantic duplicate detection");
        check(tag.getString("encodePlayer").equals("one"),"signature calculation does not mutate encoded pattern tags");
        var flags=tag.copy();flags.putBoolean("fluidSubstitution",true);
        check(!signature.equals(io.github.sexileman.eae2pattern.pattern.PatternSignature.of(id,flags)),"encoding flags remain significant");
        var input=tag.copy();var material=new CompoundTag();material.putInt("circuit",2);input.put("materialNbt",material);
        check(!signature.equals(io.github.sexileman.eae2pattern.pattern.PatternSignature.of(id,input)),"material NBT remains significant");
        var typed=tag.copy();PatternMetadata.write(typed,null,new ResourceLocation("gtceu:wiremill"));var other=tag.copy();PatternMetadata.write(other,null,new ResourceLocation("gtceu:bender"));
        check(!io.github.sexileman.eae2pattern.pattern.PatternSignature.of(id,typed).equals(io.github.sexileman.eae2pattern.pattern.PatternSignature.of(id,other)),"different machine recipe types are not deduplicated together");
        check(!signature.equals(io.github.sexileman.eae2pattern.pattern.PatternSignature.of(new ResourceLocation("ae2:crafting_pattern"),tag)),"different pattern kinds remain distinct");
        var quantity=tag.copy();quantity.putLong("amount",2);var differentQuantity=quantity.copy();differentQuantity.putLong("amount",3);
        check(!io.github.sexileman.eae2pattern.pattern.PatternSignature.of(id,quantity).equals(io.github.sexileman.eae2pattern.pattern.PatternSignature.of(id,differentQuantity)),"different encoded quantities remain distinct");
        var orderA=tag.copy();var listA=new net.minecraft.nbt.ListTag();listA.add(net.minecraft.nbt.StringTag.valueOf("primary"));listA.add(net.minecraft.nbt.StringTag.valueOf("byproduct"));orderA.put("out",listA);
        var orderB=tag.copy();var listB=new net.minecraft.nbt.ListTag();listB.add(net.minecraft.nbt.StringTag.valueOf("byproduct"));listB.add(net.minecraft.nbt.StringTag.valueOf("primary"));orderB.put("out",listB);
        check(!io.github.sexileman.eae2pattern.pattern.PatternSignature.of(id,orderA).equals(io.github.sexileman.eae2pattern.pattern.PatternSignature.of(id,orderB)),"primary output order remains significant");
        var unknown=(mezz.jei.api.gui.ingredient.IRecipeSlotView)java.lang.reflect.Proxy.newProxyInstance(RegressionChecks.class.getClassLoader(),new Class[]{mezz.jei.api.gui.ingredient.IRecipeSlotView.class},(proxy,m,args)->switch(m.getName()){case "isEmpty"->false;case "getIngredients"->java.util.stream.Stream.empty();case "equals"->proxy==args[0];case "hashCode"->System.identityHashCode(proxy);default->null;});
        var collect=io.github.sexileman.eae2pattern.compat.jei.JeiRecipeCollector.class.getDeclaredMethod("toIngredients",List.class,boolean.class,GtChanceReader.Cursor.class,boolean.class);collect.setAccessible(true);
        boolean unknownRefused=false;try{collect.invoke(null,List.of(unknown),true,null,true);}catch(java.lang.reflect.InvocationTargetException e){unknownRefused=e.getCause() instanceof IllegalArgumentException;}
        check(unknownRefused,"non-empty JEI slot of an unsupported type rejects the recipe");
        check(((List<?>)collect.invoke(null,List.of(unknown),true,null,false)).isEmpty(),"unsupported catalyst decoration does not turn into a required material");
        var stack=stack("test:unknown_output",1);var unverified=new Ingredient(stack,1,true,false);
        check(!unverified.guaranteedOutput()&&unverified.chanceText().equals("?"),"unread probability is not presented as guaranteed 100 percent");
        check(IngredientPresentation.of(unverified,1).kind()==IngredientPresentation.Kind.UNKNOWN_OUTPUT,"unknown output confidence has an explicit presentation");
        check(io.github.sexileman.eae2pattern.compat.jei.ChanceReader.read(unknown).known()==false,"missing tooltip probability remains unknown");
    }
    private static void cyclePaths() {
        check(io.github.sexileman.eae2pattern.cycle.CyclePath.close(List.of("A","B"),List.of("A")).equals(List.of("A","B","A")),"two-material input back-edge closes A-B-A");
        check(io.github.sexileman.eae2pattern.cycle.CyclePath.close(List.of("A","B","C"),List.of("A")).equals(List.of("A","B","C","A")),"longer cycles retain ordered ancestry");
        check(io.github.sexileman.eae2pattern.cycle.CyclePath.close(List.of("X","A","B"),List.of("A")).equals(List.of("A","B","A")),"unrelated target prefix is excluded");
        check(io.github.sexileman.eae2pattern.cycle.CyclePath.close(List.of("A"),List.of("A")).equals(List.of("A","A")),"direct recursive processing is identified");
        check(io.github.sexileman.eae2pattern.cycle.CyclePath.close(List.of("A","B"),List.of("C")).isEmpty(),"acyclic dependencies do not become cycles");
        check(io.github.sexileman.eae2pattern.cycle.CyclePath.close(List.of("A","B"),List.of()).isEmpty(),"co-output overlap alone is not reported as an input cycle");
        check(io.github.sexileman.eae2pattern.cycle.CyclePath.close(List.of("A","B","C"),List.of("A","B")).equals(List.of("B","C","B")),"nearest ancestor gives the shortest witnessed cycle");
        check(io.github.sexileman.eae2pattern.cycle.CyclePath.close(java.util.Collections.nCopies(65,"A"),List.of("A")).isEmpty(),"diagnostic depth is bounded");
        var tagA=new CompoundTag();tagA.putInt("mode",1);var tagB=new CompoundTag();tagB.putInt("mode",2);
        var a=new StackSpec(StackType.ITEM,new ResourceLocation("test:part"),tagA,1);var b=new StackSpec(StackType.ITEM,new ResourceLocation("test:part"),tagB,1);
        check(io.github.sexileman.eae2pattern.cycle.CyclePath.close(List.of(a),List.of(b)).isEmpty(),"NBT-distinct materials do not form a false cycle");
    }
    private static void moreMenuLayout() {
        var actions=io.github.sexileman.eae2pattern.client.WorkbenchMoreAction.values();
        check(actions.length==8,"actual More menu has eight actions and no wildcard navigation");
        for(int[] canvas:List.of(new int[]{320,180},new int[]{480,270},new int[]{640,360})) {
            var popup=io.github.sexileman.eae2pattern.client.PopupLayout.measure(220,80,44,19,180,actions.length,canvas[0],canvas[1]);
            check(popup.y()>=6&&popup.y()+popup.height()<=canvas[1]-6,"actual More menu fits compact and normal canvases without throwing");
            for(int i=0;i<actions.length;i++)check(popup.indexAt(popup.x()+10,popup.y()+3+i*19+9)==i,"all actual More actions remain reachable");
        }
        check(actions[0].labelKey(true,false).equals("show_all")&&actions[2].labelKey(false,true).equals("unpin"),"contextual More labels retain their intended actions");
    }
    private static void guidePages() throws Exception {
        var pages=new java.util.ArrayList<appeng.client.guidebook.compiler.ParsedGuidePage>();
        for(String file:List.of("index.md","wildcard-patterns.md","batch-encoding.md")) {
            String path="assets/eae2_pattern/ae2guide/"+file;
            try(var stream=RegressionChecks.class.getClassLoader().getResourceAsStream(path)) {
                check(stream!=null,"native AE2 guide resource exists: "+file);
                var page=appeng.client.guidebook.compiler.PageCompiler.parse("eae2_pattern",new ResourceLocation("eae2_pattern",file),stream);
                check(page.getFrontmatter().navigationEntry()!=null,"native AE2 parser accepts page navigation: "+file);
                pages.add(page);
            }
        }
        check(pages.get(1).getFrontmatter().additionalProperties().get("item_ids") instanceof List<?> items && items.equals(List.of("eae2_pattern:machine_wildcard_pattern")),"native AE2 parser retains the item target shared by blank and encoded carriers");
    }
    private static void wildcardStockStatus() {
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        var sent=new io.github.sexileman.eae2pattern.net.S2CNetworkStatusPacket(9,true,1234,0,"");
        sent.encode(buffer);
        var read=io.github.sexileman.eae2pattern.net.S2CNetworkStatusPacket.decode(buffer);
        check(read.equals(sent), "ordinary stock and absent wildcard stock roundtrip independently");
        io.github.sexileman.eae2pattern.client.ClientNetworkState.accept(read);
        check(io.github.sexileman.eae2pattern.client.ClientNetworkState.blankPatterns()==1234 && io.github.sexileman.eae2pattern.client.ClientNetworkState.blankWildcards()==0, "ordinary blanks cannot satisfy wildcard UI stock");
        buffer.clear();
        sent=new io.github.sexileman.eae2pattern.net.S2CNetworkStatusPacket(10,true,0,12,"");sent.encode(buffer);
        io.github.sexileman.eae2pattern.client.ClientNetworkState.accept(io.github.sexileman.eae2pattern.net.S2CNetworkStatusPacket.decode(buffer));
        check(io.github.sexileman.eae2pattern.client.ClientNetworkState.blankWildcards()==12 && io.github.sexileman.eae2pattern.client.ClientNetworkState.blankPatterns()==0, "wildcard blanks do not satisfy normal pattern stock");
        io.github.sexileman.eae2pattern.client.ClientNetworkState.reset();buffer.release();
        var title=io.github.sexileman.eae2pattern.wildcard.WildcardTooltipText.boundName(Component.literal("Wiremill"));
        check(title.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().equals("eae2_pattern.wildcard.bound_name") && ((Component)t.getArgs()[0]).getString().equals("Wiremill"), "bound name includes the machine and no recipe count");
        check(title.getStyle().getColor().getValue()==0xE640E6 && !title.getStyle().isItalic(), "bound name uses reference magenta text without italics");
        check(new GenerationReport(1,0,0,0,0,GenerationReport.StopReason.NOT_ENOUGH_WILDCARD_PATTERNS,List.of()).summaryKey().equals("eae2_pattern.report.no_wildcards"), "wildcard shortage has a distinct explanation");
    }
    private static void searchDebounce() {
        var debounce = new io.github.sexileman.eae2pattern.client.SearchDebounce();
        check(!debounce.poll(0), "idle field does not start a search");
        debounce.changed(0);
        check(!debounce.poll(199_999_999L), "search waits a full 200ms");
        debounce.changed(150_000_000L);
        check(!debounce.poll(200_000_000L), "typing restarts the trailing delay");
        check(debounce.pending(), "pending search remains guarded");
        check(debounce.poll(350_000_000L), "one search runs 200ms after the last change");
        check(!debounce.pending() && !debounce.poll(900_000_000L), "unchanged text does not repeat the search");
        debounce.changed(1_000_000_000L);
        check(debounce.poll(1_700_000_000L), "slow ticks run one overdue search without catchup repeats");
    }
    private static void recipeStripColumns() {
        for(int width:new int[]{86,100,118,130}){
            var columns=io.github.sexileman.eae2pattern.client.RecipeStripLayout.measure(width);
            check(columns.inputMax()*22<=columns.inputWidth(),"narrow row keeps input icons inside their column");
            check(columns.outputX()+columns.outputMax()*22<=width,"narrow row keeps output icons inside the row");
            check(columns.inputMax()*22+3+io.github.sexileman.eae2pattern.client.RecipeStripLayout.overflowWidth(columns.inputWidth(),columns.inputMax())<=columns.inputWidth(),"narrow overflow text cannot enter the arrow gutter");
        }

        for (int width : new int[]{136,170,220,280}) {
            var columns=io.github.sexileman.eae2pattern.client.RecipeStripLayout.measure(width);
            check(columns.inputMax()*22+30<=columns.arrowX(), "input icons and overflow stay before the arrow");
            check(columns.outputX()+columns.outputMax()*22+30<=width, "output icons and overflow stay within the row");
            check(columns.outputX()>columns.arrowX()+9, "arrow has a separate gutter before outputs");
        }
    }
    private static void encodingMessages() {
        String raw="gtceu:macerator/macerate_titanium_buzzsaw: pattern has no ingredients after filtering: gtceu:macerator/macerate_titanium_buzzsaw";
        check(io.github.sexileman.eae2pattern.client.EncodingMessages.key(raw).equals("filtered_input"),"duplicated source ID diagnostic is reduced to an actionable input-filter explanation");
        var message=io.github.sexileman.eae2pattern.client.EncodingMessages.explain(raw);
        check(message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t&&t.getArgs().length==0,"raw recipe IDs are not inserted into the player-facing error");
        check(io.github.sexileman.eae2pattern.client.EncodingMessages.key("ME network unavailable or changed").equals("network"),"network failure has a concise localized explanation");
        check(io.github.sexileman.eae2pattern.client.EncodingMessages.key("Held wildcard changed; no item was modified").equals("held_changed"),"changed carrier message asks to reopen");
        check(io.github.sexileman.eae2pattern.client.EncodingMessages.key("unrecognized internal detail").equals("failed"),"unknown diagnostics do not leak raw exception text into the UI");
        for(int width:new int[]{320,480,640})check(io.github.sexileman.eae2pattern.client.Tooltips.maxWidth(width)<=260&&io.github.sexileman.eae2pattern.client.Tooltips.maxWidth(width)<=width-32,"tooltips wrap within a bounded GUI width");
    }
    private static void aeToolScreens() throws Exception {
        for(int[] size:List.of(new int[]{320,180},new int[]{400,240},new int[]{480,270},new int[]{640,360},new int[]{960,540})){
            var frame=io.github.sexileman.eae2pattern.client.ToolViewport.measure(size[0],size[1],552,440);
            check(frame.left()>=0&&frame.top()>=0&&frame.left()+frame.width()<=size[0]&&frame.top()+frame.height()<=size[1],"AE tool stays within logical canvas "+size[0]);
            check(frame.contains(frame.left(),frame.top())&&!frame.contains(frame.left()-1,frame.top())&&!frame.contains(frame.left()+frame.width(),frame.top()),"JEI side lanes do not belong to tool input area");
            if(size[0]>=400)check(frame.left()>=72&&size[0]-frame.left()-frame.width()>=72,"both JEI side lanes retain four ingredient columns");
            if(size[1]>=220)check(size[1]-frame.top()-frame.height()>=26,"JEI bottom search field has reserved space");
        }
        for(int[] size:List.of(new int[]{480,270},new int[]{640,360},new int[]{960,540})){
            var frame=io.github.sexileman.eae2pattern.client.ToolViewport.encoding(size[0],size[1]);
            check(frame.width()<=420&&frame.height()<=300,"encoding pages avoid oversized full-screen layouts");
            check(frame.left()>=72&&size[0]-frame.left()-frame.width()>=72,"encoding pages reserve both JEI ingredient lanes");
            check(size[1]-frame.top()-frame.height()>=26,"encoding pages reserve the JEI search field");
        }
        var unready=io.github.sexileman.eae2pattern.compat.jei.ToolGuiProperties.capture(
                io.github.sexileman.eae2pattern.client.PatternWorkbenchScreen.class,
                new net.minecraft.client.renderer.Rect2i(0,0,0,0),0,0);
        check(unready==null,"JEI opening before init does not cache invalid live properties");
        var ready=io.github.sexileman.eae2pattern.compat.jei.ToolGuiProperties.capture(
                io.github.sexileman.eae2pattern.client.PatternWorkbenchScreen.class,
                new net.minecraft.client.renderer.Rect2i(110,19,420,300),640,360);
        check(!mezz.jei.gui.GuiProperties.areEqual(unready,ready),
                "JEI detects the transition from opening to initialized screen");
        var resized=io.github.sexileman.eae2pattern.compat.jei.ToolGuiProperties.capture(
                io.github.sexileman.eae2pattern.client.PatternWorkbenchScreen.class,
                new net.minecraft.client.renderer.Rect2i(78,10,324,236),480,270);
        check(ready.getGuiXSize()==420&&ready.getScreenWidth()==640,
                "old JEI dimensions remain immutable after screen resize");
        check(!mezz.jei.gui.GuiProperties.areEqual(ready,resized),
                "JEI detects a GUI-scale or viewport change");
        var screens=new java.util.LinkedHashMap<Class<?>,mezz.jei.api.gui.handlers.IScreenHandler<?>>();
        var ghosts=new java.util.LinkedHashMap<Class<?>,mezz.jei.api.gui.handlers.IGhostIngredientHandler<?>>();
        var globals=new java.util.ArrayList<mezz.jei.api.gui.handlers.IGlobalGuiHandler>();
        var registration=(mezz.jei.api.registration.IGuiHandlerRegistration)java.lang.reflect.Proxy.newProxyInstance(RegressionChecks.class.getClassLoader(),new Class[]{mezz.jei.api.registration.IGuiHandlerRegistration.class},(proxy,method,args)->{
            switch(method.getName()){
                case "addGuiScreenHandler"->screens.put((Class<?>)args[0],(mezz.jei.api.gui.handlers.IScreenHandler<?>)args[1]);
                case "addGhostIngredientHandler"->ghosts.put((Class<?>)args[0],(mezz.jei.api.gui.handlers.IGhostIngredientHandler<?>)args[1]);
                case "addGlobalGuiHandler"->globals.add((mezz.jei.api.gui.handlers.IGlobalGuiHandler)args[0]);
                default->throw new AssertionError("Unexpected JEI registration call "+method.getName());
            }return null;
        });
        io.github.sexileman.eae2pattern.compat.jei.ToolJeiHandlers.register(registration);
        check(screens.remove(appeng.client.gui.me.crafting.CraftConfirmScreen.class)!=null,
                "cycle inspection overlay registers dynamic JEI screen bounds");
        check(screens.size()==6&&ghosts.size()==6&&globals.size()==1,"all six tool pages register screen, ghost and shared ingredient integration");
        check(screens.keySet().equals(ghosts.keySet()),"every JEI-visible page participates in ghost drag lifecycle");
        for(Class<?> type:screens.keySet())check(io.github.sexileman.eae2pattern.client.AeToolScreen.class.isAssignableFrom(type),"JEI page implements bounded panel/input contract: "+type.getSimpleName());
        var unsupported=new mezz.jei.api.ingredients.ITypedIngredient<String>(){
            public mezz.jei.api.ingredients.IIngredientType<String> getType(){return ()->String.class;}
            public String getIngredient(){return "unsupported";}
            public java.util.Optional<ItemStack> getItemStack(){return java.util.Optional.empty();}
            public <V> java.util.Optional<V> getIngredient(mezz.jei.api.ingredients.IIngredientType<V> type){return java.util.Optional.empty();}
        };
        for(var handler:ghosts.values())check(handler.getTargetsTyped(null,unsupported,false).isEmpty(),"unsupported JEI ingredient produces no mutable drag targets");
    }
    private static void materialPicker() {
        var source=StackSpec.of(StackType.ITEM,new ResourceLocation("gtceu:iron_ingot"),64);
        var name=Component.literal("Iron");var detail=Component.literal("gtceu:iron");
        var option=new io.github.sexileman.eae2pattern.client.PickerOption("iron",name,detail,source);
        check(option.value().equals("iron")&&option.detail().getString().equals("gtceu:iron"),"full displayed material ID does not change the persisted family filter");
        check(option.icon().amount()==1&&source.amount()==64,"representative icon is normalized without changing recipe quantity");
        check(option.matches("gtceu:iron")&&option.matches("gtceu:iron_ingot"),"material picker searches complete material and example item IDs");
        check(option.matches("IRON")&&!option.matches("copper"),"material picker preserves ordinary name matching");
        name.append(" changed");detail.append(" changed");check(option.label().getString().equals("Iron")&&option.detail().getString().equals("gtceu:iron"),"picker owns display components");
        var any=new io.github.sexileman.eae2pattern.client.PickerOption("",Component.literal("All materials"));
        check(any.icon()==null&&any.detail().getString().isEmpty()&&any.value().isEmpty(),"unrestricted option has no invented item identity");
        check(io.github.sexileman.eae2pattern.client.MaterialChoiceCatalog.rank(java.util.Set.of("ingots"),true)<io.github.sexileman.eae2pattern.client.MaterialChoiceCatalog.rank(java.util.Set.of("plates"),true),"material thumbnail prefers a representative ingot over a processed plate");
        check(io.github.sexileman.eae2pattern.client.MaterialChoiceCatalog.rank(java.util.Set.of("dusts"),true)<io.github.sexileman.eae2pattern.client.MaterialChoiceCatalog.rank(java.util.Set.of(),false),"available material item is preferred over fluid fallback");
    }
    private static void wildcardTooltip() {
        var generated=Component.translatable("eae2_pattern.wildcard.name","gtceu:wiremill",325);
        check(io.github.sexileman.eae2pattern.wildcard.WildcardTooltipText.isGeneratedName(generated),"old generated item names normalize without rewriting saved NBT");
        check(!io.github.sexileman.eae2pattern.wildcard.WildcardTooltipText.isGeneratedName(Component.literal("My custom pattern")),"player-chosen literal names remain untouched");
        var type=Component.literal("Wiremill");var lines=io.github.sexileman.eae2pattern.wildcard.WildcardTooltipText.lines(type,325,true,"player");
        check(lines.size()==3&&lines.stream().noneMatch(c->c.getString().isEmpty()),"compact tooltip has no blank spacer or redundant mode lines");
        var encoding=(net.minecraft.network.chat.contents.TranslatableContents)lines.get(1).getContents();
        check(encoding.getKey().endsWith("encoding_type")&&((Component)encoding.getArgs()[0]).getString().equals("Wiremill"),"encoding type displays the local category title, not a GT-only metadata field");
        var count=(net.minecraft.network.chat.contents.TranslatableContents)lines.get(2).getContents();
        check(count.getKey().endsWith("matched_patterns")&&((Component)count.getArgs()[0]).getString().equals("325"),"matched count is a separate primary field");
        check(((Component)encoding.getArgs()[0]).getStyle().getColor().equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.LIGHT_PURPLE)),"recipe type uses the native processing-pattern purple");
        check(type.getStyle().getColor()==null,"tooltip styling does not mutate cached JEI titles");
        var fixed=io.github.sexileman.eae2pattern.wildcard.WildcardTooltipText.lines(Component.literal("test:machine"),20,false,"");
        check(fixed.size()==2&&((net.minecraft.network.chat.contents.TranslatableContents)fixed.get(1).getContents()).getKey().endsWith("matched_patterns"),"fixed tooltip has two useful lines when encoder is absent");
        check(hasMode(lines.get(2),"mode_dynamic"),"dynamic tooltip names its mode");
        check(hasMode(fixed.get(1),"mode_fixed"),"fixed tooltip names its mode");
    }
    private static boolean hasMode(Component line,String key) {
        return line.getSiblings().stream()
                .anyMatch(c -> c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                        && tc.getKey().endsWith(key));
    }

    private static void stackedCarriers() {
        check(io.github.sexileman.eae2pattern.wildcard.WildcardCarrierRules.isResetRecipe("eae2_pattern:machine_wildcard_pattern_reset"),"reset recipe is excluded from generic blank-to-blank batch encoding");
        check(!io.github.sexileman.eae2pattern.wildcard.WildcardCarrierRules.isResetRecipe("other:machine_wildcard_pattern_reset"),"reset exclusion does not hide unrelated recipes");
        check(io.github.sexileman.eae2pattern.wildcard.WildcardCarrierRules.maxStackSize(false)==64,"NBT-free carriers stack to 64");
        check(io.github.sexileman.eae2pattern.wildcard.WildcardCarrierRules.maxStackSize(true)==1,"any NBT keeps carriers unstackable");
        for(int count:new int[]{1,2,64})check(io.github.sexileman.eae2pattern.wildcard.WildcardCarrierRules.validCount(count,false),"valid blank carrier count "+count);
        for(int count:new int[]{0,65})check(!io.github.sexileman.eae2pattern.wildcard.WildcardCarrierRules.validCount(count,false),"invalid blank carrier count "+count);
        check(!io.github.sexileman.eae2pattern.wildcard.WildcardCarrierRules.validCount(2,true),"tagged stack cannot duplicate encoded data");
        var one=io.github.sexileman.eae2pattern.wildcard.WildcardCarrierRules.writePlan(1,true,-1);
        check(!one.splits()&&one.destination()==-1,"single encoded carrier updates in place with full inventory");
        var stack=io.github.sexileman.eae2pattern.wildcard.WildcardCarrierRules.writePlan(64,false,7);
        check(stack.splits()&&stack.remainingBlanks()==63&&stack.destination()==7,"encode exactly one blank and keep 63 in hand");
        var two=io.github.sexileman.eae2pattern.wildcard.WildcardCarrierRules.writePlan(2,false,0);
        check(two.remainingBlanks()==1,"two blanks leave exactly one after encoding");
        for(int slot:new int[]{-1,36}){boolean failed=false;try{io.github.sexileman.eae2pattern.wildcard.WildcardCarrierRules.writePlan(64,false,slot);}catch(IllegalArgumentException expected){failed=true;}check(failed,"full/invalid destination rejects before inventory mutation "+slot);}
        boolean failed=false;try{io.github.sexileman.eae2pattern.wildcard.WildcardCarrierRules.writePlan(2,true,7);}catch(IllegalArgumentException expected){failed=true;}check(failed,"multi-item tagged write has no allocation plan");
        check(io.github.sexileman.eae2pattern.wildcard.HeldWildcardFingerprint.positionMatches(true,2,2,true,64,false),"stacked blank main-hand token uses original slot");
        check(!io.github.sexileman.eae2pattern.wildcard.HeldWildcardFingerprint.positionMatches(true,2,3,true,64,false),"switching main-hand slot rejects stacked encoding");
        check(io.github.sexileman.eae2pattern.wildcard.HeldWildcardFingerprint.positionMatches(false,-1,3,true,64,false),"stacked off-hand encoding ignores selected main-hand slot");
        check(!io.github.sexileman.eae2pattern.wildcard.HeldWildcardFingerprint.positionMatches(false,-1,3,true,2,true),"encoded items remain singular in both hands");
        check(io.github.sexileman.eae2pattern.client.OptionalPinyinSearch.contains("Iron Plate","PLATE"),"without optional JEC ordinary matching stays case-insensitive");
        check(io.github.sexileman.eae2pattern.client.OptionalPinyinSearch.contains("铁板","铁"),"without optional JEC Chinese literal matching works");
        check(!io.github.sexileman.eae2pattern.client.OptionalPinyinSearch.contains("铁板","tb"),"without JEC no bundled pinyin implementation is loaded");
    }
    private static void dynamicRules() throws Exception {
        var cache=new io.github.sexileman.eae2pattern.wildcard.WeightedCache<String,String>(2,10);
        cache.put("a","A",4);cache.put("b","B",4);check(cache.get("a").equals("A"),"LRU read retains cached value");
        cache.put("c","C",4);check(cache.get("b")==null&&cache.get("a")!=null,"LRU evicts the least recently used entry");
        check(cache.size()==2&&cache.weight()==8,"cache maintains entry and weight bounds");
        cache.put("large","never",11);check(cache.get("large")==null&&cache.weight()==8,"oversized entries are not retained");
        cache.put("a","changed",9);check(cache.get("c")==null&&cache.weight()==9,"replacement updates weight and evicts neighbors");
        cache.clear();check(cache.size()==0&&cache.weight()==0,"reload clears retained entries and accounting");
        var ingot=StackSpec.of(StackType.ITEM,new ResourceLocation("test:iron_ingot"),2);
        var plate=StackSpec.of(StackType.ITEM,new ResourceLocation("test:iron_plate"),1);
        var tool=StackSpec.of(StackType.ITEM,new ResourceLocation("other:circuit"),1);
        var fluid=new StackSpec(StackType.FLUID,new ResourceLocation("test:solder"),144);
        var tag=new CompoundTag();tag.putInt("configuration",24);tool=new StackSpec(StackType.ITEM,tool.id(),tag,1);
        var raw=new io.github.sexileman.eae2pattern.wildcard.RuleRecipe(new ResourceLocation("test:bend"),List.of(new io.github.sexileman.eae2pattern.wildcard.RuleRecipe.Slot(ingot,1),new io.github.sexileman.eae2pattern.wildcard.RuleRecipe.Slot(tool,0),new io.github.sexileman.eae2pattern.wildcard.RuleRecipe.Slot(fluid,.1f)),List.of(new io.github.sexileman.eae2pattern.wildcard.RuleRecipe.Slot(plate,.2f),new io.github.sexileman.eae2pattern.wildcard.RuleRecipe.Slot(plate.withAmount(3),1)));
        var spec=raw.encodeSpec(8,0,false);
        check(spec.inputs().get(0).amount()==16&&spec.inputs().get(2).amount()==1152,"dynamic multiplier scales items and fluid mB");
        check(spec.inputs().get(1).amount()==1&&spec.inputs().get(1).tag().getInt("configuration")==24,"dynamic tool retains circuit NBT and does not scale");
        check(raw.encodeSpec(8,0,true).inputs().size()==2,"dynamic drop-tools only removes non-consumed input");
        check(raw.encodeSpec(1,1,true).outputs().size()==1&&raw.encodeSpec(1,1,true).outputs().get(0).amount()==3,"dynamic guaranteed-only policy uses source chance");
        check(raw.encodeSpec(1,2,true).outputs().get(0).amount()==3,"dynamic main output is the first guaranteed output");
        var allChance=new io.github.sexileman.eae2pattern.wildcard.RuleRecipe(raw.id(),raw.inputs(),List.of(raw.outputs().get(0)));
        check(allChance.encodeSpec(1,2,true)==null,"main-only dynamic recipe rejects all-chance outputs");
        check(allChance.encodeSpec(1,0,true).outputs().size()==1,"all-output dynamic policy retains chance products");
        var noInput=new io.github.sexileman.eae2pattern.wildcard.RuleRecipe(raw.id(),List.of(new io.github.sexileman.eae2pattern.wildcard.RuleRecipe.Slot(tool,0)),raw.outputs());
        check(noInput.encodeSpec(1,0,true)==null,"dynamic recipe with only removed tools is not advertised");
        var huge=new io.github.sexileman.eae2pattern.wildcard.RuleRecipe(raw.id(),List.of(new io.github.sexileman.eae2pattern.wildcard.RuleRecipe.Slot(ingot.withAmount(Long.MAX_VALUE),1)),raw.outputs());
        boolean rejected=false;try{huge.encodeSpec(2,0,true);}catch(ArithmeticException expected){rejected=true;}check(rejected,"dynamic scaling rejects overflow instead of wrapping");
        check(new io.github.sexileman.eae2pattern.wildcard.PortableRuleQuery("&iron_ingot @test").matches(List.of(ingot)),"portable terms combine with AND");
        check(!new io.github.sexileman.eae2pattern.wildcard.PortableRuleQuery("&iron_ingot @other").matches(List.of(ingot)),"portable mod filter checks actual source side");
        check(new io.github.sexileman.eae2pattern.wildcard.PortableRuleQuery("&absent | &*plate").matches(List.of(plate)),"portable OR and wildcard match IDs");
        check(new io.github.sexileman.eae2pattern.wildcard.PortableRuleQuery("&iron_?ngot -@other").matches(List.of(ingot)),"portable wildcard and negation semantics");
        check(!new io.github.sexileman.eae2pattern.wildcard.PortableRuleQuery("&iron_ingot -@other").matches(List.of(ingot,tool)),"portable exclusions inspect the entire side");
        check(new io.github.sexileman.eae2pattern.wildcard.PortableRuleQuery("  ").matches(List.of()),"empty portable condition accepts empty side");
        for(String bad:List.of("铁锭","#tooltip","%tools","^red","$")){rejected=false;try{new io.github.sexileman.eae2pattern.wildcard.PortableRuleQuery(bad);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"non-portable dynamic query rejected: "+bad);}
        var rules=new CompoundTag();rules.putString("in_include","&iron_ingot");rules.putString("out_include","&iron_plate");rules.putBoolean("drop_tools",true);rules.putInt("multiplier",2);rules.putBoolean("dynamic",true);
        check(new io.github.sexileman.eae2pattern.wildcard.DynamicRule(rules).matches(raw),"portable dynamic rule matches both source sides");
        rules.putBoolean("in_all",true);check(!new io.github.sexileman.eae2pattern.wildcard.DynamicRule(rules).matches(raw),"every-slot dynamic input rejects unmatched fluid");
        rules.putInt("in_scope",1);check(new io.github.sexileman.eae2pattern.wildcard.DynamicRule(rules).matches(raw),"item-only search excludes fluid but keeps recipe fluid encoding");
        rules.putBoolean("search_tools",true);check(!new io.github.sexileman.eae2pattern.wildcard.DynamicRule(rules).matches(raw),"search-tools includes auxiliary input in advanced every-slot condition");
        rules.putBoolean("in_all",false);rules.putString("out_exclude","@test");check(!new io.github.sexileman.eae2pattern.wildcard.DynamicRule(rules).matches(raw),"dynamic output exclusion wins");rules.putString("out_exclude","");
        var excluded=new net.minecraft.nbt.ListTag();excluded.add(net.minecraft.nbt.StringTag.valueOf(raw.id().toString()));excluded.add(excluded.get(0).copy());rules.put("exclude_ids",excluded);
        check(!new io.github.sexileman.eae2pattern.wildcard.DynamicRule(rules).matches(raw),"manual dynamic recipe exception overrides rules");
        check(io.github.sexileman.eae2pattern.wildcard.WildcardEditorState.clean(rules).getList("exclude_ids",8).size()==1,"duplicate exceptions are canonicalized");
        rules.remove("exclude_ids");
        var machine=new ResourceLocation("test:bender");var data=io.github.sexileman.eae2pattern.wildcard.MachineWildcardData.dynamic(machine,rules,200);
        check(data.getInt("version")==3&&io.github.sexileman.eae2pattern.wildcard.MachineWildcardData.validate(data).equals(machine),"dynamic v3 rules round trip");
        check(!data.contains("payload")&&!data.contains("recipes")&&!data.toString().contains("fingerprint"),"dynamic item stores no concrete recipes or encoded child patterns");
        check(io.github.sexileman.eae2pattern.wildcard.MachineWildcardData.bytes(data)<2048,"simple dynamic item stays below 2 KB for 200 recipes");
        check(io.github.sexileman.eae2pattern.wildcard.MachineWildcardData.bytes(data)==io.github.sexileman.eae2pattern.wildcard.MachineWildcardData.bytes(io.github.sexileman.eae2pattern.wildcard.MachineWildcardData.dynamic(machine,rules,2048)),"rule payload does not grow with recipe count");
        for(int bad:new int[]{-1,2049}){rejected=false;try{io.github.sexileman.eae2pattern.wildcard.MachineWildcardData.dynamic(machine,rules,bad);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"dynamic initial count boundary: "+bad);}
        for(String badKey:List.of("in_form","material")){var bad=rules.copy();bad.putString(badKey,"INVALID");rejected=false;try{io.github.sexileman.eae2pattern.wildcard.WildcardEditorState.clean(bad);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"invalid structured condition rejected: "+badKey);}
        var forms=io.github.sexileman.eae2pattern.wildcard.MaterialForms.formsFromTags(List.of("forge:ingots/iron","c:plates/iron","other:gears/iron","forge:ingot"));
        check(forms.equals(java.util.Set.of("ingots","plates")),"forms use registered common tag families, not guessed singular names or arbitrary namespaces");
        check(io.github.sexileman.eae2pattern.wildcard.MaterialForms.materialsFromTags(List.of("forge:ingots/iron","c:plates/iron","forge:ingots/rubber")).equals(java.util.Set.of("iron","rubber")),"material candidates are extracted without user typing tags");
        check(io.github.sexileman.eae2pattern.wildcard.MaterialForms.formsFromTags(List.of("forge:plates","forge:plates/double/iron")).equals(java.util.Set.of("double_plates")),"nested common forms do not match generic plates");
        check(io.github.sexileman.eae2pattern.wildcard.MaterialForms.materialsFromTags(List.of("forge:plates/double/iron","forge:plates/double")).equals(java.util.Set.of("iron")),"nested form paths extract the actual material rather than double/iron");
        check(!io.github.sexileman.eae2pattern.wildcard.PortableRuleQuery.globContains("a".repeat(1000),"*a".repeat(100)+"b"),"adversarial wildcard sequence terminates without regex backtracking");
        check(io.github.sexileman.eae2pattern.wildcard.PortableRuleQuery.globContains("prefix_iron_ingot_suffix","iron_?ngot"),"portable glob retains substring matching");
        check(((Number)io.github.sexileman.eae2pattern.wildcard.ServerRecipeReader.member(new PublicContent(),"chance")).intValue()==1000,"server reader handles older public-field content");
        check(((Number)io.github.sexileman.eae2pattern.wildcard.ServerRecipeReader.member(new PublicContentRecord(1000),"chance")).intValue()==1000,"server reader handles record accessors");
    }
    public static final class PublicContent {public int chance=1000;}
    public record PublicContentRecord(int chance){}
    private static void heldWildcardWrites() {
        CompoundTag first = new CompoundTag(); first.putString("id", "eae2_pattern:machine_wildcard_pattern"); first.putByte("Count", (byte) 1);
        CompoundTag second = new CompoundTag(); second.putByte("Count", (byte) 1); second.putString("id", "eae2_pattern:machine_wildcard_pattern");
        byte[] token = io.github.sexileman.eae2pattern.wildcard.HeldWildcardFingerprint.ofTag(first);
        check(java.util.Arrays.equals(token, io.github.sexileman.eae2pattern.wildcard.HeldWildcardFingerprint.ofTag(second)), "client/server NBT insertion order does not change the held token");
        second.putString("different", "changed");
        check(!java.util.Arrays.equals(token, io.github.sexileman.eae2pattern.wildcard.HeldWildcardFingerprint.ofTag(second)), "changed held NBT cannot reuse the old write token");
        check(io.github.sexileman.eae2pattern.wildcard.HeldWildcardFingerprint.positionMatches(true, 2, 2, true, 1), "original main-hand carrier can be written");
        check(!io.github.sexileman.eae2pattern.wildcard.HeldWildcardFingerprint.positionMatches(true, 2, 3, true, 1), "switched hotbar slot rejects main-hand write");
        check(io.github.sexileman.eae2pattern.wildcard.HeldWildcardFingerprint.positionMatches(false, -1, 3, true, 1), "off-hand writes are independent of selected hotbar slot");
        check(!io.github.sexileman.eae2pattern.wildcard.HeldWildcardFingerprint.positionMatches(false, -1, 3, false, 1), "unrelated item cannot be overwritten");
        check(!io.github.sexileman.eae2pattern.wildcard.HeldWildcardFingerprint.positionMatches(true, 2, 2, true, 2), "stacked carriers cannot create duplicated encoded items");
        CompoundTag old = new CompoundTag(); old.putInt("other_mod", 42);
        CompoundTag oldDisplay = new CompoundTag(); oldDisplay.putString("Lore", "keep"); old.put("display", oldDisplay);
        CompoundTag oldGtl = new CompoundTag(); oldGtl.putInt("other_gtl", 7); old.put("gtlcore", oldGtl);
        CompoundTag encoded = new CompoundTag(); encoded.put(io.github.sexileman.eae2pattern.wildcard.MachineWildcardData.KEY, new CompoundTag());
        encoded.put("editor", new CompoundTag()); encoded.putString("encodePlayer", "serverPlayer");
        CompoundTag display = new CompoundTag(); display.putString("Name", "new"); encoded.put("display", display);
        var merged = io.github.sexileman.eae2pattern.wildcard.HeldWildcardFingerprint.mergeEncodingTags(old, encoded);
        check(merged.getInt("other_mod") == 42 && merged.getCompound("gtlcore").getInt("other_gtl") == 7, "writing preserves unrelated addon data");
        check(merged.getCompound("display").getString("Lore").equals("keep") && merged.getCompound("display").getString("Name").equals("new"), "writing replaces owned title while preserving other display data");
        check(!old.contains(io.github.sexileman.eae2pattern.wildcard.MachineWildcardData.KEY), "encoding merge does not mutate the original snapshot");
        CompoundTag rules = new CompoundTag(); rules.putByteArray("held_fingerprint", token);
        for (int operation : new int[]{5, 6}) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                new C2SGeneratePatternsPacket(10, 0, 1, List.of(), operation, rules).encode(buffer);
                var decoded = C2SGeneratePatternsPacket.decode(buffer);
                check(decoded.operation() == operation && java.util.Arrays.equals(decoded.rules().getByteArray("held_fingerprint"), token), "held target survives operation " + operation);
            } finally { buffer.release(); }
        }
        rules.putByteArray("held_fingerprint", new byte[31]); boolean rejected = false;
        try { io.github.sexileman.eae2pattern.wildcard.WildcardEditorState.clean(rules); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "malformed held target is rejected");
    }
    private static void machineWildcards() {
        java.util.function.Predicate<List<String>> iron = list -> list.contains("iron");
        java.util.function.Predicate<List<String>> rubber = list -> list.contains("rubber");
        check(io.github.sexileman.eae2pattern.client.WildcardRules.evaluate(List.of("iron", "circuit"), false, iron, rubber), "whole-side include permits other recipe ingredients");
        check(!io.github.sexileman.eae2pattern.client.WildcardRules.evaluate(List.of("iron", "rubber"), false, iron, rubber), "exclude wins without deleting a recipe ingredient");
        check(!io.github.sexileman.eae2pattern.client.WildcardRules.evaluate(List.of("iron", "circuit"), true, iron, null), "every-slot mode differs from whole-side mode");
        check(io.github.sexileman.eae2pattern.client.WildcardRules.evaluate(List.of("iron", "iron"), true, iron, null), "all scoped ingredients may satisfy the include rule");
        check(!io.github.sexileman.eae2pattern.client.WildcardRules.evaluate(List.of(), false, iron, null), "empty search scope cannot satisfy positive rules");
        check(io.github.sexileman.eae2pattern.client.WildcardRules.evaluate(List.of(), false, null, rubber), "exclude-only rules accept an empty search scope");
        check(!io.github.sexileman.eae2pattern.client.WildcardRules.evaluate(List.of(), false, null, rubber.negate()), "negative-only exclusions also apply to an empty search scope");
        var jsonA = com.google.gson.JsonParser.parseString("{\"out\":[2,1],\"input\":{\"b\":2,\"a\":1}}");
        var jsonB = com.google.gson.JsonParser.parseString("{\"input\":{\"a\":1,\"b\":2},\"out\":[2,1]}");
        check(io.github.sexileman.eae2pattern.wildcard.CanonicalRecipeJson.encode(jsonA).equals(io.github.sexileman.eae2pattern.wildcard.CanonicalRecipeJson.encode(jsonB)), "GT capability map order cannot change fingerprint after restart");
        var jsonChanged = com.google.gson.JsonParser.parseString("{\"out\":[1,2],\"input\":{\"a\":1,\"b\":2}}");
        check(!io.github.sexileman.eae2pattern.wildcard.CanonicalRecipeJson.encode(jsonA).equals(io.github.sexileman.eae2pattern.wildcard.CanonicalRecipeJson.encode(jsonChanged)), "ingredient and output array order remains significant");
        var a = new ResourceLocation("test", "machine_a");
        var b = new ResourceLocation("test", "machine_b");
        byte[] fingerprint = new byte[32]; fingerprint[0] = 1;
        check(io.github.sexileman.eae2pattern.wildcard.MachineWildcardData.matches(a, a, fingerprint, fingerprint.clone()), "real recipe remains usable in its source machine");
        check(!io.github.sexileman.eae2pattern.wildcard.MachineWildcardData.matches(a, b, fingerprint, fingerprint), "iron and rubber cannot cross machine types despite identical ingot-to-plate shape");
        check(!io.github.sexileman.eae2pattern.wildcard.MachineWildcardData.matches(a, null, fingerprint, fingerprint), "removed recipe cannot expand");
        check(!io.github.sexileman.eae2pattern.wildcard.MachineWildcardData.matches(a, a, fingerprint, new byte[32]), "changed source recipe fails closed");
        CompoundTag row = new CompoundTag(); row.putString("recipe", "test:iron_plate");
        row.putByteArray("fingerprint", fingerprint); row.put("pattern", new CompoundTag());
        var data = io.github.sexileman.eae2pattern.wildcard.MachineWildcardData.create(a, List.of(row));
        check(io.github.sexileman.eae2pattern.wildcard.MachineWildcardData.validate(data).equals(a), "machine wildcard schema round trip");
        row.putString("recipe", "test:mutated");
        check(io.github.sexileman.eae2pattern.wildcard.MachineWildcardData.readRecipes(data).getCompound(0).getString("recipe").equals("test:iron_plate"), "wildcard owns its compressed entry NBT");
        for (int bad = 0; bad < 4; bad++) {
            CompoundTag invalid = data.copy();
            if (bad == 0) invalid.putInt("version", 99);
            if (bad == 1) invalid.putString("machine", "INVALID ID");
            if (bad == 2) invalid.putInt("count", 2);
            if (bad == 3) invalid.putByteArray("payload", new byte[]{1,2,3});
            boolean rejected = false;
            try { io.github.sexileman.eae2pattern.wildcard.MachineWildcardData.validate(invalid); }
            catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "reject malformed compressed wildcard " + bad);
        }
        var maxRows = new java.util.ArrayList<CompoundTag>();
        for (int i = 0; i < 2049; i++) { var next = row.copy(); next.putString("recipe", "test:recipe_" + i); maxRows.add(next); }
        var full = io.github.sexileman.eae2pattern.wildcard.MachineWildcardData.create(a, maxRows.subList(0, 2048));
        check(io.github.sexileman.eae2pattern.wildcard.MachineWildcardData.readRecipes(full).size() == 2048, "one independent item supports 2048 real recipes");
        check(io.github.sexileman.eae2pattern.wildcard.MachineWildcardData.bytes(full) < 100000, "2048 simple recipes compress to a bounded item payload");
        boolean rejected = false;
        try { io.github.sexileman.eae2pattern.wildcard.MachineWildcardData.create(a, maxRows); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "2049 recipes require another item");
        rejected = false;
        try { io.github.sexileman.eae2pattern.wildcard.MachineWildcardData.create(a, List.of(row, row)); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "duplicate source IDs are rejected before compression");
        var legacy = new CompoundTag(); legacy.putInt("version", 1); legacy.putString("machine", a.toString());
        var legacyRows = new net.minecraft.nbt.ListTag(); legacyRows.add(row.copy()); legacy.put("recipes", legacyRows);
        check(io.github.sexileman.eae2pattern.wildcard.MachineWildcardData.readRecipes(legacy).size() == 1, "v1 bound item payload can still be read");
        var state = new CompoundTag(); state.putString("in_include", "$forge:ingots"); state.putString("out_exclude", "rubber"); state.putInt("multiplier", 8);
        var clean = io.github.sexileman.eae2pattern.wildcard.WildcardEditorState.clean(state);
        check(clean.getString("out_exclude").equals("rubber") && clean.getInt("multiplier") == 8, "independent input/output rules persist");
        state.putString("in_include", "x".repeat(257)); rejected = false;
        try { io.github.sexileman.eae2pattern.wildcard.WildcardEditorState.clean(state); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "oversized editor rules are rejected");
        var oversized = data.copy(); oversized.putByteArray("payload", new byte[512001]); rejected = false;
        try { io.github.sexileman.eae2pattern.wildcard.MachineWildcardData.readRecipes(oversized); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "compressed-size guard runs before decompression");
        try {
            var out = new java.io.ByteArrayOutputStream();
            try (var gzip = new java.util.zip.GZIPOutputStream(out)) {
                byte[] zeroes = new byte[10000]; for (int i = 0; i < 401; i++) gzip.write(zeroes);
            }
            var bomb = data.copy(); bomb.putByteArray("payload", out.toByteArray()); rejected = false;
            try { io.github.sexileman.eae2pattern.wildcard.MachineWildcardData.readRecipes(bomb); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "small compressed input cannot expand beyond the 4 MB raw bound");
        } catch (java.io.IOException unexpected) { throw new AssertionError(unexpected); }
        FriendlyByteBuf wire = new FriendlyByteBuf(Unpooled.buffer());
        try {
            io.github.sexileman.eae2pattern.config.EAE2PatternConfig.SPEC.setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
            new C2SGeneratePatternsPacket(8, 0, 1, List.of(), 4, clean).encode(wire);
            var packet = C2SGeneratePatternsPacket.decode(wire);
            check(packet.operation() == 4 && packet.rules().getString("out_exclude").equals("rubber"), "wildcard editor rules survive packet round trip");
        } finally { wire.release(); }
    }
    private static void amountLabels() {
        check(io.github.sexileman.eae2pattern.client.StackAmountLabel.format(1, false).isEmpty(), "single item has no count label");
        check(io.github.sexileman.eae2pattern.client.StackAmountLabel.format(64, false).equals("64"), "item count");
        check(io.github.sexileman.eae2pattern.client.StackAmountLabel.format(1, true).equals("1mB"), "single millibucket retains unit");
        check(io.github.sexileman.eae2pattern.client.StackAmountLabel.format(864, true).equals("864mB"), "fluid below bucket");
        check(io.github.sexileman.eae2pattern.client.StackAmountLabel.format(1000, true).equals("1B"), "bucket boundary");
        check(io.github.sexileman.eae2pattern.client.StackAmountLabel.format(2900, true).equals("2.9B"), "fractional bucket");
        check(io.github.sexileman.eae2pattern.client.StackAmountLabel.format(1000000, true).equals("1kB"), "large fluid quantity");
        check(io.github.sexileman.eae2pattern.client.StackAmountLabel.format(Long.MAX_VALUE, true).endsWith("PB"), "long amount does not overflow");
    }
    private static void patternMetadata() {
        CompoundTag root = new CompoundTag();
        root.putString("in", "ae2 payload");
        CompoundTag addon = new CompoundTag(); addon.putInt("other", 42); root.put("gtlcore", addon);
        ResourceLocation bender = new ResourceLocation("gtceu:bender");
        PatternMetadata.write(root, "crydawny", bender);
        check(root.getString("encodePlayer").equals("crydawny"), "ExtendedAE Plus native encoder key");
        check(root.getCompound("gtlcore").getList("patternQuickUploadRecipeTypes", 8).getString(0).equals("gtceu:bender"), "GTLCore native recipe type list");
        check(root.getString("in").equals("ae2 payload") && root.getCompound("gtlcore").getInt("other") == 42, "preserve AE2 and unrelated addon NBT");
        PatternMetadata.write(root, "serverPlayer", bender);
        check(root.getString("encodePlayer").equals("serverPlayer"), "server encoder replaces stale attribution");
        check(root.getCompound("gtlcore").getList("patternQuickUploadRecipeTypes", 8).size() == 1, "idempotent recipe type metadata");
        CompoundTag before = root.copy(); PatternMetadata.write(root, null, null);
        check(root.equals(before), "absent optional metadata does not erase existing data");
        CompoundTag absent = new CompoundTag(); PatternMetadata.write(absent, "", null);
        check(absent.isEmpty(), "no blank attribution or invented machine type");
    }
    private static StackSpec stack(String id, long amount) { return StackSpec.of(StackType.ITEM, new ResourceLocation(id), amount); }
    private static RecipePlan.Entry entry(List<Ingredient> inputs, List<Ingredient> tools, List<Ingredient> outputs) {
        // The icon is deliberately absent: these domain tests do not start Forge or initialize item registries.
        return new RecipePlan.Entry(PatternKind.PROCESSING, new ResourceLocation("test:recipe"), Component.literal("test"), null, inputs, tools, outputs, true);
    }
    private static void policies() {
        CompoundTag tag = new CompoundTag(); tag.putInt("Configuration", 24);
        StackSpec circuit = new StackSpec(StackType.ITEM, new ResourceLocation("gtceu:programmed_circuit"), tag, 1);
        StackSpec ingot = stack("test:ingot", 2), plate = stack("test:plate", 1);
        RecipePlan.Entry e = entry(List.of(Ingredient.consumed(ingot), Ingredient.notConsumed(circuit)),
                List.of(Ingredient.notConsumed(stack("test:mold", 1))),
                List.of(Ingredient.produced(stack("test:dust", 1), .25f), Ingredient.produced(plate, 1)));
        check(e.mainOutputIndex() == 1, "first guaranteed output, even after chance output");
        check(e.hasProbabilisticByproduct(), "chance output before main is a byproduct");
        PatternSpec filtered = e.specForPolicy(8, 1, true);
        check(filtered.inputs().size() == 1 && filtered.inputs().get(0).amount() == 16, "exclude tools and multiply consumed inputs");
        check(filtered.outputs().size() == 1 && filtered.outputs().get(0).amount() == 8, "guaranteed output policy");
        PatternSpec kept = e.specForPolicy(8, 0, false);
        check(kept.inputs().size() == 3, "include separate catalyst slots");
        check(kept.inputs().get(1).amount() == 1 && kept.inputs().get(2).amount() == 1, "do not multiply non-consumed inputs");
        check(kept.inputs().get(1).tag().getInt("Configuration") == 24, "configured circuit NBT survives policy");
        RecipePlan.Entry allChance = entry(List.of(Ingredient.consumed(ingot)), List.of(), List.of(Ingredient.produced(plate, .1f)));
        check(allChance.allOutputsProbabilistic() && allChance.hasProbabilisticByproduct(), "all outputs are chance byproducts");
        check(allChance.specForPolicy(1, 1, true) == null && allChance.specForPolicy(1, 2, true) == null, "empty-output policies skip recipe");
        check(allChance.specForPolicy(1, 0, true) != null, "retain all-chance recipes with all-output policy");
        RecipePlan.Entry onlyTool = entry(List.of(Ingredient.notConsumed(circuit)), List.of(), List.of(Ingredient.produced(plate, 1)));
        check(!onlyTool.canEncode(0, true), "skip empty-input processing pattern");
        check(e.isSelected(), "policy changes preserve selection");
        check(new RecipeFilters(ItemSearchQuery.empty(), ItemSearchQuery.empty(), 3, 0, false).rejects(e), "regular-input filter excludes non-consumed tools");
        check(!new RecipeFilters(ItemSearchQuery.empty(), ItemSearchQuery.empty(), 0, 4, false).rejects(allChance), "all-chance output filter includes no-main recipes");
        check(new RecipeFilters(ItemSearchQuery.empty(), ItemSearchQuery.empty(), 0, 1, false).rejects(allChance), "guaranteed-only filter rejects all-chance recipe");
        RecipePlan.Entry crafting = new RecipePlan.Entry(PatternKind.CRAFTING, new ResourceLocation("test:crafting"), Component.literal("crafting"), null,
                List.of(Ingredient.consumed(ingot)), List.of(), List.of(Ingredient.produced(plate, 1)), true);
        check(crafting.specForPolicy(64, 0, true).outputs().get(0).amount() == 1, "processing multiplier leaves crafting unchanged");
        RecipePlan.Entry manyInputs = entry(java.util.Collections.nCopies(82, Ingredient.consumed(ingot)), List.of(), List.of(Ingredient.produced(plate, 1)));
        check(!manyInputs.canEncode(0, false), "combined input count respects AE2 limit");
        check(Ingredient.produced(plate, .9999f).probabilistic(), "99.99 percent is not guaranteed");
        check(Ingredient.produced(plate, .0001f).chanceText().equals("0.01%"), "preserve tiny GT probabilities");
        tag.putInt("Configuration", 0);
        check(circuit.tag().getInt("Configuration") == 24, "own the source NBT");
    }
    private static void wireFormat() {
        CompoundTag tag = new CompoundTag(); tag.putString("large", "x".repeat(6000));
        PatternSpec spec = PatternSpec.processing(new ResourceLocation("test:wire"),
                List.of(new StackSpec(StackType.ITEM, new ResourceLocation("gtceu:circuit"), tag, 1)), List.of(stack("test:plate", 2)));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            spec.write(buf); check(buf.readableBytes() > 6000, "wire size includes NBT");
            check(PatternSpec.read(buf).equals(spec), "NBT / amount packet round trip");
            buf.clear(); buf.writeByte(1); buf.writeResourceLocation(new ResourceLocation("test:bad")); buf.writeVarInt(Integer.MAX_VALUE);
            boolean rejected = false;
            try { PatternSpec.read(buf); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "reject oversized slot count before allocating");
        } finally { buf.release(); }
    }
    private static void searches() throws Exception {
        Constructor<RecipePlan.Haystack> ctor = RecipePlan.Haystack.class.getDeclaredConstructor(List.class, List.class, List.class, List.class);
        ctor.setAccessible(true);
        var haystack = ctor.newInstance(List.of(), List.of("Double Iron Plate", "pipe|connector"), List.of("gtceu:iron_plate"), List.of("Greg Tech", "gtceu"));
        Method match = Class.forName(ItemSearchQuery.class.getName() + "$Alternative").getDeclaredMethod("matches", RecipePlan.Haystack.class);
        match.setAccessible(true);
        var alternatives = ItemSearchQuery.class.getDeclaredField("alternatives"); alternatives.setAccessible(true);
        for (String query : List.of("iron plate", "@\"Greg Tech\"", "&gtceu:iron_plate", "copper|iron", "-copper iron", "\"pipe|connector\"", "*Iron*")) {
            boolean found = false;
            for (Object branch : (List<?>) alternatives.get(ItemSearchQuery.parse(query))) if ((boolean) match.invoke(branch, haystack)) found = true;
            check(found, "search should match: " + query);
        }
        boolean found = false;
        for (Object branch : (List<?>) alternatives.get(ItemSearchQuery.parse("iron -plate"))) if ((boolean) match.invoke(branch, haystack)) found = true;
        check(!found, "excluded term vetoes an alternative");
    }
    public static final class OldContent { public int chance = 2500, maxChance = 10000; }
    public record NewContent(int chance, int maxChance) {}
    private static void layoutAndGrid() {
        for (int[] size : List.of(new int[]{320, 180}, new int[]{480, 241}, new int[]{480, 242}, new int[]{480, 270},
                new int[]{480, 271}, new int[]{480, 272}, new int[]{640, 331}, new int[]{640, 332}, new int[]{640, 360}, new int[]{1920, 1080})) {
            var l = io.github.sexileman.eae2pattern.client.WorkbenchLayout.measure(size[0], size[1]);
            check(l.bodyBottom() - l.bodyTop() >= 38, "at least one full multi-icon row at " + size[0] + "x" + size[1]);
            check(l.top() + l.toolbarY() + 19 < l.bodyTop(), "toolbar does not overlap list");
            check(l.footerTop() - l.bodyBottom() >= 5, "generation section has an explicit gap below the list");
            check(l.footerControlsY() >= l.footerTop() && l.footerControlsY() + 19 <= l.footerTop() + l.footerHeight(), "generation controls stay inside footer card");
            check(l.footerTop() + l.footerHeight() < l.statsY() && l.statsY() + 9 < l.top() + l.height(), "footer card and status summary do not overlap");
            check(l.left() >= 0 && l.left() + l.width() <= size[0], "panel stays on screen");
        }
        check(CraftingGrid.slot(2, 2) == 3 && CraftingGrid.slot(3, 2) == 4, "2x2 recipe row stride");
        check(CraftingGrid.slot(1, 1) == 3 && CraftingGrid.slot(2, 1) == 6, "1x3 recipe row stride");
        check(CraftingGrid.slot(8, 3) == 8, "full 3x3 grid");
        for (int count = 1; count <= 8; count++) {
            var popup = io.github.sexileman.eae2pattern.client.PopupLayout.measure(280, 150, 40, 19, 180, count, 320, 180);
            check(popup.x() >= 6 && popup.x() + popup.width() <= 314 && popup.y() >= 6 && popup.y() + popup.height() <= 174,
                    "popup stays inside minimum GUI canvas with " + count + " choices");
            check(popup.indexAt(popup.x() + 5, popup.y() + popup.height() - 4) == count - 1, "popup last row hit test");
            check(popup.indexAt(popup.x() - 1, popup.y() + 5) == -1 && popup.indexAt(popup.x() + 5, popup.y()) == -1, "popup border and outside click do not select");
        }
    }
    private static void gtVersions() throws Exception {
        Method chance = GtChanceReader.class.getDeclaredMethod("chanceOf", Object.class); chance.setAccessible(true);
        check((float) chance.invoke(null, new OldContent()) == .25f, "GT legacy public fields");
        check((float) chance.invoke(null, new NewContent(2500, 10000)) == .25f, "GT record accessors");
        check((float) chance.invoke(null, new NewContent(0, 10000)) == 0, "GT non-consumed chance");
    }

    private static void manualMultiplier() {
        int repeated = 1;
        for (int n = 0; n < 16; n++) repeated = ProcessingMultiplier.doubleAmount(repeated);
        check(repeated == ProcessingMultiplier.MAX, "repeated doubling reaches maximum");
        check(ProcessingMultiplier.doubleAmount(repeated) == repeated, "doubling caps without overflow");
        for (int n = 0; n < 16; n++) repeated = ProcessingMultiplier.halveAmount(repeated);
        check(repeated == 1 && ProcessingMultiplier.halveAmount(1) == 1, "repeated halving stops at one");
        check(ProcessingMultiplier.normalize(100) == 64 && ProcessingMultiplier.normalize(0) == 1, "legacy saved multiplier normalizes to valid button step");
        var buttonEntry = entry(List.of(Ingredient.consumed(stack("test:button_input", 3))), List.of(), List.of(Ingredient.produced(stack("test:button_output", 5), 1)));
        var buttonSpec = buttonEntry.specForPolicy(ProcessingMultiplier.doubleAmount(ProcessingMultiplier.doubleAmount(1)), 0, true);
        check(buttonSpec.inputs().get(0).amount() == 12 && buttonSpec.outputs().get(0).amount() == 20, "button multiplier scales both inputs and outputs");
        check(ProcessingMultiplier.parse("1") == ProcessingMultiplier.DEFAULT, "default multiplier is one");
        check(ProcessingMultiplier.parse("100") == 100, "manual non-preset multiplier");
        check(ProcessingMultiplier.parse("65536") == ProcessingMultiplier.MAX, "maximum manual multiplier");
        for (String invalid : List.of("", "0", "65537", "9999999999999", "-1", "1.5", "x", " 2", "２"))
            check(ProcessingMultiplier.parse(invalid) == 0, "invalid multiplier disables generation: " + invalid);
        check(ProcessingMultiplier.acceptsText(""), "allow clearing field while editing");
        check(!ProcessingMultiplier.acceptsText("-1") && !ProcessingMultiplier.acceptsText("100000"), "field rejects non-numeric or overlong text");
        RecipePlan.Entry e = entry(List.of(Ingredient.consumed(stack("test:input", 2))), List.of(), List.of(Ingredient.produced(stack("test:output", 3), 1)));
        PatternSpec scaled = e.specForPolicy(ProcessingMultiplier.parse("100"), 0, true);
        check(scaled.inputs().get(0).amount() == 200 && scaled.outputs().get(0).amount() == 300, "manual multiplier reaches generation specification");
    }

    private static void grouping() throws Exception {
        RecipePlan.Entry a = entry(List.of(Ingredient.consumed(stack("test:ingot", 1))), List.of(), List.of(Ingredient.produced(stack("test:plate", 1), 1)));
        RecipePlan.Entry b = entry(List.of(Ingredient.consumed(stack("test:block", 1))), List.of(), List.of(Ingredient.produced(stack("test:plate", 9), 1)));
        check(RecipeGroups.keys(a, 1).get(0).id().equals(RecipeGroups.keys(b, 1).get(0).id()), "same-output grouping ignores route and amount");
        var ctor = RecipePlan.Haystack.class.getDeclaredConstructor(List.class, List.class, List.class, List.class); ctor.setAccessible(true);
        var tags = RecipePlan.Haystack.class.getDeclaredField("tags"); tags.setAccessible(true);
        var outputs = RecipePlan.Entry.class.getDeclaredField("outputHaystack"); outputs.setAccessible(true);
        var inputs = RecipePlan.Entry.class.getDeclaredField("inputHaystack"); inputs.setAccessible(true);
        var outA = ctor.newInstance(List.of(), List.of(), List.of(), List.of()); tags.set(outA, List.of("forge:plates", "forge:plates/iron", "forge:plates")); outputs.set(a, outA);
        var outB = ctor.newInstance(List.of(), List.of(), List.of(), List.of()); tags.set(outB, List.of("forge:plates")); outputs.set(b, outB);
        var inA = ctor.newInstance(List.of(), List.of(), List.of(), List.of()); tags.set(inA, List.of("forge:ingots")); inputs.set(a, inA);
        check(RecipeGroups.keys(a, 2).size() == 2, "preserve all distinct output tags");
        check(RecipeGroups.keys(a, 3).get(0).id().equals("tag:forge:ingots"), "input tag grouping uses the input side");
        RecipeGroups.Builder builder = new RecipeGroups.Builder(List.of(a, b), 2);
        while (!builder.step()) { }
        var groups = builder.finish();
        check(groups.size() == 2, "one material may appear in multiple tag groups");
        var plateGroup = groups.stream().filter(g -> g.key().id().equals("tag:forge:plates")).findFirst().orElseThrow();
        check(plateGroup.entries().size() == 2, "tag group contains all matching routes without duplicate membership");
        var rows = RecipeGroups.rows(List.of(a, b), groups, java.util.Set.of("tag:forge:plates", "tag:forge:plates/iron"), 2);
        check(rows.stream().filter(r -> r.entry() == a).count() == 2, "recipe is visible under both matching tags");
        a.setSelected(false);
        check(rows.stream().filter(r -> r.entry() == a).noneMatch(r -> r.entry().isSelected()), "selection is shared across tags");
        RecipePlan plan = new RecipePlan(new ResourceLocation("test:machine"), Component.literal("machine"), PatternKind.PROCESSING, List.of(a, b));
        check(plan.selectedSpecs(1, false, true).size() == 1, "generation uses unique source entries, not repeated group rows");
        check(plan.selectedSpecs(List.of(a), 1, false, true).isEmpty() && plan.selectedCount() == 1, "hidden selected recipes are not generated by a filtered subset");
        check(RecipeGroups.rows(List.of(a, b), groups, java.util.Set.of(), 2).size() == 2, "collapsed groups retain headers only");
        check(RecipeGroups.rows(List.of(a, b), groups, java.util.Set.of(), 0).size() == 2, "ungrouped view shows every source recipe once");
        var empty = ctor.newInstance(List.of(), List.of(), List.of(), List.of()); tags.set(empty, List.of()); outputs.set(b, empty);
        check(RecipeGroups.keys(b, 2).get(0).id().equals("untagged"), "untagged recipes are retained");
        RecipeGroups.Builder sliced = new RecipeGroups.Builder(java.util.Collections.nCopies(3000, b), 1);
        int steps = 1; while (!sliced.step()) steps++;
        check(steps >= 3 && sliced.finish().get(0).entries().size() == 3000, "group construction is time-sliced");
        CompoundTag c24 = new CompoundTag(); c24.putInt("Configuration", 24);
        CompoundTag c0 = new CompoundTag(); c0.putInt("Configuration", 0);
        StackSpec first = new StackSpec(StackType.ITEM, new ResourceLocation("test:circuit"), c24, 1);
        StackSpec second = new StackSpec(StackType.ITEM, new ResourceLocation("test:circuit"), c0, 1);
        check(!RecipeDiagnostics.sameMaterial(first, second), "click filtering distinguishes circuit NBT");
        var circuitA = entry(List.of(Ingredient.consumed(stack("test:in", 1))), List.of(), List.of(Ingredient.produced(first, 1)));
        var circuitB = entry(List.of(Ingredient.consumed(stack("test:in", 1))), List.of(), List.of(Ingredient.produced(second, 1)));
        check(!RecipeGroups.keys(circuitA, 1).get(0).id().equals(RecipeGroups.keys(circuitB, 1).get(0).id()), "same-output groups distinguish NBT");
        check(RecipeDiagnostics.sameMaterial(first, first.withAmount(64)), "click filtering ignores display count");
        RecipePlan.Entry chance = entry(List.of(Ingredient.consumed(stack("test:in", 1))), List.of(), List.of(Ingredient.produced(stack("test:out", 1), .5f)));
        check(RecipeDiagnostics.skipReason(chance, 1, true).equals("skip_no_guaranteed"), "explain all-chance output exclusion");
    }

    private static void inspectionPackets() {
        PatternSpec spec = PatternSpec.processing(new ResourceLocation("test:preview"), List.of(stack("test:in", 8)), List.of(stack("test:out", 16)));
        S2CInspectionPacket original = new S2CInspectionPacket(7, 2, true, 1, 0, List.of(), List.of(spec), List.of("preview"));
        FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
        try {
            original.encode(b); check(S2CInspectionPacket.decode(b).equals(original), "canonical server preview packet round trip");
            b.clear(); new C2SGeneratePatternsPacket(8, 0, 1, List.of(spec), 1).encode(b);
            check(b.readUnsignedByte() == 1 && b.readVarInt() == 8, "read-only inspection operation is explicit on the wire");
            check(b.readVarInt() == 0 && b.readVarInt() == 1 && b.readVarInt() == 1 && PatternSpec.read(b).equals(spec), "inspection request preserves the full pattern specification");
        } finally { b.release(); }
    }

    private static void slotPresentation() {
        StackSpec material = stack("test:tool", 1);
        var nc = IngredientPresentation.of(Ingredient.notConsumed(material), 0);
        check(nc.kind() == IngredientPresentation.Kind.NOT_CONSUMED && nc.chance().isEmpty(), "non-consumed slot is not described as 0% consumption");
        check(nc.badge().equals("∞"), "non-consumed slot has a compact visual badge");
        var chanceInput = IngredientPresentation.of(Ingredient.consumed(material, .25f), 0);
        check(chanceInput.kind() == IngredientPresentation.Kind.CHANCE_INPUT && chanceInput.chance().equals("25%"), "chance input tooltip preserves exact chance");
        var chanceOutput = IngredientPresentation.of(Ingredient.produced(material, .125f), 1);
        check(chanceOutput.kind() == IngredientPresentation.Kind.CHANCE_OUTPUT && chanceOutput.chance().equals("12.5%"), "output probability uses production semantics");
        check(IngredientPresentation.of(Ingredient.produced(material, 0), 1).kind() == IngredientPresentation.Kind.CHANCE_OUTPUT, "zero-chance output is not a non-consumed input");
        check(IngredientPresentation.of(Ingredient.consumed(material), 0).equals(IngredientPresentation.REGULAR), "regular input does not get a probability badge");
        RecipePlan.Entry mixed = entry(List.of(Ingredient.notConsumed(material), Ingredient.consumed(material)), List.of(), List.of(Ingredient.produced(stack("test:out", 1), 1)));
        check(IngredientPresentation.encoded(mixed, material.withAmount(64), 0, true).equals(IngredientPresentation.REGULAR), "excluded tool cannot mislabel the remaining encoded material");
        check(IngredientPresentation.encoded(mixed, material.withAmount(65), 0, false).kind() == IngredientPresentation.Kind.MIXED, "merged consumption rules are identified instead of inventing a chance");
        RecipePlan.Entry tools = entry(List.of(), List.of(Ingredient.notConsumed(material)), List.of(Ingredient.produced(stack("test:out", 1), 1)));
        check(IngredientPresentation.encoded(tools, material, 0, false).kind() == IngredientPresentation.Kind.NOT_CONSUMED, "separate catalyst metadata survives encoded preview rendering");

        var rows = DetailRowLayout.of(List.of(false, true, false, true), Boolean::booleanValue);
        check(rows.height() == 96 && rows.top(1) == 18 && rows.top(2) == 48 && rows.top(3) == 66, "detail rows reserve full slot height without overlapping text");
        check(rows.indexAt(17.9) == 0 && rows.indexAt(18) == 1 && rows.indexAt(47.9) == 1 && rows.indexAt(48) == 2 && rows.indexAt(66) == 3, "detail tooltip hit tests follow the same offsets as rendering");
        check(rows.indexAt(-1) == -1 && rows.indexAt(96) == -1, "detail border and outside points do not select a row");
        var measured = DetailRowLayout.measured(List.of(18, 42, 65, 30), Integer::intValue);
        check(measured.height() == 155 && measured.top(2) == 60, "wrapped notice and special material contribute actual heights");
        check(measured.indexAt(59) == 1 && measured.indexAt(60) == 2 && measured.indexAt(124) == 2, "hit testing remains aligned through wrapped notice");
        check(measured.indexAt(125) == 3 && measured.indexAt(155) == -1, "last material and content boundary after notice");
        var empty = DetailRowLayout.of(List.of(), ignored -> false);
        check(empty.height() == 0 && empty.indexAt(0) == -1, "empty details have no stale tooltip targets");
    }
}
