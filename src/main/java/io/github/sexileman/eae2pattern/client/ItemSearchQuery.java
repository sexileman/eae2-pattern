package io.github.sexileman.eae2pattern.client;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One search box of the preview, following JEI's own query syntax.
 *
 * <p>JEI splits the query on {@code |} into alternatives, then splits each alternative on whitespace into terms
 * (quotes keep the spaces), and each term may start with a prefix that picks which haystack is searched. All
 * terms of one alternative have to match, any alternative is enough - the same rules are implemented here, so a
 * query that works in JEI behaves the same way in the preview.</p>
 *
 * <table>
 *   <tr><th>prefix</th><th>JEI searches</th><th>preview searches</th></tr>
 *   <tr><td>(none)</td><td>ingredient names</td><td>names of this side of the recipe</td></tr>
 *   <tr><td>{@code @}</td><td>mod names and mod ids</td><td>same, plus the short form JEI accepts</td></tr>
 *   <tr><td>{@code #}</td><td>tooltip text</td><td>tooltip text</td></tr>
 *   <tr><td>{@code $}</td><td>tags</td><td>item tags</td></tr>
 *   <tr><td>{@code &}</td><td>resource location</td><td>item id</td></tr>
 *   <tr><td>{@code %}</td><td>creative tabs</td><td>creative tabs</td></tr>
 *   <tr><td>{@code -}</td><td>exclude</td><td>exclude</td></tr>
 * </table>
 *
 * <p>{@code *} and {@code ?} are accepted as wildcards, which JEI does not have, and {@code ^} (colour) is not
 * supported - both are documented in the README.</p>
 */
public final class ItemSearchQuery {

    private static final ItemSearchQuery EMPTY = new ItemSearchQuery("", List.of());

    /** {@code -?"quoted term"|bareWord} - the same shape JEI's own filter uses. */
    private static final Pattern TERM_PATTERN = Pattern.compile("(-?\".*?(?:\"|$)|\\S+)");
    private static final Pattern QUOTES = Pattern.compile("\"");

    private final String raw;
    private final List<Alternative> alternatives;

    private ItemSearchQuery(String raw, List<Alternative> alternatives) {
        this.raw = raw;
        this.alternatives = alternatives;
    }

    public static ItemSearchQuery empty() {
        return EMPTY;
    }

    public static ItemSearchQuery parse(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return EMPTY;
        }

        List<Alternative> parsed = new ArrayList<>();
        for (String group : raw.split("\\|")) {
            Alternative alternative = parseAlternative(group);
            if (!alternative.isEmpty()) {
                parsed.add(alternative);
            }
        }
        return parsed.isEmpty() ? EMPTY : new ItemSearchQuery(raw, List.copyOf(parsed));
    }

    private static Alternative parseAlternative(String group) {
        List<Term> positive = new ArrayList<>();
        List<Term> negative = new ArrayList<>();

        Matcher matcher = TERM_PATTERN.matcher(group);
        while (matcher.find()) {
            String token = matcher.group(1);
            boolean exclude = token.length() > 1 && token.startsWith("-");
            if (exclude) {
                token = token.substring(1);
            }
            token = QUOTES.matcher(token).replaceAll("");
            if (token.isEmpty()) {
                continue;
            }
            Term term = Term.of(token);
            (exclude ? negative : positive).add(term);
        }
        return new Alternative(List.copyOf(positive), List.copyOf(negative));
    }

    public boolean isEmpty() {
        return this.alternatives.isEmpty();
    }

    public String raw() {
        return this.raw;
    }

    /**
     * @param side which half of the recipe this box looks at; nothing from the other half is ever consulted, so a
     *             products query cannot match a mod that only appears among the ingredients
     */
    public boolean matches(RecipePlan.Entry entry, Side side) {
        RecipePlan.Haystack haystack = side == Side.INPUT ? entry.inputHaystack() : entry.outputHaystack();
        for (Alternative alternative : this.alternatives) {
            if (alternative.matches(haystack)) {
                return true;
            }
        }
        return false;
    }

    public enum Side {
        INPUT,
        OUTPUT
    }

    /** One {@code |}-separated branch: every positive term has to match, no negative term may match. */
    private record Alternative(List<Term> positive, List<Term> negative) {

        boolean isEmpty() {
            return this.positive.isEmpty() && this.negative.isEmpty();
        }

        boolean matches(RecipePlan.Haystack haystack) {
            for (Term term : this.positive) {
                if (!term.test(haystack)) {
                    return false;
                }
            }
            for (Term term : this.negative) {
                if (term.test(haystack)) {
                    return false;
                }
            }
            return true;
        }
    }

    private enum Kind {
        NAME,
        MOD,
        TOOLTIP,
        TAG,
        ID,
        TAB
    }

    private record Term(Kind kind, String literal, @Nullable Pattern glob) {

        static Term of(String token) {
            Kind kind = Kind.NAME;
            char first = token.charAt(0);
            if (token.length() > 1) {
                kind = switch (first) {
                    case '@' -> Kind.MOD;
                    case '#' -> Kind.TOOLTIP;
                    case '$' -> Kind.TAG;
                    case '&' -> Kind.ID;
                    case '%' -> Kind.TAB;
                    // '^' is JEI's colour search. Colours are not modelled here, so the rest of the term is
                    // searched as text rather than silently matching nothing.
                    case '^' -> Kind.NAME;
                    default -> Kind.NAME;
                };
                if (kind != Kind.NAME || first == '^') {
                    token = token.substring(1);
                }
            }
            // The previous version of the preview had one box for both sides and used '>' / '<' to pick one.
            // Those prefixes no longer mean anything, so they are dropped instead of being searched for.
            if (token.startsWith(">") || token.startsWith("<")) {
                token = token.substring(1);
            }
            String lower = token.toLowerCase(Locale.ROOT);
            boolean wildcard = lower.indexOf('*') >= 0 || lower.indexOf('?') >= 0;
            return new Term(kind, lower, wildcard ? toGlob(lower) : null);
        }

        private static Pattern toGlob(String input) {
            StringBuilder regex = new StringBuilder();
            StringBuilder literal = new StringBuilder();
            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);
                if (c == '*' || c == '?') {
                    if (literal.length() > 0) {
                        regex.append(Pattern.quote(literal.toString()));
                        literal.setLength(0);
                    }
                    regex.append(c == '*' ? ".*" : ".");
                } else {
                    literal.append(c);
                }
            }
            if (literal.length() > 0) {
                regex.append(Pattern.quote(literal.toString()));
            }
            return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
        }

        boolean test(RecipePlan.Haystack haystack) {
            List<String> values = switch (this.kind) {
                case NAME -> haystack.names();
                case MOD -> haystack.mods();
                case TOOLTIP -> haystack.tooltips();
                case TAG -> haystack.tags();
                case ID -> haystack.ids();
                case TAB -> haystack.tabs();
            };
            if (this.literal.isEmpty()) {
                return false;
            }
            for (String value : values) {
                String lower = value.toLowerCase(Locale.ROOT);
                if (this.glob != null ? this.glob.matcher(lower).matches() : lower.contains(this.literal)) {
                    return true;
                }
            }
            return false;
        }
    }
}
