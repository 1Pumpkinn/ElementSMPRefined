package rose.elementSMPRefined.ability.main.frost;

import rose.elementSMPRefined.ElementSMPRefined;
import rose.elementSMPRefined.config.Constants;
import rose.elementSMPRefined.API.element.ElementContext;
import rose.elementSMPRefined.API.element.ElementType;
import rose.elementSMPRefined.API.ability.BaseAbility;
import rose.elementSMPRefined.managers.ConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

public class FrostPunchAbility extends BaseAbility {
    private final ElementSMPRefined plugin;
    public static final String META_FROZEN_PUNCH_READY = "frost_frozen_punch_ready";

    public FrostPunchAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("frost_frozen_punch", ElementType.FROST, 2, 10, 2, configManager);
        this.plugin = (ElementSMPRefined) plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();

        // Set metadata indicating the next punch will freeze
        long until = System.currentTimeMillis() + Constants.Duration.FROST_PUNCH_READY_MS;
        player.setMetadata(META_FROZEN_PUNCH_READY, new FixedMetadataValue(plugin, until));

        // Visual and audio feedback
        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.5f);

        Location loc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 30, 0.3, 0.3, 0.3, 0.1, null, true);
        player.getWorld().spawnParticle(Particle.CLOUD, loc, 15, 0.3, 0.3, 0.3, 0.05, null, true);

        return true;
    }

    @Override
    public String getName() {
        return ChatColor.AQUA + "Frozen Punch";
    }

    @Override
    public String getDescription() {
        return "Your next punch freezes an enemy in place for 5 seconds, preventing all movement.";
    }
}