package net.rose.elementSMPRefined;

import net.rose.elementSMPRefined.ability.main.basic.fire.StarFallAbility;
import net.rose.elementSMPRefined.ability.main.upgraded.lava.EruptionAbility;
import net.rose.elementSMPRefined.core.AbstractElementPlugin;
import net.rose.elementSMPRefined.ability.main.mythic.life.LifeTotemAbility;
import net.rose.elementSMPRefined.util.visual.model.AirCutterVisual;
import net.rose.elementSMPRefined.util.visual.model.ChainVisual;
import net.rose.elementSMPRefined.util.visual.model.EnergyBlastVisual;
import net.rose.elementSMPRefined.util.visual.model.MagmaBeamVisual;
import net.rose.elementSMPRefined.util.visual.model.MagnetarVisual;
import net.rose.elementSMPRefined.util.visual.model.ElementGemOrbitVisual;
import net.rose.elementSMPRefined.util.visual.model.IceBlockVisual;
import net.rose.elementSMPRefined.util.visual.model.IceSpikeVisual;
import net.rose.elementSMPRefined.ability.main.upgraded.frost.FrostIcicleBarrageAbility;
import net.rose.elementSMPRefined.util.visual.model.LightningSpearVisual;
import net.rose.elementSMPRefined.util.visual.model.MetalShieldVisual;
import net.rose.elementSMPRefined.util.visual.model.TornadoVisual;

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
        EnergyBlastVisual.removeAll();
        MetalShieldVisual.removeAll();
        ElementGemOrbitVisual.removeAll();
        MagnetarVisual.removeAll();
        TornadoVisual.removeAll();
        IceSpikeVisual.removeAll();
        IceBlockVisual.removeAll();
        FrostIcicleBarrageAbility.removeAll();
        EruptionAbility.removeAll();
        LifeTotemAbility.removeAll();
        getLogger().info("ElementSMPRefined disabled successfully!");
    }
}