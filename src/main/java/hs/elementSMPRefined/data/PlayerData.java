package hs.elementSMPRefined.data;

import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.API.element.ElementId;

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
    private ElementId currentElementId;
    private final EnumSet<ElementType> ownedItems;
    private final Set<ElementId> ownedItemIds;
    private int mana;
    private int currentElementUpgradeLevel;
    private final Set<UUID> trustedPlayers;

    public PlayerData(UUID uuid) {
        this.uuid = Objects.requireNonNull(uuid, "uuid cannot be null");
        this.ownedItems = EnumSet.noneOf(ElementType.class);
        this.ownedItemIds = new HashSet<>();
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

    public ElementId getCurrentElementId() {
        return currentElementId;
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

    public Set<ElementId> getOwnedItemIds() {
        return new HashSet<>(ownedItemIds);
    }

    public Set<UUID> getTrustedPlayers() {
        return new HashSet<>(trustedPlayers);
    }

    /** Sets the current element and resets its upgrade level to 0. */
    public void setCurrentElement(ElementType element) {
        setCurrentElement(element == null ? null : ElementId.builtin(element));
    }

    /** Sets the current element without touching the upgrade level - used by loaders. */
    public void setCurrentElementWithoutReset(ElementType element) {
        setCurrentElementWithoutReset(element == null ? null : ElementId.builtin(element));
    }

    public void setCurrentElement(ElementId id) {
        setCurrentElementWithoutReset(id);
        if (id != null) {
            this.currentElementUpgradeLevel = 0;
        }
    }

    public void setCurrentElementWithoutReset(ElementId id) {
        this.currentElementId = id;
        this.currentElement = toBuiltinType(id);
    }

    private ElementType toBuiltinType(ElementId id) {
        if (id == null || !id.namespace().equals("elements")) {
            return null;
        }
        try {
            return ElementType.valueOf(id.key().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
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
        return type != null && hasElementItem(ElementId.builtin(type));
    }

    public void addElementItem(ElementType type) {
        if (type != null) addElementItem(ElementId.builtin(type));
    }

    public void removeElementItem(ElementType type) {
        if (type != null) removeElementItem(ElementId.builtin(type));
    }

    public boolean hasElementItem(ElementId id) {
        return id != null && ownedItemIds.contains(id);
    }

    public void addElementItem(ElementId id) {
        if (id == null) return;
        ownedItemIds.add(id);
        ElementType type = toBuiltinType(id);
        if (type != null) ownedItems.add(type);
    }

    public void removeElementItem(ElementId id) {
        if (id == null) return;
        ownedItemIds.remove(id);
        ElementType type = toBuiltinType(id);
        if (type != null) ownedItems.remove(type);
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