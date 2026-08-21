package hs.elementSMPRefined.API.element;

import hs.elementSMPRefined.API.ability.Ability;
import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.managers.ManaManager;
import hs.elementSMPRefined.managers.TrustManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Base implementation every element should extend.
 * <p>
 * Handles the parts that are identical for every element - mana spending,
 * upgrade-level gating, and forwarding ability name/description to the two
 * abilities you hand it - so a concrete element only has to describe what
 * makes it unique: its passives, display text, and its two {@link Ability}
 * instances.
 * <p>
 * See {@code hs.elementSMPRefined.util.example.ExampleElement} for a minimal
 * template to copy when adding a new element.
 */
public abstract class BaseElement implements Element {
    protected final ElementSMPRefined plugin;
    protected final Ability ability1;
    protected final Ability ability2;

    protected BaseElement(JavaPlugin plugin, Ability ability1, Ability ability2) {
        this.plugin = (ElementSMPRefined) plugin;
        this.ability1 = ability1;
        this.ability2 = ability2;
    }

    public ElementSMPRefined getPlugin() {
        return plugin;
    }

    @Override
    public final boolean ability1(ElementContext context) {
        return activate(context, ability1, 1, context.getConfigManager().getAbility1Cost(getId()), this::canCancelAbility1);
    }

    @Override
    public final boolean ability2(ElementContext context) {
        Player player = context.getPlayer();
        if (context.getUpgradeLevel() < 1) {
            player.sendMessage(ChatColor.RED + "You need Upgrade I before you can use Upgrade II abilities.");
            return false;
        }
        return activate(context, ability2, 2, context.getConfigManager().getAbility2Cost(getId()), this::canCancelAbility2);
    }

    /**
     * Shared activation flow: check upgrade level, let an active/cancellable ability
     * toggle off for free, otherwise check and spend mana on a successful cast.
     */
    private boolean activate(ElementContext context, Ability ability, int requiredLevel, int cost,
                             java.util.function.Predicate<ElementContext> canCancel) {
        Player player = context.getPlayer();
        if (!checkUpgradeLevel(player, context.getUpgradeLevel(), requiredLevel)) return false;

        if (canCancel.test(context)) {
            ability.execute(context);
            return true;
        }

        if (!hasMana(player, context.getManaManager(), cost)) return false;

        if (ability.execute(context)) {
            context.getManaManager().spend(player, cost);
            return true;
        }
        return false;
    }

    protected boolean checkUpgradeLevel(Player player, int upgradeLevel, int requiredLevel) {
        if (upgradeLevel < requiredLevel) {
            player.sendMessage(ChatColor.RED + "You need Upgrade " +
                    (requiredLevel == 1 ? "I" : "II") + " to use this ability.");
            return false;
        }
        return true;
    }

    protected boolean hasMana(Player player, ManaManager mana, int cost) {
        if (mana.get(player.getUniqueId()).getMana() < cost) {
            player.sendMessage(ChatColor.RED + "Not enough mana (" + cost + ")");
            return false;
        }
        return true;
    }

    /**
     * Whether ability1 should toggle off (no mana check) instead of activating again.
     * Off by default - override for abilities that support being cancelled mid-use.
     */
    protected boolean canCancelAbility1(ElementContext context) {
        return false;
    }

    /**
     * Whether ability2 should toggle off (no mana check) instead of activating again.
     * Off by default - override for abilities that support being cancelled mid-use.
     */
    protected boolean canCancelAbility2(ElementContext context) {
        return false;
    }

    protected boolean isValidTarget(Player player, LivingEntity target, TrustManager trust) {
        if (target.equals(player)) return false;
        return !(target instanceof Player other) || !trust.isTrusted(player.getUniqueId(), other.getUniqueId());
    }

    protected boolean isValidTarget(ElementContext context, LivingEntity target) {
        return isValidTarget(context.getPlayer(), target, context.getTrustManager());
    }

    /**
     * Default clean-up: deactivates both abilities. Override to also strip potion
     * effects, cancel passive tasks, clear metadata, etc. - call {@code super.clearEffects(player)}
     * so the abilities still get deactivated.
     */
    @Override
    public void clearEffects(Player player) {
        ability1.setActive(player, false);
        ability2.setActive(player, false);
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