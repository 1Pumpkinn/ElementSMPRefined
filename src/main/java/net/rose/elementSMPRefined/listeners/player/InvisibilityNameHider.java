package net.rose.elementSMPRefined.listeners.player;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hides an invisible player's name in chat and death messages
 */
public class InvisibilityNameHider implements Listener {

    /** Shown in place of an invisible player's name. Swap for "???" or similar if you'd rather. */
    private static final String HIDDEN_NAME = " ";

    private boolean isInvisible(Player player) {
        return player.hasPotionEffect(PotionEffectType.INVISIBILITY);
    }

    /**
     * Blank out the sender's name in their own chat messages while they're invisible.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        if (!isInvisible(sender)) {
            return;
        }

        event.renderer((source, sourceDisplayName, message, viewer) -> message);
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Component deathMessage = event.deathMessage();
        if (deathMessage == null) {
            return;
        }

        String updated = PlainTextComponentSerializer.plainText().serialize(deathMessage);
        boolean changed = false;

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!isInvisible(online)) {
                continue;
            }

            String name = online.getName();
            if (updated.contains(name)) {
                updated = replaceName(updated, name);
                changed = true;
            }
        }

        if (changed) {
            event.deathMessage(Component.text(updated));
        }
    }

    private String replaceName(String text, String name) {
        // \b keeps this from touching names that happen to be substrings of other words/names.
        String pattern = "\\b" + Pattern.quote(name) + "\\b";
        return text.replaceAll(pattern, Matcher.quoteReplacement(HIDDEN_NAME));
    }
}