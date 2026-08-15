package hs.elementSMPRefined.ability.passive.earth;

import hs.elementSMPRefined.API.element.BaseElement;
import hs.elementSMPRefined.API.element.ElementContext;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.API.ability.Ability;
import hs.elementSMPRefined.ability.main.earth.EarthTunnelAbility;
import hs.elementSMPRefined.ability.main.earth.GraspAbility;
import hs.elementSMPRefined.services.EffectService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class EarthElement extends BaseElement {
    public static final String META_MINE_UNTIL = "earth_mine_until";
    public static final String META_TUNNELING = "earth_tunneling";

    private final Ability ability1;
    private final Ability ability2;

    public EarthElement(JavaPlugin plugin) {
        super(plugin);
        this.ability1 = new EarthTunnelAbility(plugin);
        this.ability2 = new GraspAbility(plugin);
    }

    @Override
    public ElementType getType() {
        return ElementType.EARTH;
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, Integer.MAX_VALUE, 0, true, false));
    }

    @Override
    protected boolean executeAbility1(ElementContext context) {
        return ability1.execute(context);
    }

    @Override
    protected boolean executeAbility2(ElementContext context) {
        return ability2.execute(context);
    }

    @Override
    protected boolean canCancelAbility1(ElementContext context) {
        // Check if the player has the tunneling metadata - if so, they can cancel
        return context.getPlayer().hasMetadata(META_TUNNELING);
    }

    @Override
    public void clearEffects(Player player) {
        EffectService.removeElementPotionEffect(player, PotionEffectType.HERO_OF_THE_VILLAGE);
        player.removeMetadata(META_MINE_UNTIL, plugin);
        player.removeMetadata(META_TUNNELING, plugin);
        ability1.setActive(player, false);
        ability2.setActive(player, false);
    }

    @Override
    public String getDisplayName() {
        return ChatColor.YELLOW + "Earth";
    }

    @Override
    public String getDescription() {
        return "Masters of stone and earth. Earth users can tunnel through blocks and grasp enemies with earthen hands.";
    }

    @Override
    public String getAbility1Name() {
        return ability1.getName();
    }

    @Override
    public String getAbility1Description() {
        return ability1.getDescription();
    }

    @Override
    public String getAbility2Name() {
        return ability2.getName();
    }

    @Override
    public String getAbility2Description() {
        return ability2.getDescription();
    }
}