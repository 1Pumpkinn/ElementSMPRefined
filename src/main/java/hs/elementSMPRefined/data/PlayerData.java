package hs.elementSMPRefined.data;

import hs.elementSMPRefined.API.element.ElementType;

import java.util.*;

/**
 * A player's element progress, mana, owned items, and trust list.
 * <p>
 * This class is a plain data holder with no knowledge of how it gets
 * persisted - see {@link PlayerDataSerializer} for the YAML mapping and
 * {@link PlayerDataRepository} for the storage contract. That split means
 * this class can be constructed and mutated freely in unit tests without
 * touching a Bukkit config or a file on disk.
 */
public final class PlayerData {

    /** Starting mana for a brand-new player. */
    public static final int DEFAULT_MANA = 100;

    /** Upgrade levels are clamped to [0, MAX_UPGRADE_LEVEL]. */
    public static final int MAX_UPGRADE_LEVEL = 2;

    private final UUID uuid;
    private ElementType currentElement;
    private final EnumSet<ElementType> ownedItems;
    private int mana;
    private int currentElementUpgradeLevel;
    private final Set<UUID> trustedPlayers;

    public PlayerData(UUID uuid) {
        this.uuid = Objects.requireNonNull(uuid, "uuid cannot be null");
        this.ownedItems = EnumSet.noneOf(ElementType.class);
        this.mana = DEFAULT_MANA;
        this.currentElementUpgradeLevel = 0;
        this.trustedPlayers = new HashSet<>();
    }

    public UUID getUuid() {
        return uuid;
    }

    public ElementType getCurrentElement() {
        return currentElement;
    }

    public ElementType getElementType() {
        return currentElement;
    }

    public int getCurrentElementUpgradeLevel() {
        return currentElementUpgradeLevel;
    }

    public int getMana() {
        return mana;
    }

    public Set<ElementType> getOwnedItems() {
        return EnumSet.copyOf(ownedItems);
    }

    public Set<UUID> getTrustedPlayers() {
        return new HashSet<>(trustedPlayers);
    }

    /** Sets the current element and resets its upgrade level to 0. */
    public void setCurrentElement(ElementType element) {
        this.currentElement = element;
        if (element != null) {
            this.currentElementUpgradeLevel = 0;
        }
    }

    /** Sets the current element without touching the upgrade level - used by loaders. */
    public void setCurrentElementWithoutReset(ElementType element) {
        this.currentElement = element;
    }

    public void setCurrentElementUpgradeLevel(int level) {
        this.currentElementUpgradeLevel = Math.max(0, Math.min(MAX_UPGRADE_LEVEL, level));
    }

    public void setMana(int mana) {
        this.mana = Math.max(0, mana);
    }

    public void addMana(int delta) {
        setMana(this.mana + delta);
    }

    /** Upgrade level only applies to whichever element is currently active; anything else reads as 0. */
    public int getUpgradeLevel(ElementType type) {
        if (type != null && type.equals(currentElement)) {
            return currentElementUpgradeLevel;
        }
        return 0;
    }

    public void setUpgradeLevel(ElementType type, int level) {
        if (type != null && type.equals(currentElement)) {
            setCurrentElementUpgradeLevel(level);
        }
    }

    public Map<ElementType, Integer> getUpgradesView() {
        Map<ElementType, Integer> map = new EnumMap<>(ElementType.class);
        if (currentElement != null) {
            map.put(currentElement, currentElementUpgradeLevel);
        }
        return Collections.unmodifiableMap(map);
    }

    public boolean hasElementItem(ElementType type) {
        return ownedItems.contains(type);
    }

    public void addElementItem(ElementType type) {
        ownedItems.add(type);
    }

    public void removeElementItem(ElementType type) {
        ownedItems.remove(type);
    }

    public boolean isTrusted(UUID uuid) {
        return trustedPlayers.contains(uuid);
    }

    public void addTrustedPlayer(UUID uuid) {
        trustedPlayers.add(uuid);
    }

    public void removeTrustedPlayer(UUID uuid) {
        trustedPlayers.remove(uuid);
    }

    public void setTrustedPlayers(Set<UUID> trusted) {
        trustedPlayers.clear();
        if (trusted != null) {
            trustedPlayers.addAll(trusted);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
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
                ", mana=" + mana +
                ", upgradeLevel=" + currentElementUpgradeLevel +
                '}';
    }
}