package hs.elementSMPRefined;

import hs.elementSMPRefined.core.AbstractElementPlugin;
import hs.elementSMPRefined.API.element.Element;
import org.bukkit.event.Listener;

/**
 * Main plugin class - significantly simplified by extending AbstractElementPlugin.
 * This class now only contains plugin-specific logic, with common functionality
 * handled by the abstract base class.
 */
public final class ElementSMPRefined extends AbstractElementPlugin {

    /** Register an addon element and its optional provider listeners. */
    public void registerAddonElement(Element element) {
        getElementManager().registerAddonElement(element);
    }

    /** Register a listener owned by an external addon. */
    public void registerAddonListener(Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Addon listener cannot be null");
        }
        getServer().getPluginManager().registerEvents(listener, this);
    }

    @Override
    protected void onPluginEnable() {
        getLogger().info("ElementSMPRefined plugin enabled successfully!");
    }

    @Override
    protected void onPluginDisable() {
        getLogger().info("ElementSMPRefined plugin disabled successfully!");
    }
}