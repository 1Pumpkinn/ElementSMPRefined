package net.rose.elementSMPRefined.core.API;

import net.rose.elementSMPRefined.core.API.element.ElementId;

import java.util.UUID;

/** Immutable snapshot of a player's ElementSMPRefined state. */
public record PlayerElementState(UUID playerId, ElementId elementId, int upgradeLevel, int mana) {
}
