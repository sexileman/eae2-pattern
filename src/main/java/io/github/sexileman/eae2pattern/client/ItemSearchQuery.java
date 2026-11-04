package io.github.sexileman.eae2pattern.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Requirement: "the preview should support searching and filtering with JEI's syntax".
 *
 * <p>The pieces of JEI's search syntax that actually matter when you are staring at a list of recipe outputs
 * are reproduced here: free text matching the display name, {@code @modid} to narrow by mod, {@code #tag} to
 * narrow by item tag, {@code *} / {@code ?} wildcards, and a {@code -} prefix to exclude a term. All terms must
 * match, exactly like JEI's own filter.</p>
 *
 * <p>We intentionally do not delegate to JEI's ingredient filter: that one filters the ingredient list by
 * prefix, cannot say {@code #tag}, and has no notion of "the output of this recipe".</p>
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
                parsed.add(Term.of(TermKind.TEXT, token, negated));
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

    public boolean matches(RecipePlan.Entry entry) {
        if (this.terms.isEmpty()) {
            return true;
        }
        Candidate candidate = Candidate.of(entry);
        for (Term term : this.terms) {
            boolean hit = term.test(candidate);
            if (term.negated() ? hit : !hit) {
                return false;
            }
        }
        return true;
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

        boolean test(Candidate candidate) {
            return switch (this.kind) {
                case MOD -> matchesOne(candidate.modId());
                case TAG -> anyOf(candidate.tags());
                case TEXT -> anyOf(candidate.texts());
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

    /** The haystack for one entry, built once per entry per keystroke. */
    private record Candidate(String name, String itemId, String modId, List<String> texts, List<String> tags) {

        private static final Map<Item, List<String>> TAG_CACHE = new HashMap<>();
        private static final int TAG_CACHE_LIMIT = 4096;

        static Candidate of(RecipePlan.Entry entry) {
            ItemStack icon = entry.icon();
            String name = entry.label().getString();
            String itemId = "";
            String modId = "";
            List<String> tags = List.of();
            if (!icon.isEmpty()) {
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(icon.getItem());
                itemId = key.toString();
                modId = key.getNamespace();
                tags = tagsOf(icon.getItem());
            }
            List<String> texts = List.of(
                    name,
                    itemId,
                    modId,
                    entry.spec().recipeId().toString(),
                    entry.spec().kind().name()
            );
            return new Candidate(name, itemId, modId, texts, tags);
        }

        private static List<String> tagsOf(Item item) {
            List<String> cached = TAG_CACHE.get(item);
            if (cached != null) {
                return cached;
            }
            List<String> tags = new ArrayList<>();
            item.builtInRegistryHolder().tags().map(TagKey::location).map(ResourceLocation::toString)
                    .forEach(tags::add);
            List<String> result = List.copyOf(tags);
            if (TAG_CACHE.size() < TAG_CACHE_LIMIT) {
                TAG_CACHE.put(item, result);
            }
            return result;
        }
    }
}
