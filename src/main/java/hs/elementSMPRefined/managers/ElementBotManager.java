package hs.elementSMPRefined.managers;

import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.ElementSMPRefined;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ElementBotManager {
    public static final String BOT_METADATA = "element_smp_bot";
    private static final int ABILITY_INTERVAL_TICKS = 100;
    private final ElementSMPRefined plugin;
    private final NamespacedKey elementKey;
    private final Map<UUID, Mob> bots = new HashMap<>();
    private final Map<UUID, Integer> abilityTicks = new HashMap<>();

    public ElementBotManager(ElementSMPRefined plugin) {
        this.plugin = plugin;
        this.elementKey = new NamespacedKey(plugin, "bot_element");
        new BukkitRunnable() {
            @Override public void run() { tickBots(); }
        }.runTaskTimer(plugin, 20L, 10L);
    }

    public void spawn(Player owner, ElementType element) {
        stop(owner);
        Location location = owner.getLocation().add(owner.getLocation().getDirection().normalize().multiply(3));
        Zombie bot = owner.getWorld().spawn(location, Zombie.class);
        bot.setMetadata(BOT_METADATA, new FixedMetadataValue(plugin, owner.getUniqueId().toString()));
        bot.getPersistentDataContainer().set(elementKey, PersistentDataType.STRING, element.name());
        bot.setCustomName(ChatColor.GOLD + element.name() + " Bot");
        bot.setCustomNameVisible(true);
        bot.setCanPickupItems(false);
        bot.setAdult();
        bot.getEquipment().setItemInMainHand(new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_SWORD));
        bot.setTarget(owner);
        bots.put(owner.getUniqueId(), bot);
        abilityTicks.put(bot.getUniqueId(), ABILITY_INTERVAL_TICKS);
        owner.sendMessage(ChatColor.GREEN + "Spawned a " + element.name() + " elemental bot.");
    }

    public void stop(Player owner) {
        Mob bot = bots.remove(owner.getUniqueId());
        if (bot != null) {
            abilityTicks.remove(bot.getUniqueId());
            if (bot.isValid()) bot.remove();
        }
    }

    public void stopAll() {
        bots.values().forEach(bot -> { if (bot.isValid()) bot.remove(); });
        bots.clear();
        abilityTicks.clear();
    }

    public boolean owns(Entity entity) { return entity.hasMetadata(BOT_METADATA); }

    public void remove(Entity entity) {
        bots.values().removeIf(bot -> bot.getUniqueId().equals(entity.getUniqueId()));
        abilityTicks.remove(entity.getUniqueId());
    }

    private void tickBots() {
        for (Map.Entry<UUID, Mob> entry : Map.copyOf(bots).entrySet()) {
            Mob bot = entry.getValue();
            Player owner = plugin.getServer().getPlayer(entry.getKey());
            if (!bot.isValid() || bot.isDead() || owner == null || !owner.isOnline()
                    || !owner.getWorld().equals(bot.getWorld())) {
                stopById(entry.getKey(), bot);
                continue;
            }
            bot.setTarget(owner);
            int remaining = abilityTicks.merge(bot.getUniqueId(), -10, Integer::sum);
            if (remaining <= 0 && bot.getLocation().distanceSquared(owner.getLocation()) <= 400) {
                String value = bot.getPersistentDataContainer().get(elementKey, PersistentDataType.STRING);
                castAbility(bot, owner, ElementType.valueOf(value));
                abilityTicks.put(bot.getUniqueId(), ABILITY_INTERVAL_TICKS);
            }
        }
    }

    private void stopById(UUID ownerId, Mob bot) {
        bots.remove(ownerId);
        abilityTicks.remove(bot.getUniqueId());
        if (bot.isValid()) bot.remove();
    }

    private void castAbility(Mob bot, Player target, ElementType element) {
        Location targetLocation = target.getLocation();
        bot.getWorld().spawnParticle(particle(element), bot.getLocation().add(0, 1, 0), 18, .45, .6, .45, .05);
        bot.getWorld().playSound(bot.getLocation(), sound(element), .8f, 1.1f);
        switch (element) {
            case AIR -> {
                Vector leap = targetLocation.toVector().subtract(bot.getLocation().toVector()).normalize().multiply(1.4).setY(.65);
                bot.setVelocity(leap);
                target.setVelocity(target.getVelocity().add(leap.clone().multiply(.35)));
            }
            case WATER -> { target.damage(4.0, bot); slow(target, 50, 1); }
            case FIRE -> { target.setFireTicks(80); target.damage(5.0, bot); }
            case EARTH -> { target.damage(5.0, bot); slow(target, 60, 2); target.setVelocity(new Vector(0, .7, 0)); }
            case LIFE -> bot.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, false, true));
            case DEATH -> target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 1, false, true));
            case METAL -> {
                target.damage(6.0, bot);
                target.setVelocity(target.getVelocity().add(targetLocation.toVector().subtract(bot.getLocation().toVector()).normalize().multiply(.8)));
            }
            case FROST -> { slow(target, 100, 3); target.setFreezeTicks(Math.min(target.getMaxFreezeTicks(), 100)); }
        }
    }

    private void slow(Player target, int duration, int amplifier) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, amplifier, false, true));
    }

    private Particle particle(ElementType element) {
        return switch (element) {
            case FIRE -> Particle.FLAME; case WATER -> Particle.BUBBLE; case AIR -> Particle.CLOUD;
            case EARTH -> Particle.SMOKE; case LIFE -> Particle.HAPPY_VILLAGER; case DEATH -> Particle.SOUL;
            case METAL -> Particle.CRIT; case FROST -> Particle.SNOWFLAKE;
        };
    }

    private Sound sound(ElementType element) {
        return switch (element) {
            case FIRE -> Sound.ENTITY_BLAZE_SHOOT; case WATER -> Sound.ENTITY_PLAYER_SPLASH;
            case AIR -> Sound.ENTITY_PLAYER_ATTACK_SWEEP; case EARTH -> Sound.BLOCK_STONE_BREAK;
            case LIFE -> Sound.BLOCK_AMETHYST_BLOCK_CHIME; case DEATH -> Sound.ENTITY_WITHER_AMBIENT;
            case METAL -> Sound.BLOCK_ANVIL_LAND; case FROST -> Sound.BLOCK_GLASS_BREAK;
        };
    }
}