package net.rose.elementSMPRefined.core.API.element;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.Ability;
import net.rose.elementSMPRefined.lang.Lang;
import net.rose.elementSMPRefined.managers.CooldownManager;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Base implementation every element should extend.
 * <p>
 * Handles the parts that are identical for every element - cooldown gating,
 * upgrade-level gating, and forwarding ability name/description to the two
 * abilities you hand it - so a concrete element only has to describe what
 * makes it unique: its passives, display text, and its two {@link Ability}
 * instances.
 * <p>
 * See {@code net.rose.elementSMPRefined.util.example.ExampleElement} for a minimal
 * template to copy when adding a new element.
 */
public abstract class BaseElement implements Element {
    /**
     * Upgrade level at which an element's second passive unlocks. The first passive is
     * active from the start (level 0).
     */
    public static final int PASSIVE_2_UPGRADE_LEVEL = 2;

    protected final ElementSMPRefined plugin;
    private final ElementType type;
    protected final Ability ability1;
    protected final Ability ability2;

    protected BaseElement(JavaPlugin plugin, ElementType type, Ability ability1, Ability ability2) {
        this.plugin = (ElementSMPRefined) plugin;
        this.type = Objects.requireNonNull(type, "type");
        this.ability1 = Objects.requireNonNull(ability1, "ability1");
        this.ability2 = Objects.requireNonNull(ability2, "ability2");
        warnIfMisplaced(ability1, 1);
        warnIfMisplaced(ability2, 2);
    }

    /**
     * Abilities built with the element/slot constructor know where they belong; flag the
     * copy-paste mistake of handing an element another element's ability, or the right ability
     * in the wrong slot, instead of silently casting the wrong thing.
     */
    private void warnIfMisplaced(Ability ability, int slot) {
        ElementType declaredType = ability.getElementType();
        int declaredSlot = ability.getSlot();
        if ((declaredType != null && declaredType != type)
                || (declaredSlot != 0 && declaredSlot != slot)) {
            this.plugin
                    .getLogger()
                    .warning(
                            type
                                    + " slot "
                                    + slot
                                    + " was given ability '"
                                    + ability.getAbilityId()
                                    + "' which is declared for "
                                    + declaredType
                                    + " slot "
                                    + declaredSlot);
        }
    }

    @Override
    public final ElementType getType() {
        return type;
    }

    @Override
    public final Ability getAbility(int slot) {
        return switch (slot) {
            case 1 -> ability1;
            case 2 -> ability2;
            default -> throw new IllegalArgumentException("Ability slot must be 1 or 2: " + slot);
        };
    }

    @Override
    public final boolean ability1(ElementContext context) {
        return activate(context, ability1, this::canCancelAbility1);
    }

    @Override
    public final boolean ability2(ElementContext context) {
        return activate(context, ability2, this::canCancelAbility2);
    }

    private boolean activate(
            ElementContext context, Ability ability, Predicate<ElementContext> canCancel) {
        Player player = context.getPlayer();
        if (!checkUpgradeLevel(
                player, context.getUpgradeLevel(), ability.getRequiredUpgradeLevel())) {
            return false;
        }

        if (canCancel.test(context)) {
            ability.execute(context);
            return true;
        }

        if (!checkCooldown(player, context.getCooldownManager(), ability.getAbilityId()))
            return false;

        if (ability.execute(context)) {
            context.getCooldownManager()
                    .startCooldown(player, ability.getAbilityId(), ability.getCooldownSeconds());
            return true;
        }
        return false;
    }

    protected boolean checkUpgradeLevel(Player player, int upgradeLevel, int requiredLevel) {
        if (upgradeLevel >= requiredLevel) {
            return true;
        }
        // A player with no upgrade at all is pointed at Upgrade I first, even for an
        // Upgrade II ability.
        if (upgradeLevel < 1 && requiredLevel > 1) {
            player.sendMessage(Lang.BASE_ELEMENT_YOU_NEED_UPGRADE_I_BEFORE);
        } else {
            player.sendMessage(Lang.baseElementYouNeedUpgrade(romanNumeral(requiredLevel)));
        }
        return false;
    }

    private static String romanNumeral(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(level);
        };
    }

    protected boolean checkCooldown(
            Player player, CooldownManager cooldownManager, String abilityId) {
        // No chat message on cooldown - the action bar HUD already shows remaining time.
        return cooldownManager.isReady(player, abilityId);
    }

    /**
     * Whether ability1 should toggle off (no cooldown check) instead of activating again.
     * Off by default - override for abilities that support being cancelled mid-use.
     */
    protected boolean canCancelAbility1(ElementContext context) {
        return false;
    }

    /**
     * Whether ability2 should toggle off (no cooldown check) instead of activating again.
     * Off by default - override for abilities that support being cancelled mid-use.
     */
    protected boolean canCancelAbility2(ElementContext context) {
        return false;
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

    @Override
    public int getAbility1CooldownSeconds() {
        return ability1.getCooldownSeconds();
    }

    @Override
    public int getAbility2CooldownSeconds() {
        return ability2.getCooldownSeconds();
    }

    @Override
    public String getAbility1Id() {
        return ability1.getAbilityId();
    }

    @Override
    public String getAbility2Id() {
        return ability2.getAbilityId();
    }
}
