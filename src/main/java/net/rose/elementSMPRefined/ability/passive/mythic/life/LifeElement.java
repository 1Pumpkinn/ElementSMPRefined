package net.rose.elementSMPRefined.ability.passive.mythic.life;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementSMPRefined.core.API.element.BaseElement;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.ability.main.mythic.life.SporeBlossomAbility;
import net.rose.elementSMPRefined.ability.main.mythic.life.LifeTotemAbility;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.services.EffectService;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LifeElement extends BaseElement {

    public LifeElement(JavaPlugin plugin, ConfigManager configManager) {
        super(plugin, ElementType.LIFE,
                new LifeTotemAbility(plugin, configManager),
                new SporeBlossomAbility(plugin, configManager));
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {

    }

    @Override
    public String getDisplayName() {
        return ChatColor.GREEN + "Life";
    }

    @Override
    public List<String> getPassiveBenefits() {
        return List.of(
                "Breaking leaves drops golden apples instead of apples",
                "Axes chop down the whole tree",
                "Golden apples give extra Absorption");
    }
}