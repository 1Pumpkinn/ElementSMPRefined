package net.rose.elementSMPRefined.commands.supporters;

import net.rose.elementSMPRefined.core.API.element.ElementType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

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
}
