# ElementSMPRefined - Code Improvements Documentation

## Overview
This document describes the major improvements made to the ElementSMPRefined codebase to make adding elements easier, improve the API, and make the system more data-driven.

## Key Improvements

### 1. Central Registry System

#### ElementRegistry (`hs.elementSMPRefined.registry.ElementRegistry`)
- **Purpose**: Central registry for all elements with automatic registration
- **Benefits**: 
  - No more manual element registration in ElementManager
  - Type-safe element lookup
  - Metadata management for elements
  - Prevention of duplicate registrations
- **Usage**:
```java
// Register an element with metadata
elementRegistry.register(new FireElement(plugin), 
    ElementRegistry.ElementData.builder()
        .displayName("Fire")
        .description("Masters of flame and destruction")
        .color("RED")
        .isBasic(true)
        .build());

// Get an element
Element fireElement = elementRegistry.get(ElementType.FIRE);

// Get element metadata
ElementRegistry.ElementData data = elementRegistry.getData(ElementType.FIRE);
```

#### ItemRegistry (`hs.elementSMPRefined.registry.ItemRegistry`)
- **Purpose**: Central registry for all custom items
- **Benefits**:
  - Unified item management
  - Metadata-driven item configuration
  - Easy item lookup by ID
- **Usage**:
```java
// Register an item
itemRegistry.register("fire_core", fireCoreItem,
    ItemRegistry.ItemData.builder()
        .displayName("Fire Core")
        .description("Core of fire element")
        .material(Material.BLAZE_POWDER)
        .build());

// Get an item
ElementItem item = itemRegistry.getItem("fire_core");
```

### 2. Status Effect Manager

#### StatusEffectManager (`hs.elementSMPRefined.status.StatusEffectManager`)
- **Purpose**: Centralized management of status effects (stun, slow, silence, etc.)
- **Features**:
  - Pre-defined status effects (STUN, SLOW, SILENCE, WEAKNESS, FREEZE, BLEED, BURN)
  - Configurable duration, amplification, and stacking
  - Damage over time support
  - Automatic effect monitoring and cleanup
  - Easy API for applying/removing effects
  - **Event-based stun system**: Stun and Freeze use event cancellation instead of potion effects for cleaner implementation
- **Usage**:
```java
// Apply a stun effect for 5 seconds
statusEffectManager.applyEffect(player, StatusEffectType.STUN, 100); // 100 ticks = 5 seconds

// Apply a slow effect with amplifier
statusEffectManager.applyEffect(player, StatusEffectType.SLOW, 200, 2); // 10 seconds, amplifier 2

// Check if player is stunned
if (statusEffectManager.isStunned(player)) {
    // Prevent action
}

// Remove an effect
statusEffectManager.removeEffect(player, StatusEffectType.STUN);

// Remove all effects
statusEffectManager.removeAllEffects(player);
```

- **Integration**: Access via `plugin.getStatusEffectManager()`

### 3. Element Builder API

#### ElementBuilder (`hs.elementSMPRefined.elements.ElementBuilder`)
- **Purpose**: Fluent builder pattern for creating elements with minimal code
- **Benefits**:
  - Eliminates boilerplate code
  - Clean, readable element creation
  - Less error-prone than manual implementation
  - Easy to modify and maintain
- **Usage**:
```java
Element fireElement = new ElementBuilder(plugin)
    .type(ElementType.FIRE)
    .displayName("Fire")
    .description("Masters of flame and destruction")
    .color(ChatColor.RED)
    .isBasic(true)
    .passiveEffect(PotionEffectType.FIRE_RESISTANCE, 0)
    .clearEffect(PotionEffectType.FIRE_RESISTANCE)
    .ability1(fireballAbility, "Fireball", "Launch a fireball")
    .ability2(meteorShowerAbility, "Meteor Shower", "Rain meteors")
    .build();
```

### 4. Annotation-Based Registration

#### @RegisterElement Annotation
- **Purpose**: Enable automatic discovery and registration of elements
- **Usage**:
```java
@RegisterElement(
    value = ElementType.FIRE,
    isBasic = true,
    displayName = "Fire",
    description = "Masters of flame and destruction",
    color = "RED"
)
public class FireElement extends BaseElement {
    // Implementation...
}
```

#### @RegisterItem Annotation
- **Purpose**: Enable automatic discovery and registration of items
- **Usage**:
```java
@RegisterItem(
    id = "fire_core",
    displayName = "Fire Core",
    description = "Core of fire element",
    isConsumable = false
)
public class FireCoreItem implements ElementItem {
    // Implementation...
}
```

### 5. Data-Driven Configuration

#### ElementConfiguration (`hs.elementSMPRefined.config.ElementConfiguration`)
- **Purpose**: Load element settings from config.yml
- **Features**:
  - Per-element configuration
  - Enable/disable elements
  - Configure ability costs
  - Set damage/defense multipliers
- **Config Format**:
```yaml
elements:
  fire:
    display_name: "Fire"
    description: "Masters of flame and destruction"
    color: "RED"
    enabled: true
    is_basic: true
    ability1_cost: 50
    ability2_cost: 75
    damage_multiplier: 1.1
    defense_multiplier: 1.0
```

#### Enhanced ConfigManager
- Added support for element configuration
- Added status effect settings
- Backward compatible with existing config

### 6. Improved Element Interface

#### Enhanced Element Interface
- Added standard methods for display information
- Consistent API across all elements
- Better integration with GUI and commands

## Migration Guide

### For Existing Elements

**Old Approach** (FireElement.java):
```java
public class FireElement extends BaseElement {
    private final Ability ability1;
    private final Ability ability2;

    public FireElement(ElementSMPRefined plugin) {
        super(plugin);
        this.ability1 = new FireballAbility(plugin);
        this.ability2 = new MeteorShowerAbility(plugin);
    }

    @Override
    public ElementType getType() { return ElementType.FIRE; }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, true, false));
    }

    @Override
    protected boolean executeAbility1(ElementContext context) {
        return ability1.execute(context);
    }

    @Override
    protected boolean executeAbility2(ElementContext context) {
        return ability2.execute(context);
    }

    @Override
    public void clearEffects(Player player) {
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        ability1.setActive(player, false);
        ability2.setActive(player, false);
    }

    @Override
    public String getDisplayName() { return ChatColor.RED + "Fire"; }

    @Override
    public String getDescription() { return ChatColor.GRAY + "Masters of flame..."; }

    @Override
    public String getAbility1Name() { return ability1.getName(); }

    @Override
    public String getAbility1Description() { return ability1.getDescription(); }

    @Override
    public String getAbility2Name() { return ability2.getName(); }

    @Override
    public String getAbility2Description() { return ability2.getDescription(); }
}
```

**New Approach** (using ElementBuilder):
```java
public static Element create(ElementSMPRefined plugin) {
    Ability fireballAbility = new FireballAbility(plugin);
    Ability meteorShowerAbility = new MeteorShowerAbility(plugin);

    return new ElementBuilder(plugin)
        .type(ElementType.FIRE)
        .displayName("Fire")
        .description("Masters of flame and destruction")
        .color(ChatColor.RED)
        .isBasic(true)
        .passiveEffect(PotionEffectType.FIRE_RESISTANCE, 0)
        .clearEffect(PotionEffectType.FIRE_RESISTANCE)
        .ability1(fireballAbility, "Fireball", "Launch a fireball")
        .ability2(meteorShowerAbility, "Meteor Shower", "Rain meteors")
        .build();
}
```

**Benefits**: ~70% less code, much more readable, easier to maintain

### For Adding New Elements

**Step 1**: Add element type to enum (if new)
```java
public enum ElementType {
    // ... existing elements
    LIGHTNING  // Add new element type
}
```

**Step 2**: Create element using builder
```java
Element lightningElement = new ElementBuilder(plugin)
    .type(ElementType.LIGHTNING)
    .displayName("Lightning")
    .description("Masters of storms and electricity")
    .color(ChatColor.YELLOW)
    .isBasic(false)
    .passiveEffect(PotionEffectType.SPEED, 1)
    .ability1(lightningStrikeAbility, "Lightning Strike", "Strike with lightning")
    .ability2(thunderStormAbility, "Thunder Storm", "Create a storm")
    .build();
```

**Step 3**: Register in ElementManager
```java
elementRegistry.register(lightningElement, 
    ElementRegistry.ElementData.builder()
        .displayName("Lightning")
        .description("Masters of storms and electricity")
        .color("YELLOW")
        .isBasic(false)
        .build());
```

**Step 4**: Add configuration to config.yml
```yaml
elements:
  lightning:
    display_name: "Lightning"
    description: "Masters of storms and electricity"
    color: "YELLOW"
    enabled: true
    is_basic: false
    ability1_cost: 50
    ability2_cost: 75
    damage_multiplier: 1.2
    defense_multiplier: 1.0
```

## API Improvements

### Status Effect API
```java
// Apply effects
statusEffectManager.applyEffect(player, StatusEffectType.STUN, 100);
statusEffectManager.applyEffect(player, StatusEffectType.SLOW, 200, 2);

// Check effects
boolean stunned = statusEffectManager.isStunned(player);
boolean silenced = statusEffectManager.isSilenced(player);
boolean frozen = statusEffectManager.isFrozen(player);

// Get effect info
int remainingDuration = statusEffectManager.getRemainingDuration(player, StatusEffectType.STUN);

// Remove effects
statusEffectManager.removeEffect(player, StatusEffectType.STUN);
statusEffectManager.removeAllEffects(player);
```

### Event-Based Status Effects
The stun and freeze systems now use event cancellation instead of potion effects:

**Stun**: Cancels movement, interactions, block breaking/placing
**Freeze**: Cancels movement only (allows interactions)
**Silence**: Cancels interactions and ability usage

This approach is cleaner than using negative potion effects and provides better control over what actions are prevented.

### Registry API
```java
// Element registry
Element element = elementRegistry.get(ElementType.FIRE);
ElementRegistry.ElementData data = elementRegistry.getData(ElementType.FIRE);
Collection<Element> allElements = elementRegistry.getAllElements();

// Item registry
ElementItem item = itemRegistry.getItem("fire_core");
ItemRegistry.ItemData itemData = itemRegistry.getData("fire_core");
Collection<ElementItem> allItems = itemRegistry.getAllItems();
```

### Manager Access
```java
// Get managers from plugin instance
ConfigManager configManager = plugin.getConfigManager();
ElementManager elementManager = plugin.getElementManager();
ManaManager manaManager = plugin.getManaManager();
TrustManager trustManager = plugin.getTrustManager();
ItemManager itemManager = plugin.getItemManager();
StatusEffectManager statusEffectManager = plugin.getStatusEffectManager();
DataStore dataStore = plugin.getDataStore();
```

## File Structure

### New Files Created
```
src/main/java/hs/elementSMPRefined/
├── registry/
│   ├── ElementRegistry.java          # Central element registry
│   ├── ItemRegistry.java             # Central item registry
│   ├── RegisterElement.java          # Annotation for element registration
│   ├── RegisterItem.java             # Annotation for item registration
│   └── AnnotationRegistry.java       # Automatic annotation scanner
├── status/
│   ├── StatusEffectManager.java      # Status effect management
│   └── StatusEffectType.java         # Status effect types enum
├── listeners/
│   └── StatusEffectListener.java     # Event-based status effect handling
├── config/
│   └── ElementConfiguration.java     # Element configuration loader
├── elements/
│   ├── ElementBuilder.java           # Element builder API
│   ├── example/
│   │   └── ExampleNewElement.java    # Example element creation
│   └── impl/
│       └── fire/
│           └── FireElementRefactored.java  # Refactored fire element example
```

### Modified Files
```
src/main/java/hs/elementSMPRefined/
├── ElementSMPRefined.java            # Added StatusEffectManager and StatusEffectListener
├── managers/
│   ├── ElementManager.java           # Uses ElementRegistry
│   ├── ItemManager.java              # Uses ItemRegistry
│   └── ConfigManager.java            # Added element configuration support
├── elements/
│   ├── Element.java                  # Added display methods
│   └── BaseElement.java              # Updated for new interface
├── status/
│   └── StatusEffectManager.java      # Updated to use event-based stun system
└── resources/
    └── config.yml                    # Added element configuration section
```

## Benefits Summary

1. **Easier Element Addition**: New elements can be added with ~70% less code
2. **Data-Driven**: Configuration loaded from config.yml, no hard-coding
3. **Cleaner API**: Fluent builder patterns, consistent interfaces
4. **Better Organization**: Central registries, clear separation of concerns
5. **Status Effects**: Built-in support for stun, slow, silence, etc.
6. **Extensible**: Easy to add new status effects, elements, items
7. **Maintainable**: Less boilerplate, clearer code structure
8. **Type-Safe**: Compile-time checking for element/item types
9. **Automatic Registration**: Annotation-based discovery system
10. **Backward Compatible**: Existing code continues to work

## Next Steps

1. **Migrate Existing Elements**: Gradually convert existing elements to use the new ElementBuilder API
2. **Add More Status Effects**: Expand the StatusEffectManager with additional effect types
3. **Create Listeners**: Add status effect listeners for combat integration
4. **Documentation**: Create JavaDoc for all new APIs
5. **Testing**: Add unit tests for registry and status effect systems
6. **Performance**: Monitor and optimize registry performance if needed

## Example Use Cases

### Creating a Custom Status Effect
```java
// Register custom status effect
statusEffectManager.registerEffectData(StatusEffectType.CUSTOM,
    StatusEffectData.builder()
        .displayName("Custom Effect")
        .description("A custom status effect")
        .potionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0))
        .isStackable(false)
        .maxDuration(300)
        .damagePerSecond(0.5)
        .build());
```

### Creating a Custom Item
```java
// Register custom item
itemRegistry.register("custom_item", customItem,
    ItemRegistry.ItemData.builder()
        .displayName("Custom Item")
        .description("A custom item")
        .material(Material.DIAMOND)
        .isConsumable(true)
        .maxStackSize(16)
        .requiresPermission(false)
        .build());
```

This improved architecture makes the codebase much more maintainable and extensible while significantly reducing the amount of code needed to add new features.