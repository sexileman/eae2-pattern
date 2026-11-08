package io.github.sexileman.eae2pattern.cycle;
import java.util.*;
/** Finds an actual input back-edge in one selected planning ancestry, not a global recipe graph. */
public final class CyclePath {
    public static final int MAX_DEPTH=64,MAX_REPORTS=8,MAX_REJECTIONS=64;
    private CyclePath(){}
    public static <K> List<K> close(List<K> rootToLeaf,Collection<K> consumed) {
        if(rootToLeaf.isEmpty()||rootToLeaf.size()>MAX_DEPTH)return List.of();
        for(int i=rootToLeaf.size()-1;i>=0;i--)if(consumed.contains(rootToLeaf.get(i))){
            var path=new ArrayList<K>(rootToLeaf.subList(i,rootToLeaf.size()));path.add(rootToLeaf.get(i));return List.copyOf(path);
        }
        return List.of();
    }
}
