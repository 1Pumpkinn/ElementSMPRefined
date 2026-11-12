package net.rose.elementevents.lifedeath;

import io.papermc.paper.dialog.Dialog;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementSMPRefined.data.YamlFiles;
import net.rose.elementSMPRefined.util.parse.ParseUtil;
import net.rose.elementevents.mythic.MythicBook;
import net.rose.elementevents.mythic.MythicBook.Kind;
import net.rose.elementevents.mythic.MythicBookProtection;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The Life &amp; Death event.
 * <ul>
 *   <li>Players kill hostile mobs to earn <b>Death</b> points and passive mobs to earn <b>Life</b>
 *       points (see {@link LifeDeathListener} for what counts as which).</li>
 *   <li>When the timer runs out (or {@code /element event end}) the player with the most Death
 *       points receives the {@link Kind#DEATH} book and the player with the most Life points the
 *       {@link Kind#LIFE} book. Ties go to whoever reached the score first. A player who tops both
 *       categories wins both books.</li>
 *   <li>The book goes into the winner's inventory; if it is full it is dropped at their feet
 *       (protected by {@link MythicBookProtection}). A winner who is offline gets it the next time
 *       they join - those are kept in {@code life_death_pending.yml}.</li>
 * </ul>
 * Scores live in memory only: a restart mid-event cancels it (pending books are kept).
 */
public class LifeDeathEvent {
    private static final String PENDING_FILE = "life_death_pending.yml";
    static final Component TITLE =
            MiniMessage.miniMessage()
                    .deserialize(
                            "<bold><white>Life & Death Event</white></bold>");

    private static final class Score {
        final int[] counts = new int[Kind.values().length];
        final long[] reachedAt = new long[Kind.values().length];
    }

    private final ElementSMPRefined plugin;
    private final File pendingFile;
    private final Map<UUID, Score> scores = new LinkedHashMap<>();

    private boolean active;
    private int totalSeconds;
    private int remainingSeconds;
    private BossBar bossBar;
    private BukkitTask task;

    public LifeDeathEvent(ElementSMPRefined plugin) {
        this.plugin = plugin;
        this.pendingFile = new File(plugin.getDataFolder(), PENDING_FILE);
    }

    // ---- state ------------------------------------------------------------------------------

    public boolean isActive() {
        return active;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public int defaultDurationSeconds() {
        return Math.max(1, plugin.getConfig().getInt("events.life_death.duration_seconds", 1800));
    }

    /** @return false if an event is already running. */
    public boolean start(int durationSeconds) {
        if (active) return false;
        scores.clear();
        active = true;
        totalSeconds = Math.max(1, durationSeconds);
        remainingSeconds = totalSeconds;

        bossBar =
                BossBar.bossBar(
                        TITLE, 1.0f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        Bukkit.getOnlinePlayers().forEach(p -> p.showBossBar(bossBar));
        task =
                plugin.getTaskScheduler()
                        .runTimer(
                                this::tick,
                                Constants.Timing.TICKS_PER_SECOND,
                                Constants.Timing.TICKS_PER_SECOND);

        Bukkit.broadcast(
                Component.text("The Life & Death event has begun! ", NamedTextColor.GOLD)
                        .append(
                                Component.text(
                                        "Kill hostile mobs for Death, passive mobs for Life. "
                                                + "Ends in "
                                                + ParseUtil.formatCompact(totalSeconds)
                                                + ".",
                                        NamedTextColor.YELLOW)));
        return true;
    }

    /** Ends the event now and hands out the books. @return false if none was running. */
    public boolean end() {
        if (!active) return false;
        List<String> lines = new ArrayList<>();
        UUID[] winners = pickWinners();
        shutdown();
        // A mythic element can only be held by one player: don't hand out a book for one that is
        // already claimed.
        for (Kind kind : Kind.values()) {
            if (winners[kind.ordinal()] != null
                    && plugin.getMythicElements().isClaimed(kind.element())) {
                plugin.getLogger()
                        .info(
                                "Life & Death: the "
                                        + kind.elementName()
                                        + " is already claimed - no book awarded.");
                winners[kind.ordinal()] = null;
            }
        }

        for (Kind kind : Kind.values()) {
            UUID winner = winners[kind.ordinal()];
            if (winner == null) {
                lines.add(kind.id() + ": nobody scored");
                continue;
            }
            award(winner, kind);
            lines.add(kind.id() + ": " + nameOf(winner));
        }
        Bukkit.broadcast(Component.text("The Life & Death event has ended!", NamedTextColor.GOLD));
        Map<Kind, String> winnerNames = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            UUID winner = winners[kind.ordinal()];
            winnerNames.put(kind, winner == null ? null : nameOf(winner));
        }
        Dialog results = LifeDeathDialogs.results(allStandings(), winnerNames);
        Bukkit.getOnlinePlayers().forEach(p -> p.showDialog(results));
        plugin.getLogger().info("Life & Death event ended - " + String.join(", ", lines));
        scores.clear();
        return true;
    }

    /** Stops the event without awarding anything. @return false if none was running. */
    public boolean cancel() {
        if (!active) return false;
        shutdown();
        scores.clear();
        Bukkit.broadcast(
                Component.text("The Life & Death event was cancelled.", NamedTextColor.RED));
        return true;
    }

    /** Hides the boss bar and stops the timer. Idempotent; safe on plugin disable. */
    public void cleanup() {
        shutdown();
    }

    private void shutdown() {
        active = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (bossBar != null) {
            Bukkit.getOnlinePlayers().forEach(p -> p.hideBossBar(bossBar));
            bossBar = null;
        }
    }

    private void tick() {
        remainingSeconds--;
        if (remainingSeconds <= 0) {
            end();
            return;
        }
        if (bossBar != null) {
            bossBar.progress(Math.max(0f, Math.min(1f, (float) remainingSeconds / totalSeconds)));
        }
    }

    /** Shows the boss bar to someone who joined mid-event. */
    public void showTo(Player player) {
        if (active && bossBar != null) player.showBossBar(bossBar);
    }

    // ---- scoring ----------------------------------------------------------------------------

    public void recordKill(Player killer, Kind kind) {
        if (!active) return;
        Score score = scores.computeIfAbsent(killer.getUniqueId(), id -> new Score());
        int i = kind.ordinal();
        score.counts[i]++;
        score.reachedAt[i] = System.nanoTime();
    }

    /** Opens the current standings in a dialog for {@code viewer}. */
    public void showStandings(Player viewer) {
        viewer.showDialog(LifeDeathDialogs.standings(remainingSeconds, allStandings()));
    }

    private Map<Kind, Map<String, Integer>> allStandings() {
        Map<Kind, Map<String, Integer>> out = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) out.put(kind, standings(kind));
        return out;
    }

    /** The players ranked best-first for {@code kind}, as {@code name -> kills}. */
    public Map<String, Integer> standings(Kind kind) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (UUID id : ranked(kind)) out.put(nameOf(id), scores.get(id).counts[kind.ordinal()]);
        return out;
    }

    private List<UUID> ranked(Kind kind) {
        int i = kind.ordinal();
        return scores.entrySet().stream()
                .filter(e -> e.getValue().counts[i] > 0)
                .sorted(
                        Comparator.<Map.Entry<UUID, Score>>comparingInt(
                                        e -> -e.getValue().counts[i])
                                .thenComparingLong(e -> e.getValue().reachedAt[i]))
                .map(Map.Entry::getKey)
                .toList();
    }

    /** One winner per {@link Kind} (indexed by ordinal), null where nobody scored. */
    private UUID[] pickWinners() {
        UUID[] winners = new UUID[Kind.values().length];
        for (Kind kind : Kind.values()) {
            List<UUID> ranked = ranked(kind);
            winners[kind.ordinal()] = ranked.isEmpty() ? null : ranked.getFirst();
        }
        return winners;
    }

    // ---- giving books -----------------------------------------------------------------------

    /** Gives the book now if the player is online, otherwise queues it for their next join. */
    private void award(UUID id, Kind kind) {
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            give(online, kind);
        } else {
            queuePending(id, kind);
        }
    }

    /**
     * Puts the book in the player's inventory; if it is full the book is dropped at their feet
     * instead. Either way it is protected from destruction.
     */
    public void give(Player player, Kind kind) {
        if (MythicBook.giveOrDrop(plugin, player, kind)) {
            player.sendMessage(
                    Component.text(
                            "Your inventory is full - the " + bookName(kind) + " was dropped at your feet.",
                            NamedTextColor.YELLOW));
        }
        player.sendMessage(
                Component.text("You received the " + bookName(kind) + "!", NamedTextColor.GOLD));
    }

    private void queuePending(UUID id, Kind kind) {
        try {
            YamlConfiguration yaml = YamlFiles.loadStrict(pendingFile);
            List<String> list = new ArrayList<>(yaml.getStringList("pending." + id));
            list.add(kind.id());
            yaml.set("pending." + id, list);
            YamlFiles.saveAtomic(yaml, pendingFile);
        } catch (Exception e) {
            plugin.getLogger()
                    .log(
                            Level.SEVERE,
                            "Could not queue the " + kind.id() + " book for " + id + " - give it manually!",
                            e);
        }
    }

    /** Hands over any books won while offline. Call on join. */
    public void deliverPending(Player player) {
        if (!pendingFile.exists()) return;
        try {
            YamlConfiguration yaml = YamlFiles.loadStrict(pendingFile);
            String path = "pending." + player.getUniqueId();
            List<String> list = yaml.getStringList(path);
            if (list.isEmpty()) return;
            yaml.set(path, null);
            YamlFiles.saveAtomic(yaml, pendingFile);
            for (String raw : list) {
                Kind kind = Kind.parse(raw);
                if (kind != null) give(player, kind);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not read " + PENDING_FILE, e);
        }
    }

    // ---- helpers ----------------------------------------------------------------------------

    private static String nameOf(UUID id) {
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name != null ? name : id.toString();
    }

    static String capitalise(Kind kind) {
        return kind.id().substring(0, 1).toUpperCase() + kind.id().substring(1);
    }

    /** Plain-text book name for chat messages (the item names carry colour codes). */
    private static String bookName(Kind kind) {
        return "Book of " + capitalise(kind);
    }
}