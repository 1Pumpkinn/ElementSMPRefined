package hs.elementSMPRefined.ability.main.water;

import hs.elementSMPRefined.API.ability.BaseAbility;
import hs.elementSMPRefined.API.element.ElementContext;
import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.util.visual.SoundUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Water element's control ability. Only usable while the caster is in water: grabs
 * the entity they're looking at, drags them down/under, deals a burst of drowning
 * damage, and stuns them for 2.5 seconds.
 */
public class WaterPullDownAbility extends BaseAbility {

    private static final double RANGE = 6.0;
    private static final int DRAG_TICKS = 15; // 0.75s drag before the dunk lands
    private static final double MAX_PULL_DEPTH = 4.0;
    private static final double DROWN_DAMAGE = 6.0; // 3 hearts
    private static final int STUN_TICKS = 50; // 2.5 seconds

    private final ElementSMPRefined plugin;

    public WaterPullDownAbility(JavaPlugin plugin) {
        super("water_pull_down", 75, 12, 2);
        this.plugin = (ElementSMPRefined) plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();

        if (!player.isInWater()) {
            player.sendMessage(ChatColor.RED + "You need to be in water to pull someone under!");
            return false;
        }

        LivingEntity target = findTarget(player);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "No target found!");
            return false;
        }

        if (!isValidTarget(context, target)) {
            player.sendMessage(ChatColor.RED + "You cannot pull down trusted players!");
            return false;
        }

        SoundUtils.playTo(player, SoundUtils.Element.WATER);
        player.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_SWIM, 1.0f, 0.7f);

        dragUnder(player, target, context);
        return true;
    }

    private LivingEntity findTarget(Player player) {
        Location eyeLoc = player.getEyeLocation();
        Vector dir = eyeLoc.getDirection();

        RayTraceResult result = player.getWorld().rayTraceEntities(eyeLoc, dir, RANGE,
                entity -> entity instanceof LivingEntity
                        && !entity.equals(player)
                        && !(entity instanceof ArmorStand));

        return result != null && result.getHitEntity() instanceof LivingEntity le ? le : null;
    }

    private void dragUnder(Player player, LivingEntity target, ElementContext context) {
        new BukkitRunnable() {
            int ticks = 0;
            double lastOffset = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !target.isValid() || target.isDead()) {
                    cancel();
                    return;
                }

                double targetOffset = Math.min((ticks + 1) * (MAX_PULL_DEPTH / DRAG_TICKS), MAX_PULL_DEPTH);
                double delta = targetOffset - lastOffset;
                lastOffset = targetOffset;

                Vector pull = new Vector(0, -delta, 0);

                Vector horizontal = player.getLocation().toVector().subtract(target.getLocation().toVector());
                horizontal.setY(0);
                if (horizontal.lengthSquared() > 0.04) {
                    horizontal = horizontal.normalize().multiply(0.06);
                    pull.add(horizontal);
                }
                target.setVelocity(pull);

                Location loc = target.getLocation();
                target.getWorld().spawnParticle(Particle.BUBBLE, loc, 6, 0.3, 0.3, 0.3, 0.02, null, true);
                target.getWorld().spawnParticle(Particle.UNDERWATER, loc, 4, 0.3, 0.2, 0.3, 0.01, null, true);
                if (ticks % 4 == 0) {
                    target.getWorld().playSound(loc, Sound.BLOCK_WATER_AMBIENT, 0.8f, 0.6f);
                }

                ticks++;
                if (ticks >= DRAG_TICKS) {
                    finishDrown(player, target);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void finishDrown(Player player, LivingEntity target) {
        if (!target.isValid() || target.isDead()) return;

        target.setVelocity(new Vector(0, 0, 0));
        target.setRemainingAir(0);

        Location loc = target.getLocation();
        target.getWorld().playSound(loc, Sound.ENTITY_PLAYER_HURT_DROWN, 1.0f, 0.8f);
        target.getWorld().spawnParticle(Particle.BUBBLE_POP, loc.add(0, 1, 0), 25, 0.4, 0.5, 0.4, 0.1, null, true);

        if (!target.isDead()) {
            target.damage(DROWN_DAMAGE, player);
        }
        if (target.isDead()) return;

        if (target instanceof Player targetPlayer) {
            plugin.getStatusEffectManager().applyStun(targetPlayer, STUN_TICKS);
        } else if (target instanceof Mob mob) {
            boolean wasAware = mob.isAware();
            mob.setAware(false);
            mob.setAI(false);
            mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, STUN_TICKS, 10, false, false));
            mob.setVelocity(new Vector(0, 0, 0));

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (mob.isValid()) {
                        mob.setAware(wasAware);
                        mob.setAI(true);
                        mob.removePotionEffect(PotionEffectType.SLOWNESS);
                    }
                }
            }.runTaskLater(plugin, STUN_TICKS);
        }
    }

    @Override
    protected boolean isValidTarget(ElementContext context, LivingEntity entity) {
        if (entity instanceof Player targetPlayer) {
            return !context.getTrustManager().isTrusted(context.getPlayer().getUniqueId(), targetPlayer.getUniqueId());
        }
        return true;
    }

    @Override
    public String getName() {
        return ChatColor.AQUA + "Pull Down";
    }

    @Override
    public String getDescription() {
        return ChatColor.GRAY + "While in water, drag a target under, dealing drowning damage and stunning them for 2.5s. (75 mana)";
    }
}