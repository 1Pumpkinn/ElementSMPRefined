package hs.elementSMPRefined.API.addon;

import hs.elementSMPRefined.ElementSMPRefined;

/**
 * Entry point for an addon that extends ElementSMPRefined.
 * Addons should register elements and listeners from {@link #register}.
 */
public interface ElementAddon {
    String getName();

    void register(ElementSMPRefined plugin);
}