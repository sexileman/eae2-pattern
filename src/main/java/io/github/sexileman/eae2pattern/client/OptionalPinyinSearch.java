package io.github.sexileman.eae2pattern.client;

import io.github.sexileman.eae2pattern.EAE2Pattern;
import java.lang.reflect.InvocationTargetException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;

/** Client-only optional bridge. Uses JEC's live context, so no duplicate dictionary or stale result cache. */
public final class OptionalPinyinSearch {
    private OptionalPinyinSearch() {}
    private static final class Holder {
        static final BiPredicate<String,String> MATCH = bind(OptionalPinyinSearch.class.getClassLoader());
    }
    public static boolean contains(String value, String query) { return Holder.MATCH.test(value, query); }

    /** Also used by isolated optional-mod checks with the actual installed JEC jar. */
    public static BiPredicate<String,String> bind(ClassLoader loader) {
        BiPredicate<String,String> pinyin = (value,query) -> false;
        try {
            var method = Class.forName("me.towdium.jecharacters.utils.Match", false, loader)
                    .getMethod("contains", String.class, CharSequence.class);
            var enabled = new AtomicBoolean(true);
            pinyin = (value,query) -> {
                if (!enabled.get()) return false;
                try { return (Boolean) method.invoke(null, value, query); }
                catch (ReflectiveOperationException | LinkageError error) {
                    if (error instanceof InvocationTargetException target && target.getCause() instanceof VirtualMachineError fatal) throw fatal;
                    if (enabled.getAndSet(false)) EAE2Pattern.LOGGER.warn("Optional JEC search disabled: {}", error.toString());
                    return false;
                }
            };
        } catch (ClassNotFoundException absent) {
            // JEC is optional; ordinary text matching works without it.
        } catch (ReflectiveOperationException | LinkageError incompatible) {
            EAE2Pattern.LOGGER.warn("Optional JEC interface unavailable: {}", incompatible.toString());
        }
        var optional = pinyin;
        return (value,query) -> {
            if (value == null || query == null) return false;
            String text=value.toLowerCase(Locale.ROOT), needle=query.toLowerCase(Locale.ROOT);
            if (text.contains(needle)) return true;
            if (needle.chars().noneMatch(c -> c >= 'a' && c <= 'z')
                    || text.codePoints().noneMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN)) return false;
            return optional.test(text,needle);
        };
    }
}
