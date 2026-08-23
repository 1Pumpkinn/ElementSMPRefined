package hs.elementSMPRefined.managers;

import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.ElementSMPRefined;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages elemental companion bots: spawning, AI (targeting/movement/ability use),
 * and passive effects. Bots actively hunt hostile players/mobs near their owner,
 * alternate between both of their element's abilities based on cooldown and range,
 * and carry the same passive perks a player of that element would have.
 */
public final class ElementBotManager implements Listener {
    public static final String BOT_METADATA = "element_smp_bot";

    private static final int RUN_PERIOD_TICKS = 5;      // AI tick rate (4x/sec)
    private static final int PASSIVE_REFRESH_TICKS = 100; // how often passive effects are re-applied
    private static final int RETARGET_INTERVAL_TICKS = 60; // how often we look for a new target
    private static final int REPATH_INTERVAL_TICKS = 10;   // how often we recompute the path
    private static final double SEARCH_RADIUS = 16.0;
    private static final double TARGET_LOSE_RADIUS_SQ = (SEARCH_RADIUS * 1.5) * (SEARCH_RADIUS * 1.5);
    private static final double GUARD_LEASH_RADIUS_SQ = 64.0;   // start following owner past 8 blocks
    private static final double LIFE_SUPPORT_RADIUS_SQ = 100.0; // heal owner within 10 blocks
    private static final double AOE_RADIUS = 4.0;

    private final ElementSMPRefined plugin;
    private final NamespacedKey elementKey;
    private final Map<UUID, Mob> bots = new HashMap<>();
    private final Map<UUID, BotState> states = new HashMap<>();

    private static final class BotState {
        int ability1Cd;
        int ability2Cd;
        int retargetTicks;
        int repathTicks;
        int passiveTicks;
        UUID targetId;
    }

    public ElementBotManager(ElementSMPRefined plugin) {
        this.plugin = plugin;
        this.elementKey = new NamespacedKey(plugin, "bot_element");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        new BukkitRunnable() {
            @Override public void run() { tickBots(); }
        }.runTaskTimer(plugin, 20L, RUN_PERIOD_TICKS);
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
        bot.setShouldBurnInDay(false);
        bot.setPersistent(true);
        bot.setRemoveWhenFarAway(false);
        bot.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        equipProtectedDiamondArmor(bot);
        bot.setTarget(null);

        var speedAttr = bot.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            double naturalBase = speedAttr.getBaseValue(); // zombie's actual spawn speed (~0.23), not the attribute's generic default
            speedAttr.setBaseValue(naturalBase * speedMultiplier(element));
        }

        bots.put(owner.getUniqueId(), bot);
        BotState state = new BotState();
        states.put(bot.getUniqueId(), state);
        applyPassiveTick(bot, element, state); // apply immediately instead of waiting for first refresh

        owner.sendMessage(ChatColor.GREEN + "Spawned a " + element.name() + " elemental bot.");
    }

    public void stop(Player owner) {
        Mob bot = bots.remove(owner.getUniqueId());
        if (bot != null) {
            states.remove(bot.getUniqueId());
            if (bot.isValid()) bot.remove();
        }
    }

    public void stopAll() {
        bots.values().forEach(bot -> { if (bot.isValid()) bot.remove(); });
        bots.clear();
        states.clear();
    }

    public boolean owns(Entity entity) { return entity.hasMetadata(BOT_METADATA); }

    public void remove(Entity entity) {
        bots.values().removeIf(bot -> bot.getUniqueId().equals(entity.getUniqueId()));
        states.remove(entity.getUniqueId());
    }

    // ------------------------------------------------------------------
    // AI loop
    // ------------------------------------------------------------------

    private void tickBots() {
        for (Map.Entry<UUID, Mob> entry : Map.copyOf(bots).entrySet()) {
            Mob bot = entry.getValue();
            Player owner = plugin.getServer().getPlayer(entry.getKey());
            if (!bot.isValid() || bot.isDead() || owner == null || !owner.isOnline()
                    || !owner.getWorld().equals(bot.getWorld())) {
                stopById(entry.getKey(), bot);
                continue;
            }

            ElementType element = elementOf(bot);
            if (element == null) continue;
            BotState state = states.computeIfAbsent(bot.getUniqueId(), id -> new BotState());

            // Safety net: vanilla hostile-mob AI can otherwise latch onto the owner (or a
            // trusted player) as a target. Never let that stick.
            LivingEntity vanillaTarget = bot.getTarget();
            if (vanillaTarget != null && isProtected(vanillaTarget, owner)) {
                bot.setTarget(null);
            }

            applyPassiveTick(bot, element, state);

            LivingEntity target = resolveTarget(bot, owner, state);

            if (state.ability1Cd > 0) state.ability1Cd -= RUN_PERIOD_TICKS;
            if (state.ability2Cd > 0) state.ability2Cd -= RUN_PERIOD_TICKS;

            if (target != null) {
                bot.setTarget(target); // hands movement + melee swings to the zombie's own attack AI
                state.repathTicks = REPATH_INTERVAL_TICKS; // reset so guard-mode repaths immediately if target is lost

                double distSq = bot.getLocation().distanceSquared(target.getLocation());
                if (distSq <= abilityOneRangeSq(element) && state.ability1Cd <= 0) {
                    castAbilityOne(bot, owner, target, element);
                    state.ability1Cd = abilityOneCooldown(element);
                } else if (state.ability2Cd <= 0) {
                    castAbilityTwo(bot, owner, target, element);
                    state.ability2Cd = abilityTwoCooldown(element);
                }
            } else {
                double ownerDistSq = bot.getLocation().distanceSquared(owner.getLocation());
                if (ownerDistSq > GUARD_LEASH_RADIUS_SQ && (state.repathTicks -= RUN_PERIOD_TICKS) <= 0) {
                    bot.getPathfinder().moveTo(owner, 1.0);
                    state.repathTicks = REPATH_INTERVAL_TICKS;
                }

                if (element == ElementType.LIFE && ownerDistSq <= LIFE_SUPPORT_RADIUS_SQ
                        && state.ability1Cd <= 0 && isHurt(owner)) {
                    castLifeSupport(bot, owner);
                    state.ability1Cd = abilityOneCooldown(element);
                }
            }
        }
    }

    private LivingEntity resolveTarget(Mob bot, Player owner, BotState state) {
        if (state.targetId != null) {
            Entity candidate = plugin.getServer().getEntity(state.targetId);
            if (candidate instanceof LivingEntity le && le.isValid() && !le.isDead()
                    && le.getWorld().equals(bot.getWorld())
                    && le.getLocation().distanceSquared(bot.getLocation()) <= TARGET_LOSE_RADIUS_SQ
                    && !isProtected(le, owner)) {
                return le;
            }
            state.targetId = null;
        }
        if ((state.retargetTicks -= RUN_PERIOD_TICKS) <= 0) {
            state.retargetTicks = RETARGET_INTERVAL_TICKS;
            LivingEntity found = findHostileTarget(bot, owner);
            state.targetId = found != null ? found.getUniqueId() : null;
            return found;
        }
        return null;
    }

    private LivingEntity findHostileTarget(Mob bot, Player owner) {
        LivingEntity best = null;
        double bestDistSq = SEARCH_RADIUS * SEARCH_RADIUS;
        for (LivingEntity nearby : bot.getLocation().getNearbyLivingEntities(SEARCH_RADIUS)) {
            if (!(nearby instanceof Player p) || isProtected(nearby, owner) || owns(nearby)) continue;
            if (!p.isOnline() || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) continue;
            double distSq = nearby.getLocation().distanceSquared(bot.getLocation());
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = nearby;
            }
        }
        return best;
    }

    private boolean isProtected(LivingEntity entity, Player owner) {
        if (entity.equals(owner)) return true;
        if (entity instanceof Player p) {
            return plugin.getTrustManager().isTrusted(owner.getUniqueId(), p.getUniqueId());
        }
        return false;
    }

    private boolean isValidVictim(LivingEntity le, Mob bot, Player owner) {
        return !le.equals(bot) && !owns(le) && !isProtected(le, owner);
    }

    private boolean isHurt(Player owner) {
        var attr = owner.getAttribute(Attribute.MAX_HEALTH);
        double max = attr != null ? attr.getValue() : owner.getHealth();
        return owner.getHealth() < max;
    }

    private void stopById(UUID ownerId, Mob bot) {
        bots.remove(ownerId);
        states.remove(bot.getUniqueId());
        if (bot.isValid()) bot.remove();
    }

    private ElementType elementOf(Mob bot) {
        String value = bot.getPersistentDataContainer().get(elementKey, PersistentDataType.STRING);
        return value == null ? null : ElementType.valueOf(value);
    }

    // ------------------------------------------------------------------
    // Passives (mirrors each element's player passive benefits)
    // ------------------------------------------------------------------

    private void applyPassiveTick(Mob bot, ElementType element, BotState state) {
        if (element == ElementType.AIR) {
            bot.setFallDistance(0f); // Air passive: no fall damage, checked every tick
        }
        if ((state.passiveTicks -= RUN_PERIOD_TICKS) > 0) return;
        state.passiveTicks = PASSIVE_REFRESH_TICKS;
        int duration = PASSIVE_REFRESH_TICKS + 20;

        // Undead bots would otherwise burn in daylight; keep every element immune to that,
        // on top of setShouldBurnInDay(false) set at spawn.
        bot.setFireTicks(0);
        bot.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, duration, 0, true, false));

        switch (element) {
            case FIRE -> {
                // Fire's own passive is already covered by the universal fire resistance above.
            }
            case WATER -> bot.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, duration, 0, true, false));
            case EARTH -> {
                var attr = bot.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
                if (attr != null) attr.setBaseValue(0.5);
            }
            case FROST -> {
                Material below = bot.getLocation().clone().subtract(0, 1, 0).getBlock().getType();
                if (isSnowOrIce(below)) {
                    bot.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 1, true, false));
                }
            }
            case LIFE -> {
                var attr = bot.getAttribute(Attribute.MAX_HEALTH);
                if (attr != null && attr.getBaseValue() < 30.0) attr.setBaseValue(30.0);
                bot.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, duration, 0, true, false));
            }
            case DEATH -> {
                long time = bot.getWorld().getTime();
                boolean night = time >= 13000 && time < 23000;
                if (night) {
                    bot.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, duration, 0, true, false));
                } else {
                    bot.removePotionEffect(PotionEffectType.INVISIBILITY);
                }
            }
            case METAL -> {
                var attr = bot.getAttribute(Attribute.ATTACK_SPEED);
                if (attr != null) attr.setBaseValue(attr.getDefaultValue() + 0.4);
            }
            case AIR -> { /* handled every tick above */ }
        }
    }

    private boolean isSnowOrIce(Material material) {
        return switch (material) {
            case SNOW, SNOW_BLOCK, ICE, PACKED_ICE, BLUE_ICE, FROSTED_ICE -> true;
            default -> false;
        };
    }

    // On-hit passives that need a real combat event: Fire Aspect and Wither-on-hit for the
    // bot's own attacks, and arrow immunity for Metal bots.
    @EventHandler(ignoreCancelled = true)
    public void onBotCombat(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Mob victimBot && owns(victimBot)
                && elementOf(victimBot) == ElementType.METAL && event.getDamager() instanceof Arrow) {
            event.setCancelled(true);
            return;
        }

        Entity rawDamager = event.getDamager();
        Entity source = (rawDamager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter)
                ? shooter : rawDamager;
        if (!(source instanceof Mob bot) || !owns(bot) || !(event.getEntity() instanceof LivingEntity victim)) return;

        ElementType element = elementOf(bot);
        if (element == ElementType.FIRE) {
            victim.setFireTicks(Math.max(victim.getFireTicks(), 60));
        } else if (element == ElementType.DEATH && Math.random() < 0.25) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0, false, true));
        }
    }

    // ------------------------------------------------------------------
    // Abilities - two per element, chosen by range/cooldown in tickBots()
    // ------------------------------------------------------------------

    private void castAbilityOne(Mob bot, Player owner, LivingEntity target, ElementType element) {
        Location origin = bot.getLocation().add(0, 1, 0);
        bot.getWorld().spawnParticle(particle(element), origin, 20, .5, .6, .5, .05);
        bot.getWorld().playSound(bot.getLocation(), sound(element), .8f, 1.0f);

        switch (element) {
            case AIR -> { // Slicing Wind: ranged cutting gust
                Vector dir = target.getLocation().toVector().subtract(bot.getLocation().toVector()).normalize();
                target.damage(4.0, bot);
                target.setVelocity(target.getVelocity().add(dir.multiply(.6).setY(.2)));
            }
            case WATER -> { // Water Bubble: close-range burst that slows everyone caught in it
                for (LivingEntity le : bot.getLocation().getNearbyLivingEntities(AOE_RADIUS)) {
                    if (!isValidVictim(le, bot, owner)) continue;
                    le.damage(4.0, bot);
                    slow(le, 60, 1);
                }
            }
            case FIRE -> { // Fire Geyser: close-range burst that ignites and pops enemies up
                for (LivingEntity le : bot.getLocation().getNearbyLivingEntities(AOE_RADIUS)) {
                    if (!isValidVictim(le, bot, owner)) continue;
                    le.damage(5.0, bot);
                    le.setFireTicks(80);
                    le.setVelocity(le.getVelocity().add(new Vector(0, .6, 0)));
                }
            }
            case EARTH -> { // Grasp: melee root
                target.damage(6.0, bot);
                slow(target, 60, 3);
            }
            case LIFE -> { // Life drain strike
                target.damage(3.0, bot);
                var attr = bot.getAttribute(Attribute.MAX_HEALTH);
                double max = attr != null ? attr.getValue() : bot.getHealth();
                bot.setHealth(Math.min(max, bot.getHealth() + 2.0));
            }
            case DEATH -> { // Death Sidestep: quick strike + weaken
                target.damage(5.0, bot);
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, false, true));
            }
            case METAL -> { // Metal Chain: pull target in and hit
                target.damage(5.0, bot);
                Vector pull = bot.getLocation().toVector().subtract(target.getLocation().toVector()).normalize().multiply(.8).setY(.15);
                target.setVelocity(target.getVelocity().add(pull));
            }
            case FROST -> { // Frost Circle: close-range slowing burst
                for (LivingEntity le : bot.getLocation().getNearbyLivingEntities(AOE_RADIUS)) {
                    if (!isValidVictim(le, bot, owner)) continue;
                    le.damage(3.0, bot);
                    slow(le, 80, 2);
                }
            }
        }
    }

    private void castAbilityTwo(Mob bot, Player owner, LivingEntity target, ElementType element) {
        Location origin = bot.getLocation().add(0, 1, 0);
        bot.getWorld().spawnParticle(particle(element), origin, 26, .6, .7, .6, .06);
        bot.getWorld().playSound(bot.getLocation(), sound(element), .9f, 1.2f);

        switch (element) {
            case AIR -> { // Air Dash: leap gap-closer
                Vector leap = target.getLocation().toVector().subtract(bot.getLocation().toVector()).normalize().multiply(1.5).setY(.6);
                bot.setVelocity(leap);
                target.damage(3.0, bot);
            }
            case WATER -> { // Pull Down: yank the target down and slow it
                target.setVelocity(target.getVelocity().add(new Vector(0, -.7, 0)));
                target.damage(4.0, bot);
                slow(target, 60, 2);
            }
            case FIRE -> { // Meteor Crash: heavier ranged hit for targets it can't AoE
                target.damage(7.0, bot);
                target.setFireTicks(100);
                target.setVelocity(target.getVelocity().add(new Vector(0, .5, 0)));
            }
            case EARTH -> { // Earth Tunnel: burrow gap-closer
                bot.teleport(target.getLocation().clone().subtract(target.getLocation().getDirection().multiply(1.5)));
                target.damage(4.0, bot);
            }
            case LIFE -> { // Regen burst: big self-heal + push the enemy off
                var attr = bot.getAttribute(Attribute.MAX_HEALTH);
                double max = attr != null ? attr.getValue() : bot.getHealth();
                bot.setHealth(Math.min(max, bot.getHealth() + 6.0));
                Vector push = bot.getLocation().toVector().subtract(target.getLocation().toVector()).multiply(-1).normalize().multiply(.6).setY(.3);
                target.setVelocity(target.getVelocity().add(push));
            }
            case DEATH -> { // Disarm: heavy debuff
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1, false, true));
                target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0, false, true));
                target.damage(3.0, bot);
            }
            case METAL -> { // Metal Dash: charge through the target
                Vector dash = target.getLocation().toVector().subtract(bot.getLocation().toVector()).normalize().multiply(1.4);
                bot.setVelocity(dash.clone().setY(.2));
                target.damage(6.0, bot);
                target.setVelocity(target.getVelocity().add(dash.multiply(.5)));
            }
            case FROST -> { // Frost Punch: heavy single hit + freeze
                target.damage(7.0, bot);
                target.setFreezeTicks(Math.min(target.getMaxFreezeTicks(), 140));
                slow(target, 100, 3);
            }
        }
    }

    private void castLifeSupport(Mob bot, Player owner) {
        bot.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, owner.getLocation().add(0, 1, 0), 20, .5, .6, .5, .05);
        bot.getWorld().playSound(bot.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, .8f, 1.2f);
        var attr = owner.getAttribute(Attribute.MAX_HEALTH);
        double max = attr != null ? attr.getValue() : owner.getHealth();
        owner.setHealth(Math.min(max, owner.getHealth() + 4.0));
    }

    private void equipProtectedDiamondArmor(Zombie bot) {
        ItemStack helmet = new ItemStack(Material.DIAMOND_HELMET);
        ItemStack chestplate = new ItemStack(Material.DIAMOND_CHESTPLATE);
        ItemStack leggings = new ItemStack(Material.DIAMOND_LEGGINGS);
        ItemStack boots = new ItemStack(Material.DIAMOND_BOOTS);
        for (ItemStack piece : new ItemStack[]{helmet, chestplate, leggings, boots}) {
            piece.addUnsafeEnchantment(Enchantment.PROTECTION, 3);
        }
        var equipment = bot.getEquipment();
        equipment.setHelmet(helmet);
        equipment.setChestplate(chestplate);
        equipment.setLeggings(leggings);
        equipment.setBoots(boots);
        // Don't let it drop the gear if it dies.
        equipment.setHelmetDropChance(0f);
        equipment.setChestplateDropChance(0f);
        equipment.setLeggingsDropChance(0f);
        equipment.setBootsDropChance(0f);
        equipment.setItemInMainHandDropChance(0f);
    }

    private void slow(LivingEntity target, int duration, int amplifier) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, amplifier, false, true));
    }

    // ------------------------------------------------------------------
    // Tuning tables
    // ------------------------------------------------------------------

    private int abilityOneCooldown(ElementType element) {
        return switch (element) {
            case FIRE -> 70; case WATER -> 55; case AIR -> 50; case EARTH -> 65;
            case LIFE -> 80; case DEATH -> 60; case METAL -> 55; case FROST -> 60;
        };
    }

    private int abilityTwoCooldown(ElementType element) {
        return switch (element) {
            case FIRE -> 140; case WATER -> 90; case AIR -> 80; case EARTH -> 100;
            case LIFE -> 160; case DEATH -> 110; case METAL -> 90; case FROST -> 130;
        };
    }

    private double abilityOneRangeSq(ElementType element) {
        return switch (element) {
            case AIR -> 144.0;               // ranged gust
            case METAL -> 100.0;              // chain pull reaches out
            case WATER, FIRE, FROST -> 36.0;  // close-range AoE burst
            case EARTH, DEATH, LIFE -> 16.0;  // melee range
        };
    }

    // Relative to the mob's default MOVEMENT_SPEED attribute (vanilla zombie ~0.23),
    // not an absolute speed value — 1.0 means "normal zombie speed".
    private double speedMultiplier(ElementType element) {
        return switch (element) {
            case AIR, METAL -> 1.15;
            case EARTH -> 0.85;
            default -> 1.0;
        };
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