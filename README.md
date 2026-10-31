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

## Resource pack

`src/main/resources/elementsmprefined` is the resource pack the item-display visuals need.

Life's Energy Blast (green beam, white core, star flares) also ships a core shader. Beam and flare
vertices carry a marker tint (`61,255,91`, see `items/energy_beam.json`); the shader fullbrights them,
maps the greyscale textures onto a bloom ramp (green halo -> neon green -> mint -> white core) and
fades the glow at glancing angles. The beam is four crossed gradient planes. The client chooses the render pipeline from the carrier
item, and a stick is drawn cutout (no blending), so beam and flares are carried by a translucent block item
(`ModelItems.translucent`) to get real alpha blending. Core shaders are
copies of the vanilla files, so each Minecraft version gets its own pack overlay (`pack.mcmeta`):

| Overlay | Pack format | Minecraft |
|---------|-------------|-----------|
| `overlay_26_2` | 88 | 26.2 |
| `overlay_26_3` | 97 | 26.3 |

On other versions the overlays are skipped and the beam falls back to the plain models (still a green
beam, just not shaded). When a new version changes `core/entity.*` or `core/item.*`, copy the new vanilla
files into a new overlay and re-add the few `energyFacing` / `energyBeamColor` lines.

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
| `/grace <start\|stop\|status\|extend\|shorten\|settings\|set>` | Server grace period (times like `10m`, `1h30m`; `/grace settings` lists every option) | `element.admin` |
| `/dimension <enable\|disable\|toggle\|status\|settings\|set> <nether\|end\|all> [time]` | Toggle portal travel, optionally for a set time | `element.admin` |

## Building

Requires JDK 25.

```
./gradlew build        # build/libs/ElementSMPRefined-<version>-shaded.jar
./gradlew test         # unit tests
./gradlew runServer    # local Paper test server
```

## Adding an element

Copy `util/example/ExampleElement` and `ExampleAbility`, add a value to `ElementType`, and register
the element in `ElementManager.registerAllElements()`.
