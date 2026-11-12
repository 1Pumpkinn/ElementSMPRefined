package net.rose.elementSMPRefined.commands.element.base;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.altar.ForgeRecipe;
import net.rose.elementSMPRefined.commands.supporters.CommandSupport;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.API.event.ElementChangeEvent;
import net.rose.elementSMPRefined.core.API.event.UpgradeLevelChangeEvent;
import net.rose.elementSMPRefined.data.PlayerData;
import net.rose.elementSMPRefined.items.recipes.ForgedUpgraderItem;
import net.rose.elementSMPRefined.items.recipes.UpgraderItem;
import net.rose.elementSMPRefined.lang.Lang;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.util.sound.SoundUtils;
import net.rose.elementSMPRefined.util.visual.ElementColours;
import net.rose.elementevents.mythic.MythicBook;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * <pre>
 * /withdraw upgrader    turn your highest upgrade back into an Upgrader item
 * /withdraw forged      turn a forged element back into its basic element + a Forged Upgrader
 * /withdraw mythic      turn a mythic element back into its book (you get a random basic element)
 * </pre>
 * <p>
 * <b>upgrader</b>: withdraws ONE upgrade level at a time, highest first. At level 2 it
 * gives back an Upgrader II (you keep Upgrade I); run it again at level 1 and it gives
 * back an Upgrader I (level 0).
 * <p>
 * <b>forged</b>: the reverse of the Forged Upgrader (Lava -&gt; Fire, Metal -&gt; Earth,
 * Frost -&gt; Water, Storm -&gt; Air). Your upgrade level is kept, exactly as it is when
 * a Forged Upgrader is used.
 * <p>
 * <b>mythic</b>: gives the Life/Death book back and frees that mythic element so anyone can
 * claim it again. You are left with a random basic element (as after a reroll) and keep your
 * upgrade level.
 */
public class WithdrawCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("upgrader", "forged", "mythic");

    private final ElementSMPRefined plugin;
    private final ElementManager elementManager;

    public WithdrawCommand(ElementSMPRefined plugin) {
        this.plugin = plugin;
        this.elementManager = plugin.getElementManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.WITHDRAW_PLAYERS_ONLY);
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(Lang.WITHDRAW_USAGE);
            return true;
        }

        // Shares the reroll lock so this can't run mid reroll / forge animation.
        if (elementManager.isCurrentlyRolling(player)) {
            player.sendMessage(Lang.ELEMENT_MANAGER_YOU_ARE_ALREADY_REROLLING);
            return true;
        }

        PlayerData data = elementManager.data(player.getUniqueId());
        ElementType current = data == null ? null : data.getCurrentElement();
        if (current == null) {
            player.sendMessage(Lang.UPGRADER_NO_ELEMENT_YET);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "upgrader" -> withdrawUpgrader(player, data, current);
            case "forged" -> withdrawForged(player, current);
            case "mythic" -> withdrawMythic(player, current);
            default -> player.sendMessage(Lang.WITHDRAW_USAGE);
        }
        return true;
    }

    private void withdrawUpgrader(Player player, PlayerData data, ElementType current) {
        int level = data.getUpgradeLevel(current);
        if (level < 1) {
            return;
        }

        int newLevel = level - 1;

        UpgradeLevelChangeEvent event =
                new UpgradeLevelChangeEvent(
                        player,
                        current,
                        level,
                        newLevel,
                        UpgradeLevelChangeEvent.Cause.WITHDRAW);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            player.sendMessage(Lang.WITHDRAW_CANCELLED);
            return;
        }

        data.setUpgradeLevel(current, newLevel);
        plugin.getDataStore().save(data);
        elementManager.applyUpsides(player);

        // Hands back the upgrader for the level that was just removed (II first, then I).
        ItemStack upgrader = UpgraderItem.make(plugin, level);
        boolean dropped = upgrader != null && give(player, upgrader);

        SoundUtils.playTo(player, SoundUtils.UI.WITHDRAW);
    }

    private void withdrawForged(Player player, ElementType current) {
        Optional<ForgeRecipe> forge = ForgeRecipe.forTarget(current);
        if (forge.isEmpty()) {
            return;
        }

        ElementType base = forge.get().base();
        elementManager.assignElement(player, base, ElementChangeEvent.Cause.UNFORGE);
        boolean dropped = give(player, ForgedUpgraderItem.make(plugin));

        var element = elementManager.get(base);
        NamedTextColor color =
                element != null
                        ? ElementColours.fromLegacy(element.getDisplayName())
                        : NamedTextColor.AQUA;
        // Replaces the generic "Element Assigned!" title assignElement() just showed.
        player.showTitle(
                Title.title(
                        Component.text("Element Unforged").color(NamedTextColor.GOLD),
                        Component.text(current.name() + " -> " + base.name()).color(color),
                        Title.Times.times(
                                Duration.ofMillis(500),
                                Duration.ofMillis(2000),
                                Duration.ofMillis(500))));

        // assignElement() just played the success jingle - cut it and play the power-down
        // instead.
        player.stopSound(SoundUtils.UI.SUCCESS.sound());
        SoundUtils.playTo(player, SoundUtils.UI.WITHDRAW);
    }

    private void withdrawMythic(Player player, ElementType current) {
        MythicBook.Kind kind = MythicBook.kindFor(current);
        if (kind == null) {
            player.sendMessage(Lang.WITHDRAW_NOT_MYTHIC);
            return;
        }

        ElementType[] basics = elementManager.getBasicElements();
        ElementType base = basics[ThreadLocalRandom.current().nextInt(basics.length)];

        plugin.getMythicElements().release(current, player.getUniqueId());
        elementManager.assignElement(player, base, ElementChangeEvent.Cause.MYTHIC_WITHDRAW);
        MythicBook.giveOrDrop(plugin, player, kind);

        var element = elementManager.get(base);
        NamedTextColor color =
                element != null
                        ? ElementColours.fromLegacy(element.getDisplayName())
                        : NamedTextColor.AQUA;
        player.showTitle(
                Title.title(
                        Component.text("Element Withdrawn").color(NamedTextColor.GOLD),
                        Component.text(current.name() + " -> " + base.name()).color(color),
                        Title.Times.times(
                                Duration.ofMillis(500),
                                Duration.ofMillis(2000),
                                Duration.ofMillis(500))));

        player.stopSound(SoundUtils.UI.SUCCESS.sound());
        SoundUtils.playTo(player, SoundUtils.UI.WITHDRAW);

        plugin.getServer()
                .broadcast(
                        Component.text(
                                player.getName()
                                        + " has withdrawn the "
                                        + kind.elementName()
                                        + ". Its book is free to claim again.",
                                NamedTextColor.GOLD));
    }

    /** Adds the item to the inventory, dropping whatever doesn't fit. @return true if anything was dropped */
    private boolean give(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack stack : leftover.values()) {
            dropFor(player, stack);
        }
        return !leftover.isEmpty();
    }

    /**
     * Throws the item out in front of the player (like pressing Q) rather than spawning it on
     * their feet, and reserves it for them so nobody else can grab it first.
     */
    private void dropFor(Player player, ItemStack stack) {
        Location eye = player.getEyeLocation();
        Item dropped = player.getWorld().dropItem(eye, stack);
        dropped.setVelocity(eye.getDirection().multiply(0.3));
        dropped.setPickupDelay(40);
        dropped.setOwner(player.getUniqueId());
        dropped.setThrower(player.getUniqueId());

        if (!dropped.isValid()) {
            // Something (another plugin / a region) cancelled the item spawn.
            plugin.getLogger()
                    .warning(
                            "Could not drop withdrawn item for "
                                    + player.getName()
                                    + " - the item spawn was cancelled by another plugin.");
        }
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return CommandSupport.filterStartingWith(SUBCOMMANDS, args[0]);
        }
        return new ArrayList<>();
    }
}
