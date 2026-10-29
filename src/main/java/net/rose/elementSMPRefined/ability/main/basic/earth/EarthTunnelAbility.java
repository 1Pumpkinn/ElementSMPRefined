package net.rose.elementSMPRefined.ability.main.basic.earth;

import org.bukkit.Bukkit;
import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.ability.passive.basic.earth.EarthElement;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.util.bukkit.MetadataHelper;
import net.rose.elementSMPRefined.lang.Lang;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class EarthTunnelAbility extends BaseAbility {

    private final ElementSMPRefined plugin;
    private final MetadataHelper metadataHelper;

    private final Map<UUID, ItemStack> tunnelTools = new HashMap<>();

    public EarthTunnelAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("earth_tunnel", ElementType.EARTH, 1, Constants.Cooldown.ABILITY_1_SECONDS, 1, configManager);
        this.plugin = (ElementSMPRefined) plugin;
        this.metadataHelper = ((ElementSMPRefined) plugin).getMetadataHelper();
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();

        // If ability is already active (check metadata), cancel it WITHOUT consuming mana
        if (metadataHelper.hasFlag(player, EarthElement.META_TUNNELING)) {
            metadataHelper.remove(player, EarthElement.META_TUNNELING);
            tunnelTools.remove(player.getUniqueId());
            player.sendMessage(Lang.EARTH_TUNNEL_TUNNELING_CANCELLED);
            setActive(player, false);
            return true;
        }

        metadataHelper.setLong(player, EarthElement.META_TUNNELING, System.currentTimeMillis() + Constants.Duration.EARTH_TUNNEL_MS);

        // If a pickaxe is in hand right now, lock it in for the whole tunnel (Fortune / Silk Touch included).
        ItemStack started = player.getInventory().getItemInMainHand();
        if (Tag.ITEMS_PICKAXES.isTagged(started.getType())) {
            tunnelTools.put(player.getUniqueId(), started.clone());
        } else {
            tunnelTools.remove(player.getUniqueId());
        }

        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, 1f, 0.8f);
        player.sendMessage(Lang.EARTH_TUNNEL_TUNNELING_STARTED_PRESS_AGAIN_CANCEL);

        setActive(player, true);

        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline() || !metadataHelper.hasFlag(player, EarthElement.META_TUNNELING)) {
                tunnelTools.remove(player.getUniqueId());
                setActive(player, false);
                task.cancel();
                return;
            }

            long until = metadataHelper.getLong(player, EarthElement.META_TUNNELING, 0);
            if (System.currentTimeMillis() > until) {
                metadataHelper.remove(player, EarthElement.META_TUNNELING);
                player.sendMessage(Lang.EARTH_TUNNEL_TUNNELING_ENDED);
                tunnelTools.remove(player.getUniqueId());
                setActive(player, false);
                task.cancel();
                return;
            }

            Vector direction = player.getLocation().getDirection().normalize();

            Location mineLocation;
            if (direction.getY() < -0.5) {
                mineLocation = player.getLocation().add(direction.multiply(1.0));
            } else {
                mineLocation = player.getEyeLocation().add(direction.multiply(1.5));
            }
            breakTunnel(mineLocation, player);

            player.getWorld().spawnParticle(Particle.BLOCK, mineLocation, 10, 0.5, 0.5, 0.5, 0.1, Material.STONE.createBlockData(), true);
        }, 0L, 2L);

        return true;
    }

    private void breakTunnel(Location center, Player player) {
        World world = center.getWorld();
        if (world == null) return;

        // Pickaxe locked in when the tunnel started (null if none was in hand then).
        ItemStack tool = tunnelTools.get(player.getUniqueId());

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    Location blockLoc = center.clone().add(x, y, z);
                    Block block = blockLoc.getBlock();

                    if (TUNNELABLE.contains(block.getType())) {
                        if (tool != null) {
                            // Drops are rolled against the locked-in pickaxe, so Fortune / Silk Touch apply.
                            block.breakNaturally(tool);
                        } else {
                            block.breakNaturally();
                        }
                        world.playSound(blockLoc, Sound.BLOCK_STONE_BREAK, 0.3f, 1.0f);
                    }
                }
            }
        }
    }

    private static final Set<Material> TUNNELABLE = EnumSet.of(
            Material.STONE, Material.DEEPSLATE, Material.DIRT, Material.GRASS_BLOCK,
            Material.COBBLESTONE, Material.ANDESITE, Material.DIORITE, Material.GRANITE,
            Material.MUD, Material.ROOTED_DIRT, Material.COARSE_DIRT, Material.ICE, Material.BLUE_ICE,
            Material.PACKED_ICE,

            Material.GRAVEL, Material.SAND, Material.RED_SAND, Material.SANDSTONE,
            Material.TUFF, Material.CALCITE, Material.DRIPSTONE_BLOCK,

            Material.BLACKSTONE, Material.CRIMSON_NYLIUM, Material.WARPED_HYPHAE,
            Material.SOUL_SAND, Material.BASALT,  Material.SMOOTH_BASALT, Material.SOUL_SOIL,

            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,

            Material.RAW_COPPER_BLOCK, Material.RAW_IRON_BLOCK, Material.RAW_GOLD_BLOCK

    );

    @Override
    public String getName() {
        return ChatColor.GOLD + "Earth Tunnel";
    }

    @Override
    public String getDescription() {
        return "Digs a tunnel where you're looking. Use your held pickaxe's if you want to apply Fortune and Silk Touch. Use again to stop tunneling.";
    }
}