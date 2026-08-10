# Util Directory Improvements

## Overview
All utility classes in the `util` directory have been significantly enhanced with improved functionality, better APIs, and more comprehensive features.

## 1. ItemUtil (`util.bukkit.ItemUtil`)

### New Features
- **ItemBuilder Pattern**: Fluent API for creating ItemStacks
- **Enhanced Metadata**: Better custom data management
- **Item Operations**: Counting, removing, and comparing items
- **Null-Safe Operations**: All methods handle null gracefully
- **Optional Returns**: Modern Optional-based API

### Key Improvements
```java
// ItemBuilder for easy item creation
ItemStack item = ItemUtil.builder(Material.DIAMOND_SWORD)
    .name("§cFire Sword")
    .lore("§7A powerful fire weapon")
    .enchant(Enchantment.FIRE_ASPECT, 2)
    .unbreakable(true)
    .build();

// Better element detection
Optional<ElementType> type = ItemUtil.getElementType(plugin, item);

// Item operations
int count = ItemUtil.countMatching(inventory, item);
int removed = ItemUtil.removeMatching(inventory, item, amount);
```

## 2. MetadataHelper (`util.bukkit.MetadataHelper`)

### New Features
- **More Data Types**: Support for Double, Long, and generic objects
- **Caching**: Built-in cache for timed metadata
- **Batch Operations**: Set/get multiple values at once
- **Computed Values**: Lazy computation with caching
- **Better Cleanup**: Automatic cleanup of expired metadata

### Key Improvements
```java
// More data types
metadataHelper.setDouble(entity, "damage", 15.5);
metadataHelper.setLong(entity, "timestamp", System.currentTimeMillis());

// Computed values
String value = metadataHelper.getOrCompute(entity, "expensive_computation", () -> {
    return performExpensiveCalculation();
});

// Batch operations
Map<String, Object> values = Map.of(
    "health", 100,
    "mana", 50,
    "level", 5
);
metadataHelper.setBatch(entity, values);

// Automatic cleanup
metadataHelper.cleanupExpiredCache();
```

## 3. DamageUtils (`util.combat.DamageUtils`)

### New Features
- **Damage Types**: PHYSICAL, MAGICAL, ELEMENTAL, TRUE, ENVIRONMENTAL, DOT
- **Damage Effects**: BURNING, POISONING, WITHERING, SLOWING, WEAKENING, BLINDING, STUNNING
- **Enhanced Configuration**: Builder pattern for damage config
- **Damage Calculation**: Armor and resistance modifiers
- **Damage Over Time**: DOT support with scheduling
- **Visual Feedback**: Damage numbers and colors

### Key Improvements
```java
// Damage types and effects
DamageConfig config = DamageConfig.elemental(target, 10.0, source,
    DamageEffect.BURNING, DamageEffect.WEAKENING);

// True damage
DamageConfig trueDamage = DamageConfig.trueDamage(target, 15.0);

// Damage calculation
double modifiedDamage = DamageUtils.calculateModifiedDamage(target, baseDamage, DamageType.MAGICAL);

// Builder pattern
DamageConfig config = new DamageConfig.Builder(target, source)
    .amount(10.0)
    .damageType(DamageType.ELEMENTAL)
    .ignoreArmor(true)
    .knockback(direction, 1.5)
    .effect(DamageEffect.BURNING)
    .showDamageNumbers(true)
    .build();
```

## 4. TaskScheduler (`util.scheduling.TaskScheduler`)

### New Features
- **Async Support**: Asynchronous task execution
- **Named Tasks**: Task management by name
- **Player-Specific Tasks**: Tasks tied to specific players
- **Conditional Tasks**: Run until/while conditions
- **Chained Tasks**: Sequential task execution
- **Retry Logic**: Automatic retry on failure
- **Timeout Support**: Tasks with timeout handling

### Key Improvements
```java
// Async tasks
taskScheduler.runAsync(() -> performHeavyComputation());

// Named tasks
taskScheduler.runNamed("recurring_effect", effectTask, 20);
taskScheduler.cancelNamed("recurring_effect");

// Player-specific tasks
taskScheduler.runForPlayer(playerId, "buff", buffTask, 100);
taskScheduler.cancelAllPlayerTasks(playerId);

// Conditional tasks
taskScheduler.runUntil(task, () -> target.isDead(), 0, 20);

// Chained tasks
taskScheduler.runSequence(task1, task2, task3);

// Retry logic
taskScheduler.runWithRetry(unstableTask, 3, 20);
```

## 5. TimeUtils (`util.time.TimeUtils`)

### New Features
- **Enhanced Formatting**: Multiple time format options
- **Flexible Parsing**: Parse time strings like "1h 30m"
- **Date Formatting**: Date and time formatting
- **Time Calculations**: Elapsed time, time until, etc.
- **Conversion Methods**: Comprehensive time unit conversions
- **Expiration Tracking**: Enhanced expiration record

### Key Improvements
```java
// Formatting
String formatted = TimeUtils.formatDuration(3665000); // "1h 1m 5s"
String detailed = TimeUtils.formatDurationDetailed(3665123); // "1h 1m 5s 123ms"
String mmss = TimeUtils.formatMMSS(90); // "01:30"
String hhmmss = TimeUtils.formatHHMMSS(3661); // "01:01:01"

// Parsing
long millis = TimeUtils.parseDuration("1h 30m 45s");
int seconds = TimeUtils.parseDurationSeconds("2m 30s");

// Time calculations
long elapsed = TimeUtils.elapsedSince(timestamp);
String elapsedFormatted = TimeUtils.elapsedSinceFormatted(timestamp);
long until = TimeUtils.timeUntil(futureTimestamp);

// Enhanced expiration
Expiration exp = Expiration.fromHours(2);
String remaining = exp.getRemainingTimeString();
```

## 6. ParticlePatterns (`util.visual.ParticlePatterns`)

### New Features
- **3D Patterns**: Spheres, helixes, spirals
- **Animations**: Animated expanding rings, rotating circles, rising spirals
- **Special Effects**: Bursts, vortexes, waves
- **Animation Manager**: Control multiple particle animations
- **Enhanced Configs**: More configuration options

### Key Improvements
```java
// 3D patterns
SphereConfig sphere = SphereConfig.of(center, 5.0, Particle.FLAME);
ParticlePatterns.spawnSphere(sphere);

HelixConfig helix = HelixConfig.of(center, 3.0, 10.0, 3, Particle.WATER);
ParticlePatterns.spawnHelix(helix);

// Animations
ParticlePatterns.animateExpandingRing(ringConfig, plugin);
ParticlePatterns.animateRotatingCircle(circleConfig, 20, plugin);
ParticlePatterns.animateRisingSpiral(spiralConfig, 2, plugin);

// Special effects
ParticlePatterns.createBurst(center, Particle.EXPLOSION, 50, 3.0);
ParticlePatterns.createVortex(center, Particle.END_ROD, 4.0, 8.0, 12, 5);
ParticlePatterns.createWave(center, Particle.WATER_DROP, 5.0, 24);

// Animation manager
AnimationManager manager = new AnimationManager(plugin);
manager.startAnimation("effect1", m -> m.addTask("effect1", task));
manager.stopAnimation("effect1");
```

## 7. SoundUtils (`util.visual.SoundUtils`)

### New Features
- **More Categories**: Combat, Ambient, Movement, Notification sounds
- **Sound Effects**: Fade in/out, pitch slide, sequences
- **Spatial Audio**: Distance falloff, directional sounds
- **Sound Manager**: Manage looping sounds for players and global
- **Variation**: Random pitch and volume variation

### Key Improvements
```java
// New sound categories
SoundUtils.playTo(player, Combat.CRITICAL);
SoundUtils.playTo(player, Ambient.MAGIC);
SoundUtils.playTo(player, Notification.ACHIEVEMENT);

// Sound effects
SoundUtils.playFadeIn(player, config, 10, 2);
SoundUtils.playFadeOut(player, config, 10, 2);
SoundUtils.playPitchSlide(player, config, 0.5f, 2.0f, 20, 1);
SoundUtils.playSequence(player, config1, config2, config3);

// Spatial audio
SoundUtils.playWithFalloff(location, config, 16.0);
SoundUtils.playDirectional(player, soundLocation, config);

// Sound manager
SoundManager manager = new SoundManager(plugin);
manager.startLoopingSound(playerId, "background", config, 20);
manager.stopPlayerSound(playerId, "background");
manager.startGlobalLoop("ambient", location, config, 40);
```

## Summary of Improvements

### Code Quality
- **Modern APIs**: Optional returns, builder patterns, functional interfaces
- **Null Safety**: Comprehensive null checking and graceful handling
- **Type Safety**: Strong typing with proper validation
- **Documentation**: Enhanced JavaDoc and code comments

### Functionality
- **More Features**: Each utility class gained significant new capabilities
- **Better Performance**: Caching, efficient algorithms, optimized operations
- **Flexibility**: Configurable behavior with sensible defaults
- **Extensibility**: Easy to extend with new patterns, sounds, etc.

### API Design
- **Fluent Interfaces**: Builder patterns for complex configurations
- **Consistent Naming**: Clear, descriptive method names
- **Logical Organization**: Related methods grouped together
- **Backward Compatibility**: Existing functionality preserved

## Usage Examples

### Complete Element Ability with New Utils
```java
// Using DamageUtils for combat
DamageConfig damageConfig = new DamageConfig.Builder(target, player)
    .amount(15.0)
    .damageType(DamageType.ELEMENTAL)
    .knockback(direction, 1.5)
    .effect(DamageEffect.BURNING)
    .showDamageNumbers(true)
    .build();

DamageResult result = DamageUtils.applyDamage(damageConfig);

// Using ParticlePatterns for visual effects
SphereConfig sphere = SphereConfig.hollow(target.getLocation(), 3.0, Particle.FLAME);
ParticlePatterns.spawnSphere(sphere);

// Using SoundUtils for audio
SoundUtils.playTo(player, Element.FIRE);
SoundUtils.playDelayed(player, Combat.CRITICAL, 10);

// Using TaskScheduler for delayed effects
taskScheduler.runForPlayer(playerId, "damage_over_time", () -> {
    DamageUtils.applyDamage(DamageConfig.simple(target, 2.0));
}, 20);
```

### Status Effect with New Utils
```java
// Using TimeUtils for duration
long duration = TimeUtils.parseDuration("5s");
Expiration expiration = Expiration.fromNow(duration);

// Using MetadataHelper for tracking
metadataHelper.setTimed(entity, "stun", duration);

// Using SoundUtils for feedback
SoundUtils.playTo(player, Notification.WARNING);

// Using ParticlePatterns for visual feedback
ParticlePatterns.createBurst(player.getLocation(), Particle.SPELL, 20, 2.0);
```

All utility classes are now significantly more powerful, easier to use, and provide comprehensive functionality for advanced plugin development.