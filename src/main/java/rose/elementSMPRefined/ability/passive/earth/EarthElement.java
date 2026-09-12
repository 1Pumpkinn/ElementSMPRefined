package rose.elementSMPRefined.ability.passive.earth;

import rose.elementSMPRefined.API.element.BaseElement;
import rose.elementSMPRefined.API.element.ElementContext;
import rose.elementSMPRefined.API.element.ElementType;
import rose.elementSMPRefined.ability.main.earth.EarthTunnelAbility;
import rose.elementSMPRefined.ability.main.earth.GraspAbility;
import rose.elementSMPRefined.managers.ConfigManager;
import rose.elementSMPRefined.services.EffectService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class EarthElement extends BaseElement {
    public static final String META_MINE_UNTIL = "earth_mine_until";
    public static final String META_TUNNELING = "earth_tunneling";

    public EarthElement(JavaPlugin plugin, ConfigManager configManager) {
        super(plugin, new EarthTunnelAbility(plugin, configManager), new GraspAbility(plugin, configManager));
    }

    @Override
    public ElementType getType() {
        return ElementType.EARTH;
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, PotionEffect.INFINITE_DURATION, 0, true, false));
    }

    @Override
    protected boolean canCancelAbility1(ElementContext context) {
        // Check if the player has the tunneling metadata - if so, they can cancel
        return context.getPlayer().hasMetadata(META_TUNNELING);
    }

    @Override
    public void clearEffects(Player player) {
        super.clearEffects(player);
        EffectService.removeElementPotionEffect(player, PotionEffectType.HERO_OF_THE_VILLAGE);
        player.removeMetadata(META_MINE_UNTIL, plugin);
        player.removeMetadata(META_TUNNELING, plugin);
    }

    @Override
    public String getDisplayName() {
        return ChatColor.YELLOW + "Earth";
    }

    @Override
    public String getDescription() {
        return "Masters of stone and earth. Earth users can tunnel through blocks and grasp enemies.";
    }

    @Override
    public List<String> getPassiveBenefits() {
        return List.of(
                "Hero of The Village",
                "Vein Miner"
        );
    }
}
