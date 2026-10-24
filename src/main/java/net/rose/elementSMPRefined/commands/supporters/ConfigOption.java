package net.rose.elementSMPRefined.commands.supporters;

import net.rose.elementSMPRefined.util.server.ParseUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * One admin-editable setting exposed through a command's {@code set} subcommand
 * (e.g. {@code /grace set pvp false}, {@code /dimension set pushback_strength 1.2}).
 * <p>
 * Describes the short command-facing name, the dotted config.yml path it is
 * stored under, and how to parse/validate/suggest values for it, so
 * {@code /grace} and {@code /dimension} share one implementation instead of
 * each hand-rolling their own parsing.
 */
public final class ConfigOption {
    public enum Type { BOOLEAN, DURATION, DURATION_OR_ZERO, DECIMAL, CHOICE }

    private final String name;
    private final String path;
    private final Type type;
    private final List<String> choices;
    private final double min;
    private final double max;

    private ConfigOption(String name, String path, Type type, List<String> choices, double min, double max) {
        this.name = name;
        this.path = path;
        this.type = type;
        this.choices = choices;
        this.min = min;
        this.max = max;
    }

    public static ConfigOption bool(String name, String path) {
        return new ConfigOption(name, path, Type.BOOLEAN, List.of(), 0, 0);
    }

    /** A time value that must be greater than zero. */
    public static ConfigOption duration(String name, String path) {
        return new ConfigOption(name, path, Type.DURATION, List.of(), 0, 0);
    }

    /** A time value where 0 is allowed (usually meaning "off"). */
    public static ConfigOption durationOrZero(String name, String path) {
        return new ConfigOption(name, path, Type.DURATION_OR_ZERO, List.of(), 0, 0);
    }

    public static ConfigOption decimal(String name, String path, double min, double max) {
        return new ConfigOption(name, path, Type.DECIMAL, List.of(), min, max);
    }

    public static ConfigOption choice(String name, String path, Enum<?>[] values) {
        List<String> names = Arrays.stream(values).map(Enum::name).toList();
        return new ConfigOption(name, path, Type.CHOICE, names, 0, 0);
    }

    public String name() {
        return name;
    }

    public String path() {
        return path;
    }

    public static ConfigOption find(List<ConfigOption> options, String name) {
        for (ConfigOption option : options) {
            if (option.name.equalsIgnoreCase(name)) {
                return option;
            }
        }
        return null;
    }

    public static List<String> names(List<ConfigOption> options) {
        List<String> names = new ArrayList<>();
        for (ConfigOption option : options) {
            names.add(option.name);
        }
        return names;
    }

    /** Parses raw input into the value to store in config.yml, or null if it isn't valid for this option. */
    public Object parse(String raw) {
        switch (type) {
            case BOOLEAN:
                return ParseUtil.parseBoolean(raw);
            case DURATION:
                return ParseUtil.parseSeconds(raw, false);
            case DURATION_OR_ZERO:
                return ParseUtil.parseSeconds(raw, true);
            case DECIMAL:
                try {
                    double value = Double.parseDouble(raw);
                    if (Double.isNaN(value) || value < min || value > max) {
                        return null;
                    }
                    return value;
                } catch (NumberFormatException e) {
                    return null;
                }
            case CHOICE:
                String upper = raw.toUpperCase(Locale.ROOT);
                return choices.contains(upper) ? upper : null;
            default:
                return null;
        }
    }

    /** Short human description of what a valid value looks like, for error messages. */
    public String expected() {
        return switch (type) {
            case BOOLEAN -> "true or false";
            case DURATION -> "a time like 90, 10m or 1h30m";
            case DURATION_OR_ZERO -> "a time like 90, 10m or 1h30m (0 = off)";
            case DECIMAL -> "a number from " + min + " to " + max;
            case CHOICE -> String.join(", ", choices.stream().map(c -> c.toLowerCase(Locale.ROOT)).toList());
        };
    }

    public List<String> suggestions() {
        return switch (type) {
            case BOOLEAN -> List.of("true", "false");
            case DURATION -> List.of("1m", "5m", "10m", "30m", "1h");
            case DURATION_OR_ZERO -> List.of("0", "30s", "1m", "5m", "10m");
            case DECIMAL -> List.of(String.valueOf(min), String.valueOf(max));
            case CHOICE -> choices.stream().map(c -> c.toLowerCase(Locale.ROOT)).toList();
        };
    }

    /** Formats a stored config value for display (times become 10m, 1h 5m, etc). */
    public String display(Object value) {
        if (value == null) {
            return "unset";
        }
        if ((type == Type.DURATION || type == Type.DURATION_OR_ZERO) && value instanceof Number number) {
            return ParseUtil.formatCompact(number.longValue());
        }
        if (type == Type.CHOICE) {
            return value.toString().toLowerCase(Locale.ROOT);
        }
        return value.toString();
    }
}