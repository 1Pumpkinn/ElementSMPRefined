package net.rose.elementSMPRefined.listeners.player;

import net.rose.elementSMPRefined.managers.ConfigManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;

public class GameModeListener implements Listener {
    private final ConfigManager configManager;

    public GameModeListener(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent e) {
        Player p = e.getPlayer();
        GameMode newMode = e.getNewGameMode();


        // When leaving creative (to survival/adventure/spectator), mana stays at current level
        // Normal regen will take over from ManaManager's tick
    }
}