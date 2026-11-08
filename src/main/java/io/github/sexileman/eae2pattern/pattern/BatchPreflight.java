package io.github.sexileman.eae2pattern.pattern;
import java.util.*;
import java.util.function.Function;
/** Incremental validation retains source references, not a batch of encoded NBT stacks. */
public final class BatchPreflight<T> {
    @FunctionalInterface public interface Validator<T>{void validate(T value)throws Exception;}
    private final List<T> source,valid=new ArrayList<>();
    private final List<T> view=Collections.unmodifiableList(valid);
    private final List<String> errors=new ArrayList<>();
    private final Function<T,String> name;
    private int cursor,rejected;
    public BatchPreflight(List<T> source,Function<T,String> name){this.source=List.copyOf(source);this.name=name;}
    public boolean done(){return cursor==source.size();}
    public void step(Validator<T> validator){
        if(done())return;T value=source.get(cursor++);
        try{validator.validate(value);valid.add(value);}
        catch(Exception invalid){rejected++;if(errors.size()<5){String message=name.apply(value)+": "+invalid.getMessage();errors.add(message.substring(0,Math.min(512,message.length())));}}
    }
    public int required(){if(!done())throw new IllegalStateException("Validation incomplete");return valid.size();}
    public boolean enough(long blanks){return done()&&blanks>=valid.size();}
    public List<T> valid(){if(!done())throw new IllegalStateException("Validation incomplete");return view;}
    public int rejected(){return rejected;}
    public List<String> errors(){return List.copyOf(errors);}
}
