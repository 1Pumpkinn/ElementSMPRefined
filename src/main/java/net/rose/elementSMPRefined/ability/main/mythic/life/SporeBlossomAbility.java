package net.rose.elementSMPRefined.ability.main.mythic.life;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.TeamManager;
import net.rose.elementSMPRefined.particles.element.LifeParticles;
import net.rose.elementSMPRefined.util.damage.AbilityTargets;
import net.rose.elementSMPRefined.util.damage.TrueDamage;
import net.rose.elementSMPRefined.util.scan.GroundScan;
import net.rose.elementSMPRefined.util.visual.PingCompensation;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Life ability 2: a spore blossom blooms where you aim, bursts in poison and splits.
 * <p>
 * Generation 0 is one big blossom. When it bursts it throws out 2 smaller blossoms, each of those bursts into
 * 4 smaller ones again, and the last generation just bursts. Every generation is smaller, does less damage
 * and poisons for less time (see the per-generation tables below - they are the knobs to tune).
 * Damage is small and true damage; the poison is the main part of it.
 * <p>
 * The blossoms are plain {@link BlockDisplay}s wearing {@link Material#SPORE_BLOSSOM}, so no resource pack
 * is needed.
 */
public class SporeBlossomAbility extends BaseAbility {

    // ---- Per generation: index 0 = the big blossom, 1 = its 2 halves, 2 = the final 8 ----
    private static final float[] SIZE = {2.2f, 1.3f, 0.75f};          // display scale in blocks
    private static final double[] RADIUS = {3.5, 2.4, 1.6};           // burst radius
    private static final double[] DAMAGE = {2.0, 1.0, 0.5};           // true damage per burst (1.0 = half a heart)
    private static final int[] POISON_TICKS = {120, 80, 40};          // poison length per burst
    private static final int[] SPLIT_INTO = {2, 4, 0};                // children a burst throws out
    private static final int[] MAX_FLIGHT_TICKS = {0, 12, 10};       // children burst on landing or after this long
    private static final double[] FLIGHT_SPEED = {0.0, 0.45, 0.40};   // horizontal blocks per tick

    private static final int POISON_AMPLIFIER = 0;

    private static final double RANGE = 14.0;                         // how far away the first blossom can bloom
    private static final int FIRST_FUSE_TICKS = 14;                   // the big blossom grows, then bursts
    private static final int MIN_FUSE_TICKS = 4;                      // a child that lands at once still waits this long
    private static final double POP_SPEED = 0.2;                      // upward pop when a child is thrown out
    private static final double GRAVITY = 0.035;                      // blocks per tick^2
    private static final float SPIN_PER_TICK = 0.3f;
    private static final float THROB = 0.07f;                         // +-7% size throb

    private static final Random RANDOM = new Random();
    private static final Set<Cast> ACTIVE = new HashSet<>();

    private static final Component ALREADY_ACTIVE =
            Component.text("Spore Blossom is already blooming!", NamedTextColor.RED);

    private final ElementSMPRefined plugin;

    public SporeBlossomAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("spore_blossom", ElementType.LIFE, 2, Constants.Cooldown.ABILITY_2_SECONDS, 2, configManager);
        this.plugin = (ElementSMPRefined) plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();

        if (isActiveFor(player)) {
            player.sendMessage(ALREADY_ACTIVE);
            return false;
        }

        Location bloom = findBloomPoint(player);
        if (!GroundScan.isLoaded(bloom)) return false;

        setActive(player, true);
        World w = player.getWorld();
        w.playSound(player.getLocation(), Sound.BLOCK_AZALEA_LEAVES_BREAK, 1.0f, 0.8f);
        w.playSound(bloom, Sound.BLOCK_SPORE_BLOSSOM_PLACE, 1.4f, 0.7f);

        Cast cast = new Cast(player, context.getTeamManager());
        cast.add(new Blossom(cast, 0, bloom, new Vector()));
        ACTIVE.add(cast);
        cast.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    /** Where you aim: the first block or living thing within {@link #RANGE}, backed off so it floats clear of it. */
    private Location findBloomPoint(Player player) {
        World w = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();

        RayTraceResult hit = w.rayTrace(eye, dir, RANGE, FluidCollisionMode.NEVER, true, 0.3,
                e -> e instanceof LivingEntity le && !le.equals(player));
        if (hit == null) {
            return eye.clone().add(dir.clone().multiply(RANGE));
        }

        Vector at = hit.getHitPosition();
        if (hit.getHitBlockFace() != null) {
            // The blossom is centred on this point and grows to SIZE[0] blocks, so back off by its full
            // half-size (a flat 0.6 left it sunk into the ground when you aimed down).
            at = at.clone().add(hit.getHitBlockFace().getDirection().multiply(clearance(0)));
        }
        return liftOffGround(at.toLocation(w), 0);
    }

    /** Distance from a surface a blossom's centre must keep so its largest (throbbing) size doesn't clip into it. */
    private static double clearance(int generation) {
        return SIZE[generation] * (0.5 + THROB) + 0.05;
    }

    /** If there's a block under {@code at} closer than the blossom's half-size, raise it to sit on top. */
    private static Location liftOffGround(Location at, int generation) {
        double need = clearance(generation);
        RayTraceResult down = at.getWorld().rayTraceBlocks(at, new Vector(0, -1, 0), need,
                FluidCollisionMode.NEVER, true);
        if (down != null) {
            at.setY(down.getHitPosition().getY() + need);
        }
        return at;
    }

    /** One cast: every blossom in flight, advanced together by a single task. */
    private final class Cast extends BukkitRunnable {
        final Player caster;
        final TeamManager team;
        final List<Blossom> blossoms = new ArrayList<>();
        final List<Blossom> born = new ArrayList<>();

        Cast(Player caster, TeamManager team) {
            this.caster = caster;
            this.team = team;
        }

        void add(Blossom blossom) {
            born.add(blossom);
        }

        @Override
        public void run() {
            // !isActiveFor: BaseElement.clearEffects (element change) deactivates the ability.
            if (!caster.isOnline() || caster.isDead() || !isActiveFor(caster)) {
                cancel();
                return;
            }

            blossoms.addAll(born);
            born.clear();

            for (Iterator<Blossom> it = blossoms.iterator(); it.hasNext(); ) {
                Blossom b = it.next();
                if (b.tick()) {
                    it.remove();
                }
            }

            if (blossoms.isEmpty() && born.isEmpty()) {
                cancel();
            }
        }

        @Override
        public synchronized void cancel() throws IllegalStateException {
            for (Blossom b : blossoms) b.discard();
            for (Blossom b : born) b.discard();
            blossoms.clear();
            born.clear();
            ACTIVE.remove(this);
            setActive(caster, false);
            super.cancel();
        }
    }

    private final class Blossom {
        final Cast cast;
        final int generation;
        final Vector velocity;
        final float spinOffset = RANDOM.nextFloat() * 6.28f;
        final int smoothing;

        Location pos;
        BlockDisplay display;
        int age = 0;
        boolean landed = false;

        Blossom(Cast cast, int generation, Location pos, Vector velocity) {
            this.cast = cast;
            this.generation = generation;
            this.pos = pos.clone();
            // The bloom point can be a clone of the caster's eye location; it must never carry their yaw/pitch,
            // or the display entity itself tilts to wherever they were looking.
            this.pos.setYaw(0f);
            this.pos.setPitch(0f);
            this.velocity = velocity.clone();
            this.smoothing = PingCompensation.smoothingTicks(cast.caster);
        }

        /** @return true once it has burst and can be dropped */
        boolean tick() {
            if (generation == 0) {
                if (age >= FIRST_FUSE_TICKS) {
                    burst();
                    return true;
                }
            } else {
                fly();
                boolean timeUp = age >= MAX_FLIGHT_TICKS[generation];
                if ((landed || timeUp) && age >= MIN_FUSE_TICKS) {
                    burst();
                    return true;
                }
            }

            draw();
            age++;
            return false;
        }

        private void fly() {
            if (landed) return;
            velocity.setY(velocity.getY() - GRAVITY);

            double speed = velocity.length();
            if (speed < 1.0E-6) return;
            Vector dir = velocity.clone().multiply(1.0 / speed);

            RayTraceResult hit = pos.getWorld().rayTraceBlocks(pos, dir, speed + 0.2,
                    FluidCollisionMode.NEVER, true);
            if (hit != null) {
                Vector at = hit.getHitPosition();
                if (hit.getHitBlockFace() != null) {
                    at = at.clone().add(hit.getHitBlockFace().getDirection().multiply(clearance(generation)));
                }
                pos = at.toLocation(pos.getWorld());
                velocity.zero();
                landed = true;
            } else {
                pos.add(velocity);
            }
        }

        private void burst() {
            World w = pos.getWorld();
            Location at = pos.clone();
            discard();

            LifeParticles.sporeBurst(w, at, generation);
            playBurstSound(w, at);

            hitNearby(at);
            split(at);
        }

        /**
         * Blossom-spreading burst: a soft puff and berry-pop, rustling petals, a glassy shimmer, and a quick
         * rising chime run (pentatonic, so it always sounds pleasant) that "blooms" outward over a few ticks.
         * Smaller generations are quieter, higher and play a shorter run.
         */
        private void playBurstSound(World w, Location at) {
            float lift = 0.25f * generation;
            float vol = 1.0f - 0.2f * generation;

            // Initial bloom: puff of spores + a juicy pop.
            w.playSound(at, Sound.ENTITY_PUFFER_FISH_BLOW_OUT, 0.5f * vol, 1.7f + lift * 0.3f);
            w.playSound(at, Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1.2f * vol, 0.9f + lift);
            w.playSound(at, Sound.BLOCK_SPORE_BLOSSOM_BREAK, 1.3f * vol, 0.9f + lift);
            w.playSound(at, Sound.BLOCK_CHERRY_LEAVES_BREAK, 0.9f * vol, 1.2f + lift);
            w.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f * vol, 1.2f + lift);

            // Petals unfurling: a rising chime run, one note every other tick.
            final float[] notes = {1.0f, 1.26f, 1.5f, 1.68f, 2.0f};
            int count = Math.max(2, notes.length - generation * 2);
            for (int i = 0; i < count; i++) {
                final float pitch = Math.min(2.0f, notes[i] * (1f + 0.12f * generation));
                final float noteVol = (0.55f - 0.1f * generation) * (0.8f + 0.05f * i);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    w.playSound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, noteVol, pitch);
                    w.playSound(at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, noteVol * 0.6f, pitch);
                }, 2L * (i + 1));
            }
        }

        private void hitNearby(Location at) {
            Player caster = cast.caster;
            for (LivingEntity e : at.getWorld().getNearbyLivingEntities(at, RADIUS[generation])) {
                if (!AbilityTargets.isValid(caster, e, cast.team)) continue;

                boolean dealt = TrueDamage.of(DAMAGE[generation]).attacker(caster).ability(getName())
                        .ignoreIFrames(true).apply(e);
                if (dealt) {
                    poison(e, POISON_TICKS[generation]);
                }
            }
        }

        /** Never shortens poison the target already has, so a big dose isn't cut down by a small one. */
        private void poison(LivingEntity target, int ticks) {
            PotionEffect current = target.getPotionEffect(PotionEffectType.POISON);
            if (current != null && current.getAmplifier() >= POISON_AMPLIFIER && current.getDuration() >= ticks) {
                return;
            }
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, ticks, POISON_AMPLIFIER, false, true, true));
        }

        private void split(Location at) {
            int count = SPLIT_INTO[generation];
            if (count <= 0) return;

            int child = generation + 1;
            double base = RANDOM.nextDouble() * Math.PI * 2.0;
            for (int i = 0; i < count; i++) {
                // Evenly spread around the burst, each nudged a little so the ring isn't perfect.
                double angle = base + i * (Math.PI * 2.0 / count) + (RANDOM.nextDouble() - 0.5) * 0.5;
                double speed = FLIGHT_SPEED[child] * (0.8 + RANDOM.nextDouble() * 0.4);
                Vector v = new Vector(Math.cos(angle) * speed, POP_SPEED, Math.sin(angle) * speed);
                cast.add(new Blossom(cast, child, at.clone().add(0, 0.3, 0), v));
            }
        }

        private void draw() {
            float size = SIZE[generation];
            if (generation == 0) {
                // Grows from a bud, and throbs faster as the burst gets close.
                double progress = Math.min(1.0, (double) (age + 1) / FIRST_FUSE_TICKS);
                float eased = 1f - (float) Math.pow(1.0 - progress, 3);
                float rate = 0.9f + 1.4f * (float) progress;
                size *= (0.35f + 0.65f * eased) * (1f + THROB * (float) Math.sin(age * rate * 1.3));
            } else {
                size *= 1f + THROB * (float) Math.sin(age * 1.6 + spinOffset);
            }

            // The spore blossom block model hangs from a ceiling (flower pointing down), so flip it 180 degrees
            // around X first to make the flower face up, then spin around the world's vertical axis.
            // Entity yaw/pitch is always 0 and billboarding is off, so this is the only rotation applied
            // and the blossom looks the same no matter where the caster is looking.
            Quaternionf rotation = new Quaternionf()
                    .rotateY(spinOffset + age * SPIN_PER_TICK)
                    .rotateX((float) Math.PI);
            // Scale and spin both happen around the block's corner, so shift it back to keep the centre on pos.
            Vector3f shift = new Vector3f(size / 2f).rotate(rotation).negate();
            Transformation transformation = new Transformation(
                    shift, rotation, new Vector3f(size), new Quaternionf());

            World w = pos.getWorld();
            if (!GroundScan.isLoaded(pos)) return;

            if (display == null || !display.isValid()) {
                final Transformation initial = transformation;
                display = w.spawn(pos, BlockDisplay.class, d -> {
                    d.setBlock(Material.SPORE_BLOSSOM.createBlockData());
                    d.setBillboard(Display.Billboard.FIXED);
                    d.setRotation(0f, 0f);
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.setTransformation(initial);
                    d.setTeleportDuration(smoothing);
                    d.setInterpolationDuration(smoothing);
                    d.setPersistent(false);
                    d.setInvulnerable(true);
                });
                return;
            }

            display.teleport(pos);
            display.setRotation(0f, 0f);
            display.setTransformation(transformation);
            display.setInterpolationDelay(0);
        }

        void discard() {
            if (display != null && display.isValid()) {
                display.remove();
            }
            display = null;
        }
    }

    /** Removes every blossom still in the world; called when the plugin disables. */
    public static void removeAll() {
        for (Cast cast : new ArrayList<>(ACTIVE)) {
            try {
                cast.cancel();
            } catch (IllegalStateException ignored) {
                // task was never scheduled or is already cancelled; displays were cleared above
            }
        }
        ACTIVE.clear();
    }

    @Override
    public String getName() {
        return ChatColor.GREEN + "Spore Blossom";
    }

    @Override
    public String getDescription() {
        return "Bloom a spore blossom where you aim. It bursts in poison, splits into 2 smaller blossoms that "
                + "each burst and split into 4 more.";
    }
}
