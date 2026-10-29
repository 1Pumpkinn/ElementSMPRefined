package net.rose.elementSMPRefined.listeners.handler;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.altar.ForgeRecipe;
import net.rose.elementSMPRefined.altar.ReforgeAltarManager;
import net.rose.elementSMPRefined.items.recipes.ForgedUpgraderItem;
import net.rose.elementSMPRefined.lang.Lang;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ReforgeAltarListener implements Listener {
    private final ElementSMPRefined plugin;
    private final ReforgeAltarManager altar;

    public ReforgeAltarListener(ElementSMPRefined plugin, ReforgeAltarManager altar) {
        this.plugin = plugin;
        this.altar = altar;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAltarInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        var block = event.getClickedBlock();
        if (block == null || !altar.isAltarLodestone(block)) return;

        Player player = event.getPlayer();
        // Sneaking with something in hand = "I'm trying to build against it", let vanilla do that.
        if (player.isSneaking() && event.getItem() != null) return;

        // Always cancel so a compass can't be linked to it and blocks can't be placed on it by accident.
        event.setCancelled(true);
        // Fires once per hand - only act on the main-hand pass.
        if (event.getHand() != EquipmentSlot.HAND) return;

        if (!altar.hasActiveAltar()) return;

        // The recipe depends on the player's own element (same lookup as their text display).
        Optional<ForgeRecipe> forge = altar.recipeFor(player);
        if (forge.isEmpty()) {
            player.sendMessage(Lang.UPGRADER_NO_ELEMENT_YET);
            return;
        }
        ForgeRecipe recipe = forge.get();

        PlayerInventory inv = player.getInventory();
        List<String> missing = new ArrayList<>();
        for (ForgeRecipe.Ingredient ing : recipe.ingredients()) {
            int have = count(inv, ing.material());
            if (have < ing.amount()) {
                missing.add((ing.amount() - have) + "x " + ing.name());
            }
        }
        if (!missing.isEmpty()) {
            player.sendMessage(Component.text("The altar needs more: ", NamedTextColor.RED)
                    .append(Component.text(String.join(", ", missing), NamedTextColor.YELLOW)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
            return;
        }

        // Re-check the altar still exists (another player may have beaten us to it this tick).
        if (!altar.hasActiveAltar()) return;

        for (ForgeRecipe.Ingredient ing : recipe.ingredients()) {
            take(inv, ing.material(), ing.amount());
        }

        ItemStack reward = ForgedUpgraderItem.make(plugin);
        for (ItemStack leftover : inv.addItem(reward).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        Location center = block.getLocation().add(0.5, 1.0, 0.5);
        block.getWorld().spawnParticle(Particle.END_ROD, center, 80, 1.0, 1.0, 1.0, 0.1);
        block.getWorld().playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.2f);
        altar.completeAltar();
    }

    private int count(PlayerInventory inv, Material material) {
        ItemStack plain = new ItemStack(material);
        int total = 0;
        for (ItemStack stack : inv.getStorageContents()) {
            if (stack != null && stack.isSimilar(plain)) total += stack.getAmount();
        }
        return total;
    }

    private void take(PlayerInventory inv, Material material, int amount) {
        ItemStack plain = new ItemStack(material);
        ItemStack[] contents = inv.getStorageContents();
        int remaining = amount;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || !stack.isSimilar(plain)) continue;
            int removed = Math.min(remaining, stack.getAmount());
            remaining -= removed;
            if (removed >= stack.getAmount()) {
                contents[i] = null;
            } else {
                stack.setAmount(stack.getAmount() - removed);
            }
        }
        inv.setStorageContents(contents);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        if (altar.isAltarLodestone(event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("The altar's lodestone can't be broken.", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(altar::isAltarLodestone);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(altar::isAltarLodestone);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (altar.isAltarLodestone(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBurn(BlockBurnEvent event) {
        if (altar.isAltarLodestone(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(altar::isAltarLodestone)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(altar::isAltarLodestone)) {
            event.setCancelled(true);
        }
    }
}
