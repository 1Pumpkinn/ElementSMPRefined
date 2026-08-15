package hs.elementSMPRefined.data;

import hs.elementSMPRefined.API.element.ElementType;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/**
 * Immutable player data holder using modern Java patterns
 */
public final class PlayerData {
    private final UUID uuid;
    private ElementType currentElement;
    private final EnumSet<ElementType> ownedItems;
    private int mana;
    private int currentElementUpgradeLevel;
    private final Set<UUID> trustedPlayers;

    // === CONSTRUCTORS ===

    public PlayerData(UUID uuid) {
        this.uuid = Objects.requireNonNull(uuid, "UUID cannot be null");
        this.ownedItems = EnumSet.noneOf(ElementType.class);
        this.mana = 100;
        this.currentElementUpgradeLevel = 0;
        this.trustedPlayers = new HashSet<>();
    }

    public PlayerData(UUID uuid, ConfigurationSection section) {
        this(uuid);
        if (section != null) {
            loadFromSection(section);
        }
    }

    private void loadFromSection(ConfigurationSection section) {
        // Load element
        String elem = section.getString("element");
        if (elem != null) {
            try {
                this.currentElement = ElementType.valueOf(elem);
            } catch (IllegalArgumentException ignored) {
                // Invalid element, leave as null
            }
        }

        // Load mana
        this.mana = section.getInt("mana", 100);

        // Load upgrade level
        this.currentElementUpgradeLevel = section.getInt("currentUpgradeLevel", 0);

        // Load owned items
        List<String> items = section.getStringList("items");
        if (items != null) {
            for (String name : items) {
                try {
                    ElementType type = ElementType.valueOf(name);
                    this.ownedItems.add(type);
                } catch (IllegalArgumentException ignored) {
                    // Skip invalid items
                }
            }
        }

        // Load trust list
        ConfigurationSection trustSection = section.getConfigurationSection("trust");
        if (trustSection != null) {
            for (String key : trustSection.getKeys(false)) {
                try {
                    this.trustedPlayers.add(UUID.fromString(key));
                } catch (IllegalArgumentException ignored) {
                    // Invalid UUID, skip
                }
            }
        }
    }

    // === GETTERS ===

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

    // === SETTERS (with validation) ===

    public void setCurrentElement(ElementType element) {
        this.currentElement = element;
        if (element != null) {
            this.currentElementUpgradeLevel = 0;
        }
    }

    public void setCurrentElementWithoutReset(ElementType element) {
        this.currentElement = element;
    }

    public void setCurrentElementUpgradeLevel(int level) {
        this.currentElementUpgradeLevel = Math.max(0, Math.min(2, level));
    }

    public void setMana(int mana) {
        this.mana = Math.max(0, mana);
    }

    public void addMana(int delta) {
        setMana(this.mana + delta);
    }

    // === ELEMENT-SPECIFIC METHODS ===

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

    // === OWNED ITEMS ===

    public boolean hasElementItem(ElementType type) {
        return ownedItems.contains(type);
    }

    public void addElementItem(ElementType type) {
        ownedItems.add(type);
    }

    public void removeElementItem(ElementType type) {
        ownedItems.remove(type);
    }

    // === TRUST MANAGEMENT ===

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

    // === UTILITY ===

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