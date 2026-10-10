package net.rose.elementSMPRefined;

import net.rose.elementSMPRefined.ability.main.basic.fire.StarFallAbility;
import net.rose.elementSMPRefined.ability.main.upgraded.lava.EruptionAbility;
import net.rose.elementSMPRefined.core.AbstractElementPlugin;
import net.rose.elementSMPRefined.util.visual.model.AirCutterVisual;
import net.rose.elementSMPRefined.util.visual.model.ChainVisual;
import net.rose.elementSMPRefined.util.visual.model.MagmaBeamVisual;

/**
 * Main plugin class which significantly simplified by extending AbstractElementPlugin.
 * This class now only contains plugin-specific logic, with common functionality
 * handled by the abstract base class.
 */
public final class ElementSMPRefined extends AbstractElementPlugin {

    @Override
    protected void onPluginEnable() {
        getLogger().info("ElementSMPRefined enabled successfully!");
    }

    @Override
    protected void onPluginDisable() {
        // Sweep up any entities on plugin disable / restart
        StarFallAbility.removeAll();
        ChainVisual.removeAll();
        AirCutterVisual.removeAll();
        MagmaBeamVisual.removeAll();
        EruptionAbility.removeAll();
        getLogger().info("ElementSMPRefined disabled successfully!");
    }
}