package net.rose.elementSMPRefined;

import net.rose.elementSMPRefined.ability.main.basic.fire.StarFallAbility;
import net.rose.elementSMPRefined.core.AbstractElementPlugin;
import net.rose.elementSMPRefined.util.visual.model.AirCutterVisual;
import net.rose.elementSMPRefined.util.visual.model.ChainVisual;

/**
 * Main plugin class which significantly simplified by extending AbstractElementPlugin.
 * This class now only contains plugin-specific logic, with common functionality
 * handled by the abstract base class.
 */
public final class ElementSMPRefined extends AbstractElementPlugin {

    @Override
    protected void onPluginEnable() {
        getLogger().info("ElementSMPRefined plugin enabled successfully!");
    }

    @Override
    protected void onPluginDisable() {
        // Sweep up any entities on plugin disable / restart
        StarFallAbility.removeAll();
        ChainVisual.removeAll();
        AirCutterVisual.removeAll();
        getLogger().info("ElementSMPRefined plugin disabled successfully!");
    }
}