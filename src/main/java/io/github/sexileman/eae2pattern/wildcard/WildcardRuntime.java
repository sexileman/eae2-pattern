package io.github.sexileman.eae2pattern.wildcard;
import appeng.api.crafting.*;
import io.github.sexileman.eae2pattern.pattern.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.*;
/** Cache immutable encoded data, never shared potentially mutable AE2 detail instances. */
public final class WildcardRuntime {
    private static final WeightedCache<String,List<CompoundTag>> CACHE=new WeightedCache<>(32,16_000_000);
    private static final class IdentityKey {
        final Recipe<?> recipe;final long scope;IdentityKey(Recipe<?> recipe,long scope){this.recipe=recipe;this.scope=scope;}
        public int hashCode(){return 31*System.identityHashCode(recipe)+Long.hashCode(scope);}
        public boolean equals(Object other){return other instanceof IdentityKey key&&key.recipe==recipe&&key.scope==scope;}
    }
    private record ReadResult(RuleRecipe recipe){}
    private static final WeightedCache<IdentityKey,ReadResult> READ=new WeightedCache<>(8192,8_000_000);
    private record Scope(java.lang.ref.WeakReference<RecipeManager> manager, long id) {}
    private record SourceKey(long scope,ResourceLocation type) {}
    private static final Map<Level,Scope> SCOPES = new WeakHashMap<>();
    private static long nextScope;
    private static final WeightedCache<SourceKey,List<Recipe<?>>> SOURCES = new WeightedCache<>(32,1_000_000);
    private static final java.util.concurrent.atomic.AtomicLong hits=new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong misses=new java.util.concurrent.atomic.AtomicLong();
    private static synchronized long scope(Level level) {
        var manager=level.getRecipeManager();var scope=SCOPES.get(level);
        if(scope==null||scope.manager().get()!=manager){
            scope=new Scope(new java.lang.ref.WeakReference<>(manager),++nextScope);SCOPES.put(level,scope);
        }
        return scope.id();
    }
    private WildcardRuntime(){}
    public static synchronized void clear(){CACHE.clear();READ.clear();SOURCES.clear();SCOPES.clear();}
    public static synchronized RuleRecipe read(Recipe<?> recipe,Level level){
        var key=new IdentityKey(recipe,scope(level));var cached=READ.get(key);if(cached!=null)return cached.recipe();
        var r=ServerRecipeReader.read(recipe,level);int cost=128;
        if(r!=null)for(var slots:List.of(r.inputs(),r.outputs()))for(var slot:slots)cost+=256+(slot.stack().tag()==null?0:MachineWildcardData.bytes(slot.stack().tag())*3);
        READ.put(key,new ReadResult(r),cost);return r;
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    public static synchronized List<Recipe<?>> recipes(Level level,ResourceLocation type){
        var sourceKey=new SourceKey(scope(level),type);
        var cached=SOURCES.get(sourceKey);if(cached!=null)return cached;
        var registry=net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE;
        if(!registry.containsKey(type))throw new IllegalArgumentException("Unknown machine recipe type");
        var list=new ArrayList<Recipe<?>>((List)level.getRecipeManager().getAllRecipesFor((RecipeType)registry.get(type)));
        list.sort(Comparator.comparing(r->r.getId().toString()));
        var snapshot=List.copyOf(list);SOURCES.put(sourceKey,snapshot,128+Math.multiplyExact(snapshot.size(),8));return snapshot;
    }
    public static String key(CompoundTag data,Level level){CompoundTag keyData=data;if(data.getInt("version")==3){keyData=data.copy();keyData.remove("count");keyData.getCompound("rules").remove("category");}
        return scope(level)+":"+level.dimension().location()+":"+java.util.HexFormat.of().formatHex(HeldWildcardFingerprint.ofTag(keyData));}
    public static void prime(CompoundTag data,Level level,List<CompoundTag> patterns){
        int weight=128;for(var p:patterns)weight=Math.addExact(weight,MachineWildcardData.bytes(p)*3+128);
        CACHE.put(key(data,level),patterns.stream().map(CompoundTag::copy).toList(),weight);
    }
    public static List<IPatternDetails> expand(CompoundTag data,Level level){
        String key=key(data,level);var encoded=CACHE.get(key);
        if(encoded==null){misses.incrementAndGet();try{encoded=resolve(data,level);prime(data,level,encoded);}catch(RuntimeException invalid){CACHE.put(key,List.of(),128);throw invalid;}}else hits.incrementAndGet();
        var result=new ArrayList<IPatternDetails>(encoded.size());
        for(var tag:encoded){var details=PatternDetailsHelper.decodePattern(ItemStack.of(tag.copy()),level);if(details!=null)result.add(details);}
        return result;
    }
    private static List<CompoundTag> resolve(CompoundTag data,Level level){
        var result=new ArrayList<CompoundTag>();var type=ResourceLocation.tryParse(data.getString("machine"));if(type==null)return List.of();
        if(data.getInt("version")==3){
            MachineWildcardData.validate(data);int bytes=0;
            var state=WildcardEditorState.clean(data.getCompound("rules"));var rule=new DynamicRule(state);
            for(var recipe:recipes(level,type)){
                var raw=read(recipe,level);if(raw==null||!rule.matches(raw))continue;
                var spec=raw.encodeSpec(state.getInt("multiplier"),state.getInt("output_policy"),state.getBoolean("drop_tools"));if(spec==null)continue;
                if(result.size()==MachineWildcardData.MAX_RECIPES)throw new IllegalArgumentException("Dynamic rule exceeds 2048 recipes; narrow the rule");
                try{var encoded=PatternEncoder.encode(level,spec).save(new CompoundTag());bytes+=MachineWildcardData.bytes(encoded);if(bytes>4_000_000)throw new IllegalArgumentException("Dynamic expansion exceeds 4 MB");result.add(encoded);}catch(PatternEncoder.EncodingException invalid){/* unsupported source is not advertised */}
            }
        }else{
            var rows=MachineWildcardData.readRecipes(data);
            for(int i=0;i<rows.size();i++){
                var row=rows.getCompound(i);var recipe=level.getRecipeManager().byKey(new ResourceLocation(row.getString("recipe"))).orElse(null);
                if(recipe==null||!MachineWildcardData.matches(type,MachineWildcard.type(recipe),row.getByteArray("fingerprint"),MachineWildcard.fingerprint(recipe)))continue;
                var tag=row.getCompound("pattern");if(tag.getString("id").equals("ae2:processing_pattern"))result.add(tag.copy());
            }
        }return result;
    }
    public static long cacheHits(){return hits.get();}public static long cacheMisses(){return misses.get();}
}
