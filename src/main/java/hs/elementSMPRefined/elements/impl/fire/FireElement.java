package hs.elementSMPRefined.elements.impl.fire;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.elements.BaseElement;
import hs.elementSMPRefined.elements.ElementContext;
import hs.elementSMPRefined.elements.ElementType;
import hs.elementSMPRefined.elements.abilities.Ability;
import hs.elementSMPRefined.elements.abilities.impl.fire.FireGeyserAbility;
import hs.elementSMPRefined.elements.abilities.impl.fire.MeteorRideAbility;
import hs.elementSMPRefined.services.EffectService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class FireElement extends BaseElement {
    private final Ability ability1;
    private final Ability ability2;

    public FireElement(ElementSMPRefined plugin) {
        super(plugin);
        this.ability1 = new FireGeyserAbility(plugin);
        this.ability2 = new MeteorRideAbility(plugin);
    }

    @Override
    public ElementType getType() {
        return ElementType.FIRE;
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        // Upside 1: Infinite Fire Resistance
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, true, false));

        // Upside 2: Fire Aspect on hits (handled in listener)
        // No passive effect needed here
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
        EffectService.removeElementPotionEffect(player, PotionEffectType.FIRE_RESISTANCE);
        ability1.setActive(player, false);
        ability2.setActive(player, false);
    }

    @Override
    public String getDisplayName() {
        return ChatColor.RED + "Fire";
    }

    @Override
    public String getDescription() {
        return ChatColor.GRAY + "Masters of flame and destruction. Fire users are immune to fire damage and can rain destruction from above.";
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