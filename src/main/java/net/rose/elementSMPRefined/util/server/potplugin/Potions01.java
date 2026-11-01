package net.rose.elementSMPRefined.util.server.potplugin;

import net.rose.elementSMPRefined.util.server.potplugin.enchantlimits.EnchantLimits;
import net.rose.elementSMPRefined.util.server.potplugin.enchantlimits.EnchantLimitsCommand;
import net.rose.elementSMPRefined.util.server.potplugin.enchantlimits.EnchantLimitsListener;
import net.rose.elementSMPRefined.util.server.potplugin.listener.BrewingListener;
import net.rose.elementSMPRefined.util.server.potplugin.potlimits.PotLimitsCommand;
import net.rose.elementSMPRefined.util.server.potplugin.potlimits.PotionLimits;
import net.rose.elementSMPRefined.util.server.potplugin.potlimits.PotLimitsListener;
import net.rose.elementSMPRefined.util.server.potplugin.util.ExtensionRecipes;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class Potions01 extends JavaPlugin {

    @Override
    public void onEnable() {
        ExtensionRecipes.register(this);

        getServer().getPluginManager().registerEvents(new BrewingListener(), this);

        saveDefaultConfig();
        PotionLimits limits = new PotionLimits(this);
        limits.load();

        PotLimitsListener limitsListener = new PotLimitsListener(this, limits);
        getServer().getPluginManager().registerEvents(limitsListener, this);
        limitsListener.startSweepTask();

        PluginCommand command = getCommand("potlimits");
        if (command != null) {
            command.setExecutor(new PotLimitsCommand(limits, limitsListener));
        }

        EnchantLimits enchantLimits = new EnchantLimits(this);
        enchantLimits.load();

        EnchantLimitsListener enchantListener = new EnchantLimitsListener(this, enchantLimits);
        getServer().getPluginManager().registerEvents(enchantListener, this);
        enchantListener.startSweepTask();

        PluginCommand enchantCommand = getCommand("enchantlimits");
        if (enchantCommand != null) {
            enchantCommand.setExecutor(new EnchantLimitsCommand(enchantLimits, enchantListener));
        }
    }

    @Override
    public void onDisable() {
        ExtensionRecipes.unregister();
    }
}
