package io.github.sexileman.eae2pattern.client;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One search box of the preview, following JEI's query syntax closely enough to feel familiar.
 *
 * <p>Supported, all terms ANDed together:</p>
 * <ul>
 *   <li>{@code iron} - free text, matched against the names, ids, paths and namespaces of the ingredients of the
 *       side this box belongs to;</li>
 *   <li>{@code @gtceu} - only entries that involve a given mod;</li>
 *   <li>{@code #forge:plates} - only entries that involve an item tag;</li>
 *   <li>{@code plate*} / {@code plate?} - wildcards;</li>
 *   <li>{@code -slab} or {@code !slab} - exclude a term.</li>
 * </ul>
 *
 * <p>The preview has one box for the products and one for the ingredients, so the {@code >} and {@code <}
 * prefixes JEI has no equivalent for are no longer needed. They are still accepted and stripped, so a query
 * copied from the previous version keeps working.</p>
 */
public final class ItemSearchQuery {

    private static final ItemSearchQuery EMPTY = new ItemSearchQuery("", List.of());

    private final String raw;
    private final List<Term> terms;

    private ItemSearchQuery(String raw, List<Term> terms) {
        this.raw = raw;
        this.terms = terms;
    }

    public static ItemSearchQuery empty() {
        return EMPTY;
    }

    public static ItemSearchQuery parse(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return EMPTY;
        }
        List<Term> parsed = new ArrayList<>();
        for (String token : raw.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            boolean negated = false;
            if (token.length() > 1 && (token.startsWith("-") || token.startsWith("!"))) {
                negated = true;
                token = token.substring(1);
            }
            if (token.isEmpty()) {
                continue;
            }
            if (token.startsWith("#")) {
                parsed.add(Term.of(TermKind.TAG, token.substring(1), negated));
            } else if (token.startsWith("@")) {
                parsed.add(Term.of(TermKind.MOD, token.substring(1), negated));
            } else {
                String body = token;
                if (body.startsWith(">") || body.startsWith("<")) {
                    body = body.substring(1);
                }
                if (!body.isEmpty()) {
                    parsed.add(Term.of(TermKind.TEXT, body, negated));
                }
            }
        }
        return parsed.isEmpty() ? EMPTY : new ItemSearchQuery(raw, List.copyOf(parsed));
    }

    public boolean isEmpty() {
        return this.terms.isEmpty();
    }

    public String raw() {
        return this.raw;
    }

    /** @param side the texts, mod ids and tags of one side of a recipe row */
    public boolean matches(RecipePlan.Entry entry, Side side) {
        List<String> texts = side == Side.INPUT ? entry.inputTexts() : entry.outputTexts();
        for (Term term : this.terms) {
            boolean hit = term.test(entry, texts);
            if (term.negated() ? hit : !hit) {
                return false;
            }
        }
        return true;
    }

    public enum Side {
        INPUT,
        OUTPUT
    }

    private enum TermKind {
        TEXT,
        MOD,
        TAG
    }

    private record Term(TermKind kind, @Nullable Pattern glob, String literal, boolean negated) {

        static Term of(TermKind kind, String body, boolean negated) {
            String lower = body.toLowerCase(Locale.ROOT);
            Pattern glob = (lower.indexOf('*') >= 0 || lower.indexOf('?') >= 0) ? toGlob(lower) : null;
            return new Term(kind, glob, lower, negated);
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

        boolean test(RecipePlan.Entry entry, List<String> texts) {
            return switch (this.kind) {
                case MOD -> anyOf(entry.modIds());
                case TAG -> anyOf(entry.tags());
                case TEXT -> anyOf(texts);
            };
        }

        private boolean anyOf(List<String> values) {
            for (String value : values) {
                if (matchesOne(value)) {
                    return true;
                }
            }
            return false;
        }

        private boolean matchesOne(String value) {
            String lower = value.toLowerCase(Locale.ROOT);
            return this.glob != null ? this.glob.matcher(lower).matches() : lower.contains(this.literal);
        }
    }
}
