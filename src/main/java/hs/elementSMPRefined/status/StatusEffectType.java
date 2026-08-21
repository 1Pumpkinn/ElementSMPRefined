package hs.elementSMPRefined.status;

/**
 * Enum representing all possible status effect types
 */
public enum StatusEffectType {

    // Other status effects
    SLOW,
    SILENCE,
    WEAKNESS,
    FREEZE,
    BLEED,
    BURN,
    POISON,
    BLIND,
    WITHER,
    FEAR,
    CONFUSION,
    ROOT,

    FULL_STUN,           // Cannot move, look around, or interact
    PARTIAL_STUN,        // Cannot move or look around, but can interact
    STUN,                // Cannot move, but can look and interact// Same as STUN (cannot move but can look and interact)

    // Disarm types - handled by DisarmManager, not StatusEffectManager,
    ABILITY_DISARM,      // Cannot use element abilities
    WEAPON_DISARM,       // A random weapon the player owns (sword/axe/spear/mace) goes on cooldown
    MAIN_HAND_DISARM     // Whatever is currently in the player's main hand goes on cooldown
}