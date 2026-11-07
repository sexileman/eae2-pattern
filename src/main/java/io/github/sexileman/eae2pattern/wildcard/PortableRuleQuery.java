package io.github.sexileman.eae2pattern.wildcard;
import io.github.sexileman.eae2pattern.pattern.StackSpec;
import java.util.*;
import java.util.regex.Pattern;
/** Language-independent dynamic filters: exact same evaluation on client and dedicated server. */
public final class PortableRuleQuery {
    private final List<List<Term>> alternatives=new ArrayList<>();
    private record Term(char kind,String text,boolean negative){}
    public PortableRuleQuery(String raw){
        for(String group:raw.split("\\|",-1)){
            var terms=new ArrayList<Term>();
            var matcher=Pattern.compile("\"[^\"]*\"|[^\\s]+" ).matcher(group);
            while(matcher.find()){
                String t=matcher.group().replace("\"","");boolean negative=t.startsWith("-");if(negative)t=t.substring(1);
                if(t.isEmpty())continue;char kind=t.charAt(0);if(kind!='&'&&kind!='$'&&kind!='@')throw new IllegalArgumentException("Dynamic search supports $tags, &IDs and @mod IDs; use fixed mode for translated names/tooltips");
                String value=t.substring(1).toLowerCase(Locale.ROOT);if(value.isEmpty())throw new IllegalArgumentException("Empty dynamic query term");
                terms.add(new Term(kind,value,negative));
            }if(!terms.isEmpty())alternatives.add(terms);
        }
    }
    /** Greedy glob matching avoids exponential regex backtracking for server-supplied rules. */
    public static boolean globContains(String text,String query){
        String pattern="*"+query+"*";int t=0,p=0,star=-1,retry=0;
        while(t<text.length()){
            if(p<pattern.length()&&(pattern.charAt(p)=='?'||pattern.charAt(p)==text.charAt(t))){p++;t++;}
            else if(p<pattern.length()&&pattern.charAt(p)=='*'){star=p++;retry=t;}
            else if(star>=0){p=star+1;t=++retry;}else return false;
        }
        while(p<pattern.length()&&pattern.charAt(p)=='*')p++;return p==pattern.length();
    }
    public boolean isEmpty(){return alternatives.isEmpty();}
    public boolean matches(List<StackSpec> stacks){
        if(isEmpty())return true;
        for(var group:alternatives){boolean yes=true;for(var t:group){boolean found=false;for(var s:stacks){
            Collection<String> values=switch(t.kind()){case '$'->MaterialForms.tags(s);case '@'->List.of(s.id().getNamespace());default->List.of(s.id().toString());};
            for(String v:values)if(globContains(v.toLowerCase(Locale.ROOT),t.text())){found=true;break;}if(found)break;
        }if(found==t.negative()){yes=false;break;}}if(yes)return true;}return false;
    }
}
