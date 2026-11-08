package io.github.sexileman.eae2pattern.pattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
/** Ignores only known display/provenance fields; preserves machine hints and every encoding flag. */
public final class PatternSignature {
    private PatternSignature(){}
    public static String of(ResourceLocation item,CompoundTag original){
        if(item==null)return "";var tag=original==null?new CompoundTag():original.copy();
        tag.remove("encodePlayer");tag.remove("display");
        var data=new CompoundTag();data.putString("item",item.toString());data.put("pattern",tag);
        return java.util.HexFormat.of().formatHex(io.github.sexileman.eae2pattern.wildcard.HeldWildcardFingerprint.ofTag(data));
    }
    public static String of(appeng.api.stacks.AEItemKey key){return key==null?"":of(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(key.getItem()),key.getTag());}
}
