package net.rose.elementSMPRefined.util.visual;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Shows "Element SMP" as the title of the TAB player list, in a gradient. Just the title - no
 * dividers or footer - so the list keeps its normal size.
 * <p>
 * The look is plain MiniMessage, so it can be designed on a MiniMessage editor and pasted
 * straight into {@link #HEADER_MINI}.
 * <p>
 * Call {@link #register(JavaPlugin)} once on enable: it covers players who join later and anyone
 * already online (e.g. after a reload).
 */
public class ElementSmpTablist implements Listener {

    /** The title. Paste a new one from the MiniMessage editor here. */
    private static final String HEADER_MINI =
            "<gradient:#ffffff:#90fe92:#00bfc2:#00fbff:#ffa200:#c20000><bold>Element SMP</bold></gradient>";

    private static final Component HEADER = MiniMessage.miniMessage().deserialize(HEADER_MINI);

    /** Registers the join listener and applies the tab list to everyone currently online. */
    public static void register(JavaPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(new ElementSmpTablist(), plugin);
        for (Player player : Bukkit.getOnlinePlayers()) {
            apply(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        apply(event.getPlayer());
    }

    private static void apply(Player player) {
        player.sendPlayerListHeaderAndFooter(HEADER, Component.empty());
    }
}