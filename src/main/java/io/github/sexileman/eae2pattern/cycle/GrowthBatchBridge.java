package io.github.sexileman.eae2pattern.cycle;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.pattern.AEProcessingPattern;

import io.github.sexileman.eae2pattern.EAE2Pattern;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** Prefer EAP's scaling contract when enabled; otherwise use the native phase descriptor. */
public final class GrowthBatchBridge {
    public record Batch(IPatternDetails pattern, long times) {}

    private record PatternAccess(Method allowed, Method limit) {}

    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private GrowthBatchBridge() {}

    private static void unavailable(Throwable failure) {
        if (WARNED.compareAndSet(false, true)) {
            EAE2Pattern.LOGGER.warn(
                    "[EAE2/Cycles] event=optional_adapter_unavailable adapter=eap-scaling; using"
                            + " native batch contract",
                    failure);
        }
    }

    private static final class Eap {
        static final Method FACTORY = findFactory();
        static final ClassValue<Optional<PatternAccess>> ACCESS =
                new ClassValue<>() {
                    protected Optional<PatternAccess> computeValue(Class<?> type) {
                        try {
                            return Optional.of(
                                    new PatternAccess(
                                            type.getMethod("eap$allowScaling"),
                                            type.getMethod("eap$getMultiplierLimit")));
                        } catch (ReflectiveOperationException | LinkageError failure) {
                            unavailable(failure);
                            return Optional.empty();
                        }
                    }
                };

        private static Method findFactory() {
            try {
                return Class.forName(
                                "com.extendedae_plus.util.smartDoubling.PatternScaler",
                                false,
                                GrowthBatchBridge.class.getClassLoader())
                        .getMethod("createScaled", AEProcessingPattern.class, long.class);
            } catch (ClassNotFoundException absent) {
                return null;
            } catch (ReflectiveOperationException | LinkageError failure) {
                unavailable(failure);
                return null;
            }
        }
    }

    /** Cache field access, never the instance: EAP can replace INSTANCE when reloading config. */
    static int readSingletonInt(java.lang.reflect.Field singleton, java.lang.reflect.Field value)
            throws ReflectiveOperationException {
        return value.getInt(singleton.get(null));
    }

    private static final class GlobalLimit {
        static int read() throws ReflectiveOperationException {
            return readSingletonInt(Holder.INSTANCE_FIELD, Holder.FIELD);
        }

        private static final class Holder {
            static final java.lang.reflect.Field INSTANCE_FIELD;
            static final java.lang.reflect.Field FIELD;

            static {
                try {
                    var type =
                            Class.forName(
                                    "com.extendedae_plus.config.ModConfig",
                                    false,
                                    GrowthBatchBridge.class.getClassLoader());
                    INSTANCE_FIELD = type.getField("INSTANCE");
                    FIELD = type.getField("smartScalingMaxMultiplier");
                } catch (ReflectiveOperationException failure) {
                    throw new ExceptionInInitializerError(failure);
                }
            }
        }
    }

    public static Batch prepare(IPatternDetails original, long requested) {
        if (requested < 1) throw new IllegalArgumentException("Invalid phase batch size");
        if (requested == 1 || !(original instanceof AEProcessingPattern))
            return new Batch(original, 1);
        if (Eap.FACTORY != null) {
            try {
                var access = Eap.ACCESS.get(original.getClass());
                if (access.isPresent()
                        && Boolean.TRUE.equals(access.get().allowed().invoke(original))) {
                    int limit = ((Number) access.get().limit().invoke(original)).intValue();
                    if (limit <= 0) limit = GlobalLimit.read();
                    long times = limit > 0 ? Math.min(requested, limit) : requested;
                    if (times <= 1) return new Batch(original, 1);
                    Object scaled = Eap.FACTORY.invoke(null, original, times);
                    if (scaled instanceof IPatternDetails details) return new Batch(details, times);
                    unavailable(
                            new IllegalStateException(
                                    "EAP factory returned an unsupported descriptor"));
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                unavailable(failure);
            }
        }
        return new Batch(new GrowthBatchPattern(original, requested), requested);
    }
}
