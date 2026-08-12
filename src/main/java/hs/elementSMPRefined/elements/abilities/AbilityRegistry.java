package hs.elementSMPRefined.elements.abilities;

import hs.elementSMPRefined.elements.ElementType;
import hs.elementSMPRefined.elements.abilities.impl.air.*;
import hs.elementSMPRefined.elements.abilities.impl.death.*;
import hs.elementSMPRefined.elements.abilities.impl.earth.*;
import hs.elementSMPRefined.elements.abilities.impl.fire.FireGeyserAbility;
import hs.elementSMPRefined.elements.abilities.impl.fire.MeteorRideAbility;
import hs.elementSMPRefined.elements.abilities.impl.frost.*;
import hs.elementSMPRefined.elements.abilities.impl.life.*;
import hs.elementSMPRefined.elements.abilities.impl.metal.*;
import hs.elementSMPRefined.elements.abilities.impl.water.*;
import hs.elementSMPRefined.ElementSMPRefined;

import java.util.*;

/**
 * Centralized registry for all abilities.
 * Provides type-safe access to abilities and handles registration.
 */
public final class AbilityRegistry {
    private final ElementSMPRefined plugin;
    private final Map<ElementType, AbilitySet> abilities = new EnumMap<>(ElementType.class);

    /**
     * Immutable record holding an element's two abilities
     */
    public record AbilitySet(Ability ability1, Ability ability2) {
        public AbilitySet {
            Objects.requireNonNull(ability1, "ability1 cannot be null");
            Objects.requireNonNull(ability2, "ability2 cannot be null");
        }
    }

    public AbilityRegistry(ElementSMPRefined plugin) {
        this.plugin = plugin;
        registerAll();
    }

    /**
     * Register all abilities for all elements
     */
    private void registerAll() {
        // Air
        register(ElementType.AIR,
                new SlicingWindAbility(plugin),
                new AirDashAbility(plugin)
        );

        // Water
        register(ElementType.WATER,
                new WaterGeyserAbility(plugin),
                new WaterBeamAbility(plugin)
        );

        // Fire
        register(ElementType.FIRE,
                new FireGeyserAbility(plugin),
                new MeteorRideAbility(plugin)
        );

        // Earth
        register(ElementType.EARTH,
                new EarthTunnelAbility(plugin),
                new EarthCharmAbility(plugin)
        );

        // Life
        register(ElementType.LIFE,
                new LifeRegenAbility(plugin),
                new LifeHealingBeamAbility(plugin)
        );

        // Death
        register(ElementType.DEATH,
                new DeathWitherSkullAbility(plugin),
                new DeathSummonUndeadAbility(plugin)
        );

        // Metal
        register(ElementType.METAL,
                new MetalChainAbility(plugin),
                new MetalDashAbility(plugin)
        );

        // Frost
        register(ElementType.FROST,
                new FrostCircleAbility(plugin),
                new FrostPunchAbility(plugin)
        );

        plugin.getLogger().info("Registered " + abilities.size() + " element ability sets");
    }

    /**
     * Register abilities for an element
     */
    private void register(ElementType type, Ability ability1, Ability ability2) {
        abilities.put(type, new AbilitySet(ability1, ability2));
    }

    /**
     * Get abilities for an element
     */
    public Optional<AbilitySet> getAbilities(ElementType type) {
        return Optional.ofNullable(abilities.get(type));
    }

    /**
     * Get ability 1 for an element
     */
    public Optional<Ability> getAbility1(ElementType type) {
        return getAbilities(type).map(AbilitySet::ability1);
    }

    /**
     * Get ability 2 for an element
     */
    public Optional<Ability> getAbility2(ElementType type) {
        return getAbilities(type).map(AbilitySet::ability2);
    }

    /**
     * Get all registered element types
     */
    public Set<ElementType> getRegisteredElements() {
        return Collections.unmodifiableSet(abilities.keySet());
    }
}