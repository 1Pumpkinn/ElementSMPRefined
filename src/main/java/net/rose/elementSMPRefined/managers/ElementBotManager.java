package net.rose.elementSMPRefined.managers;

import net.rose.elementSMPRefined.API.element.ElementType;
import net.rose.elementSMPRefined.ElementSMPRefined;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages elemental companion bots: spawning, AI (targeting/movement/ability use),
 * and passive effects. Bots hunt hostile players/mobs near their owner, alternate
 * between both of their element's abilities based on cooldown and range, and carry
 * the same passive perks a player of that element would have.
 *
 * Combat AI summary (see the "AI loop" section for the actual code):
 * - A near-dead target makes the bot lead with whichever ready ability hits harder,
 *   so it closes out kills instead of chipping away with the weaker option.
 * - Abilities are tagged DAMAGE, GAP_CLOSER, or SUPPORT ({@link AbilityKind}); closing
 *   the gap with a mobility ability commits the bot to a short melee brawl instead of
 *   immediately kiting back out.
 * - Ranged elements only kite when it's worth it: out of mana or with nothing coming
 *   off cooldown soon, they brawl in melee instead of dancing at range doing nothing.
 * - Getting hit - or the owner getting hit - makes the bot retaliate immediately, and
 *   getting comboed (several hits in quick succession) makes it break off and create
 *   space rather than eating the whole combo.
 * - Movement (strafing, kiting, retreating) only steers the bot while it's grounded,
 *   so getting knocked airborne doesn't turn into free mid-air repositioning.
 */
public final class ElementBotManager implements Listener {
    public static final String BOT_METADATA = "element_smp_bot";

    private static final int RUN_PERIOD_TICKS = 2; // AI tick rate (10x/sec) - fast enough to catch
    // a human player's brief window inside ability range, not just slow-moving mobs
    private static final int PASSIVE_REFRESH_TICKS = 100; // how often passive effects are re-applied
    private static final int RETARGET_INTERVAL_TICKS = 60; // how often we look for a new target
    private static final int REPATH_INTERVAL_TICKS = 10;   // how often we recompute the path
    private static final double SEARCH_RADIUS = 16.0;
    private static final double TARGET_LOSE_RADIUS_SQ = (SEARCH_RADIUS * 1.5) * (SEARCH_RADIUS * 1.5);
    private static final double GUARD_LEASH_RADIUS_SQ = 64.0;   // start following owner past 8 blocks
    private static final double LIFE_SUPPORT_RADIUS_SQ = 100.0; // heal owner within 10 blocks
    private static final double AOE_RADIUS = 4.0;
    private static final double DEFENSE_CALL_RADIUS_SQ = 40.0 * 40.0; // owner's cry for help travels further than passive search
    private static final double LOW_HEALTH_FRACTION = 0.3; // below this, bot fights defensively / tries to disengage
    private static final double KITE_TOO_CLOSE_FRACTION = 0.45; // fraction of a ranged element's ability1 range considered "too close"
    private static final double EXECUTE_HEALTH_FRACTION = 0.25; // below this, target's death takes priority over ability economy
    private static final double MELEE_ADJACENT_RANGE_SQ = 9.0;  // ~3 blocks - close enough to jump-attack

    // A cooldown further out than this isn't "coming back soon" - the bot just brawls
    // instead of kiting to preserve range for a spell that's still seconds away.
    private static final int BRAWL_COOLDOWN_GRACE_TICKS = 40;
    // How long a gap-closer (Air Dash, Metal Dash, Earth Tunnel, Backstab) commits the
    // bot to melee afterward, instead of immediately kiting back out to range.
    private static final int BRAWL_COMMIT_TICKS = 70;

    // A single hit isn't a combo - only several landing within this window count toward
    // one, and only past COMBO_HIT_THRESHOLD does the bot break off to create space
    // (COMBO_ESCAPE_TICKS) rather than eating the whole combo standing still.
    private static final int COMBO_WINDOW_TICKS = 30;
    private static final int COMBO_HIT_THRESHOLD = 3;
    private static final int COMBO_ESCAPE_TICKS = 60;

    // Real players juke/retreat and apply knockback on every hit, unlike hostile mobs
    // which mostly hold still in melee; without this buffer the bot's target keeps
    // slipping just outside ability range between AI ticks. Added to the linear range
    // before squaring, so it's a flat few blocks of forgiveness at any distance.
    private static final double RANGE_BUFFER_BLOCKS = 1.5;

    // Bots run their own private mana pool, separate from the owner's ManaManager, using
    // the same per-element cost/regen lookups a real player's cast goes through.
    private static final int MANA_REGEN_INTERVAL_TICKS = 20; // regen tick is once/second, like player mana

    // Prints what a bot is doing and why (targeting, casts, retargets, movement mode) to
    // the console. Flip to false to quiet things down once you're done watching a fight.
    private static final boolean DEBUG_LOGGING = false;
    private static final int LOG_INTERVAL_TICKS = 20; // ~once a second

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
        int logTicks;      // throttles the periodic combat-state debug log
        UUID targetId;
        UUID loggedTargetId; // last target we printed an "acquired" log line for
        int mana;           // bot's own private mana pool, separate from its owner's
        int manaRegenTicks; // counts down to the next once-per-second regen tick
        int strafeTicks;    // ticks left before flipping circle-strafe direction
        int strafeDir = 1;  // +1/-1, which way we're currently side-stepping
        int jumpTicks;      // ticks left before the next jump-attack is allowed
        int aggressiveTicks; // >0 while the bot is committed to brawling in melee
        int comboWindowTicks; // ticks left before a new hit no longer counts toward the same combo
        int comboHitsTaken;   // hits landed on us within the current combo window
        int comboEscapeTicks; // >0 while backing off after being comboed
        String loggedMoveMode; // last movement-mode string we printed, so logs only fire on change
    }

    /**
     * What an ability is *for*: DAMAGE hurts the target, GAP_CLOSER primarily moves the
     * bot into melee (damage is a bonus), SUPPORT doesn't damage the target at all.
     */
    private enum AbilityKind { DAMAGE, GAP_CLOSER, SUPPORT }

    /**
     * Tuning table for one element's combat kit, keyed by {@link ElementType} - one place
     * for every cooldown/range/speed/fx number instead of six separate switch methods.
     *
     * ability1/2Damage are representative single-target figures used only to pick a
     * finisher against a low-health target (see {@link #tickBots()}) - they mirror, but
     * don't drive, the actual damage in {@link #castAbilityOne}/{@link #castAbilityTwo},
     * so keep both in sync when retuning.
     */
    private record CombatProfile(
            int ability1Cooldown,
            int ability2Cooldown,
            double ability1Range,
            double ability2Range,
            AbilityKind ability1Kind,
            AbilityKind ability2Kind,
            double speedMultiplier, // relative to the mob's default MOVEMENT_SPEED (vanilla zombie ~0.23)
            double ability1Damage,
            double ability2Damage,
            Particle particle,
            Sound sound
    ) {
        double ability1RangeSq() {
            double r = ability1Range + RANGE_BUFFER_BLOCKS;
            return r * r;
        }

        double ability2RangeSq() {
            double r = ability2Range + RANGE_BUFFER_BLOCKS;
            return r * r;
        }
    }

    private static final Map<ElementType, CombatProfile> PROFILES = buildProfiles();

    private static Map<ElementType, CombatProfile> buildProfiles() {
        Map<ElementType, CombatProfile> map = new EnumMap<>(ElementType.class);
        //                                       a1Cd a2Cd  a1Rng a2Rng  a1Kind              a2Kind                   speed  a1Dmg a2Dmg   particle                  sound
        map.put(ElementType.AIR, new CombatProfile(50, 80, 12.0, 12.0, AbilityKind.DAMAGE, AbilityKind.GAP_CLOSER, 1.15, 4.0, 3.0,
                Particle.CLOUD, Sound.ENTITY_PLAYER_ATTACK_SWEEP));
        map.put(ElementType.WATER, new CombatProfile(55, 90, 6.0, 6.0, AbilityKind.DAMAGE, AbilityKind.DAMAGE, 1.0, 4.0, 4.0,
                Particle.BUBBLE, Sound.ENTITY_PLAYER_SPLASH));
        map.put(ElementType.FIRE, new CombatProfile(70, 140, 6.0, 6.0, AbilityKind.DAMAGE, AbilityKind.DAMAGE, 1.0, 5.0, 7.0,
                Particle.FLAME, Sound.ENTITY_BLAZE_SHOOT));
        map.put(ElementType.EARTH, new CombatProfile(65, 100, 4.0, 10.0, AbilityKind.DAMAGE, AbilityKind.GAP_CLOSER, 0.85, 6.0, 4.0,
                Particle.SMOKE, Sound.BLOCK_STONE_BREAK));
        map.put(ElementType.LIFE, new CombatProfile(80, 160, 4.0, 6.0, AbilityKind.DAMAGE, AbilityKind.SUPPORT, 1.0, 3.0, 0.0,
                Particle.HAPPY_VILLAGER, Sound.BLOCK_AMETHYST_BLOCK_CHIME));
        map.put(ElementType.DEATH, new CombatProfile(60, 110, 4.0, 6.0, AbilityKind.DAMAGE, AbilityKind.GAP_CLOSER, 1.0, 5.0, 10.0,
                Particle.SOUL, Sound.ENTITY_WITHER_AMBIENT));
        map.put(ElementType.METAL, new CombatProfile(55, 90, 10.0, 11.0, AbilityKind.DAMAGE, AbilityKind.GAP_CLOSER, 1.15, 5.0, 6.0,
                Particle.CRIT, Sound.BLOCK_ANVIL_LAND));
        map.put(ElementType.FROST, new CombatProfile(60, 130, 6.0, 4.0, AbilityKind.DAMAGE, AbilityKind.DAMAGE, 1.0, 3.0, 7.0,
                Particle.SNOWFLAKE, Sound.BLOCK_GLASS_BREAK));
        return Map.copyOf(map);
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
        equipProtectedDiamondArmor(bot);
        bot.setTarget(null);

        CombatProfile profile = PROFILES.get(element);

        var speedAttr = bot.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            double naturalBase = speedAttr.getBaseValue(); // zombie's actual spawn speed (~0.23), not the attribute's generic default
            speedAttr.setBaseValue(naturalBase * profile.speedMultiplier());
        }

        // A little baseline knockback resistance stops the bot being juggled clean out of
        // its own ability range on every hit; EARTH's passive raises this further (0.5).
        var knockbackAttr = bot.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockbackAttr != null) {
            knockbackAttr.setBaseValue(Math.max(knockbackAttr.getBaseValue(), 0.1));
        }

        bots.put(owner.getUniqueId(), bot);
        BotState state = newBotState();
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

    private BotState newBotState() {
        BotState state = new BotState();
        state.mana = plugin.getConfigManager().getMaxMana(); // bots start topped up, same as a fresh player
        return state;
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
        // Iterate the live map directly and defer any removals until after the loop,
        // instead of allocating a defensive copy of the whole map every AI tick (10x/sec).
        List<UUID> toRemove = null;
        for (Map.Entry<UUID, Mob> entry : bots.entrySet()) {
            Mob bot = entry.getValue();
            Player owner = plugin.getServer().getPlayer(entry.getKey());
            if (!bot.isValid() || bot.isDead() || owner == null || !owner.isOnline()
                    || !owner.getWorld().equals(bot.getWorld())) {
                states.remove(bot.getUniqueId());
                if (bot.isValid()) bot.remove();
                if (toRemove == null) toRemove = new ArrayList<>();
                toRemove.add(entry.getKey());
                continue;
            }

            ElementType element = elementOf(bot);
            if (element == null) continue;
            CombatProfile profile = PROFILES.get(element);
            BotState state = states.computeIfAbsent(bot.getUniqueId(), id -> newBotState());

            int ability1Cost = plugin.getConfigManager().getAbility1Cost(element);
            int ability2Cost = plugin.getConfigManager().getAbility2Cost(element);

            applyPassiveTick(bot, element, state);
            regenManaTick(state);

            LivingEntity target = resolveTarget(bot, owner, state);

            if (state.ability1Cd > 0) state.ability1Cd -= RUN_PERIOD_TICKS;
            if (state.ability2Cd > 0) state.ability2Cd -= RUN_PERIOD_TICKS;
            if (state.comboWindowTicks > 0) state.comboWindowTicks -= RUN_PERIOD_TICKS;
            if (state.comboEscapeTicks > 0) state.comboEscapeTicks -= RUN_PERIOD_TICKS;

            if (target != null) {
                bot.setTarget(target); // hands movement + melee swings to the zombie's own attack AI
                state.repathTicks = REPATH_INTERVAL_TICKS; // reset so guard-mode repaths immediately if target is lost

                if (DEBUG_LOGGING && !target.getUniqueId().equals(state.loggedTargetId)) {
                    state.loggedTargetId = target.getUniqueId();
                    log(owner, element, "ACQUIRED target " + describeEntity(target)
                            + " dist=" + String.format("%.1f", Math.sqrt(bot.getLocation().distanceSquared(target.getLocation()))) + " blocks");
                }

                double distSq = bot.getLocation().distanceSquared(target.getLocation());
                boolean hasLineOfSight = bot.hasLineOfSight(target);

                boolean ability1Ready = distSq <= profile.ability1RangeSq() && state.ability1Cd <= 0
                        && hasLineOfSight && state.mana >= ability1Cost;
                boolean ability2Ready = distSq <= profile.ability2RangeSq() && state.ability2Cd <= 0
                        && hasLineOfSight && state.mana >= ability2Cost;

                // A near-dead target's death takes priority over normal ability economy:
                // lead with whichever ready ability actually hits harder instead of always
                // defaulting to ability1, so the bot closes out the kill instead of poking
                // with the weaker option while the finish is sitting right there.
                boolean finishWithAbilityTwo = isExecutable(target) && ability2Ready
                        && (!ability1Ready || profile.ability2Damage() > profile.ability1Damage());

                if (finishWithAbilityTwo) {
                    performAbilityTwo(bot, owner, target, element, profile, state, ability2Cost,
                            "EXECUTE finisher, target at " + String.format("%.0f%%", targetHealthPercent(target)) + " HP");
                } else if (ability1Ready) {
                    performAbilityOne(bot, owner, target, element, profile, state, ability1Cost, "primary");
                } else if (ability2Ready) {
                    performAbilityTwo(bot, owner, target, element, profile, state, ability2Cost,
                            "fallback (ability1 not ready/in range)");
                } else if (DEBUG_LOGGING && (state.logTicks -= RUN_PERIOD_TICKS) <= 0) {
                    state.logTicks = LOG_INTERVAL_TICKS;
                    log(owner, element, String.format(
                            "idle vs %s | dist=%.1f (a1 range=%.1f, a2 range=%.1f) | LOS=%s | a1Cd=%d a2Cd=%d | mana=%d/%d",
                            describeEntity(target), Math.sqrt(distSq),
                            profile.ability1Range(), profile.ability2Range(),
                            hasLineOfSight, state.ability1Cd, state.ability2Cd,
                            state.mana, plugin.getConfigManager().getMaxMana()));
                }

                handleTacticalMovement(bot, owner, element, target, profile, distSq, state, ability1Cost, ability2Cost);
            } else {
                if (DEBUG_LOGGING && state.loggedTargetId != null) {
                    state.loggedTargetId = null;
                    state.loggedMoveMode = null;
                    log(owner, element, "LOST target - back to guarding owner");
                }

                double ownerDistSq = bot.getLocation().distanceSquared(owner.getLocation());
                if (ownerDistSq > GUARD_LEASH_RADIUS_SQ && (state.repathTicks -= RUN_PERIOD_TICKS) <= 0) {
                    bot.getPathfinder().moveTo(owner, 1.0);
                    state.repathTicks = REPATH_INTERVAL_TICKS;
                }

                if (element == ElementType.LIFE && ownerDistSq <= LIFE_SUPPORT_RADIUS_SQ
                        && state.ability1Cd <= 0 && state.mana >= ability1Cost && isHurt(owner)) {
                    castLifeSupport(bot, owner);
                    state.ability1Cd = profile.ability1Cooldown();
                    int before = state.mana;
                    state.mana -= ability1Cost;
                    if (DEBUG_LOGGING) log(owner, element, "CAST life-support heal on owner"
                            + " | mana " + before + " -> " + state.mana);
                }

                if (DEBUG_LOGGING && (state.logTicks -= RUN_PERIOD_TICKS) <= 0) {
                    state.logTicks = LOG_INTERVAL_TICKS;
                    log(owner, element, "no target found nearby (search radius=" + SEARCH_RADIUS + " blocks)");
                }
            }
        }
        if (toRemove != null) {
            for (UUID id : toRemove) bots.remove(id);
        }
    }

    private void performAbilityOne(Mob bot, Player owner, LivingEntity target, ElementType element,
                                   CombatProfile profile, BotState state, int cost, String reason) {
        castAbilityOne(bot, owner, target, element, profile);
        state.ability1Cd = profile.ability1Cooldown();
        int before = state.mana;
        state.mana -= cost;
        if (DEBUG_LOGGING) log(owner, element, "CAST ability1 [" + profile.ability1Kind() + "] (" + reason + ") on "
                + describeEntity(target) + " | mana " + before + " -> " + state.mana + " | ability1 cd -> " + state.ability1Cd + "t");
    }

    private void performAbilityTwo(Mob bot, Player owner, LivingEntity target, ElementType element,
                                   CombatProfile profile, BotState state, int cost, String reason) {
        castAbilityTwo(bot, owner, target, element, profile);
        state.ability2Cd = profile.ability2Cooldown();
        int before = state.mana;
        state.mana -= cost;
        if (profile.ability2Kind() == AbilityKind.GAP_CLOSER) {
            // Closed the gap on purpose - commit to the brawl for a few seconds instead of
            // immediately turning back around and kiting out to range again.
            state.aggressiveTicks = BRAWL_COMMIT_TICKS;
        }
        if (DEBUG_LOGGING) log(owner, element, "CAST ability2 [" + profile.ability2Kind() + "] (" + reason + ") on "
                + describeEntity(target) + " | mana " + before + " -> " + state.mana + " | ability2 cd -> " + state.ability2Cd + "t");
    }

    private boolean isExecutable(LivingEntity target) {
        return targetHealthPercent(target) <= EXECUTE_HEALTH_FRACTION * 100.0;
    }

    private double targetHealthPercent(LivingEntity target) {
        var attr = target.getAttribute(Attribute.MAX_HEALTH);
        double max = attr != null ? attr.getValue() : target.getHealth();
        return max > 0 ? (target.getHealth() / max) * 100.0 : 100.0;
    }

    /**
     * Tactical repositioning layered on top of the vanilla melee-attack goal:
     * - Critically low health triggers a panic burst of speed + a moment of Resistance
     *   so the bot can actually disengage instead of trading hits to the death.
     * - Getting comboed (see {@link #onBotAttacked}) makes the bot back off and create
     *   space for a few seconds, same as panic but without the buffs.
     * - Ranged elements only kite when it's worth it - out of resources, they brawl in
     *   melee instead of backpedaling with nothing to show for it.
     * - Landing a gap-closer commits the bot to that brawl for a few seconds rather than
     *   immediately turning around and kiting back out.
     * - Otherwise the bot circle-strafes and throws in the odd jump-attack at melee range
     *   while abilities cool down, so it reads as an active opponent, not a punching bag.
     */
    private void handleTacticalMovement(Mob bot, Player owner, ElementType element, LivingEntity target,
                                        CombatProfile profile, double distSq, BotState state,
                                        int ability1Cost, int ability2Cost) {
        // Only AIR (gust, 12 blocks) and METAL (chain, 10 blocks) meaningfully out-range
        // melee; the 6-block AoE bursts (WATER/FIRE/FROST) are still close-quarters kits
        // and shouldn't be treated as "ranged" or they'd back off from their own AoE.
        boolean isRangedFavored = profile.ability1Range() >= 8.0;

        var maxHealthAttr = bot.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : bot.getHealth();
        boolean panicking = bot.getHealth() <= maxHealth * LOW_HEALTH_FRACTION;

        if (panicking) {
            logMoveMode(owner, element, state, String.format(
                    "PANIC - health %.1f/%.1f (%.0f%%), disengaging from %s",
                    bot.getHealth(), maxHealth, (bot.getHealth() / maxHealth) * 100.0, describeEntity(target)));
            bot.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, RUN_PERIOD_TICKS + 5, 1, true, false));
            bot.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, RUN_PERIOD_TICKS + 5, 0, true, false));
            retreatFrom(bot, target, 0.25);
            return;
        }

        if (state.comboEscapeTicks > 0) {
            logMoveMode(owner, element, state, "ESCAPE - creating space after being comboed by " + describeEntity(target));
            retreatFrom(bot, target, 0.22);
            return;
        }

        if (state.aggressiveTicks > 0) state.aggressiveTicks -= RUN_PERIOD_TICKS;

        boolean canAffordEither = state.mana >= Math.min(ability1Cost, ability2Cost);
        boolean anyAbilitySoon = state.ability1Cd <= BRAWL_COOLDOWN_GRACE_TICKS || state.ability2Cd <= BRAWL_COOLDOWN_GRACE_TICKS;
        boolean brawling = state.aggressiveTicks > 0 || !canAffordEither || !anyAbilitySoon;

        double kiteThreshold = profile.ability1Range() * KITE_TOO_CLOSE_FRACTION;
        if (!brawling && isRangedFavored && distSq < kiteThreshold * kiteThreshold) {
            logMoveMode(owner, element, state, String.format(
                    "KITE - %s closed to %.1f blocks (kite threshold %.1f), backing off",
                    describeEntity(target), Math.sqrt(distSq), kiteThreshold));
            retreatFrom(bot, target, 0.2);
            return;
        }

        logMoveMode(owner, element, state, brawling
                ? "BRAWL - mana/cooldowns not ready for ranged play, fighting " + describeEntity(target) + " in melee"
                : "ENGAGE - strafing/jump-attacking " + describeEntity(target));

        // Right on top of the target, strafing just reads as jitter - let plain melee
        // (plus the jump-attack crits below) take over instead of fighting the vanilla
        // attack goal for control of those last couple of blocks.
        if (distSq > MELEE_ADJACENT_RANGE_SQ) {
            applyCircleStrafe(bot, target, state);
        }
        maybeJumpAttack(bot, distSq, state, brawling);
    }

    // Logs a movement-mode line only when the mode actually changes, so the console shows
    // "the bot started kiting" once instead of every single tick it spends kiting.
    private void logMoveMode(Player owner, ElementType element, BotState state, String mode) {
        if (!DEBUG_LOGGING || mode.equals(state.loggedMoveMode)) return;
        state.loggedMoveMode = mode;
        log(owner, element, "MOVE: " + mode);
    }

    // Side-steps the bot around its target between ability casts instead of standing
    // still, making it harder to hit. Grounded-only (no free mid-air steering), and
    // stronger than a subtle nudge would be, since it has to overcome the vanilla melee
    // goal's pull straight toward the target or it just reads as barely moving.
    private void applyCircleStrafe(Mob bot, LivingEntity target, BotState state) {
        if (!bot.isOnGround()) return;

        if ((state.strafeTicks -= RUN_PERIOD_TICKS) <= 0) {
            state.strafeTicks = 30 + (int) (Math.random() * 40);
            state.strafeDir = -state.strafeDir;
        }

        Vector towardTarget = target.getLocation().toVector().subtract(bot.getLocation().toVector());
        towardTarget.setY(0);
        if (towardTarget.lengthSquared() < 0.0001) return;
        towardTarget.normalize();

        Vector perpendicular = new Vector(-towardTarget.getZ(), 0, towardTarget.getX()).multiply(state.strafeDir);
        bot.setVelocity(bot.getVelocity().add(perpendicular.multiply(0.22)));
    }

    // Occasional grounded hop at melee range, mirroring a player's jump-attack crits.
    // While brawling (out of mana/cooldowns), hops more often for a "crit flurry" - the
    // bot's stand-in for a player who's out of spells and just fighting hard.
    private void maybeJumpAttack(Mob bot, double distSq, BotState state, boolean aggressive) {
        if (!bot.isOnGround() || distSq > MELEE_ADJACENT_RANGE_SQ) return;
        if ((state.jumpTicks -= RUN_PERIOD_TICKS) > 0) return;
        state.jumpTicks = aggressive ? (12 + (int) (Math.random() * 15)) : (25 + (int) (Math.random() * 30));

        Vector velocity = bot.getVelocity();
        bot.setVelocity(new Vector(velocity.getX(), 0.42, velocity.getZ()));
    }

    // Pushes the bot away from its target on the horizontal plane, grounded-only (a mob
    // knocked airborne shouldn't be able to steer itself back down mid-flight). Also
    // fires a single jump impulse instead of a per-tick upward nudge when something is
    // actually blocking the retreat path.
    private void retreatFrom(Mob bot, LivingEntity target, double speed) {
        if (!bot.isOnGround()) return;

        Vector away = bot.getLocation().toVector().subtract(target.getLocation().toVector());
        away.setY(0);
        if (away.lengthSquared() < 0.0001) return;
        away.normalize();

        Vector current = bot.getVelocity();
        double newX = current.getX() + away.getX() * speed;
        double newZ = current.getZ() + away.getZ() * speed;
        double newY = isBlockedAhead(bot, away) ? 0.42 : current.getY(); // vanilla jump velocity if something's in the way

        bot.setVelocity(new Vector(newX, newY, newZ));
    }

    // Solid block at foot/knee height one step ahead - gates the jump impulse in
    // retreatFrom to only fire when the bot would actually back into something.
    private boolean isBlockedAhead(Mob bot, Vector horizontalDirection) {
        Location feet = bot.getLocation();
        Location probe = feet.clone().add(horizontalDirection.getX() * 0.6, 0, horizontalDirection.getZ() * 0.6);
        Material atFeet = probe.getBlock().getType();
        Material atKnee = probe.clone().add(0, 1, 0).getBlock().getType();
        return atFeet.isSolid() || atKnee.isSolid();
    }

    private LivingEntity resolveTarget(Mob bot, Player owner, BotState state) {
        if (state.targetId != null) {
            Entity candidate = plugin.getServer().getEntity(state.targetId);
            if (candidate instanceof LivingEntity le && le.isValid() && !le.isDead()
                    && le.getWorld().equals(bot.getWorld())
                    && le.getLocation().distanceSquared(bot.getLocation()) <= TARGET_LOSE_RADIUS_SQ) {
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

    // Hunts hostile players first (the real threat to the owner), falling back to hostile
    // mobs (zombies, skeletons, etc.) when no enemy player is around, per the bot's guard
    // duty. Players are always preferred over mobs even if a mob is slightly closer.
    private LivingEntity findHostileTarget(Mob bot, Player owner) {
        ElementType element = elementOf(bot);
        LivingEntity bestPlayer = null;
        double bestPlayerDistSq = SEARCH_RADIUS * SEARCH_RADIUS;
        LivingEntity bestMob = null;
        double bestMobDistSq = SEARCH_RADIUS * SEARCH_RADIUS;

        for (LivingEntity nearby : bot.getLocation().getNearbyLivingEntities(SEARCH_RADIUS)) {
            if (nearby.equals(bot) || owns(nearby)) continue;
            double distSq = nearby.getLocation().distanceSquared(bot.getLocation());

            if (nearby instanceof Player p) {
                if (!p.isOnline() || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
                    if (DEBUG_LOGGING) log(owner, element, "scan: skipping " + p.getName()
                            + " - gamemode=" + p.getGameMode());
                    continue;
                }
                if (distSq < bestPlayerDistSq) {
                    bestPlayerDistSq = distSq;
                    bestPlayer = nearby;
                }
            } else if (nearby instanceof Monster) {
                if (distSq < bestMobDistSq) {
                    bestMobDistSq = distSq;
                    bestMob = nearby;
                }
            }
        }

        LivingEntity chosen = bestPlayer != null ? bestPlayer : bestMob;
        if (DEBUG_LOGGING && chosen == null) {
            log(owner, element, "scan: no valid hostile target within " + SEARCH_RADIUS + " blocks");
        }
        return chosen;
    }

    private void log(Player owner, ElementType element, String message) {
        plugin.getLogger().info("[ElementBot] " + owner.getName() + "'s " + element + " bot: " + message);
    }

    // Same as log(), but for call sites that only have the bot entity, not a resolved
    // owner Player (falls back to "owner" if the owner is offline).
    private void logBot(Mob bot, String message) {
        if (!DEBUG_LOGGING) return;
        ElementType element = elementOf(bot);
        String ownerName = "owner";
        if (bot.hasMetadata(BOT_METADATA) && !bot.getMetadata(BOT_METADATA).isEmpty()) {
            try {
                UUID ownerId = UUID.fromString(bot.getMetadata(BOT_METADATA).get(0).asString());
                Player owner = plugin.getServer().getPlayer(ownerId);
                if (owner != null) ownerName = owner.getName();
            } catch (IllegalArgumentException ignored) {
                // malformed/missing metadata - keep the "owner" fallback
            }
        }
        plugin.getLogger().info("[ElementBot] " + ownerName + "'s " + element + " bot: " + message);
    }

    private String describeEntity(LivingEntity entity) {
        if (entity instanceof Player p) return "player " + p.getName();
        return entity.getType().name().toLowerCase() + " (" + entity.getUniqueId().toString().substring(0, 8) + ")";
    }

    // No ownership/trust protection: this bot is for testing and should be willing to
    // hit any living entity in range, including its own owner. The only exclusions are
    // itself and other element bots, so bots don't end up fighting each other.
    private boolean isValidVictim(LivingEntity le, Mob bot, Player owner) {
        return !le.equals(bot) && !owns(le);
    }

    private boolean isHurt(Player owner) {
        var attr = owner.getAttribute(Attribute.MAX_HEALTH);
        double max = attr != null ? attr.getValue() : owner.getHealth();
        return owner.getHealth() < max;
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

    // Bots regen mana once per second, mirroring ManaManager's real-player regen rate/cap,
    // but entirely in-memory - there's no PlayerData/disk persistence for a bot's pool.
    private void regenManaTick(BotState state) {
        int maxMana = plugin.getConfigManager().getMaxMana();
        if (state.mana >= maxMana) {
            state.mana = maxMana;
            return;
        }
        if ((state.manaRegenTicks -= RUN_PERIOD_TICKS) > 0) return;
        state.manaRegenTicks = MANA_REGEN_INTERVAL_TICKS;
        state.mana = Math.min(maxMana, state.mana + plugin.getConfigManager().getManaRegenPerSecond());
    }

    private boolean isSnowOrIce(Material material) {
        return switch (material) {
            case SNOW, SNOW_BLOCK, ICE, PACKED_ICE, BLUE_ICE, FROSTED_ICE -> true;
            default -> false;
        };
    }

    // If the owner gets hit by another player, the bot locks onto the attacker right away
    // instead of waiting for its next passive scan - reacts like a teammate, not a turret.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOwnerAttacked(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player owner)) return;
        Mob bot = bots.get(owner.getUniqueId());
        if (bot == null || !bot.isValid() || bot.isDead()) return;

        Entity rawDamager = event.getDamager();
        Entity source = (rawDamager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter)
                ? shooter : rawDamager;
        if (!(source instanceof Player attacker) || attacker.equals(owner)) return;
        if (!attacker.getWorld().equals(bot.getWorld())) return;
        if (attacker.getLocation().distanceSquared(bot.getLocation()) > DEFENSE_CALL_RADIUS_SQ) return;

        BotState state = states.computeIfAbsent(bot.getUniqueId(), id -> newBotState());
        state.targetId = attacker.getUniqueId();
        state.retargetTicks = RETARGET_INTERVAL_TICKS;
        state.loggedMoveMode = null; // force the next movement-mode line to print, since the situation changed
        if (DEBUG_LOGGING) {
            log(owner, elementOf(bot), "REACT: owner attacked by " + describeEntity(attacker)
                    + " (" + String.format("%.1f", Math.sqrt(attacker.getLocation().distanceSquared(bot.getLocation())))
                    + " blocks away) - defending");
        }
    }

    // Self-defense: a bot that gets hit retaliates against its attacker immediately rather
    // than waiting for the next passive scan. Also tracks how many hits have landed in
    // quick succession - past COMBO_HIT_THRESHOLD within COMBO_WINDOW_TICKS, that's a
    // combo, and the bot breaks off to create space (handleTacticalMovement) instead of
    // eating the rest of it standing still. Ignores the bot's own owner and other bots.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBotAttacked(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Mob bot) || !owns(bot)) return;

        Entity rawDamager = event.getDamager();
        Entity source = (rawDamager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter)
                ? shooter : rawDamager;
        if (!(source instanceof LivingEntity attacker) || attacker.equals(bot) || owns(attacker)) return;

        String ownerIdStr = bot.hasMetadata(BOT_METADATA) && !bot.getMetadata(BOT_METADATA).isEmpty()
                ? bot.getMetadata(BOT_METADATA).get(0).asString() : null;
        if (ownerIdStr != null && attacker instanceof Player attackerPlayer
                && attackerPlayer.getUniqueId().toString().equals(ownerIdStr)) {
            return; // don't retaliate against our own owner
        }

        BotState state = states.computeIfAbsent(bot.getUniqueId(), id -> newBotState());
        state.targetId = attacker.getUniqueId();
        state.retargetTicks = RETARGET_INTERVAL_TICKS;
        state.loggedMoveMode = null; // force the next movement-mode line to print, since the situation changed

        state.comboHitsTaken = state.comboWindowTicks > 0 ? state.comboHitsTaken + 1 : 1;
        state.comboWindowTicks = COMBO_WINDOW_TICKS;
        if (state.comboHitsTaken >= COMBO_HIT_THRESHOLD) {
            state.comboHitsTaken = 0;
            state.aggressiveTicks = 0; // an in-progress brawl commitment doesn't override fleeing a combo
            state.comboEscapeTicks = COMBO_ESCAPE_TICKS;
            logBot(bot, "COMBO: took " + COMBO_HIT_THRESHOLD + "+ hits from " + describeEntity(attacker) + " in quick succession - breaking off");
        } else {
            logBot(bot, "REACT: hit by " + describeEntity(attacker) + " - retaliating");
        }
    }

    // On-hit passives that need a real combat event: set-target-on-fire and Wither-on-hit for
    // the bot's own attacks, and arrow immunity for Metal bots. (Flavor only - independent of
    // the player Fire passive, which is now Auto Smelt rather than an on-hit effect.)
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
    // Abilities - two per element, chosen by range/cooldown/execute-priority in tickBots()
    // ------------------------------------------------------------------

    private void castAbilityOne(Mob bot, Player owner, LivingEntity target, ElementType element, CombatProfile profile) {
        Location origin = bot.getLocation().add(0, 1, 0);
        // force=true so this renders regardless of a nearby player's particle setting
        // (Minimal/Decreased) or view-distance culling - matches every player-cast ability.
        bot.getWorld().spawnParticle(profile.particle(), origin, 20, .5, .6, .5, .05, null, true);
        bot.getWorld().playSound(bot.getLocation(), profile.sound(), .8f, 1.0f);

        switch (element) {
            case AIR -> { // Slicing Wind: ranged cutting gust
                Vector dir = target.getLocation().toVector().subtract(bot.getLocation().toVector()).normalize();
                spawnTravelLine(bot, target, Particle.CLOUD);
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
                spawnTravelLine(bot, target, Particle.CRIT);
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

    private void castAbilityTwo(Mob bot, Player owner, LivingEntity target, ElementType element, CombatProfile profile) {
        Location origin = bot.getLocation().add(0, 1, 0);
        bot.getWorld().spawnParticle(profile.particle(), origin, 26, .6, .7, .6, .06, null, true);
        bot.getWorld().playSound(bot.getLocation(), profile.sound(), .9f, 1.2f);

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
            case DEATH -> { // Backstab: blink behind the target and stab for true damage + weaken
                bot.teleport(target.getLocation().clone().subtract(target.getLocation().getDirection().multiply(1.2)));
                target.damage(8.0, bot);
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 200, 0, false, true));
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
        bot.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, owner.getLocation().add(0, 1, 0), 20, .5, .6, .5, .05, null, true);
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
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        for (ItemStack piece : new ItemStack[]{helmet, chestplate, leggings, boots}) {
            piece.addUnsafeEnchantment(Enchantment.PROTECTION, 3);
        }
        for (ItemStack piece : new ItemStack[]{sword}) {
            piece.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
        }
        var equipment = bot.getEquipment();
        equipment.setHelmet(helmet);
        equipment.setChestplate(chestplate);
        equipment.setLeggings(leggings);
        equipment.setBoots(boots);
        equipment.setItemInMainHand(sword);
        // Don't let it drop the gear if it dies.
        equipment.setHelmetDropChance(0f);
        equipment.setChestplateDropChance(0f);
        equipment.setLeggingsDropChance(0f);
        equipment.setBootsDropChance(0f);
        equipment.setItemInMainHandDropChance(0f);

        // Flat damage bump: mobs don't get the attack-cooldown mechanic a player wielding
        // this sword would, so basic attacks stay relevant even with abilities on cooldown.
        var damageAttr = bot.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damageAttr != null) {
            damageAttr.setBaseValue(damageAttr.getBaseValue() + 2.0);
        }
    }

    private void slow(LivingEntity target, int duration, int amplifier) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, amplifier, false, true));
    }

    // Short particle trail from bot to target so ranged abilities (Slicing Wind, Metal
    // Chain) read as travelling to the target instead of a puff on the bot's own head.
    private void spawnTravelLine(Mob bot, LivingEntity target, Particle trailParticle) {
        Location from = bot.getLocation().add(0, 1.2, 0);
        Location to = target.getLocation().add(0, 1.0, 0);
        Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        if (distance < 0.1) return;

        Vector step = direction.normalize().multiply(0.5); // one sample point every half block
        int steps = (int) Math.ceil(distance / 0.5);
        Location point = from.clone();
        World world = bot.getWorld();
        for (int i = 0; i <= steps; i++) {
            world.spawnParticle(Particle.SWEEP_ATTACK, point, 0, 0, 0, 0, 0, null, true);
            world.spawnParticle(trailParticle, point, 2, 0.08, 0.08, 0.08, 0.0, null, true);
            point.add(step);
        }
    }
}