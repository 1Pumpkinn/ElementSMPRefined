package net.rose.elementSMPRefined;

import net.rose.elementSMPRefined.core.AbstractElementPlugin;
import org.bukkit.entity.SulfurCube;

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
        getLogger().info("ElementSMPRefined plugin disabled successfully!");
    }
}