package net.rose.elementSMPRefined.data;

import net.rose.elementSMPRefined.core.API.element.ElementType;

import java.util.*;

/**
 * A player's element progress, owned items, and trust list.
 * <p>
 * This class is a plain data holder with no knowledge of how it gets
 * persisted - see {@link PlayerDataSerializer} for the YAML mapping and
 * {@link PlayerDataRepository} for the storage contract. That split means
 * this class can be constructed and mutated freely in unit tests without
 * touching a Bukkit config or a file on disk.
 */
public final class PlayerData {

    /** Upgrade levels are clamped to [0, MAX_UPGRADE_LEVEL]. */
    public static final int MAX_UPGRADE_LEVEL = 2;

    private final UUID uuid;
    private ElementType currentElement;
    private int currentElementUpgradeLevel;
    private final Set<UUID> trustedPlayers;
    private int pendingRerollerRefunds;
    private int pendingAdvancedRerollerRefunds;

    public PlayerData(UUID uuid) {
        this.uuid = Objects.requireNonNull(uuid, "uuid cannot be null");
        this.currentElementUpgradeLevel = 0;
        this.trustedPlayers = new HashSet<>();
    }

    public UUID getUuid() {
        return uuid;
    }

    public ElementType getCurrentElement() {
        return currentElement;
    }

    public int getCurrentElementUpgradeLevel() {
        return currentElementUpgradeLevel;
    }

    public Set<UUID> getTrustedPlayers() {
        return new HashSet<>(trustedPlayers);
    }

    /** Sets the current element and resets its upgrade level to 0. */
    public void setCurrentElement(ElementType element) {
        setCurrentElementWithoutReset(element);

        if (element != null) {
            this.currentElementUpgradeLevel = 0;
        }
    }

    /** Sets the current element without touching the upgrade level - used by loaders. */
    public void setCurrentElementWithoutReset(ElementType element) {
        this.currentElement = element;
    }

    public void setCurrentElementUpgradeLevel(int level) {
        this.currentElementUpgradeLevel = Math.clamp(level,
                0, MAX_UPGRADE_LEVEL);
    }

    /** Upgrade level only applies to whichever element is currently active; anything else reads as 0. */
    public int getUpgradeLevel(ElementType type) {
        if (type != null && type == currentElement) {
            return currentElementUpgradeLevel;
        }

        return 0;
    }

    public void setUpgradeLevel(ElementType type, int level) {
        if (type != null && type == currentElement) {
            setCurrentElementUpgradeLevel(level);
        }
    }

    public boolean isTrusted(UUID uuid) {
        return trustedPlayers.contains(uuid);
    }

    public void addTrustedPlayer(UUID uuid) {
        trustedPlayers.add(uuid);
    }

    public void setTrustedPlayers(Set<UUID> trusted) {
        trustedPlayers.clear();

        if (trusted != null) {
            trustedPlayers.addAll(trusted);
        }
    }

    /**
     * Reroller items consumed by a roll that never finished (the player
     * logged off mid-animation) are owed back to the player. These are
     * queued here rather than handed back immediately since the player is
     * already offline by the time a roll aborts - see
     * {@link net.rose.elementSMPRefined.listeners.player.PlayerLifecycle} for
     * where they actually get delivered, on the player's next join.
     */
    public int getPendingRerollerRefunds() {
        return pendingRerollerRefunds;
    }

    public void addPendingRerollerRefund() {
        pendingRerollerRefunds++;
    }

    /** Used by the serializer to restore the count from disk. */
    public void setPendingRerollerRefunds(int count) {
        this.pendingRerollerRefunds = Math.max(0, count);
    }

    /** Returns the owed count and resets it to zero - call only once the item has actually been handed back. */
    public int consumePendingRerollerRefunds() {
        int count = pendingRerollerRefunds;
        pendingRerollerRefunds = 0;
        return count;
    }

    public int getPendingAdvancedRerollerRefunds() {
        return pendingAdvancedRerollerRefunds;
    }

    public void addPendingAdvancedRerollerRefund() {
        pendingAdvancedRerollerRefunds++;
    }

    public void setPendingAdvancedRerollerRefunds(int count) {
        this.pendingAdvancedRerollerRefunds = Math.max(0, count);
    }

    public int consumePendingAdvancedRerollerRefunds() {
        int count = pendingAdvancedRerollerRefunds;
        pendingAdvancedRerollerRefunds = 0;
        return count;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PlayerData that = (PlayerData) o;

        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    @Override
    public String toString() {
        return "PlayerData{" +
                "uuid=" + uuid +
                ", element=" + currentElement +
                ", upgradeLevel=" + currentElementUpgradeLevel +
                '}';
    }
}
