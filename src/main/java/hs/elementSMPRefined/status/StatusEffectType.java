package hs.elementSMPRefined.status;

/**
 * Enum representing all possible status effect types
 */
public enum StatusEffectType {
    // Stun types with different restriction levels
    FULL_STUN,           // Cannot move, look around, or interact
    PARTIAL_STUN,        // Cannot move or look around, but can interact
    STUN,                // Cannot move, but can look and interact
    
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
    ROOT,                // Same as STUN (cannot move but can look and interact)
    DISARM
}