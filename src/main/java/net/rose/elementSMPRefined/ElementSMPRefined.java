package net.rose.elementSMPRefined;

import net.rose.elementSMPRefined.ability.main.basic.earth.ShockwaveAbility;
import net.rose.elementSMPRefined.ability.main.basic.fire.StarFallAbility;
import net.rose.elementSMPRefined.ability.main.basic.water.KrakenSlamAbility;
import net.rose.elementSMPRefined.ability.main.mythic.life.LifeTotemAbility;
import net.rose.elementSMPRefined.ability.main.mythic.life.SporeBlossomAbility;
import net.rose.elementSMPRefined.ability.main.upgraded.frost.FrostIcicleBarrageAbility;
import net.rose.elementSMPRefined.ability.main.upgraded.lava.EruptionAbility;
import net.rose.elementSMPRefined.core.AbstractElementPlugin;
import net.rose.elementSMPRefined.util.server.pack.ResourcePackManager;
import net.rose.elementSMPRefined.util.visual.model.AirCutterVisual;
import net.rose.elementSMPRefined.util.visual.model.HellsChainVisual;
import net.rose.elementSMPRefined.util.visual.model.DeathsVoidVisual;
import net.rose.elementSMPRefined.util.visual.model.ElementGemOrbitVisual;
import net.rose.elementSMPRefined.util.visual.model.FreezeEffectVisual;
import net.rose.elementSMPRefined.util.visual.model.IceSpikeVisual;
import net.rose.elementSMPRefined.util.visual.model.LightningSpearVisual;
import net.rose.elementSMPRefined.util.visual.model.MagmaBeamVisual;
import net.rose.elementSMPRefined.util.visual.model.MagnetarVisual;
import net.rose.elementSMPRefined.util.visual.model.MetalShieldVisual;
import net.rose.elementSMPRefined.util.visual.model.TornadoVisual;

public final class ElementSMPRefined extends AbstractElementPlugin {

    private ResourcePackManager resourcePackManager;

    @Override
    protected void beforeRegisterComponents() {
        resourcePackManager = new ResourcePackManager(this, getFile());
        resourcePackManager.start();
    }

    public ResourcePackManager getResourcePackManager() {
        return resourcePackManager;
    }

    @Override
    protected void onPluginEnable() {
        getLogger().info("ElementSMPRefined enabled successfully!");
    }

    @Override
    protected void onPluginDisable() {
        if (resourcePackManager != null) {
            resourcePackManager.stop();
        }
        // Sweep up any entities on plugin disable / restart. Each sweep is isolated so one
        // failure can't leave the others' entities behind.
        sweep("StarFall", StarFallAbility::removeAll);
        sweep("Hell's Chain", HellsChainVisual::removeAll);
        sweep("AirCutter", AirCutterVisual::removeAll);
        sweep("LightningSpear", LightningSpearVisual::removeAll);
        sweep("MagmaBeam", MagmaBeamVisual::removeAll);
        sweep("SporeBlossom", SporeBlossomAbility::removeAll);
        sweep("MetalShield", MetalShieldVisual::removeAll);
        sweep("ElementGemOrbit", ElementGemOrbitVisual::removeAll);
        sweep("Magnetar", MagnetarVisual::removeAll);
        sweep("Tornado", TornadoVisual::removeAll);
        sweep("Death's Void", DeathsVoidVisual::removeAll);
        sweep("IceSpike", IceSpikeVisual::removeAll);
        sweep("FreezeEffect", FreezeEffectVisual::removeAll);
        sweep("FrostIcicleBarrage", FrostIcicleBarrageAbility::removeAll);
        sweep("Eruption", EruptionAbility::removeAll);
        sweep("LifeTotem", LifeTotemAbility::removeAll);
        sweep("Shockwave", ShockwaveAbility::removeAll);
        sweep("KrakenSlam", KrakenSlamAbility::removeAll);
        getLogger().info("ElementSMPRefined disabled successfully!");
    }

    private void sweep(String name, Runnable sweep) {
        try {
            sweep.run();
        } catch (Exception e) {
            getLogger()
                    .log(
                            java.util.logging.Level.WARNING,
                            "Failed to sweep " + name + " on disable",
                            e);
        }
    }
}
