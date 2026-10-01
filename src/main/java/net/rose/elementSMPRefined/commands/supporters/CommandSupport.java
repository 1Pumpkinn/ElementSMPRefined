package net.rose.elementSMPRefined.commands.supporters;

import net.rose.elementSMPRefined.core.API.element.ElementType;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Small stateless helpers shared by /element subcommands - parsing input,
 * building tab-complete suggestion lists, etc. Kept separate from any one
 * subcommand so none of them have to reach into a sibling command's class
 * to share this logic.
 */
public final class CommandSupport {
    private CommandSupport() {}

    public static List<String> filterStartingWith(Collection<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)))
                .toList();
    }

    public static List<String> getOnlinePlayerNames(String prefix) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)))
                .toList();
    }

    public static List<String> getElementNames() {
        return ElementType.playable().stream()
                .map(type -> type.name().toLowerCase(Locale.ROOT))
                .toList();
    }

    public static List<String> getElementNames(String prefix) {
        return filterStartingWith(getElementNames(), prefix);
    }

    public static Optional<ElementType> parseElementType(String input) {
        return ElementType.parse(input).filter(ElementType::isPlayable);
    }

    /** Every settable config key, in dotted-path form (e.g. "recipes.advanced_reroller_enabled"). */
    public static final List<String> GLOBAL_CONFIG_KEYS = List.of("recipes.advanced_reroller_enabled");

    public static List<String> getConfigKeys(String prefix) {
        return filterStartingWith(GLOBAL_CONFIG_KEYS, prefix);
    }

    public static List<String> getParticleNameSuggestions() {
        List<String> suggestions = new ArrayList<>();
        for (Particle particle : Particle.values()) {
            suggestions.add(particle.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        }
        suggestions.add("dust");
        suggestions.add("glow");
        suggestions.add("soul-fire-flame");
        suggestions.add("dripping-lava");
        return suggestions.stream().distinct().toList();
    }
}