package net.rose.elementSMPRefined.lang;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;

/**
 * Every player-facing message, grouped by feature. Constants are fixed messages; methods take
 * the values that vary.
 */
public final class Lang {

    private Lang() {}

    // Helpers

    private static Component green(String message) { return text(message, GREEN); }
    private static Component red(String message) { return text(message, RED); }
    private static Component yellow(String message) { return text(message, YELLOW); }
    private static Component gray(String message) { return text(message, GRAY); }

    /** Parses a name that may contain legacy colour codes; uncoloured parts get {@code fallback}. */
    private static Component legacy(Object name, NamedTextColor fallback) {
        return LegacyComponentSerializer.legacySection()
                .deserialize(String.valueOf(name))
                .colorIfAbsent(fallback);
    }

    /** "label: value", with the label and value in different colours. */
    private static Component labelled(String label, NamedTextColor labelColor, Object value, NamedTextColor valueColor) {
        return textOfChildren(text(label, labelColor), text(String.valueOf(value), valueColor));
    }

    private static Component enabledOrDisabled(boolean enabled) {
        return enabled ? green("enabled") : red("disabled");
    }

    /** "{subject} enabled." / "{subject} disabled." */
    private static Component switched(String subject, boolean enabled) {
        return text(subject + " ", GRAY)
                .append(enabledOrDisabled(enabled))
                .append(gray("."));
    }

    private static Component stateWord(boolean disabled) {
        return disabled ? red("DISABLED") : green("ENABLED");
    }

    private static Component playerNotFound(Object name) {
        return red("Player '" + name + "' not found.");
    }

    // Messages that several commands share.
    private static final Component ONLY_PLAYERS_CAN_USE = red("Only players can use this command!");
    private static final Component COMMAND_PLAYERS_ONLY = red("This command can only be used by players.");
    private static final Component INVALID_TIME = red("Invalid time! Use seconds or units, e.g. 90, 10m, 1h30m.");

    // Abilities

    public static final Component DEATH_BACKSTAB_NO_TARGET_IN_RANGE = red("No target in range.");

    public static final Component EARTH_TUNNEL_TUNNELING_CANCELLED = yellow("Tunneling cancelled");

    // Shown when the tunnel starts: no "started" text, just a hint that it can be cancelled.
    public static final Component EARTH_TUNNEL_TUNNELING_STARTED_PRESS_AGAIN_CANCEL =
            text("Press again to cancel.", GOLD);

    public static final Component EARTH_TUNNEL_TUNNELING_ENDED = yellow("Tunneling ended");

    public static final Component BASE_ELEMENT_YOU_NEED_UPGRADE_I_BEFORE =
            red("You need Upgrade I before you can use Upgrade II abilities.");

    public static Component baseElementYouNeedUpgrade(Object level) {
        return red("You need Upgrade " + level + " to use this ability.");
    }

    // /trust

    public static final Component TRUST_PLAYERS_ONLY = text("Players only");

    public static final Component TRUST_USAGE_TRUST_LIST_ADD_REMOVE =
            yellow("Usage: /trust <list|add|remove> [player]");

    public static final Component TRUST_TRUSTED = labelled("Trusted: ", AQUA, "(none)", WHITE);

    public static Component trustTrustedList(Object displayNames) {
        return labelled("Trusted: ", AQUA, displayNames, WHITE);
    }

    public static Component trustUnknownPlayers(Object unknownUUIDs) {
        return gray("Unknown players: " + unknownUUIDs);
    }

    public static final Component TRUST_USE_TRUST_REMOVE_UUID_CLEAN =
            gray("Use '/trust remove <uuid>' to clean up invalid entries");

    public static final Component TRUST_USAGE_TRUST_ADD_PLAYER = red("Usage: /trust add <player>");

    public static final Component TRUST_PLAYER_NOT_FOUND = red("Player not found");

    public static final Component TRUST_YOU_CANNOT_TRUST_YOURSELF = red("You cannot trust yourself");

    public static final Component TRUST_YOU_ARE_ALREADY_MUTUALLY_TRUSTED =
            yellow("You are already mutually trusted.");

    public static Component trustSentTrustRequest(Object name) {
        return green("Sent trust request to " + name);
    }

    public static final Component TRUST_USAGE_TRUST_ACCEPT_PLAYER_UUID =
            red("Usage: /trust accept <player|uuid>");

    public static final Component TRUST_NO_PENDING_REQUEST_FROM_THAT =
            yellow("No pending request from that player.");

    public static final Component TRUST_YOU_ARE_NOW_MUTUALLY_TRUSTED =
            green("You are now mutually trusted.");

    public static Component trustAcceptedYourTrustRequest(Object name) {
        return green(name + " accepted your trust request.");
    }

    public static final Component TRUST_USAGE_TRUST_DENY_PLAYER_UUID =
            red("Usage: /trust deny <player|uuid>");

    public static final Component TRUST_DENIED_TRUST_REQUEST = yellow("Denied trust request.");

    public static Component trustDeniedYourTrustRequest(Object name) {
        return red(name + " denied your trust request.");
    }

    public static final Component TRUST_USAGE_TRUST_REMOVE_PLAYER = red("Usage: /trust remove <player>");

    public static final Component TRUST_PLAYER_MUST_BE_ONLINE_OR =
            red("Player must be online or provide UUID");

    public static final Component TRUST_REMOVED_MUTUAL_TRUST = yellow("Removed mutual trust.");

    // /util

    public static final Component UTIL_THIS_COMMAND_CAN_ONLY_BE = COMMAND_PLAYERS_ONLY;

    public static final Component UTIL_YOU_DON_T_HAVE_PERMISSION =
            red("You don't have permission to use this command.");

    public static final Component UTIL_YOU_HAVE_BEEN_GIVEN_UTILITY =
            green("You have been given utility items!");

    public static final Component UTIL_64X_UPGRADER_I = yellow("• 64x Upgrader I");

    public static final Component UTIL_64X_UPGRADER_II = yellow("• 64x Upgrader II");

    public static final Component UTIL_64X_REROLLER = yellow("• 64x Reroller");

    public static final Component UTIL_64X_FORGED_UPGRADER = text("• 64x Forged Upgrader", DARK_PURPLE);

    // /element config

    public static final Component CONFIG_CONFIGURATION_RELOADED_SUCCESSFULLY =
            green("Configuration reloaded successfully!");

    public static final Component CONFIG_USAGE_ELEMENT_CONFIG_SET_KEY =
            red("Usage: /element config set <key> <value>");

    public static Component configSet(Object key, Object value) {
        return green("Set " + key + " to " + value);
    }

    public static Component configErrorSettingValue(Object message) {
        return red("Error setting value: " + message);
    }

    public static Component configUnknownAction(Object action) {
        return red("Unknown action: " + action);
    }

    public static final Component CONFIG_CONFIGURATION_RESET_DEFAULT_VALUES =
            green("Configuration reset to default values!");

    public static Component configKeyReset(Object key, Object defaultValue) {
        return green("Reset " + key + " to default (" + defaultValue + ").");
    }

    public static Component configNoDefaultValue(Object key) {
        return red("No default value exists for '" + key + "'.");
    }

    public static final Component CONFIG_USAGE_ELEMENT_CONFIG_ACTION =
            red("Usage: /element config <action>");

    public static final Component CONFIG_ACTIONS = gray("Actions:");

    public static final Component CONFIG_RELOAD = gray("  reload");

    public static final Component CONFIG_RESET_KEY_OMIT_KEY_RESET =
            gray("  reset [key]  -  omit key to reset everything");

    public static final Component CONFIG_SET_KEY_VALUE = gray("  set <key> <value>");

    // /element debug

    public static final Component DEBUG_USAGE_ELEMENT_DEBUG_PLAYER =
            red("Usage: /element debug [player]");

    public static Component debugPlayer(Object name) {
        return playerNotFound(name);
    }

    public static Component debugElementDebug(Object name) {
        return text("=== Element Debug: " + name + " ===", GOLD);
    }

    public static Component debugOnlineStatus(boolean online) {
        return online
                ? green("Status: ONLINE")
                : yellow("Status: OFFLINE (only persisted data is available below)");
    }

    public static Component debugSection(String title) {
        return text("--- " + title + " ---", AQUA);
    }

    public static Component debugElementmanagerReportsBuiltinType(Object builtinType) {
        return yellow("ElementManager reports (element): " + builtinType);
    }

    public static Component debugAfterCacheInvalidation(Object reloadedElement) {
        return yellow("After cache invalidation: " + reloadedElement);
    }

    public static Component debugDisplayName(Object displayName) {
        return yellow("Display name: " + displayName);
    }

    public static Component debugUpgradeLevel(int level) {
        return yellow("Upgrade level: " + level);
    }

    public static Component debugAbilityStatus(int slot, String name, String abilityId, boolean ready,
                                               long remainingSeconds, int cooldownSeconds) {
        Component status = ready
                ? green("READY")
                : red("ON COOLDOWN (" + remainingSeconds + "s left)");
        return textOfChildren(
                yellow("Ability " + slot + " '" + name + "' [" + abilityId + ", " + cooldownSeconds + "s cd]: "),
                status
        );
    }

    public static final Component DEBUG_NO_ABILITIES_ELEMENT =
            red("No element assigned - abilities unavailable.");

    public static Component debugDisarmed(boolean disarmed) {
        return textOfChildren(
                yellow("Ability disarmed: "),
                disarmed ? red("YES") : green("NO")
        );
    }

    public static Component debugActiveStatusEffects(String summary) {
        return yellow("Active status effects: " + summary);
    }

    public static Component debugActivePassiveEffects(String summary) {
        return yellow("Active passive effects: " + summary);
    }

    public static Component debugTrustedNames(String joined, int liveCount) {
        return yellow("Trusted players (" + liveCount + "): " + joined);
    }

    public static Component debugTrustCacheMismatch(int liveCount, int storedCount) {
        return red("WARNING: TrustManager cache (" + liveCount + ") disagrees with stored data (" + storedCount + ")!");
    }

    public static Component debugPendingRefunds(int basic, int advanced) {
        return yellow("Pending reroller refunds: basic=" + basic + ", advanced=" + advanced);
    }

    public static Component debugRerolling(boolean rolling) {
        return textOfChildren(
                yellow("Currently rerolling: "),
                rolling ? green("YES") : gray("NO")
        );
    }

    public static Component debugGameMode(Object mode) {
        return yellow("Game mode: " + mode);
    }

    public static final Component DEBUG_LIVE_STATE_UNAVAILABLE =
            gray("Player is offline - ability/status data unavailable.");

    public static final Component DEBUG_FOOTER = text("=== End Debug ===", GOLD);

    // /element particles

    public static final Component PARTICLES_THIS_COMMAND_CAN_ONLY_BE = COMMAND_PLAYERS_ONLY;

    public static Component particlesPlayingParticlePreset(Object key) {
        return labelled("Playing particle preset: ", GREEN, key, AQUA);
    }

    public static final Component PARTICLES_ELEMENT_PARTICLE_PRESETS =
            text("=== Element Particle Presets ===", GOLD);

    public static Component particlesAvailable(Object names) {
        return yellow("Available: " + names);
    }

    public static Component particlesPlayingAbilityEffect(Object key) {
        return labelled("Playing ability particles: ", GREEN, key, AQUA);
    }

    public static Component particlesAbilityEffects(Object names) {
        return yellow("Ability effects: " + names);
    }

    public static final Component PARTICLES_USAGE_ABILITY_EFFECT =
            gray("Usage: /element particles <ability effect>");

    public static final Component PARTICLES_USAGE_ELEMENT_PARTICLES_PRESET_SIZE =
            gray("Usage: /element particles <preset> [size] [length] [width] [color] [particle]");

    public static final Component PARTICLES_ALSO_SUPPORTED_SIZE_2_5 =
            gray("Also supported: size=2.5 length=0 width=36 color=red particle=dust");

    public static final Component PARTICLES_EXAMPLE_ELEMENT_PARTICLES_CIRCLE_2 =
            gray("Example: /element particles circle 2.5 0 36 red dust");

    // /element set

    public static final Component SET_USAGE_ELEMENT_SET_PLAYER_ELEMENT =
            red("Usage: /element set <player> <element>");

    public static Component setPlayer(Object name) {
        return playerNotFound(name);
    }

    public static Component setInvalidElementValid(Object validElements) {
        return red("Invalid element. Valid: " + validElements);
    }

    public static Component setSet(Object name, Object displayName) {
        return textOfChildren(
                green("Set " + name + "'s element to "),
                legacy(displayName, AQUA)
        );
    }

    public static Component setYourElementHasBeenSet(Object displayName) {
        return textOfChildren(
                green("Your element has been set to "),
                legacy(displayName, AQUA),
                green(" by an admin.")
        );
    }

    // Upgraders

    private static Component unlocked(String upgrade) {
        return textOfChildren(green("You have unlocked "), text(upgrade, GOLD));
    }

    public static final Component UPGRADER_NO_ELEMENT_YET = red("You don't have an element yet.");

    public static final Component UPGRADER_YOU_ALREADY_HAVE_UPGRADE_I = red("You already have Upgrade I");

    public static final Component UPGRADER_YOU_HAVE_UNLOCKED = unlocked("Upgrade I");

    public static final Component UPGRADER_YOU_NEED_UPGRADE_I_BEFORE =
            red("You need Upgrade I before you can use Upgrade II!");

    public static final Component UPGRADER_YOU_ALREADY_HAVE_UPGRADE_II = red("You already have Upgrade II");

    public static final Component UPGRADER_YOU_HAVE_UNLOCKED_2 = unlocked("Upgrade II");

    // Player lifecycle / rerolling

    public static Component lifecycleYourElementReroller(Object suffix) {
        return yellow("Your Element Reroller" + suffix + " refunded since your last reroll got interrupted.");
    }

    public static Component lifecycleYourForgedUpgrader(Object suffix) {
        return yellow("Your Forged Upgrader" + suffix + " refunded since your last reroll got interrupted.");
    }

    public static Component elementManagerYourElementIsNow(Object displayName) {
        return textOfChildren(
                text("Your element is now ", GOLD),
                legacy(displayName, AQUA)
        );
    }

    public static final Component ELEMENT_MANAGER_YOU_ARE_ALREADY_REROLLING =
            red("You are already rerolling!");

    // /autosmelt, /toggle, /elementinfo

    public static final Component AUTOSMELT_PLAYERS_ONLY = ONLY_PLAYERS_CAN_USE;

    public static final Component AUTOSMELT_WRONG_ELEMENT =
            red("Auto smelt is only available to Fire and Lava.");

    public static final Component AUTOSMELT_USAGE = yellow("Usage: /autosmelt [on|off]");

    public static final Component AUTOSMELT_ENABLED = switched("Auto smelt", true);

    public static final Component AUTOSMELT_DISABLED = switched("Auto smelt", false);

    public static final Component TOGGLE_PLAYERS_ONLY = ONLY_PLAYERS_CAN_USE;

    public static final Component TOGGLE_NO_ELEMENT =
            red("You don't have an element, so there are no passives to toggle.");

    public static final Component TOGGLE_NONE_AVAILABLE =
            yellow("Your element has no passives that can be toggled.");

    public static final Component TOGGLE_USAGE =
            yellow("Usage: /toggle [passive] [on|off]  (run /toggle to see yours)");

    public static final Component TOGGLE_LIST_HEADER = text("Passive toggles:", GOLD);

    public static Component toggleListEntry(String id, String displayName, boolean enabled) {
        return text(" - ", DARK_GRAY)
                .append(gray(displayName))
                .append(text(" (" + id + "): ", DARK_GRAY))
                .append(enabled ? green("on") : red("off"));
    }

    public static Component toggleUnknown(String input) {
        return red("Unknown passive '" + input + "'. Run /toggle to see the ones you can use.");
    }

    public static Component toggleWrongElement(String displayName, String elementNames) {
        return red(displayName + " is only available to " + elementNames + ".");
    }

    public static Component toggleChanged(String displayName, boolean enabled) {
        return switched(displayName, enabled);
    }

    public static final Component ELEMENT_INFO_PLAYERS_ONLY = ONLY_PLAYERS_CAN_USE;

    // /dimension

    public static final Component DIMENSION_TRAVEL_DISABLED = red("Dimension is disabled.");

    public static final Component DIMENSION_CMD_USAGE =
            yellow("Usage: /dimension <enable|disable|toggle> <nether|end|all> [time]  |  status  |  settings  |  set <option> <value>");

    public static final Component DIMENSION_CMD_INVALID_TARGET =
            red("Invalid dimension! Use: nether, end, or all");

    public static final Component DIMENSION_CMD_INVALID_TIME = INVALID_TIME;

    public static Component dimensionCmdToggled(String name, boolean disabled) {
        return textOfChildren(green(name + " travel is now "), stateWord(disabled));
    }

    public static Component dimensionCmdToggledTimed(String name, boolean disabled, String duration) {
        return textOfChildren(
                dimensionCmdToggled(name, disabled),
                green(" for " + duration + ".")
        );
    }

    public static Component dimensionTimerEnded(String name, boolean disabled) {
        return textOfChildren(
                yellow("The timer ran out - " + name + " travel is now "),
                stateWord(disabled)
        );
    }

    public static Component dimensionStatusLine(String name, boolean disabled, String timeLeft) {
        Component line = textOfChildren(yellow(name + ": "), stateWord(disabled));
        if (timeLeft != null) {
            line = line.append(gray(" (flips in " + timeLeft + ")"));
        }
        return line;
    }

    // Shared by /grace and /dimension "set" and "settings"

    public static Component settingsHeader(String title) {
        return text("--- " + title + " ---", GOLD);
    }

    public static Component settingLine(String name, String value) {
        return labelled(name + ": ", YELLOW, value, WHITE);
    }

    public static Component optionSet(String name, String value) {
        return textOfChildren(
                green(name + " set to "),
                yellow(value),
                green(".")
        );
    }

    public static Component optionUnknown(String name, String valid) {
        return red("Unknown option '" + name + "'. Options: " + valid);
    }

    public static Component optionInvalidValue(String name, String expected) {
        return red("Invalid value for " + name + ". Expected " + expected + ".");
    }

    public static Component optionUsage(String command) {
        return yellow("Usage: /" + command + " set <option> <value>  (see /" + command + " settings)");
    }

    // /grace

    public static Component gracePeriodBossBarTitle(String timeLeft) {
        return labelled("Grace Period: ", GREEN, timeLeft, YELLOW);
    }

    public static final Component GRACE_PERIOD_ENDED =
            red("The grace period has ended. PvP is now enabled!");

    public static final Component GRACE_PERIOD_PVP_DISABLED =
            red("PvP is disabled during the grace period!");

    public static final Component GRACE_CMD_USAGE =
            yellow("Usage: /grace <start [time] | stop | status | extend <time> | shorten <time> | settings | set <option> <value>>");

    public static final Component GRACE_CMD_ALREADY_ACTIVE =
            red("A grace period is already active! Use /grace stop first.");

    public static final Component GRACE_CMD_NOT_ACTIVE = red("There is no grace period active.");

    public static final Component GRACE_CMD_INVALID_NUMBER = INVALID_TIME;

    public static final Component GRACE_CMD_SHORTEN_TOO_FAR =
            red("That would end the grace period - use /grace stop instead.");

    public static Component graceCmdAdjusted(String change, String timeLeft) {
        return textOfChildren(
                green("Grace period " + change + ". "),
                yellow(timeLeft),
                green(" remaining.")
        );
    }

    public static Component graceCmdStatus(String timeLeft) {
        return textOfChildren(
                green("Grace period active - "),
                yellow(timeLeft),
                green(" remaining. PvP is disabled.")
        );
    }

    // Cooldown action bar

    // Shown by CooldownActionBarTask once a second while at least one of the
    // player's two abilities is on cooldown; ability names are truncated to a
    // fixed prefix length so a long ability name can't push the other side of
    // the bar off-screen or make it flicker as it re-renders each tick.
    private static final int COOLDOWN_BAR_NAME_MAX_LENGTH = 16;

    private static final int ABILITY1_REQUIRED_UPGRADE = 1;
    private static final int ABILITY2_REQUIRED_UPGRADE = 2;

    private static final Key ICON_FONT = Key.key("elementsmp", "icons");
    private static final String LOCK_GLYPH = "\uE000";

    public static Component cooldownActionBar(String ability1Name, long ability1RemainingSeconds,
                                              String ability2Name, long ability2RemainingSeconds,
                                              int upgradeLevel) {
        return textOfChildren(
                cooldownBarSegment(ability1Name, ability1RemainingSeconds, upgradeLevel < ABILITY1_REQUIRED_UPGRADE),
                text("   ", DARK_GRAY),
                cooldownBarSegment(ability2Name, ability2RemainingSeconds, upgradeLevel < ABILITY2_REQUIRED_UPGRADE)
        );
    }

    private static Component cooldownBarSegment(String abilityName, long remainingSeconds, boolean locked) {
        String label = truncateAbilityName(abilityName);

        if (locked) {
            // Upgrade not reached yet: dark grey and struck through, with a red lock.
            return textOfChildren(
                    text(label, DARK_GRAY).decoration(TextDecoration.STRIKETHROUGH, true),
                    text(" "),
                    text(LOCK_GLYPH, RED).font(ICON_FONT)
            );
        }

        Component status = remainingSeconds <= 0 ? green("Ready") : red(remainingSeconds + "s");
        return textOfChildren(gray(label), text(" "), status);
    }

    private static String truncateAbilityName(String abilityName) {
        if (abilityName == null) return "";
        return abilityName.length() <= COOLDOWN_BAR_NAME_MAX_LENGTH
                ? abilityName
                : abilityName.substring(0, COOLDOWN_BAR_NAME_MAX_LENGTH - 1) + "\u2026";
    }
}