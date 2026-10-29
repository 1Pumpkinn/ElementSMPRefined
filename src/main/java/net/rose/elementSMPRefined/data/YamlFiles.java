package net.rose.elementSMPRefined.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Safe YAML file helpers so a bad edit, a crash mid-write or a plugin update can never wipe
 * saved data:
 * <ul>
 *   <li>{@link #loadStrict} - throws on a broken file instead of silently returning an EMPTY
 *       config (which {@code YamlConfiguration.loadConfiguration} does, and which then gets saved
 *       back over the real data).</li>
 *   <li>{@link #saveAtomic} - writes a temp file and moves it into place, so a crash mid-write
 *       leaves the old file intact instead of a truncated one.</li>
 *   <li>{@link #backup} - timestamped rolling copies.</li>
 *   <li>{@link #mergeMissing} - adds keys that are new in the bundled defaults without touching
 *       anything the server owner already has.</li>
 * </ul>
 */
public final class YamlFiles {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private YamlFiles() {}

    /** Loads a YAML file, throwing if it can't be read or parsed. A missing/empty file is an empty config. */
    public static YamlConfiguration loadStrict(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        if (file.exists() && file.length() > 0) {
            yaml.load(file);
        }
        return yaml;
    }

    /** Writes {@code yaml} to {@code file} via a temp file + move, so the old file survives a crash. */
    public static void saveAtomic(FileConfiguration yaml, File file) throws IOException {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create directory " + parent);
        }
        Path target = file.toPath();
        Path temp = target.resolveSibling(file.getName() + ".tmp");
        Files.writeString(temp, yaml.saveToString(), StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Copies {@code file} into {@code backupDir} as {@code <prefix>-<timestamp>.yml} and deletes the
     * oldest copies beyond {@code keep}. Skipped if the file is missing/empty, or if the newest
     * backup is younger than {@code minAgeMinutes} - so a flurry of restarts after a bad update
     * can't push the last good backup out of the rotation.
     */
    public static void backup(File file, File backupDir, String prefix, int keep, long minAgeMinutes) throws IOException {
        if (!file.exists() || file.length() == 0) return;
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            throw new IOException("Could not create backup directory " + backupDir);
        }

        File[] existing = backupDir.listFiles((dir, name) -> name.startsWith(prefix + "-") && name.endsWith(".yml"));
        List<File> backups = existing == null ? List.of()
                : Arrays.stream(existing).sorted(Comparator.comparingLong(File::lastModified).reversed()).toList();

        if (!backups.isEmpty()) {
            long ageMillis = System.currentTimeMillis() - backups.get(0).lastModified();
            if (ageMillis < minAgeMinutes * 60_000L) return;
        }

        File copy = new File(backupDir, prefix + "-" + LocalDateTime.now().format(STAMP) + ".yml");
        Files.copy(file.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING);

        // 'backups' was sorted before the new copy existed, so the new one counts as +1.
        for (int i = Math.max(0, keep - 1); i < backups.size(); i++) {
            Files.deleteIfExists(backups.get(i).toPath());
        }
    }

    /** Copies a broken file next to itself as {@code <name>.broken-<timestamp>} so it can be repaired by hand. */
    public static File quarantine(File file) throws IOException {
        File copy = new File(file.getParentFile(), file.getName() + ".broken-" + LocalDateTime.now().format(STAMP));
        Files.copy(file.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return copy;
    }

    /**
     * Adds every key that exists in {@code defaults} but not in {@code live}'s own file contents
     * (bundled defaults that Bukkit layers underneath don't count as present). Existing values are
     * never changed or removed; comments on newly added keys are copied across.
     *
     * @return true if anything was added
     */
    public static boolean mergeMissing(FileConfiguration live, ConfigurationSection defaults) {
        boolean changed = false;
        List<String> added = new java.util.ArrayList<>();

        for (String key : defaults.getKeys(true)) {
            if (live.contains(key, true)) continue;
            if (defaults.isConfigurationSection(key)) {
                added.add(key);
                continue;
            }
            live.set(key, defaults.get(key));
            added.add(key);
            changed = true;
        }

        // Comments last: setting them needs the path to exist, and sections only exist once a child was set.
        for (String key : added) {
            if (!live.contains(key, true)) continue;
            List<String> comments = defaults.getComments(key);
            if (!comments.isEmpty()) live.setComments(key, comments);
            List<String> inline = defaults.getInlineComments(key);
            if (!inline.isEmpty()) live.setInlineComments(key, inline);
        }
        return changed;
    }
}
