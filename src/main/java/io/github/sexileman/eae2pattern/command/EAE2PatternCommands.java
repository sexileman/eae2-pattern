package io.github.sexileman.eae2pattern.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.sexileman.eae2pattern.EAE2Pattern;
import io.github.sexileman.eae2pattern.config.EAE2PatternConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * In-game configuration editing with immediate effect.
 *
 * <p>Forge configs are read through a live {@code ConfigValue} on every use, so writing a new value through
 * {@link ForgeConfigSpec.ConfigValue#set(Object)} changes behaviour on the very next pattern this mod encodes.
 * Calling {@code save()} afterwards persists it to {@code serverconfig/eae2_pattern-server.toml}, which means the
 * player no longer has to stop the server, edit a file and restart to, say, bump the pattern limit or add an
 * ingredient to the ignore list.</p>
 *
 * <p>Values are validated here rather than left to the config loader: a mistyped limit that only fails at load
 * time would silently fall back to the default, which is exactly the confusion this command exists to avoid.</p>
 */
public final class EAE2PatternCommands {

    private static final Map<String, com.mojang.brigadier.arguments.ArgumentType<?>> TYPES = new LinkedHashMap<>();
    private static final Map<String, Setting> SETTINGS = new LinkedHashMap<>();

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_KEYS =
            (context, builder) -> SharedSuggestionProvider.suggest(SETTINGS.keySet(), builder);

    static {
        Setting.bool("filters.strip_container_items", EAE2PatternConfig.STRIP_CONTAINER_ITEMS);
        Setting.list("filters.ignored_stacks", EAE2PatternConfig.IGNORED_STACKS);
        Setting.bool("encoding.allow_substitutes", EAE2PatternConfig.ALLOW_SUBSTITUTES);
        Setting.bool("cycles.experimental_execution", EAE2PatternConfig.EXPERIMENTAL_CYCLES);
        Setting.bool("cycles.chat_diagnostics", EAE2PatternConfig.CYCLE_CHAT_DIAGNOSTICS);
        Setting.bool("encoding.allow_fluid_substitutes", EAE2PatternConfig.ALLOW_FLUID_SUBSTITUTES);
        Setting.bool("encoding.require_network_source", EAE2PatternConfig.REQUIRE_ENCODING_TERMINAL);
        Setting.integer("limits.max_patterns_per_request", EAE2PatternConfig.MAX_PATTERNS_PER_REQUEST, 1, 65536);
        Setting.integer("limits.max_request_bytes", EAE2PatternConfig.MAX_REQUEST_BYTES, 8192, 8_000_000);
    }

    private EAE2PatternCommands() {
    }

    @Mod.EventBusSubscriber(modid = EAE2Pattern.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onRegisterCommands(RegisterCommandsEvent event) {
            register(event.getDispatcher());
        }
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> config = Commands.literal("config")
                .then(Commands.literal("list").executes(EAE2PatternCommands::list))
                .then(Commands.literal("get")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .suggests(SUGGEST_KEYS)
                                .executes(EAE2PatternCommands::get)))
                .then(typedSet())
                .then(Commands.literal("add")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("key", StringArgumentType.word())
                                .suggests((ctx,builder)->SharedSuggestionProvider.suggest(SETTINGS.values().stream().filter(Setting::list).map(Setting::path),builder))
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(context -> mutateList(context, true)))))
                .then(Commands.literal("remove")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("key", StringArgumentType.word())
                                .suggests((ctx,builder)->SharedSuggestionProvider.suggest(SETTINGS.values().stream().filter(Setting::list).map(Setting::path),builder))
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(context -> mutateList(context, false)))))
                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("key", StringArgumentType.word())
                                .suggests(SUGGEST_KEYS)
                                .executes(EAE2PatternCommands::reset)))
                .then(Commands.literal("reload").requires(source->source.hasPermission(2)).executes(EAE2PatternCommands::reload));

        dispatcher.register(Commands.literal(EAE2Pattern.MOD_ID).then(config)
                .then(Commands.literal("cycles").then(Commands.literal("report")
                        .executes(io.github.sexileman.eae2pattern.cycle.CycleReports::report))));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        for (Setting setting : SETTINGS.values()) {
            context.getSource().sendSuccess(() -> Component.translatable("eae2_pattern.cmd.entry",
                    setting.path(), setting.read().get()), false);
        }
        context.getSource().sendSuccess(() -> Component.translatable("eae2_pattern.cmd.hint"), false);
        context.getSource().sendSuccess(()->Component.translatable("eae2_pattern.cmd.legacy_containers"),false);
        return SETTINGS.size();
    }

    private static int get(CommandContext<CommandSourceStack> context) {
        Setting setting = setting(context);
        if (setting == null) {
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("eae2_pattern.cmd.entry",
                setting.path(), setting.read().get()), false);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> typedSet(){
        var command=Commands.literal("set").requires(source->source.hasPermission(2));
        SETTINGS.forEach((path,setting)->command.then(Commands.literal(path)
                .then(Commands.argument("value",TYPES.get(path))
                        .executes(context->set(context,setting,context.getArgument("value",Object.class).toString())))));
        return command;
    }
    private static int set(CommandContext<CommandSourceStack> context,Setting setting,String raw) {
        if(setting.path().equals("filters.strip_container_items"))context.getSource().sendSuccess(()->Component.translatable("eae2_pattern.cmd.legacy_containers"),false);
        String error = setting.write().apply(raw);
        if (error != null) {
            reportError(context, error, setting);
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("eae2_pattern.cmd.set",
                setting.path(), setting.read().get()), true);
        EAE2Pattern.LOGGER.info("Config {} set to {} by {}", setting.path(), setting.read().get(),
                context.getSource().getTextName());
        return 1;
    }

    private static int mutateList(CommandContext<CommandSourceStack> context, boolean add) {
        Setting setting = setting(context);
        if (setting == null) {
            return 0;
        }
        if (!setting.list()) {
            context.getSource().sendFailure(Component.translatable("eae2_pattern.cmd.err.not_list", setting.path()));
            return 0;
        }
        List<String> values = new ArrayList<>(setting.readList().get());
        String raw = StringArgumentType.getString(context, "value");
        for (String part : raw.split(",")) {
            String entry = part.trim();
            if (entry.isEmpty()) {
                continue;
            }
            if (add) {
                if (!values.contains(entry)) {
                    values.add(entry);
                }
            } else {
                values.remove(entry);
            }
        }
        String error = setting.write().apply(String.join(", ", values));
        if (error != null) {
            reportError(context, error, setting);
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("eae2_pattern.cmd.set",
                setting.path(), setting.read().get()), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) {
        Setting setting = setting(context);
        if (setting == null) {
            return 0;
        }
        String error = setting.write().apply(setting.defaultValue());
        if (error != null) {
            reportError(context, error, setting);
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("eae2_pattern.cmd.reset",
                setting.path(), setting.read().get()), true);
        return 1;
    }

    /**
     * Forces every cached value to be read again. Forge keeps the parsed config in memory, so this only matters
     * after another part of the game replaced it; it exists because players reasonably expect a "reload" verb.
     */
    private static int reload(CommandContext<CommandSourceStack> context) {
        for (Setting setting : SETTINGS.values()) {
            setting.invalidate();
        }
        context.getSource().sendSuccess(() -> Component.translatable("eae2_pattern.cmd.reloaded"), true);
        return 1;
    }

    private static void reportError(CommandContext<CommandSourceStack> context, String errorKey, Setting setting) {
        context.getSource().sendFailure(Component.translatable(errorKey, setting.path()));
        context.getSource().sendSuccess(() -> Component.translatable("eae2_pattern.cmd.current",
                setting.path(), setting.read().get()), false);
    }

    @Nullable
    private static Setting setting(CommandContext<CommandSourceStack> context) {
        String key = StringArgumentType.getString(context, "key");
        Setting setting = SETTINGS.get(key);
        if (setting == null) {
            setting = SETTINGS.get("filters." + key);
        }
        if (setting == null) {
            setting = SETTINGS.get("encoding." + key);
        }
        if (setting == null) {
            setting = SETTINGS.get("limits." + key);
        }
        if (setting == null) {
            context.getSource().sendFailure(Component.translatable("eae2_pattern.cmd.err.unknown_key", key));
            return null;
        }
        return setting;
    }

    /** One editable config entry: how to read it, how to write it back, and what to reset it to. */
    private record Setting(String path, Supplier<String> read, Supplier<List<String>> readList,
                           Function<String, String> write, String defaultValue, boolean list,
                           Runnable invalidate) {

        static void bool(String path, ForgeConfigSpec.BooleanValue value) {
            TYPES.put(path,com.mojang.brigadier.arguments.BoolArgumentType.bool());
            put(new Setting(path, () -> String.valueOf(value.get()), List::of,
                    text -> {
                        String lower = text.trim().toLowerCase(Locale.ROOT);
                        if (!lower.equals("true") && !lower.equals("false")) {
                            return "eae2_pattern.cmd.err.boolean";
                        }
                        value.set(lower.equals("true"));
                        value.save();
                        return null;
                    },
                    String.valueOf(value.getDefault()), false, value::clearCache));
        }

        static void integer(String path, ForgeConfigSpec.IntValue value, int min, int max) {
            TYPES.put(path,com.mojang.brigadier.arguments.IntegerArgumentType.integer(min,max));
            put(new Setting(path, () -> String.valueOf(value.get()), List::of,
                    text -> {
                        int parsed;
                        try {
                            parsed = Integer.parseInt(text.trim());
                        } catch (NumberFormatException e) {
                            return "eae2_pattern.cmd.err.integer";
                        }
                        if (parsed < min || parsed > max) {
                            return "eae2_pattern.cmd.err.range";
                        }
                        value.set(parsed);
                        value.save();
                        return null;
                    },
                    String.valueOf(value.getDefault()), false, value::clearCache));
        }

        static void list(String path, ForgeConfigSpec.ConfigValue<List<? extends String>> value) {
            TYPES.put(path,StringArgumentType.greedyString());
            put(new Setting(path,
                    () -> String.join(", ", value.get()),
                    () -> {
                        List<String> copy = new ArrayList<>();
                        for (String entry : value.get()) {
                            copy.add(entry);
                        }
                        return copy;
                    },
                    text -> {
                        List<String> parsed = new ArrayList<>();
                        for (String part : text.split(",")) {
                            String entry = part.trim();
                            if (!entry.isEmpty()) {
                                parsed.add(entry);
                            }
                        }
                        value.set(parsed);
                        value.save();
                        return null;
                    },
                    String.join(", ", value.getDefault()), true, value::clearCache));
        }

        private static void put(Setting setting) {
            SETTINGS.put(setting.path(), setting);
        }
    }
}
