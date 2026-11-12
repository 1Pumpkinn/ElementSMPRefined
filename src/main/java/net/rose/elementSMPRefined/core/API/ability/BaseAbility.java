package net.rose.elementSMPRefined.core.API.ability;

import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.util.damage.AbilityTargets;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Base class every ability should extend. Provides the id / cooldown / required-level
 * bookkeeping, per-player "currently active" tracking and the shared target check, so a
 * concrete ability only has to describe itself and implement {@link #execute}.
 * <p>
 * You never call {@code execute} yourself - {@code BaseElement.ability1()/ability2()} do,
 * after the upgrade level and cooldown have been checked. The cooldown only starts when
 * {@code execute} returns {@code true}, so a failed cast stays free.
 */
public abstract class BaseAbility implements Ability {
    private final String abilityId;
    private final int cooldownSeconds;
    private final int requiredUpgradeLevel;
    private final @Nullable ElementType elementType; // null for template abilities
    private final int abilitySlot; // 1 or 2, paired with elementType; 0 when unassigned
    private final @Nullable ConfigManager configManager;
    private final Set<UUID> activePlayers = new HashSet<>();

    /**
     * Create an ability that isn't bound to a specific element slot (templates such as
     * {@code ExampleAbility}). Built-in abilities should use the constructor below so they
     * know which element and slot they occupy.
     *
     * @param abilityId The unique identifier for this ability
     * @param cooldownSeconds The cooldown in seconds
     * @param requiredUpgradeLevel The minimum upgrade level required
     */
    public BaseAbility(String abilityId, int cooldownSeconds, int requiredUpgradeLevel) {
        this(abilityId, null, 0, cooldownSeconds, requiredUpgradeLevel, null);
    }

    /**
     * Create an ability bound to a slot of an element.
     *
     * @param abilityId The unique identifier for this ability
     * @param elementType The element this ability belongs to
     * @param abilitySlot 1 for ability1, 2 for ability2
     * @param cooldownSeconds The cooldown in seconds
     * @param requiredUpgradeLevel The minimum upgrade level required
     * @param configManager Live config access, available to the ability through {@link
     *     #getConfigManager()} so tunables can be read at cast time instead of hardcoded
     */
    public BaseAbility(
            String abilityId,
            @Nullable ElementType elementType,
            int abilitySlot,
            int cooldownSeconds,
            int requiredUpgradeLevel,
            @Nullable ConfigManager configManager) {
        this.abilityId = Objects.requireNonNull(abilityId, "abilityId");
        if (abilityId.isBlank()) {
            throw new IllegalArgumentException("abilityId must not be blank");
        }
        if (cooldownSeconds < 0) {
            throw new IllegalArgumentException(abilityId + ": cooldownSeconds must be >= 0");
        }
        if (abilitySlot < 0 || abilitySlot > 2) {
            throw new IllegalArgumentException(abilityId + ": abilitySlot must be 0, 1 or 2");
        }
        this.cooldownSeconds = cooldownSeconds;
        this.requiredUpgradeLevel = requiredUpgradeLevel;
        this.elementType = elementType;
        this.abilitySlot = abilitySlot;
        this.configManager = configManager;
    }

    @Override
    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    @Override
    public int getRequiredUpgradeLevel() {
        return requiredUpgradeLevel;
    }

    @Override
    public String getAbilityId() {
        return abilityId;
    }

    @Override
    public @Nullable ElementType getElementType() {
        return elementType;
    }

    @Override
    public int getSlot() {
        return abilitySlot;
    }

    /** Live config access, or {@code null} for abilities created without one. */
    protected @Nullable ConfigManager getConfigManager() {
        return configManager;
    }

    @Override
    public boolean isActiveFor(Player player) {
        return activePlayers.contains(player.getUniqueId());
    }

    @Override
    public void setActive(Player player, boolean active) {
        if (active) {
            activePlayers.add(player.getUniqueId());
        } else {
            activePlayers.remove(player.getUniqueId());
        }
    }

    @Override
    public abstract String getName();

    @Override
    public abstract String getDescription();

    /**
     * Whether {@code target} may be hit by this ability: see {@link AbilityTargets#isValid} (not
     * the caster, alive, not an armor stand or villager, not a creative/spectator player and not
     * on the caster's team or an allied team). Override for more complex logic.
     */
    protected boolean isValidTarget(ElementContext context, LivingEntity target) {
        return AbilityTargets.isValid(context.getPlayer(), target, context.getTeamManager());
    }
}
