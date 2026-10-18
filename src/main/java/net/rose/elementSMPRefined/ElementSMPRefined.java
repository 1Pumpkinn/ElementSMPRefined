package net.rose.elementSMPRefined;

import net.rose.elementSMPRefined.ability.main.basic.fire.StarFallAbility;
import net.rose.elementSMPRefined.ability.main.upgraded.lava.EruptionAbility;
import net.rose.elementSMPRefined.core.AbstractElementPlugin;
import net.rose.elementSMPRefined.util.visual.model.AirCutterVisual;
import net.rose.elementSMPRefined.util.visual.model.ChainVisual;
import net.rose.elementSMPRefined.util.visual.model.MagmaBeamVisual;
import net.rose.elementSMPRefined.util.visual.model.MagnetarVisual;
import net.rose.elementSMPRefined.util.visual.model.ElementGemOrbitVisual;
import net.rose.elementSMPRefined.util.visual.model.IceSpikeVisual;
import net.rose.elementSMPRefined.util.visual.model.LightningSpearVisual;
import net.rose.elementSMPRefined.util.visual.model.MetalShieldVisual;
import net.rose.elementSMPRefined.util.visual.model.TornadoVisual;

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
        LightningSpearVisual.removeAll();
        MagmaBeamVisual.removeAll();
        MetalShieldVisual.removeAll();
        ElementGemOrbitVisual.removeAll();
        MagnetarVisual.removeAll();
        TornadoVisual.removeAll();
        IceSpikeVisual.removeAll();
        EruptionAbility.removeAll();
        getLogger().info("ElementSMPRefined disabled successfully!");
    }
}