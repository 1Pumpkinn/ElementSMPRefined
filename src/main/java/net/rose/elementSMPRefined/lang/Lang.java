package net.rose.elementSMPRefined.lang;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * All player-facing message text lives here so wording/colors can be edited
 * in one place instead of hunting through every listener/ability/manager
 * that sends a message.
 * <p>
 * Every field/method returns an Adventure {@link Component} ready to hand
 * straight to {@code Audience#sendMessage}. Messages with dynamic content
 * are exposed as small static methods instead of format strings so the
 * colors of each segment stay intact.
 */
public final class Lang {

    private Lang() {}

    /**
     * Element display names still carry legacy section codes (e.g. {@code ChatColor.AQUA + "Water"}),
     * and handing those to {@code Component.text} makes Adventure log a LegacyFormattingDetected
     * warning every time. This turns them into a proper coloured component instead; if the name
     * has no colour of its own it falls back to {@code fallback}.
     */
    private static Component legacy(Object text, NamedTextColor fallback) {
        return LegacyComponentSerializer.legacySection()
                .deserialize(String.valueOf(text))
                .colorIfAbsent(fallback);
    }

    // --- Death element - Backstab ability ---
    public static final Component DEATH_BACKSTAB_NO_TARGET_IN_RANGE =
            Component.text("No target in range.", NamedTextColor.RED);


    // --- Earth element - Tunnel ability ---
    public static final Component EARTH_TUNNEL_TUNNELING_CANCELLED =
            Component.text("Tunneling cancelled", NamedTextColor.YELLOW);

    public static final Component EARTH_TUNNEL_TUNNELING_STARTED_PRESS_AGAIN_CANCEL =
            Component.text("Tunneling started Press again to cancel.", NamedTextColor.GOLD);

    public static final Component EARTH_TUNNEL_TUNNELING_ENDED =
            Component.text("Tunneling ended", NamedTextColor.YELLOW);


    // --- Frost element - Freezing Circle ability ---
    public static final Component FROST_CIRCLE_FREEZING_CIRCLE_IS_ALREADY_ACTIVE =
            Component.text("Freezing Circle is already active!", NamedTextColor.RED);


    // --- Life element - Regen aura ability ---
    public static final Component LIFE_REGEN_REGEN_AURA_APPLIED_YOU_TRUSTED =
            Component.text("Regen aura applied to you and trusted allies!", NamedTextColor.GREEN);


    // --- Metal element - Chain ability ---
    public static final Component METAL_CHAIN_NO_TARGET_FOUND =
            Component.text("No target found!", NamedTextColor.RED);

    public static final Component METAL_CHAIN_YOU_CANNOT_CHAIN_TRUSTED_PLAYERS =
            Component.text("You cannot chain trusted players!", NamedTextColor.RED);


    // --- Water element - Bubble ability ---
    public static final Component WATER_BUBBLE_YOUR_WATER_BUBBLE_IS_ALREADY =
            Component.text("Your dome is already active!", NamedTextColor.RED);

    // --- /trust command ---
    public static final Component TRUST_PLAYERS_ONLY =
            Component.text("Players only");

    public static final Component TRUST_USAGE_TRUST_LIST_ADD_REMOVE =
            Component.text("Usage: /trust <list|add|remove> [player]", NamedTextColor.YELLOW);

    public static final Component TRUST_TRUSTED =
            Component.textOfChildren(
                    Component.text("Trusted: ", NamedTextColor.AQUA),
                    Component.text("(none)", NamedTextColor.WHITE)
            );

    public static Component trustTrusted2(Object displayNames) {
        return Component.textOfChildren(
                Component.text("Trusted: ", NamedTextColor.AQUA),
                Component.text(String.valueOf(displayNames), NamedTextColor.WHITE)
        );
    }

    public static Component trustUnknownPlayers(Object unknownUUIDs) {
        return Component.textOfChildren(
                Component.text("Unknown players: ", NamedTextColor.GRAY),
                Component.text(String.valueOf(unknownUUIDs), NamedTextColor.GRAY)
        );
    }

    public static final Component TRUST_USE_TRUST_REMOVE_UUID_CLEAN =
            Component.text("Use '/trust remove <uuid>' to clean up invalid entries", NamedTextColor.GRAY);

    public static final Component TRUST_USAGE_TRUST_ADD_PLAYER =
            Component.text("Usage: /trust add <player>", NamedTextColor.RED);

    public static final Component TRUST_PLAYER_NOT_FOUND =
            Component.text("Player not found", NamedTextColor.RED);

    public static final Component TRUST_YOU_CANNOT_TRUST_YOURSELF =
            Component.text("You cannot trust yourself", NamedTextColor.RED);

    public static final Component TRUST_YOU_ARE_ALREADY_MUTUALLY_TRUSTED =
            Component.text("You are already mutually trusted.", NamedTextColor.YELLOW);

    public static Component trustSentTrustRequest(Object name) {
        return Component.textOfChildren(
                Component.text("Sent trust request to ", NamedTextColor.GREEN),
                Component.text(String.valueOf(name), NamedTextColor.GREEN)
        );
    }

    public static final Component TRUST_USAGE_TRUST_ACCEPT_PLAYER_UUID =
            Component.text("Usage: /trust accept <player|uuid>", NamedTextColor.RED);

    public static final Component TRUST_NO_PENDING_REQUEST_FROM_THAT =
            Component.text("No pending request from that player.", NamedTextColor.YELLOW);

    public static final Component TRUST_YOU_ARE_NOW_MUTUALLY_TRUSTED =
            Component.text("You are now mutually trusted.", NamedTextColor.GREEN);

    public static Component trustAcceptedYourTrustRequest(Object name) {
        return Component.textOfChildren(
                Component.text(String.valueOf(name), NamedTextColor.GREEN),
                Component.text(" accepted your trust request.", NamedTextColor.GREEN)
        );
    }

    public static final Component TRUST_USAGE_TRUST_DENY_PLAYER_UUID =
            Component.text("Usage: /trust deny <player|uuid>", NamedTextColor.RED);

    public static final Component TRUST_DENIED_TRUST_REQUEST =
            Component.text("Denied trust request.", NamedTextColor.YELLOW);

    public static Component trustDeniedYourTrustRequest(Object name) {
        return Component.textOfChildren(
                Component.text(String.valueOf(name), NamedTextColor.RED),
                Component.text(" denied your trust request.", NamedTextColor.RED)
        );
    }

    public static final Component TRUST_USAGE_TRUST_REMOVE_PLAYER =
            Component.text("Usage: /trust remove <player>", NamedTextColor.RED);

    public static final Component TRUST_PLAYER_MUST_BE_ONLINE_OR =
            Component.text("Player must be online or provide UUID", NamedTextColor.RED);

    public static final Component TRUST_REMOVED_MUTUAL_TRUST =
            Component.text("Removed mutual trust.", NamedTextColor.YELLOW);


    // --- /util command ---
    public static final Component UTIL_THIS_COMMAND_CAN_ONLY_BE =
            Component.text("This command can only be used by players.", NamedTextColor.RED);

    public static final Component UTIL_YOU_DON_T_HAVE_PERMISSION =
            Component.text("You don't have permission to use this command.", NamedTextColor.RED);

    public static final Component UTIL_YOU_HAVE_BEEN_GIVEN_UTILITY =
            Component.text("You have been given utility items!", NamedTextColor.GREEN);

    public static final Component UTIL_64X_UPGRADER_I =
            Component.text("• 64x Upgrader I", NamedTextColor.YELLOW);

    public static final Component UTIL_64X_UPGRADER_II =
            Component.text("• 64x Upgrader II", NamedTextColor.YELLOW);

    public static final Component UTIL_64X_REROLLER =
            Component.text("• 64x Reroller", NamedTextColor.YELLOW);

    public static final Component UTIL_64X_ADVANCED_REROLLER =
            Component.text("• 64x Advanced Reroller", NamedTextColor.DARK_PURPLE);


    // --- /element config command ---
    public static final Component CONFIG_CONFIGURATION_RELOADED_SUCCESSFULLY =
            Component.text("Configuration reloaded successfully!", NamedTextColor.GREEN);

    public static final Component CONFIG_USAGE_ELEMENT_CONFIG_SET_KEY =
            Component.text("Usage: /element config set <key> <value>", NamedTextColor.RED);

    public static Component configSet(Object key, Object value) {
        return Component.textOfChildren(
                Component.text("Set ", NamedTextColor.GREEN),
                Component.text(String.valueOf(key), NamedTextColor.GREEN),
                Component.text(" to ", NamedTextColor.GREEN),
                Component.text(String.valueOf(value), NamedTextColor.GREEN)
        );
    }

    public static Component configErrorSettingValue(Object message) {
        return Component.textOfChildren(
                Component.text("Error setting value: ", NamedTextColor.RED),
                Component.text(String.valueOf(message), NamedTextColor.RED)
        );
    }

    public static Component configUnknownAction(Object action) {
        return Component.textOfChildren(
                Component.text("Unknown action: ", NamedTextColor.RED),
                Component.text(String.valueOf(action), NamedTextColor.RED)
        );
    }

    public static final Component CONFIG_CONFIGURATION_RESET_DEFAULT_VALUES =
            Component.text("Configuration reset to default values!", NamedTextColor.GREEN);

    public static Component configReset2(Object key, Object key2) {
        return Component.textOfChildren(
                Component.text("Reset ", NamedTextColor.GREEN),
                Component.text(String.valueOf(key), NamedTextColor.GREEN),
                Component.text(" to default (", NamedTextColor.GREEN),
                Component.text(String.valueOf(key2), NamedTextColor.GREEN),
                Component.text(").", NamedTextColor.GREEN)
        );
    }

    public static Component configNoDefaultValueExists2(Object key) {
        return Component.textOfChildren(
                Component.text("No default value exists for '", NamedTextColor.RED),
                Component.text(String.valueOf(key), NamedTextColor.RED),
                Component.text("'.", NamedTextColor.RED)
        );
    }

    public static final Component CONFIG_USAGE_ELEMENT_CONFIG_ACTION =
            Component.text("Usage: /element config <action>", NamedTextColor.RED);

    public static final Component CONFIG_ACTIONS =
            Component.text("Actions:", NamedTextColor.GRAY);

    public static final Component CONFIG_RELOAD =
            Component.text("  reload", NamedTextColor.GRAY);

    public static final Component CONFIG_RESET_KEY_OMIT_KEY_RESET =
            Component.text("  reset [key]  -  omit key to reset everything", NamedTextColor.GRAY);

    public static final Component CONFIG_SET_KEY_VALUE =
            Component.text("  set <key> <value>", NamedTextColor.GRAY);


    // --- /element debug command ---
    public static final Component DEBUG_USAGE_ELEMENT_DEBUG_PLAYER =
            Component.text("Usage: /element debug [player]", NamedTextColor.RED);

    public static Component debugPlayer(Object args) {
        return Component.textOfChildren(
                Component.text("Player '", NamedTextColor.RED),
                Component.text(String.valueOf(args), NamedTextColor.RED),
                Component.text("' not found.", NamedTextColor.RED)
        );
    }

    public static Component debugElementDebug(Object name) {
        return Component.textOfChildren(
                Component.text("=== Element Debug: ", NamedTextColor.GOLD),
                Component.text(String.valueOf(name), NamedTextColor.GOLD),
                Component.text(" ===", NamedTextColor.GOLD)
        );
    }

    public static Component debugOnlineStatus(boolean online) {
        return online
                ? Component.text("Status: ONLINE", NamedTextColor.GREEN)
                : Component.text("Status: OFFLINE (only persisted data is available below)", NamedTextColor.YELLOW);
    }

    /** Section divider, e.g. "--- Persisted Data ---". */
    public static Component debugSection(String title) {
        return Component.text("--- " + title + " ---", NamedTextColor.AQUA);
    }

    public static Component debugElementmanagerReportsBuiltinType(Object builtinType) {
        return Component.textOfChildren(
                Component.text("ElementManager reports (element): ", NamedTextColor.YELLOW),
                Component.text(String.valueOf(builtinType), NamedTextColor.YELLOW)
        );
    }

    public static Component debugAfterCacheInvalidation(Object reloadedElement) {
        return Component.textOfChildren(
                Component.text("After cache invalidation: ", NamedTextColor.YELLOW),
                Component.text(String.valueOf(reloadedElement), NamedTextColor.YELLOW)
        );
    }

    public static Component debugDisplayName(Object displayName) {
        return Component.textOfChildren(
                Component.text("Display name: ", NamedTextColor.YELLOW),
                Component.text(String.valueOf(displayName), NamedTextColor.YELLOW)
        );
    }

    public static Component debugUpgradeLevel(int level) {
        return Component.textOfChildren(
                Component.text("Upgrade level: ", NamedTextColor.YELLOW),
                Component.text(String.valueOf(level), NamedTextColor.YELLOW)
        );
    }

    /** One line per ability: name/id, its configured cooldown, and its current live status against {@code CooldownManager}. */
    public static Component debugAbilityStatus(int slot, String name, String abilityId, boolean ready,
                                               long remainingSeconds, int cooldownSeconds) {
        Component status = ready
                ? Component.text("READY", NamedTextColor.GREEN)
                : Component.textOfChildren(
                Component.text("ON COOLDOWN (", NamedTextColor.RED),
                Component.text(remainingSeconds + "s", NamedTextColor.RED),
                Component.text(" left)", NamedTextColor.RED)
        );
        return Component.textOfChildren(
                Component.text("Ability " + slot + " '" + name + "' [" + abilityId + ", " + cooldownSeconds + "s cd]: ", NamedTextColor.YELLOW),
                status
        );
    }

    public static final Component DEBUG_NO_ABILITIES_ELEMENT =
            Component.text("No element assigned - abilities unavailable.", NamedTextColor.RED);

    public static Component debugDisarmed(boolean disarmed) {
        return Component.textOfChildren(
                Component.text("Ability disarmed: ", NamedTextColor.YELLOW),
                disarmed ? Component.text("YES", NamedTextColor.RED) : Component.text("NO", NamedTextColor.GREEN)
        );
    }

    public static Component debugActiveStatusEffects(String summary) {
        return Component.textOfChildren(
                Component.text("Active status effects: ", NamedTextColor.YELLOW),
                Component.text(summary, NamedTextColor.YELLOW)
        );
    }

    /**
     * The player's currently-active passive potion effects - i.e. the ones an
     * element's {@code applyUpsides} actually granted them right now, distinct
     * from {@link #debugActiveStatusEffects}, which covers combat status effects
     * like SLOW/STUN/BURN.
     */
    public static Component debugActivePassiveEffects(String summary) {
        return Component.textOfChildren(
                Component.text("Active passive effects: ", NamedTextColor.YELLOW),
                Component.text(summary, NamedTextColor.YELLOW)
        );
    }

    public static Component debugTrustedNames(String joined, int liveCount) {
        return Component.textOfChildren(
                Component.text("Trusted players (" + liveCount + "): ", NamedTextColor.YELLOW),
                Component.text(joined, NamedTextColor.YELLOW)
        );
    }

    /**
     * Only shown when {@code TrustManager}'s live in-memory cache and the raw
     * stored trust list disagree - a real cache-drift bug (something changed
     * trust without invalidating one side), not a normal debug line.
     */
    public static Component debugTrustCacheMismatch(int liveCount, int storedCount) {
        return Component.text(
                "WARNING: TrustManager cache (" + liveCount + ") disagrees with stored data (" + storedCount + ")!",
                NamedTextColor.RED
        );
    }

    public static Component debugPendingRefunds(int basic, int advanced) {
        return Component.textOfChildren(
                Component.text("Pending reroller refunds: ", NamedTextColor.YELLOW),
                Component.text("basic=" + basic + ", advanced=" + advanced, NamedTextColor.YELLOW)
        );
    }

    public static Component debugRerolling(boolean rolling) {
        return Component.textOfChildren(
                Component.text("Currently rerolling: ", NamedTextColor.YELLOW),
                rolling ? Component.text("YES", NamedTextColor.GREEN) : Component.text("NO", NamedTextColor.GRAY)
        );
    }

    public static Component debugGameMode(Object mode) {
        return Component.textOfChildren(
                Component.text("Game mode: ", NamedTextColor.YELLOW),
                Component.text(String.valueOf(mode), NamedTextColor.YELLOW)
        );
    }

    public static final Component DEBUG_LIVE_STATE_UNAVAILABLE =
            Component.text("Player is offline - ability/status data unavailable.", NamedTextColor.GRAY);

    public static final Component DEBUG_FOOTER =
            Component.text("=== End Debug ===", NamedTextColor.GOLD);


    // --- /element particles command ---
    public static final Component PARTICLES_THIS_COMMAND_CAN_ONLY_BE =
            Component.text("This command can only be used by players.", NamedTextColor.RED);

    public static Component particlesPlayingParticlePreset(Object key) {
        return Component.textOfChildren(
                Component.text("Playing particle preset: ", NamedTextColor.GREEN),
                Component.text(String.valueOf(key), NamedTextColor.AQUA)
        );
    }

    public static final Component PARTICLES_ELEMENT_PARTICLE_PRESETS =
            Component.text("=== Element Particle Presets ===", NamedTextColor.GOLD);

    public static Component particlesAvailable(Object getNames) {
        return Component.textOfChildren(
                Component.text("Available: ", NamedTextColor.YELLOW),
                Component.text(String.valueOf(getNames), NamedTextColor.YELLOW)
        );
    }

    public static final Component PARTICLES_USAGE_ELEMENT_PARTICLES_PRESET_SIZE =
            Component.text("Usage: /element particles <preset> [size] [length] [width] [color] [particle]", NamedTextColor.GRAY);

    public static final Component PARTICLES_ALSO_SUPPORTED_SIZE_2_5 =
            Component.text("Also supported: size=2.5 length=0 width=36 color=red particle=dust", NamedTextColor.GRAY);

    public static final Component PARTICLES_EXAMPLE_ELEMENT_PARTICLES_CIRCLE_2 =
            Component.text("Example: /element particles circle 2.5 0 36 red dust", NamedTextColor.GRAY);


    // --- /element set command ---
    public static final Component SET_USAGE_ELEMENT_SET_PLAYER_ELEMENT =
            Component.text("Usage: /element set <player> <element>", NamedTextColor.RED);

    public static Component setPlayer(Object args) {
        return Component.textOfChildren(
                Component.text("Player '", NamedTextColor.RED),
                Component.text(String.valueOf(args), NamedTextColor.RED),
                Component.text("' not found.", NamedTextColor.RED)
        );
    }

    public static Component setInvalidElementValid(Object elementManager) {
        return Component.textOfChildren(
                Component.text("Invalid element. Valid: ", NamedTextColor.RED),
                Component.text(String.valueOf(elementManager), NamedTextColor.RED)
        );
    }

    public static Component setSet(Object name, Object displayName) {
        return Component.textOfChildren(
                Component.text("Set ", NamedTextColor.GREEN),
                Component.text(String.valueOf(name), NamedTextColor.GREEN),
                Component.text("'s element to ", NamedTextColor.GREEN),
                legacy(displayName, NamedTextColor.AQUA)
        );
    }

    public static Component setYourElementHasBeenSet(Object displayName) {
        return Component.textOfChildren(
                Component.text("Your element has been set to ", NamedTextColor.GREEN),
                legacy(displayName, NamedTextColor.AQUA),
                Component.text(" by an admin.", NamedTextColor.GREEN)
        );
    }


    // --- BaseElement upgrade/mana gating ---
    public static final Component BASE_ELEMENT_YOU_NEED_UPGRADE_I_BEFORE =
            Component.text("You need Upgrade I before you can use Upgrade II abilities.", NamedTextColor.RED);

    public static Component baseElementYouNeedUpgrade(Object II) {
        return Component.textOfChildren(
                Component.text("You need Upgrade ", NamedTextColor.RED),
                Component.text(String.valueOf(II), NamedTextColor.RED),
                Component.text(" to use this ability.", NamedTextColor.RED)
        );
    }


    // --- Upgrader item handler ---
    public static final Component UPGRADER_NO_ELEMENT_YET =
            Component.text("You don't have an element yet.", NamedTextColor.RED);

    public static final Component UPGRADER_YOU_ALREADY_HAVE_UPGRADE_I =
            Component.text("You already have Upgrade I", NamedTextColor.RED);

    public static final Component UPGRADER_YOU_HAVE_UNLOCKED =
            Component.textOfChildren(
                    Component.text("You have unlocked ", NamedTextColor.GREEN),
                    Component.text("Upgrade I", NamedTextColor.GOLD)
            );

    public static final Component UPGRADER_YOU_NEED_UPGRADE_I_BEFORE =
            Component.text("You need Upgrade I before you can use Upgrade II!", NamedTextColor.RED);

    public static final Component UPGRADER_YOU_ALREADY_HAVE_UPGRADE_II =
            Component.text("You already have Upgrade II", NamedTextColor.RED);

    public static final Component UPGRADER_YOU_HAVE_UNLOCKED_2 =
            Component.textOfChildren(
                    Component.text("You have unlocked ", NamedTextColor.GREEN),
                    Component.text("Upgrade II", NamedTextColor.GOLD)
            );


    // --- Player join/quit lifecycle reroller reminders ---
    public static Component lifecycleYourElementReroller(Object was) {
        return Component.textOfChildren(
                Component.text("Your Element Reroller", NamedTextColor.YELLOW),
                Component.text(String.valueOf(was), NamedTextColor.YELLOW),
                Component.text(" refunded since your last reroll got interrupted.", NamedTextColor.YELLOW)
        );
    }

    public static Component lifecycleYourAdvancedReroller(Object was) {
        return Component.textOfChildren(
                Component.text("Your Advanced Reroller", NamedTextColor.YELLOW),
                Component.text(String.valueOf(was), NamedTextColor.YELLOW),
                Component.text(" refunded since your last reroll got interrupted.", NamedTextColor.YELLOW)
        );
    }


    // --- Element assignment/reroll manager ---
    public static Component elementManagerYourElementIsNow(Object id) {
        return Component.textOfChildren(
                Component.text("Your element is now ", NamedTextColor.GOLD),
                legacy(id, NamedTextColor.AQUA)
        );
    }

    public static final Component ELEMENT_MANAGER_YOU_ARE_ALREADY_REROLLING =
            Component.text("You are already rerolling!", NamedTextColor.RED);


    // --- Example ability template ---
    public static final Component EXAMPLE_EXAMPLE_ABILITY_DEACTIVATED =
            Component.text("Example ability deactivated", NamedTextColor.RED);

    public static final Component EXAMPLE_EXAMPLE_ABILITY_ACTIVATED =
            Component.text("Example ability activated", NamedTextColor.GREEN);


    // --- /elements command ---
    public static final Component ELEMENT_INFO_PLAYERS_ONLY =
            Component.text("Only players can use this command!", NamedTextColor.RED);


    public static final Component WATER_BUBBLE_FADED =
            Component.text("Your dome faded away.", NamedTextColor.AQUA);


    // --- Dimension travel disable ---
    public static final Component DIMENSION_TRAVEL_DISABLED =
            Component.text("Dimension is disabled.", NamedTextColor.RED);


    // --- /dimension command ---
    public static final Component DIMENSION_CMD_USAGE =
            Component.text("Usage: /dimension <enable|disable|status> <nether|end|all>", NamedTextColor.YELLOW);

    public static final Component DIMENSION_CMD_INVALID_TARGET =
            Component.text("Invalid dimension! Use: nether, end, or all", NamedTextColor.RED);

    public static Component dimensionCmdToggled(String name, boolean disabled) {
        return Component.textOfChildren(
                Component.text(name + " travel is now ", NamedTextColor.GREEN),
                disabled
                        ? Component.text("DISABLED", NamedTextColor.RED)
                        : Component.text("ENABLED", NamedTextColor.GREEN)
        );
    }

    public static Component dimensionCmdStatus(boolean netherDisabled, boolean endDisabled) {
        return Component.textOfChildren(
                Component.text("Nether: ", NamedTextColor.YELLOW),
                netherDisabled ? Component.text("DISABLED", NamedTextColor.RED) : Component.text("ENABLED", NamedTextColor.GREEN),
                Component.text("  End: ", NamedTextColor.YELLOW),
                endDisabled ? Component.text("DISABLED", NamedTextColor.RED) : Component.text("ENABLED", NamedTextColor.GREEN)
        );
    }


    // --- Grace period ---
    public static Component gracePeriodBossBarTitle(String timeLeft) {
        return Component.textOfChildren(
                Component.text("Grace Period: ", NamedTextColor.GREEN),
                Component.text(timeLeft, NamedTextColor.YELLOW)
        );
    }

    public static final Component GRACE_PERIOD_STARTED =
            Component.text("The grace period has started! PvP is disabled while it's active.", NamedTextColor.GREEN);

    public static final Component GRACE_PERIOD_ENDED =
            Component.text("The grace period has ended. PvP is now enabled!", NamedTextColor.RED);

    public static final Component GRACE_PERIOD_PVP_DISABLED =
            Component.text("PvP is disabled during the grace period!", NamedTextColor.RED);


    // --- /grace command ---
    public static final Component GRACE_CMD_USAGE =
            Component.text("Usage: /grace <start|stop|status> [duration_seconds] [hunger_protection_seconds]", NamedTextColor.YELLOW);

    public static final Component GRACE_CMD_ALREADY_ACTIVE =
            Component.text("A grace period is already active! Use /grace stop first.", NamedTextColor.RED);

    public static final Component GRACE_CMD_NOT_ACTIVE =
            Component.text("There is no grace period active.", NamedTextColor.RED);

    public static final Component GRACE_CMD_INVALID_NUMBER =
            Component.text("Duration and hunger protection must be positive whole numbers of seconds.", NamedTextColor.RED);

    public static Component baseElementAbilityOnCooldown(long seconds) {
        return Component.textOfChildren();
    }


    // --- Cooldown action bar HUD ---
    // Shown by CooldownActionBarTask once a second while at least one of the
    // player's two abilities is on cooldown; ability names are truncated to a
    // fixed prefix length so a long ability name can't push the other side of
    // the bar off-screen or make it flicker as it re-renders each tick.
    private static final int COOLDOWN_BAR_NAME_MAX_LENGTH = 14;

    public static Component cooldownActionBar(String ability1Name, long ability1RemainingSeconds,
                                              String ability2Name, long ability2RemainingSeconds) {
        return Component.textOfChildren(
                cooldownBarSegment(ability1Name, ability1RemainingSeconds),
                Component.text("   ", NamedTextColor.DARK_GRAY),
                cooldownBarSegment(ability2Name, ability2RemainingSeconds)
        );
    }

    private static Component cooldownBarSegment(String abilityName, long remainingSeconds) {
        String label = truncateAbilityName(abilityName);
        if (remainingSeconds <= 0) {
            return Component.textOfChildren(
                    Component.text(label + " ", NamedTextColor.GRAY),
                    Component.text("Ready", NamedTextColor.GREEN)
            );
        }
        return Component.textOfChildren(
                Component.text(label + " ", NamedTextColor.GRAY),
                Component.text(remainingSeconds + "s", NamedTextColor.RED)
        );
    }

    private static String truncateAbilityName(String abilityName) {
        if (abilityName == null) return "";
        return abilityName.length() <= COOLDOWN_BAR_NAME_MAX_LENGTH
                ? abilityName
                : abilityName.substring(0, COOLDOWN_BAR_NAME_MAX_LENGTH - 1) + "\u2026";
    }


    public static Component graceCmdStatus(String timeLeft) {
        return Component.textOfChildren(
                Component.text("Grace period active - ", NamedTextColor.GREEN),
                Component.text(timeLeft, NamedTextColor.YELLOW),
                Component.text(" remaining.", NamedTextColor.GREEN)
        );
    }
}