package hs.elementSMPRefined.util.combat;

import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Enhanced damage utility with damage types, effects, and better configuration.
 * Provides a comprehensive API for combat damage with proper validation and effects.
 */
public final class DamageUtils {

    /**
     * Damage types for categorization and logging
     */
    public enum DamageType {
        PHYSICAL("Physical"),
        MAGICAL("Magical"),
        ELEMENTAL("Elemental"),
        TRUE("True Damage"),
        ENVIRONMENTAL("Environmental"),
        DOT("Damage Over Time");

        private final String displayName;

        DamageType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Damage effects that can be applied alongside damage
     */
    public enum DamageEffect {
        NONE,
        BURNING,
        POISONING,
        WITHERING,
        SLOWING,
        WEAKENING,
        BLINDING,
        STUNNING
    }

    /**
     * Enhanced damage configuration with more options
     */
    public record DamageConfig(
            LivingEntity target,
            double amount,
            Optional<Player> source,
            DamageType damageType,
            boolean ignoreArmor,
            boolean applyKnockback,
            Vector knockbackDirection,
            double knockbackStrength,
            DamageEffect[] effects,
            int effectDuration,
            boolean showDamageNumbers,
            ChatColor damageColor
    ) {
        public DamageConfig {
            if (amount < 0) throw new IllegalArgumentException("Damage cannot be negative");
            if (knockbackStrength < 0) {
                throw new IllegalArgumentException("Knockback strength cannot be negative");
            }
            if (effects == null) effects = new DamageEffect[0];
        }

        public static DamageConfig simple(LivingEntity target, double amount) {
            return new DamageConfig(target, amount, Optional.empty(),
                    DamageType.PHYSICAL, false, false, new Vector(0, 0, 0), 0,
                    new DamageEffect[0], 0, false, ChatColor.RED);
        }

        public static DamageConfig magical(LivingEntity target, double amount, Player source) {
            return new DamageConfig(target, amount, Optional.of(source),
                    DamageType.MAGICAL, true, false, new Vector(0, 0, 0), 0,
                    new DamageEffect[0], 0, false, ChatColor.LIGHT_PURPLE);
        }

        public static DamageConfig elemental(LivingEntity target, double amount, Player source,
                                           DamageEffect... effects) {
            return new DamageConfig(target, amount, Optional.of(source),
                    DamageType.ELEMENTAL, true, false, new Vector(0, 0, 0), 0,
                    effects, 100, true, ChatColor.AQUA);
        }

        public static DamageConfig withKnockback(LivingEntity target, double amount,
                                                 Vector direction, double strength) {
            return new DamageConfig(target, amount, Optional.empty(),
                    DamageType.PHYSICAL, false, true, direction, strength,
                    new DamageEffect[0], 0, false, ChatColor.RED);
        }

        public static DamageConfig trueDamage(LivingEntity target, double amount) {
            return new DamageConfig(target, amount, Optional.empty(),
                    DamageType.TRUE, true, false, new Vector(0, 0, 0), 0,
                    new DamageEffect[0], 0, true, ChatColor.DARK_RED);
        }

        public DamageConfig withEffect(DamageEffect effect, int duration) {
            DamageEffect[] newEffects = new DamageEffect[effects.length + 1];
            System.arraycopy(effects, 0, newEffects, 0, effects.length);
            newEffects[effects.length] = effect;
            return new DamageConfig(target, amount, source, damageType, ignoreArmor,
                    applyKnockback, knockbackDirection, knockbackStrength,
                    newEffects, duration, showDamageNumbers, damageColor);
        }

        public DamageConfig withSource(Player source) {
            return new DamageConfig(target, amount, Optional.of(source), damageType,
                    ignoreArmor, applyKnockback, knockbackDirection, knockbackStrength,
                    effects, effectDuration, showDamageNumbers, damageColor);
        }

        public DamageConfig withKnockback(Vector direction, double strength) {
            return new DamageConfig(target, amount, source, damageType,
                    ignoreArmor, true, direction, strength,
                    effects, effectDuration, showDamageNumbers, damageColor);
        }
    }

    /**
     * Result of damage application with detailed information
     */
    public record DamageResult(
            boolean applied,
            double actualDamage,
            boolean killedTarget,
            boolean wasCritical,
            DamageType damageType,
            Optional<Player> source
    ) {
        public String getSummary() {
            return String.format("%s damage: %.1f (Type: %s)",
                    source.map(p -> p.getName() + "'s").orElse(""),
                    actualDamage, damageType.getDisplayName());
        }
    }

    /**
     * Apply damage with full configuration
     */
    public static DamageResult applyDamage(DamageConfig config) {
        if (config.target().isDead() || !config.target().isValid()) {
            return new DamageResult(false, 0, false, false, config.damageType(), config.source());
        }

        double actualDamage;
        boolean wasCritical = false;

        // Apply damage
        if (config.ignoreArmor()) {
            double currentHealth = config.target().getHealth();
            double newHealth = Math.max(0, currentHealth - config.amount());
            config.target().setHealth(newHealth);
            actualDamage = currentHealth - newHealth;
        } else {
            if (config.source().isPresent()) {
                config.target().damage(config.amount(), config.source().get());
            } else {
                config.target().damage(config.amount());
            }
            actualDamage = config.amount();
        }

        // Apply knockback
        if (config.applyKnockback() && config.knockbackDirection() != null) {
            Vector knockback = config.knockbackDirection().clone()
                    .normalize()
                    .multiply(config.knockbackStrength());
            config.target().setVelocity(knockback);
        }

        // Apply effects
        applyEffects(config);

        // Show damage numbers if enabled
        if (config.showDamageNumbers() && config.target() instanceof Player player) {
            showDamageNumber(player, actualDamage, config.damageColor());
        }

        boolean killed = config.target().isDead();
        return new DamageResult(true, actualDamage, killed, wasCritical, config.damageType(), config.source());
    }

    /**
     * Apply damage effects
     */
    private static void applyEffects(DamageConfig config) {
        if (config.effects().length == 0) return;

        int duration = config.effectDuration() / 20; // Convert ticks to seconds

        for (DamageEffect effect : config.effects()) {
            switch (effect) {
                case BURNING:
                    config.target().setFireTicks(duration * 20);
                    break;
                case POISONING:
                    config.target().addPotionEffect(new PotionEffect(
                            PotionEffectType.POISON, duration * 20, 0));
                    break;
                case WITHERING:
                    config.target().addPotionEffect(new PotionEffect(
                            PotionEffectType.WITHER, duration * 20, 0));
                    break;
                case SLOWING:
                    config.target().addPotionEffect(new PotionEffect(
                            PotionEffectType.SLOWNESS, duration * 20, 1));
                    break;
                case WEAKENING:
                    config.target().addPotionEffect(new PotionEffect(
                            PotionEffectType.WEAKNESS, duration * 20, 0));
                    break;
                case BLINDING:
                    config.target().addPotionEffect(new PotionEffect(
                            PotionEffectType.BLINDNESS, duration * 20, 0));
                    break;
                case STUNNING:
                    // Stun would be handled by StatusEffectManager
                    break;
                case NONE:
                default:
                    break;
            }
        }
    }

    /**
     * Show floating damage number (simplified - would need a proper implementation)
     */
    private static void showDamageNumber(Player player, double damage, ChatColor color) {
        // This would typically spawn a hologram or armor stand with text
        // For now, we'll use action bar as a simple alternative
        String damageText = color + String.format("%.1f", damage);
        player.sendActionBar(damageText);
    }

    /**
     * Check if target is valid for combat
     */
    public static boolean isValidTarget(Player attacker, LivingEntity target,
                                        hs.elementSMPRefined.managers.TrustManager trustManager) {
        if (target.equals(attacker)) return false;
        if (target instanceof org.bukkit.entity.ArmorStand) return false;

        if (target instanceof Player targetPlayer) {
            return !trustManager.isTrusted(attacker.getUniqueId(),
                    targetPlayer.getUniqueId());
        }

        return true;
    }

    /**
     * Calculate knockback direction from attacker to target
     */
    public static Vector calculateKnockback(LivingEntity attacker, LivingEntity target,
                                            double verticalComponent) {
        Vector direction = target.getLocation().toVector()
                .subtract(attacker.getLocation().toVector())
                .normalize();
        direction.setY(verticalComponent);
        return direction;
    }

    /**
     * Calculate damage with modifiers (armor, resistance, etc.)
     */
    public static double calculateModifiedDamage(LivingEntity target, double baseDamage,
                                                 DamageType damageType) {
        if (damageType == DamageType.TRUE) {
            return baseDamage; // True damage ignores all modifiers
        }

        double modifiedDamage = baseDamage;

        // Apply armor reduction for physical damage
        if (damageType == DamageType.PHYSICAL) {
            double armor = target instanceof Player player ?
                    ((Player) target).getAttribute(Attribute.ARMOR).getValue() : 0;
            double armorReduction = Math.min(20, armor) / 25.0; // Max 80% reduction
            modifiedDamage *= (1.0 - armorReduction);
        }

        // Apply resistance for magical damage
        if (damageType == DamageType.MAGICAL || damageType == DamageType.ELEMENTAL) {
            if (target.hasPotionEffect(PotionEffectType.RESISTANCE)) {
                int resistanceLevel = target.getPotionEffect(PotionEffectType.RESISTANCE).getAmplifier();
                double resistanceReduction = (resistanceLevel + 1) * 0.2; // 20% per level
                modifiedDamage *= (1.0 - Math.min(1.0, resistanceReduction));
            }
        }

        return Math.max(0, modifiedDamage);
    }

    /**
     * Apply damage over time
     * Note: Requires plugin instance for scheduling - use TaskScheduler instead
     */
    public static void applyDOT(LivingEntity target, double damagePerTick, int durationTicks,
                                DamageType damageType, Player source) {
        throw new UnsupportedOperationException("Use TaskScheduler for damage over time effects");
    }

    /**
     * Get damage color based on damage type
     */
    public static ChatColor getDamageColor(DamageType damageType) {
        return switch (damageType) {
            case PHYSICAL -> ChatColor.RED;
            case MAGICAL -> ChatColor.LIGHT_PURPLE;
            case ELEMENTAL -> ChatColor.AQUA;
            case TRUE -> ChatColor.DARK_RED;
            case ENVIRONMENTAL -> ChatColor.YELLOW;
            case DOT -> ChatColor.GRAY;
        };
    }

    /**
     * Chain multiple damage operations
     */
    public static void damageChain(LivingEntity target, Player source,
                                    Consumer<DamageConfigBuilder>... configurators) {
        for (Consumer<DamageConfigBuilder> config : configurators) {
            DamageConfigBuilder builder = new DamageConfigBuilder(target, source);
            config.accept(builder);
            applyDamage(builder.build());
        }
    }

    /**
     * Builder for DamageConfig
     */
    public static class DamageConfigBuilder {
        private final LivingEntity target;
        private Player source;
        private double amount;
        private DamageType damageType = DamageType.PHYSICAL;
        private boolean ignoreArmor = false;
        private boolean applyKnockback = false;
        private Vector knockbackDirection = new Vector(0, 0, 0);
        private double knockbackStrength = 0;
        private final java.util.List<DamageEffect> effects = new java.util.ArrayList<>();
        private int effectDuration = 100;
        private boolean showDamageNumbers = false;
        private ChatColor damageColor = ChatColor.RED;

        public DamageConfigBuilder(LivingEntity target, Player source) {
            this.target = target;
            this.source = source;
        }

        public DamageConfigBuilder amount(double amount) {
            this.amount = amount;
            return this;
        }

        public DamageConfigBuilder damageType(DamageType damageType) {
            this.damageType = damageType;
            return this;
        }

        public DamageConfigBuilder ignoreArmor(boolean ignoreArmor) {
            this.ignoreArmor = ignoreArmor;
            return this;
        }

        public DamageConfigBuilder knockback(Vector direction, double strength) {
            this.applyKnockback = true;
            this.knockbackDirection = direction;
            this.knockbackStrength = strength;
            return this;
        }

        public DamageConfigBuilder effect(DamageEffect effect) {
            this.effects.add(effect);
            return this;
        }

        public DamageConfigBuilder effectDuration(int duration) {
            this.effectDuration = duration;
            return this;
        }

        public DamageConfigBuilder showDamageNumbers(boolean show) {
            this.showDamageNumbers = show;
            return this;
        }

        public DamageConfigBuilder damageColor(ChatColor color) {
            this.damageColor = color;
            return this;
        }

        public DamageConfig build() {
            return new DamageConfig(
                    target,
                    amount,
                    Optional.ofNullable(source),
                    damageType,
                    ignoreArmor,
                    applyKnockback,
                    knockbackDirection,
                    knockbackStrength,
                    effects.toArray(new DamageEffect[0]),
                    effectDuration,
                    showDamageNumbers,
                    damageColor
            );
        }
    }

    private DamageUtils() {}
}

