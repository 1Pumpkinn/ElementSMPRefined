package hs.elementSMPRefined;

import hs.elementSMPRefined.core.AbstractElementPlugin;

/**
 * Main plugin class - significantly simplified by extending AbstractElementPlugin.
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

    // Additional plugin-specific logic can be added here
    // The base class handles all common initialization and management
}