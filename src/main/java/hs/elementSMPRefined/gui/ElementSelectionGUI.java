package hs.elementSMPRefined.gui;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.elements.ElementType;
import hs.elementSMPRefined.managers.ElementManager;
import hs.elementSMPRefined.util.visual.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ElementSelectionGUI {
    private static final String INVENTORY_TITLE = ChatColor.DARK_PURPLE + "Rolling Element...";
    private static final Map<UUID, ElementSelectionGUI> openGuis = new ConcurrentHashMap<>();
    private static final ElementType[] BASIC_ELEMENTS = {
            ElementType.AIR, ElementType.WATER, ElementType.FIRE, ElementType.EARTH
    };

    private final ElementSMPRefined plugin;
    private final ElementManager elementManager;
    private final Player player;
    private final Inventory inventory;
    private final boolean isReroll;
    private BukkitTask animationTask;
    private int currentIndex = 0;
    private int ticksElapsed = 0;
    private ElementType selectedElement;
    private boolean isAnimating = false;
    
    public ElementSelectionGUI(ElementSMPRefined plugin, Player player, boolean isReroll) {
        this.plugin = plugin;
        this.elementManager = plugin.getElementManager();
        this.player = player;
        this.isReroll = isReroll;
        this.inventory = Bukkit.createInventory(null, 27, INVENTORY_TITLE);
        
        setupGUI();
        openGuis.put(player.getUniqueId(), this);
    }

    private void setupGUI() {
        fillWithGlass();
        startAnimation();
    }
    
    private void fillWithGlass() {
        ItemStack glass = createBorderItem();
        
        for (int i = 0; i < 27; i++) {
            if (i != 13) {
                inventory.setItem(i, glass);
            }
        }
    }
    
    private ItemStack createBorderItem() {
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            border.setItemMeta(meta);
        }
        return border;
    }
    
    private ItemStack createElementItem(ElementType type) {
        Material material;
        ChatColor color;
        String name;
        
        switch (type) {
            case FIRE:
                material = Material.FIRE_CHARGE;
                color = ChatColor.RED;
                name = "Fire Element";
                break;
            case WATER:
                material = Material.WATER_BUCKET;
                color = ChatColor.BLUE;
                name = "Water Element";
                break;
            case EARTH:
                material = Material.GRASS_BLOCK;
                color = ChatColor.GREEN;
                name = "Earth Element";
                break;
            case AIR:
                material = Material.FEATHER;
                color = ChatColor.WHITE;
                name = "Air Element";
                break;
            default:
                material = Material.BARRIER;
                color = ChatColor.GRAY;
                name = "Unknown";
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color + name);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Rolling...");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private void startAnimation() {
        if (isAnimating) return;
        
        isAnimating = true;
        Random random = new Random();
        selectedElement = BASIC_ELEMENTS[random.nextInt(BASIC_ELEMENTS.length)];
        
        int totalCycles = 20 + random.nextInt(10);
        int slowDownStart = totalCycles - 8;
        
        SoundUtils.playTo(player, SoundUtils.UI.ROLL);
        
        animationTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !player.getOpenInventory().getTopInventory().equals(inventory)) {
                    cancel();
                    cleanup();
                    return;
                }
                
                ticksElapsed++;
                
                if (ticksElapsed >= totalCycles) {
                    finishAnimation();
                    cancel();
                    return;
                }
                
                int delay;
                if (ticksElapsed < slowDownStart) {
                    delay = 2;
                } else {
                    int slowDownPhase = ticksElapsed - slowDownStart;
                    delay = 2 + (slowDownPhase * 2);
                }
                
                if (ticksElapsed % delay == 0) {
                    currentIndex = (currentIndex + 1) % BASIC_ELEMENTS.length;
                    updateCenterSlot();
                    
                    if (ticksElapsed < slowDownStart) {
                        SoundUtils.playTo(player, SoundUtils.UI.HOVER);
                    } else {
                        SoundUtils.playTo(player, SoundUtils.UI.CLICK);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    
    private void updateCenterSlot() {
        ElementType currentElement = BASIC_ELEMENTS[currentIndex];
        ItemStack item = createElementItem(currentElement);
        
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            if (isAnimating && ticksElapsed < 20) {
                lore.add(ChatColor.GRAY + "Rolling...");
            } else if (currentElement == selectedElement && ticksElapsed >= 20) {
                lore.add(ChatColor.GREEN + "Selected!");
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        inventory.setItem(13, item);
    }
    
    private void finishAnimation() {
        isAnimating = false;
        
        ItemStack finalItem = createElementItem(selectedElement);
        ItemMeta meta = finalItem.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "SELECTED!");
            meta.setLore(lore);
            finalItem.setItemMeta(meta);
        }
        
        inventory.setItem(13, finalItem);
        
        SoundUtils.playTo(player, SoundUtils.UI.SUCCESS);
        SoundUtils.playTo(player, SoundUtils.Ability.ACTIVATE);
        
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                
                player.closeInventory();
                
                if (isReroll) {
                    elementManager.setElement(player, selectedElement);
                    player.sendMessage(ChatColor.GREEN + "Your element has been changed to " + 
                        ChatColor.GOLD + selectedElement.name());
                } else {
                    elementManager.assignElement(player, selectedElement);
                    player.sendMessage(ChatColor.GREEN + "You have been assigned the " + 
                        ChatColor.GOLD + selectedElement.name() + ChatColor.GREEN + " element!");
                }
                
                cleanup();
            }
        }.runTaskLater(plugin, 40L);
    }
    
    public void open() {
        player.openInventory(inventory);
    }
    
    public void handleClick(int slot) {
        if (slot == 13 && !isAnimating) {
            startAnimation();
        }
    }
    
    public void cleanup() {
        if (animationTask != null && !animationTask.isCancelled()) {
            animationTask.cancel();
        }
        openGuis.remove(player.getUniqueId());
    }
    
    public static ElementSelectionGUI getGUI(UUID playerId) {
        return openGuis.get(playerId);
    }
    
    public static void removeGUI(UUID playerId) {
        ElementSelectionGUI gui = openGuis.get(playerId);
        if (gui != null) {
            gui.cleanup();
        }
    }
}
