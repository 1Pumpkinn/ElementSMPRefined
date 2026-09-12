package rose.elementSMPRefined.API.addon;

import rose.elementSMPRefined.API.ElementApi;

/**
 * Entry point for an addon that extends ElementSMPRefined.
 * Addons should register elements and listeners from {@link #register}.
 * <p>
 * {@link #register} receives {@link ElementApi} rather than the plugin
 * instance directly, so the supported facade is the obvious path for addon
 * authors rather than something they have to know to prefer. {@code
 * ElementApi#getPlugin()} still exists as an escape hatch for functionality
 * the facade doesn't cover yet - reaching for it is a deliberate opt-out,
 * not the default.
 */
public interface ElementAddon {
    String getName();

    void register(ElementApi api);
}