package io.github.sexileman.eae2pattern.pattern;
import java.util.*;
import java.util.function.Function;
/** A partially representable recipe is not a different, valid recipe. */
public final class CompleteIngredients {
    private CompleteIngredients(){}
    public static <S,T> List<T> map(List<S> source,Function<S,T> convert) {
        return map(source,convert,String::valueOf);
    }
    public static <S,T> List<T> map(List<S> source,Function<S,T> convert,Function<S,String> name) {
        var result=new ArrayList<T>(source.size());
        for(int i=0;i<source.size();i++){
            T value=convert.apply(source.get(i));
            if(value==null)throw new IllegalArgumentException("Unsupported ingredient at slot "+(i+1)+": "+name.apply(source.get(i)));
            result.add(value);
        }
        return List.copyOf(result);
    }
}
