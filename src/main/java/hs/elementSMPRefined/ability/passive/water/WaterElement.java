package hs.elementSMPRefined.ability.passive.water;

import hs.elementSMPRefined.API.element.BaseElement;
import hs.elementSMPRefined.API.element.ElementContext;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.API.ability.Ability;
import hs.elementSMPRefined.ability.main.water.WaterBeamAbility;
import hs.elementSMPRefined.ability.main.water.WaterGeyserAbility;
import hs.elementSMPRefined.services.EffectService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class WaterElement extends BaseElement {
    private final Ability ability1;
    private final Ability ability2;

    public WaterElement(JavaPlugin plugin) {
        super(plugin);
        this.ability1 = new WaterGeyserAbility(plugin);
        this.ability2 = new WaterBeamAbility(plugin);
    }

    @Override
    public ElementType getType() { return ElementType.WATER; }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        // Upside 1: Infinite conduit power
        player.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, Integer.MAX_VALUE, 0, true, false));
        
        if (upgradeLevel >= 2) {
            // Upside 2: Dolphins grace 5 (level 4 = dolphins grace 5)
            player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, Integer.MAX_VALUE, 4, true, false));
        }
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
    public void clearEffects(Player player) {
        EffectService.removeElementPotionEffect(player, PotionEffectType.CONDUIT_POWER);
        EffectService.removeElementPotionEffect(player, PotionEffectType.DOLPHINS_GRACE);
        ability1.setActive(player, false);
        ability2.setActive(player, false);
    }
    
    @Override
    public String getDisplayName() {
        return ChatColor.AQUA + "Water";
    }
    
    @Override
    public String getDescription() {
        return "Harness the flowing power of water to control the battlefield.";
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