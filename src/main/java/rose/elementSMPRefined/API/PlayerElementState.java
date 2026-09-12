package rose.elementSMPRefined.API;

import rose.elementSMPRefined.API.element.ElementId;

import java.util.UUID;

/** Immutable snapshot of a player's ElementSMPRefined state. */
public record PlayerElementState(UUID playerId, ElementId elementId, int upgradeLevel, int mana) {
}
