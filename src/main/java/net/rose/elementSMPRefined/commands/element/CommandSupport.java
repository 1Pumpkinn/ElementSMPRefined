package net.rose.elementSMPRefined.commands.element;

import net.rose.elementSMPRefined.core.API.element.ElementId;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ElementManager;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

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
                .collect(Collectors.toList());
    }

    public static List<String> getOnlinePlayerNames(String prefix) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
    }

    public static List<String> getElementNames() {
        return Arrays.stream(ElementType.values())
                .map(type -> type.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
    }

    public static List<String> getElementNames(String prefix) {
        return filterStartingWith(getElementNames(), prefix);
    }

    public static Optional<ElementType> parseElementType(String input) {
        try {
            return Optional.of(ElementType.valueOf(input.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Resolves either a bare builtin name ("fire") or a namespaced addon ID
     * ("elementevents:storm") to an {@link ElementId}. Used by {@code /element set}
     * so admins can target addon elements, not just the 8 builtins.
     */
    public static Optional<ElementId> parseElementId(ElementManager elementManager, String input) {
        Optional<ElementType> builtin = parseElementType(input);
        if (builtin.isPresent()) {
            return Optional.of(ElementId.builtin(builtin.get()));
        }
        try {
            ElementId id = ElementId.parse(input);
            return elementManager.getElementRegistry().isRegistered(id) ? Optional.of(id) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Builtin names plus every registered addon element's "namespace:key" ID. */
    public static List<String> getAllElementNames(ElementManager elementManager) {
        List<String> names = new ArrayList<>(getElementNames());
        for (ElementId id : elementManager.getElementRegistry().getAllIds()) {
            if (id.toBuiltinType() == null) {
                names.add(id.toString());
            }
        }
        return names;
    }

    /** Every settable global config key, in dotted-path form (e.g. "mana.max"). */
    public static final List<String> GLOBAL_CONFIG_KEYS = List.of("mana.max", "mana.regen_per_second",
            "mana.ability1_cost", "mana.ability2_cost", "status_effects.enabled",
            "status_effects.damage_per_tick", "status_effects.notification_messages",
            "recipes.advanced_reroller_enabled");

    /** Every settable per-element config key, relative to "elements.<type>.". */
    public static final List<String> ELEMENT_CONFIG_KEYS = List.of("ability1_cost", "ability2_cost", "is_basic",
            "enabled", "display_name", "color");

    public static List<String> getConfigKeys(String prefix) {
        return filterStartingWith(GLOBAL_CONFIG_KEYS, prefix);
    }

    public static List<String> getElementConfigKeys(String prefix) {
        return filterStartingWith(ELEMENT_CONFIG_KEYS, prefix);
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
