# Enhanced Stun System

## Overview
The stun system has been enhanced to support multiple stun types with different levels of restriction, allowing for more nuanced gameplay mechanics.

## Stun Types

### 1. Full Stun (`StatusEffectType.FULL_STUN`)
**Restrictions:**
- ❌ Cannot move (walking, jumping, falling)
- ❌ Cannot look around (head rotation blocked)
- ❌ Cannot interact with items/blocks
- ❌ Cannot break blocks
- ❌ Cannot place blocks

**Potion Effect:** Maximum slowness (amplifier 127) to prevent any movement

**Max Duration:** 5 seconds (300 ticks)

**Use Cases:**
- Powerful crowd control abilities
- Ultimate abilities
- Critical incapacitation effects

**Example:**
```java
statusEffectManager.applyFullStun(player, 100); // 5 seconds
// or
statusEffectManager.applyEffect(player, StatusEffectType.FULL_STUN, 100);
```

### 2. Partial Stun (`StatusEffectType.PARTIAL_STUN`)
**Restrictions:**
- ❌ Cannot move (walking, jumping, falling)
- ❌ Cannot look around (head rotation blocked)
- ✅ Can interact with items/blocks
- ✅ Can break blocks
- ✅ Can place blocks

**Potion Effect:** Maximum slowness (amplifier 127) to prevent movement

**Max Duration:** 8 seconds (400 ticks)

**Use Cases:**
- Movement and head-impairing effects
- Dizziness or disorientation
- Temporary incapacitation that allows limited counterplay

**Example:**
```java
statusEffectManager.applyPartialStun(player, 160); // 8 seconds
// or
statusEffectManager.applyEffect(player, StatusEffectType.PARTIAL_STUN, 160);
```

### 3. Stun (`StatusEffectType.STUN`)
**Restrictions:**
- ❌ Cannot move (walking, jumping, falling)
- ✅ Can look around (head rotation allowed)
- ✅ Can interact with items/blocks
- ✅ Can break blocks
- ✅ Can place blocks

**Potion Effect:** Moderate slowness (amplifier 5) to prevent movement

**Max Duration:** 8 seconds (400 ticks)

**Use Cases:**
- Standard stun effects
- Movement-impairing abilities
- Root-like effects with different flavor

**Example:**
```java
statusEffectManager.applyStun(player, 160); // 8 seconds
// or
statusEffectManager.applyEffect(player, StatusEffectType.STUN, 160);
```

### 4. Root (`StatusEffectType.ROOT`)
**Restrictions:**
- ❌ Cannot move (walking, jumping, falling)
- ✅ Can look around (head rotation allowed)
- ✅ Can interact with items/blocks
- ✅ Can break blocks
- ✅ Can place blocks

**Potion Effect:** Moderate slowness (amplifier 5) to prevent movement

**Max Duration:** 8 seconds (400 ticks)

**Use Cases:**
- Earth-based immobilization
- Nature abilities
- "Stuck in place" effects

**Note:** Functionally identical to Stun, but with different naming/flavor

**Example:**
```java
statusEffectManager.applyRoot(player, 160); // 8 seconds
// or
statusEffectManager.applyEffect(player, StatusEffectType.ROOT, 160);
```

## API Reference

### StatusEffectManager Methods

```java
// Apply stun effects
statusEffectManager.applyFullStun(Player player, int durationTicks);
statusEffectManager.applyPartialStun(Player player, int durationTicks);
statusEffectManager.applyStun(Player player, int durationTicks);
statusEffectManager.applyRoot(Player player, int durationTicks);

// Check stun status
boolean hasAnyStun = statusEffectManager.hasAnyStun(player); // Any stun type
boolean isFullyStunned = statusEffectManager.isFullyStunned(player);
boolean isPartiallyStunned = statusEffectManager.isPartiallyStunned(player);
boolean isStunned = statusEffectManager.isStunned(player);
boolean isRooted = statusEffectManager.isRooted(player);

// Remove stun effects
statusEffectManager.removeEffect(player, StatusEffectType.FULL_STUN);
statusEffectManager.removeEffect(player, StatusEffectType.PARTIAL_STUN);
statusEffectManager.removeEffect(player, StatusEffectType.STUN);
statusEffectManager.removeEffect(player, StatusEffectType.ROOT);

// Get remaining duration
int remaining = statusEffectManager.getRemainingDuration(player, StatusEffectType.FULL_STUN);
```

## Event Behavior

### PlayerMoveEvent
- **Full Stun:** Cancels both movement AND head rotation
- **Partial Stun:** Cancels both movement AND head rotation
- **Stun/Root:** Cancels movement only, allows head rotation
- **Freeze:** Cancels movement only (existing behavior)

### PlayerInteractEvent
- **Full Stun:** Cancels all interactions
- **Partial Stun:** Allows interactions
- **Stun/Root:** Allows interactions
- **Silence:** Cancels interactions (existing behavior)

### BlockBreakEvent / BlockPlaceEvent
- **Full Stun:** Cancels block breaking/placing
- **Partial Stun:** Allows block breaking/placing
- **Stun/Root:** Allows block breaking/placing

### EntityDamageEvent
- **All Stun Types:** No damage modification (removed damage multiplier)

## Player Feedback

### Action Bar Messages
Players receive action bar feedback when attempting restricted actions:

- Full Stun: "§cYou are fully stunned and cannot move or look!"
- Partial Stun: "§cYou are partially stunned and cannot move or look!"
- Stun: "§cYou are stunned and cannot move!"
- Root: "§cYou are rooted and cannot move!"
- Freeze: "§cYou are frozen and cannot move!"
- Interaction Block: "§cYou are fully stunned and cannot interact!"

### Chat Messages
When a stun is applied:
- "§cFull Stun applied for X seconds"
- "§cPartial Stun applied for X seconds"
- "§cStun applied for X seconds"
- "§cRoot applied for X seconds"

When a stun is removed:
- "§aFull Stun removed"
- "§aPartial Stun removed"
- "§aStun removed"
- "§aRoot removed"

## Design Philosophy

### Why Multiple Stun Types?

1. **Gameplay Depth**: Different abilities can have different levels of crowd control
2. **Counterplay**: Stun and Root allow players to still fight back while immobilized
3. **Flavor**: Different elements/effects can have appropriate stun types:
   - **Full Stun**: Thunder, powerful impacts, mental incapacitation
   - **Partial Stun**: Dizziness, disorientation, confusion
   - **Stun**: Standard incapacitation
   - **Root**: Earth-based, vines, entanglement

### Duration Limits
- **Full Stun:** Shorter (5s max) because it's most powerful
- **Partial Stun:** Longer (8s max) because it allows limited counterplay
- **Stun/Root:** Longer (8s max) because it allows full counterplay

## Examples by Element

### Fire Element
```java
// Fireball - brief full stun on impact
statusEffectManager.applyFullStun(target, 40); // 2 seconds
```

### Earth Element
```java
// Earth grasp - root effect
statusEffectManager.applyRoot(target, 100); // 5 seconds
```

### Frost Element
```java
// Ice trap - stun effect (can still attack but can't move)
statusEffectManager.applyStun(target, 80); // 4 seconds
```

### Air Element
```java
// Tornado - disorientation (partial stun - can't move or look)
statusEffectManager.applyPartialStun(target, 60); // 3 seconds
```

### Water Element
```java
// Water jet - brief stun
statusEffectManager.applyStun(target, 40); // 2 seconds
```

## Testing Checklist

- [ ] Full stun prevents movement and head rotation
- [ ] Full stun prevents all interactions
- [ ] Full stun prevents block breaking/placing
- [ ] Partial stun prevents movement and head rotation
- [ ] Partial stun allows interactions
- [ ] Partial stun allows block breaking/placing
- [ ] Stun prevents movement only
- [ ] Stun allows head rotation
- [ ] Stun allows interactions
- [ ] Stun allows block breaking/placing
- [ ] Root behaves identically to Stun
- [ ] All stun types do NOT increase damage taken
- [ ] Action bar messages display correctly
- [ ] Duration limits are enforced
- [ ] Stun can be removed manually
- [ ] Stun expires automatically
- [ ] Multiple stun types don't conflict
