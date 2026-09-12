package rose.elementSMPRefined.commands.element;

import rose.elementSMPRefined.ElementSMPRefined;
import rose.elementSMPRefined.util.visual.ParticlePreset;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * /element particles <preset> [size] [length] [width] [color] [particle]
 * Also accepts named form: /element particles <preset> size=2.5 color=red ...
 * No permission gate beyond "must be a player" - see the note on
 * requiresAdmin-style gating in ElementCommand for why that's worth revisiting.
 */
public class ParticlesCommand implements ElementSubCommand {
    private final ElementSMPRefined plugin;

    public ParticlesCommand(ElementSMPRefined plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        if (args.length == 1) {
            sendPresetList(player);
            return true;
        }

        Optional<ParticlePreset> preset = ParticlePreset.fromName(args[1]);
        if (preset.isEmpty()) {
            sendPresetList(player);
            return true;
        }

        ParticlePreset.PresetOptions options = parsePresetOptions(preset.get(), args);
        preset.get().play(player, plugin, options);
        player.sendMessage(ChatColor.GREEN + "Playing particle preset: " + ChatColor.AQUA + preset.get().getKey());
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return ParticlePreset.filterNames(args[1]);
        }

        if (args.length >= 3 && args.length <= 5) {
            return CommandSupport.filterStartingWith(
                    List.of("0.5", "1", "1.5", "2", "2.5", "3", "4", "5", "6", "8", "10"), args[args.length - 1]);
        }

        if (args.length == 6) {
            return CommandSupport.filterStartingWith(List.of("white", "red", "orange", "yellow", "green", "lime",
                    "aqua", "cyan", "blue", "purple", "magenta", "pink", "black", "gray", "grey", "silver",
                    "#ff0000", "#00ff00", "#0000ff"), args[5]);
        }

        if (args.length == 7) {
            return CommandSupport.filterStartingWith(CommandSupport.getParticleNameSuggestions(), args[6]);
        }

        return Collections.emptyList();
    }

    private ParticlePreset.PresetOptions parsePresetOptions(ParticlePreset preset, String[] args) {
        ParticlePreset.PresetOptions defaults = ParticlePreset.PresetOptions.defaults(preset);

        double size = defaults.sizeOr(1.0);
        double length = defaults.lengthOr(0.0);
        double width = defaults.widthOr(1.0);
        Particle particle = defaults.particle();
        Color color = defaults.color();

        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            if (arg == null || arg.isBlank()) {
                continue;
            }

            if (matchesKey(arg, "size")) {
                NamedValue nv = extractValue(args, i);
                i = nv.nextIndex();
                Double parsed = parseDouble(nv.value());
                if (parsed != null) size = parsed;
                continue;
            }
            if (matchesKey(arg, "length")) {
                NamedValue nv = extractValue(args, i);
                i = nv.nextIndex();
                Double parsed = parseDouble(nv.value());
                if (parsed != null) length = parsed;
                continue;
            }
            if (matchesKey(arg, "width")) {
                NamedValue nv = extractValue(args, i);
                i = nv.nextIndex();
                Double parsed = parseDouble(nv.value());
                if (parsed != null) width = parsed;
                continue;
            }
            if (matchesKey(arg, "color")) {
                NamedValue nv = extractValue(args, i);
                i = nv.nextIndex();
                color = parseColor(nv.value());
                continue;
            }
            if (matchesKey(arg, "particle")) {
                NamedValue nv = extractValue(args, i);
                i = nv.nextIndex();
                particle = parseParticle(nv.value());
                continue;
            }

            // Positional fallback: <preset> [size] [length] [width] [color] [particle]
            switch (i) {
                case 2 -> {
                    Double parsed = parseDouble(arg);
                    if (parsed != null) size = parsed;
                }
                case 3 -> {
                    Double parsed = parseDouble(arg);
                    if (parsed != null) length = parsed;
                }
                case 4 -> {
                    Double parsed = parseDouble(arg);
                    if (parsed != null) width = parsed;
                }
                case 5 -> color = parseColor(arg);
                case 6 -> particle = parseParticle(arg);
                default -> { /* extra trailing args ignored */ }
            }
        }

        return new ParticlePreset.PresetOptions(size, length, width, particle, color);
    }

    /** A "key=value" or "key value" style argument's value, and the loop index to resume from. */
    private record NamedValue(String value, int nextIndex) {}

    private static boolean matchesKey(String arg, String key) {
        return arg.startsWith(key + "=") || arg.equalsIgnoreCase(key);
    }

    /** Reads the value for a named arg at {@code i}, consuming the next token if the form was "key value" not "key=value". */
    private static NamedValue extractValue(String[] args, int i) {
        String arg = args[i];
        if (arg.contains("=")) {
            return new NamedValue(arg.substring(arg.indexOf('=') + 1), i);
        }
        if (i + 1 < args.length) {
            return new NamedValue(args[i + 1], i + 1);
        }
        return new NamedValue("", i);
    }

    private static Double parseDouble(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Color parseColor(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String normalized = input.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "white" -> Color.WHITE;
            case "silver", "lightgray" -> Color.SILVER;
            case "gray", "grey" -> Color.GRAY;
            case "black" -> Color.BLACK;
            case "red" -> Color.RED;
            case "orange" -> Color.ORANGE;
            case "yellow" -> Color.YELLOW;
            case "green" -> Color.GREEN;
            case "lime" -> Color.LIME;
            case "aqua", "cyan" -> Color.AQUA;
            case "blue" -> Color.BLUE;
            case "purple", "magenta" -> Color.PURPLE;
            case "pink" -> Color.FUCHSIA;
            default -> {
                if (normalized.startsWith("#") && normalized.length() == 7) {
                    try {
                        int r = Integer.parseInt(normalized.substring(1, 3), 16);
                        int g = Integer.parseInt(normalized.substring(3, 5), 16);
                        int b = Integer.parseInt(normalized.substring(5, 7), 16);
                        yield Color.fromRGB(r, g, b);
                    } catch (NumberFormatException ignored) {
                        yield null;
                    }
                }
                yield null;
            }
        };
    }

    private static Particle parseParticle(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String normalized = input.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return Particle.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return switch (normalized) {
                case "DUST", "REDSTONE" -> Particle.DUST;
                case "GLOW" -> Particle.GLOW;
                case "SOULFIRE" -> Particle.SOUL_FIRE_FLAME;
                case "DRIPLAVA" -> Particle.DRIPPING_LAVA;
                default -> null;
            };
        }
    }

    private void sendPresetList(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Element Particle Presets ===");
        player.sendMessage(ChatColor.YELLOW + "Available: " + String.join(", ", ParticlePreset.getNames()));
        player.sendMessage(ChatColor.GRAY + "Usage: /element particles <preset> [size] [length] [width] [color] [particle]");
        player.sendMessage(ChatColor.GRAY + "Also supported: size=2.5 length=0 width=36 color=red particle=dust");
        player.sendMessage(ChatColor.GRAY + "Example: /element particles circle 2.5 0 36 red dust");
    }
}
