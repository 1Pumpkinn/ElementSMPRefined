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

The plugin hosts it itself: it zips the pack from its own jar, serves it over a small built-in HTTP
server and sends it to players when they join, so there is no link to upload or paste anywhere.
Set `resource_pack.host` (your public IP or domain) and open `resource_pack.port` (default 8080) in
`config.yml`. Updating the pack is just rebuilding the plugin; players re-download automatically
because the SHA-1 changes with the contents. `/element pack <status|resend|reload>` manages it.

Life's Spore Blossom uses vanilla block displays and needs nothing from the pack.

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/ability1`, `/a1` | Use your first ability (needs Upgrade I) | |
| `/ability2`, `/a2` | Use your second ability (needs Upgrade II) | |
| `/elements [name]` | Info about all elements, or one | |
| `/team <create\|invite\|accept\|deny\|leave\|kick\|transfer\|disband\|info\|ally>` | Manage your team and allies (teammates and allies can't damage each other; limits in `team.max_members` / `team.max_allies`) | |
| `/autosmelt [on\|off]` | Toggle Fire/Lava auto smelt | |
| `/element <set\|data\|debug\|castall\|config>` | Admin tools | `element.admin` |
| `/util` | Get upgraders and rerollers | `element.admin` |
| `/eventvillager <spawn <type> [name]\|settype\|remove\|list\|types\|reload>` | Unkillable event villagers; each type has its own name, outfit and trades in `event_villagers.yml` | `element.eventvillager` (op) |
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
