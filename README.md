# ElementSMPRefined

Season One element plugin for Paper, being refined.

## Elements

| Tier | Elements |
|------|----------|
| Basic | Air, Earth, Fire, Water |
| Upgraded | Metal, Frost, Lava, Storm |
| Mythic | Life, Death |

Each element has two abilities plus passives. Players are given a random basic element, ability 1
needs Upgrade I and ability 2 needs Upgrade II, and rerollers change a player's element.

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/ability1`, `/a1` | Use your first ability (needs Upgrade I) | |
| `/ability2`, `/a2` | Use your second ability (needs Upgrade II) | |
| `/elements [name]` | Info about all elements, or one | |
| `/trust <list\|add\|remove\|accept\|deny> [player]` | Manage trusted players | |
| `/autosmelt [on\|off]` | Toggle Fire/Lava auto smelt | |
| `/element <set\|data\|debug\|config\|particles>` | Admin tools | `element.admin` |
| `/util` | Get upgraders and rerollers | `element.admin` |
| `/grace <start\|stop\|status>` | Server grace period | `element.admin` |
| `/dimension <enable\|disable\|status> <nether\|end\|all>` | Toggle portal travel | `element.admin` |

## Building

Requires JDK 25.

```
./gradlew build        # build/libs/ElementSMPRefined-<version>-shaded.jar
./gradlew obfuscate    # also writes the ProGuard-obfuscated *-obf.jar (use for releases)
./gradlew test         # unit tests
./gradlew runServer    # local Paper test server
```

## Adding an element

Copy `util/example/ExampleElement` and `ExampleAbility`, add a value to `ElementType`, and register
the element in `ElementManager.registerAllElements()`.
