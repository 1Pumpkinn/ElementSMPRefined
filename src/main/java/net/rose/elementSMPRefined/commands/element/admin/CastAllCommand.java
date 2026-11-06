package net.rose.elementSMPRefined.commands.element.admin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.commands.supporters.CommandSupport;
import net.rose.elementSMPRefined.commands.supporters.ElementSubCommand;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /element castall <1|2> [players... | *] [at <target>]} - testing tool: fires the same ability
 * slot for several players on the SAME tick, so overlap behaviour (e.g. two Magma Beams on one victim)
 * can be tested by one admin without having to sync up real players.
 * <ul>
 *     <li>No names, or {@code *}: every online player (except the {@code at} target).</li>
 *     <li>{@code at <target>}: every caster is turned to face the target on the cast tick and kept
 *     facing them for {@link #AIM_TICKS} ticks (long enough for a full Magma Beam), so nobody has to
 *     aim by hand. The target can be a different player to the casters.</li>
 * </ul>
 * Cooldowns and every other ability rule still apply; failures are reported by the ability itself.
 */
public class CastAllCommand implements ElementSubCommand {
    private static final int AIM_TICKS = 100;

    private final ElementSMPRefined plugin;

    public CastAllCommand(ElementSMPRefined plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2 || !(args[1].equals("1") || args[1].equals("2"))) {
            sender.sendMessage(
                    Component.text(
                            "Usage: /element castall <1|2> [players... | *] [at <target>]",
                            NamedTextColor.RED));
            return true;
        }
        boolean slotTwo = args[1].equals("2");

        List<String> names = new ArrayList<>();
        String targetName = null;
        for (int i = 2; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("at")) {
                if (i + 1 >= args.length) {
                    sender.sendMessage(
                            Component.text("'at' needs a target name.", NamedTextColor.RED));
                    return true;
                }
                targetName = args[i + 1];
                break;
            }
            names.add(args[i]);
        }

        Player target = null;
        if (targetName != null) {
            target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                sender.sendMessage(
                        Component.text("Target not online: " + targetName, NamedTextColor.RED));
                return true;
            }
        }

        List<Player> casters = new ArrayList<>();
        if (names.isEmpty() || (names.size() == 1 && names.getFirst().equals("*"))) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(target)) casters.add(online);
            }
        } else {
            for (String name : names) {
                Player p = Bukkit.getPlayerExact(name);
                if (p == null) {
                    sender.sendMessage(Component.text("Not online: " + name, NamedTextColor.RED));
                    return true;
                }
                if (!casters.contains(p)) casters.add(p);
            }
        }
        if (casters.isEmpty()) {
            sender.sendMessage(Component.text("No players to cast for.", NamedTextColor.RED));
            return true;
        }

        // Face everyone first, then fire them all back-to-back in this one tick.
        if (target != null) {
            for (Player caster : casters) face(caster, target);
        }
        List<String> fired = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (Player caster : casters) {
            boolean ok =
                    slotTwo
                            ? plugin.getElementManager().useAbility2(caster)
                            : plugin.getElementManager().useAbility1(caster);
            (ok ? fired : failed).add(caster.getName());
        }

        if (target != null) keepFacing(casters, target);

        sender.sendMessage(
                Component.text(
                        "Ability "
                                + args[1]
                                + " fired together for: "
                                + (fired.isEmpty() ? "nobody" : String.join(", ", fired)),
                        NamedTextColor.GREEN));
        if (!failed.isEmpty()) {
            sender.sendMessage(
                    Component.text(
                            "Did not cast (cooldown/no element/etc): " + String.join(", ", failed),
                            NamedTextColor.YELLOW));
        }
        return true;
    }

    private static void face(Player caster, Player target) {
        Location eye = caster.getEyeLocation();
        Vector look = target.getBoundingBox().getCenter().subtract(eye.toVector());
        if (look.lengthSquared() < 1.0E-6) return;
        Location facing = eye.clone().setDirection(look);
        caster.setRotation(facing.getYaw(), facing.getPitch());
    }

    private void keepFacing(List<Player> casters, Player target) {
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ >= AIM_TICKS || !target.isOnline()) {
                    cancel();
                    return;
                }
                for (Player caster : casters) {
                    if (caster.isOnline() && caster.getWorld().equals(target.getWorld()))
                        face(caster, target);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return CommandSupport.filterStartingWith(List.of("1", "2"), args[1]);
        }
        List<String> options =
                new ArrayList<>(CommandSupport.getOnlinePlayerNames(args[args.length - 1]));
        if ("at".startsWith(args[args.length - 1].toLowerCase(Locale.ROOT))) options.add("at");
        if ("*".startsWith(args[args.length - 1])) options.add("*");
        return options;
    }
}
