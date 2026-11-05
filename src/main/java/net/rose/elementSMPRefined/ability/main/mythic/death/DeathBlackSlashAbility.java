package net.rose.elementSMPRefined.ability.main.mythic.death;

import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.TeamManager;
import net.rose.elementSMPRefined.util.damage.AbilityDamage;
import net.rose.elementSMPRefined.util.visual.model.BlackSlashVisual;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class DeathBlackSlashAbility extends BaseAbility {

    private static final double RANGE = 5.0;
    private static final double CONE_DOT_THRESHOLD = 0.45;
    private static final double SLASH_DISTANCE = 2.6;
    private static final double SLASH_DROP = 0.6;
    private static final float SLASH_SCALE = 1.7f;
    private static final double KNOCKBACK = 0.45;
    private static final double FINISHER_KNOCKBACK = 1.1;
    private static final double FINISHER_LIFT = 0.25;
    private static final long TICKS_BETWEEN_SLASHES = 4L;
    private static final Particle.DustOptions BLACK_DUST =
            new Particle.DustOptions(Color.fromRGB(5, 5, 5), 1.5F);

    private static final Slash[] COMBO = {
            new Slash(3.0, -40f, 0.9f),
            new Slash(3.0, 40f, 1.0f),
            new Slash(4.0, 0f, 0.7f)
    };

    private record Slash(double damage, float roll, float pitch) {}

    private final JavaPlugin plugin;

    public DeathBlackSlashAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("death_black_slash", ElementType.DEATH, 1, Constants.Cooldown.ABILITY_1_SECONDS, 1, configManager);
        this.plugin = plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();
        TeamManager teams = context.getTeamManager();

        new BukkitRunnable() {
            private int index = 0;

            @Override
            public void run() {
                if (index >= COMBO.length || !player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }
                strike(player, teams, COMBO[index], index == COMBO.length - 1);
                index++;
            }
        }.runTaskTimer(plugin, 0L, TICKS_BETWEEN_SLASHES);

        return true;
    }

    private void strike(Player player, TeamManager teams, Slash slash, boolean finisher) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection().normalize();

        Location center = eye.clone().add(look.clone().multiply(SLASH_DISTANCE)).subtract(0, SLASH_DROP, 0);

        BlackSlashVisual.play(plugin, center, look, slash.roll(), SLASH_SCALE);
//        world.spawnParticle(Particle.DUST, center, 25, 0.9, 0.9, 0.9, 0, BLACK_DUST, true);
//        world.spawnParticle(Particle.SQUID_INK, center, 4, 0.6, 0.6, 0.6, 0.02, null, true);
        world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, slash.pitch());
        world.playSound(center, Sound.ENTITY_WITHER_SHOOT, 0.4f, 1.8f);

        for (LivingEntity target : eye.getNearbyLivingEntities(RANGE)) {
            if (target.equals(player)) continue;
            if (target instanceof Player other && teams.isFriendly(player.getUniqueId(), other.getUniqueId())) {
                continue;
            }

            Vector toTarget = target.getEyeLocation().toVector().subtract(eye.toVector());
            double distance = toTarget.length();
            if (distance == 0 || distance > RANGE) continue;
            if (look.dot(toTarget.normalize()) < CONE_DOT_THRESHOLD) continue;

            hit(player, target, look, slash, finisher);
        }
    }

    private void hit(Player player, LivingEntity target, Vector look, Slash slash, boolean finisher) {
        target.setNoDamageTicks(0);
        AbilityDamage.deal(target, getName(), () -> target.damage(slash.damage(), player));

        Vector horizontal = new Vector(look.getX(), 0, look.getZ());
        if (horizontal.lengthSquared() > 1.0E-6) {
            horizontal.normalize().multiply(finisher ? FINISHER_KNOCKBACK : KNOCKBACK);
            horizontal.setY(finisher ? FINISHER_LIFT : 0.0);
            target.setVelocity(target.getVelocity().add(horizontal));
        }

        Location hitLoc = target.getLocation().add(0, target.getHeight() * 0.6, 0);
        target.getWorld().spawnParticle(Particle.CRIT, hitLoc, 14, 0.3, 0.4, 0.3, 0.2, null, true);
//        target.getWorld().spawnParticle(Particle.DUST, hitLoc, 12, 0.3, 0.4, 0.3, 0, BLACK_DUST, true);
        target.getWorld().playSound(hitLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, finisher ? 0.7f : 1.0f);
    }

    @Override
    public String getName() {
        return ChatColor.DARK_PURPLE + "Black Slash";
    }

    @Override
    public String getDescription() {
        return "Unleash a three-hit combo of black slashes in front of you.";
    }
}
