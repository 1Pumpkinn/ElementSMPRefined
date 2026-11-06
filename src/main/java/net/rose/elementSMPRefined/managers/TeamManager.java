package net.rose.elementSMPRefined.managers;

import net.rose.elementSMPRefined.data.YamlFiles;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Teams and team-to-team alliances.
 * <p>
 * A player is in at most one team. Everyone on the same team, and everyone on a team allied with
 * it, counts as "friendly": they can't damage each other and abilities skip them (see
 * {@link #isFriendly}). The size limits come from {@code team.max_members} and
 * {@code team.max_allies} in config.yml and are read live, so changing them applies immediately.
 * Lowering a limit never removes anyone; it only stops new members/allies being added.
 * <p>
 * Teams are stored in {@code data/teams.yml}. Invites and ally requests are in-memory only and
 * expire after {@link #REQUEST_TTL_MILLIS}.
 */
public class TeamManager {

    public static final int MIN_NAME_LENGTH = 3;
    public static final int MAX_NAME_LENGTH = 16;
    private static final Pattern NAME_PATTERN =
            Pattern.compile("[A-Za-z0-9_]{" + MIN_NAME_LENGTH + "," + MAX_NAME_LENGTH + "}");

    private static final long REQUEST_TTL_MILLIS = 60_000L;
    private static final int BACKUP_KEEP = 30;
    private static final long BACKUP_MIN_AGE_MINUTES = 30;

    public enum Result {
        OK,
        INVALID_NAME,
        NAME_TAKEN,
        IN_TEAM,
        TEAM_FULL,
        SAME_TEAM,
        ALREADY_ALLIED,
        ALLY_LIMIT_SELF,
        ALLY_LIMIT_OTHER
    }

    /** One team. Read it through the getters; only {@link TeamManager} changes it. */
    public static final class Team {
        private final String id;
        private final String name;
        private UUID owner;
        private final Set<UUID> members = ConcurrentHashMap.newKeySet();
        private final Set<String> allies = ConcurrentHashMap.newKeySet();

        private Team(String name, UUID owner) {
            this.id = idOf(name);
            this.name = name;
            this.owner = owner;
            this.members.add(owner);
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public UUID getOwner() {
            return owner;
        }

        public boolean isOwner(UUID uuid) {
            return owner.equals(uuid);
        }

        public int getSize() {
            return members.size();
        }

        public Set<UUID> getMembers() {
            return new HashSet<>(members);
        }

        public Set<String> getAllyIds() {
            return new HashSet<>(allies);
        }
    }

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final File file;

    private final Map<String, Team> teams = new ConcurrentHashMap<>(); // team id -> team
    private final Map<UUID, String> memberTeam = new ConcurrentHashMap<>(); // player -> team id
    private final Map<UUID, Map<String, Long>> invites =
            new ConcurrentHashMap<>(); // invitee -> team id -> expiry
    private final Map<String, Map<String, Long>> allyRequests =
            new ConcurrentHashMap<>(); // target team id -> requesting team id -> expiry
    private final Set<UUID> teamChat =
            ConcurrentHashMap.newKeySet(); // players whose chat goes to their team only
    private boolean loadFailed = false;

    public TeamManager(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;

        File dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            throw new IllegalStateException(
                    "Could not create data directory: " + dataDir.getAbsolutePath());
        }
        this.file = new File(dataDir, "teams.yml");
        load();

        try {
            YamlFiles.backup(
                    file,
                    new File(dataDir, "backups"),
                    "teams",
                    BACKUP_KEEP,
                    BACKUP_MIN_AGE_MINUTES);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not back up teams.yml", e);
        }
    }

    private static String idOf(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    // Limits

    public int getMaxMembers() {
        return config.getTeamMaxMembers();
    }

    public int getMaxAllies() {
        return config.getTeamMaxAllies();
    }

    // Lookups

    public Team getTeam(UUID player) {
        String id = player == null ? null : memberTeam.get(player);
        return id == null ? null : teams.get(id);
    }

    public Team getTeamByName(String name) {
        return name == null ? null : teams.get(idOf(name));
    }

    public List<Team> getAllTeams() {
        List<Team> out = new ArrayList<>(teams.values());
        out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    /** Teams allied with {@code team}, sorted by name. */
    public List<Team> getAllies(Team team) {
        List<Team> out = new ArrayList<>();
        for (String id : team.allies) {
            Team ally = teams.get(id);
            if (ally != null) out.add(ally);
        }
        out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    /**
     * True when the two different players are on the same team or on allied teams. This is the
     * check every damage and ability-targeting path uses, so it's a couple of map lookups.
     */
    public boolean isFriendly(UUID a, UUID b) {
        if (a == null || b == null || a.equals(b)) return false;
        String teamA = memberTeam.get(a);
        if (teamA == null) return false;
        String teamB = memberTeam.get(b);
        if (teamB == null) return false;
        if (teamA.equals(teamB)) return true;
        Team team = teams.get(teamA);
        return team != null && team.allies.contains(teamB);
    }

    public static boolean isValidName(String name) {
        return name != null && NAME_PATTERN.matcher(name).matches();
    }

    // Teams

    public Result create(UUID owner, String name) {
        if (!isValidName(name)) return Result.INVALID_NAME;
        if (memberTeam.containsKey(owner)) return Result.IN_TEAM;
        Team team = new Team(name, owner);
        if (teams.putIfAbsent(team.id, team) != null) return Result.NAME_TAKEN;
        memberTeam.put(owner, team.id);
        save();
        return Result.OK;
    }

    /** Deletes the team, its alliances and every pending invite/request that mentions it. */
    public void disband(Team team) {
        teams.remove(team.id);
        for (UUID member : team.members) {
            memberTeam.remove(member, team.id);
            teamChat.remove(member);
        }
        for (Team other : teams.values()) {
            other.allies.remove(team.id);
        }
        for (Map<String, Long> perPlayer : invites.values()) perPlayer.remove(team.id);
        allyRequests.remove(team.id);
        for (Map<String, Long> perTeam : allyRequests.values()) perTeam.remove(team.id);
        save();
    }

    public Result addMember(Team team, UUID player) {
        if (memberTeam.containsKey(player)) return Result.IN_TEAM;
        if (team.members.size() >= getMaxMembers()) return Result.TEAM_FULL;
        team.members.add(player);
        memberTeam.put(player, team.id);
        invites.remove(player);
        save();
        return Result.OK;
    }

    /** Removes a non-owner member (works offline). The owner has to transfer or disband instead. */
    public boolean removeMember(Team team, UUID player) {
        if (team.isOwner(player) || !team.members.remove(player)) return false;
        memberTeam.remove(player, team.id);
        teamChat.remove(player);
        save();
        return true;
    }

    public boolean transferOwner(Team team, UUID newOwner) {
        if (!team.members.contains(newOwner) || team.isOwner(newOwner)) return false;
        team.owner = newOwner;
        save();
        return true;
    }

    // Alliances

    /** Allies the two teams (both ways) if neither is at the {@code team.max_allies} limit. */
    public Result addAlly(Team a, Team b) {
        if (a == b) return Result.SAME_TEAM;
        if (a.allies.contains(b.id)) return Result.ALREADY_ALLIED;
        int max = getMaxAllies();
        if (a.allies.size() >= max) return Result.ALLY_LIMIT_SELF;
        if (b.allies.size() >= max) return Result.ALLY_LIMIT_OTHER;
        a.allies.add(b.id);
        b.allies.add(a.id);
        clearAllyRequest(a, b);
        clearAllyRequest(b, a);
        save();
        return Result.OK;
    }

    public boolean removeAlly(Team a, Team b) {
        boolean changed = a.allies.remove(b.id) | b.allies.remove(a.id);
        if (changed) save();
        return changed;
    }

    // Invites (team -> player)

    public void addInvite(UUID target, Team team) {
        invites.computeIfAbsent(target, k -> new ConcurrentHashMap<>()).put(team.id, expiry());
    }

    public boolean hasInvite(UUID target, Team team) {
        return live(invites.get(target), team.id);
    }

    public void clearInvite(UUID target, Team team) {
        Map<String, Long> perPlayer = invites.get(target);
        if (perPlayer != null) perPlayer.remove(team.id);
    }

    /** Teams that currently have an unexpired invite out to {@code target}. */
    public List<Team> getInvites(UUID target) {
        return liveTeams(invites.get(target));
    }

    // Ally requests (team -> team)

    public void addAllyRequest(Team to, Team from) {
        allyRequests.computeIfAbsent(to.id, k -> new ConcurrentHashMap<>()).put(from.id, expiry());
    }

    public boolean hasAllyRequest(Team to, Team from) {
        return live(allyRequests.get(to.id), from.id);
    }

    public void clearAllyRequest(Team to, Team from) {
        Map<String, Long> perTeam = allyRequests.get(to.id);
        if (perTeam != null) perTeam.remove(from.id);
    }

    /** Teams that currently have an unexpired ally request out to {@code to}. */
    public List<Team> getAllyRequests(Team to) {
        return liveTeams(allyRequests.get(to.id));
    }

    private static long expiry() {
        return System.currentTimeMillis() + REQUEST_TTL_MILLIS;
    }

    private static boolean live(Map<String, Long> requests, String id) {
        if (requests == null) return false;
        Long expires = requests.get(id);
        if (expires == null) return false;
        if (expires < System.currentTimeMillis()) {
            requests.remove(id);
            return false;
        }
        return true;
    }

    private List<Team> liveTeams(Map<String, Long> requests) {
        List<Team> out = new ArrayList<>();
        if (requests == null) return out;
        for (String id : new ArrayList<>(requests.keySet())) {
            Team team = live(requests, id) ? teams.get(id) : null;
            if (team != null) out.add(team);
        }
        out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    /** Invites only make sense while the player is around, so drop theirs when they leave. */
    public void onPlayerQuit(UUID uuid) {
        invites.remove(uuid);
        teamChat.remove(uuid);
    }

    /** True while the player's normal chat messages are sent to their team only. */
    public boolean isTeamChat(UUID player) {
        return player != null && teamChat.contains(player);
    }

    public void setTeamChat(UUID player, boolean enabled) {
        if (enabled) teamChat.add(player);
        else teamChat.remove(player);
    }

    /** Flips team chat for the player and returns the new state. Team chat is not saved across restarts. */
    public boolean toggleTeamChat(UUID player) {
        if (teamChat.remove(player)) return false;
        teamChat.add(player);
        return true;
    }

    // Names

    /** Last known name of a player, or null if the server has never seen them. */
    public String getName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid); // lookup by UUID: no network call
        return offline.getName();
    }

    /** Member names (online or offline, owner included), sorted; unknown players show their UUID. */
    public List<String> getMemberNames(Team team) {
        List<String> out = new ArrayList<>();
        for (UUID u : team.members) {
            String name = getName(u);
            out.add(name != null ? name : u.toString());
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /**
     * Resolves what a player typed (a raw UUID, an online player's exact name, or the name of an
     * offline member) to a member of {@code team}. Never does a Mojang lookup. Null if no match.
     */
    public UUID findMember(Team team, String input) {
        try {
            UUID uuid = UUID.fromString(input);
            return team.members.contains(uuid) ? uuid : null;
        } catch (IllegalArgumentException ignored) {
            // not a UUID, try names
        }
        String wanted = input.toLowerCase(Locale.ROOT);
        for (UUID u : team.members) {
            String name = getName(u);
            if (name != null && name.toLowerCase(Locale.ROOT).equals(wanted)) return u;
        }
        return null;
    }

    // Storage

    private void load() {
        YamlConfiguration yaml;
        try {
            yaml = YamlFiles.loadStrict(file);
        } catch (IOException | InvalidConfigurationException e) {
            loadFailed = true;
            plugin.getLogger()
                    .log(
                            Level.SEVERE,
                            "teams.yml could not be read - teams will NOT be saved until it is"
                                + " fixed (nothing has been overwritten). Fix or restore it from"
                                + " data/backups/ and restart.",
                            e);
            try {
                plugin.getLogger()
                        .severe(
                                "Copied the unreadable file to "
                                        + YamlFiles.quarantine(file).getName());
            } catch (IOException ioe) {
                plugin.getLogger().log(Level.WARNING, "Could not quarantine teams.yml", ioe);
            }
            return;
        }

        ConfigurationSection root = yaml.getConfigurationSection("teams");
        if (root == null) return;

        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) continue;
            String name = section.getString("name", key);
            UUID owner = parseUuid(section.getString("owner"));
            if (owner == null || !isValidName(name) || memberTeam.containsKey(owner)) {
                plugin.getLogger().warning("Skipping invalid team '" + key + "' in teams.yml");
                continue;
            }
            Team team = new Team(name, owner);
            for (String raw : section.getStringList("members")) {
                UUID member = parseUuid(raw);
                if (member != null && !memberTeam.containsKey(member)) {
                    team.members.add(member);
                }
            }
            team.allies.addAll(section.getStringList("allies"));
            teams.put(team.id, team);
            for (UUID member : team.members) memberTeam.put(member, team.id);
        }

        // Alliances must be mutual and point at teams that still exist.
        for (Team team : teams.values()) {
            team.allies.removeIf(
                    id -> {
                        Team other = teams.get(id);
                        return other == null || other == team || !other.allies.contains(team.id);
                    });
        }
    }

    private static UUID parseUuid(String raw) {
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null; // corrupt entry - skip rather than fail the whole load
        }
    }

    private synchronized void save() {
        if (loadFailed) return; // never overwrite a file we couldn't read
        YamlConfiguration yaml = new YamlConfiguration();
        for (Team team : getAllTeams()) {
            String path = "teams." + team.id;
            yaml.set(path + ".name", team.name);
            yaml.set(path + ".owner", team.owner.toString());
            yaml.set(
                    path + ".members", team.members.stream().map(UUID::toString).sorted().toList());
            yaml.set(path + ".allies", team.allies.stream().sorted().toList());
        }
        try {
            YamlFiles.saveAtomic(yaml, file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save teams.yml to disk", e);
        }
    }
}
