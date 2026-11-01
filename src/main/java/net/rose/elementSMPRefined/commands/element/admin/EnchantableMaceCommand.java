package net.rose.elementSMPRefined.commands.element.admin;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.items.EnchantableMace;
import net.rose.elementSMPRefined.lang.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** {@code /enchantablemace [player]} - gives the one mace that keeps its enchantments. OP by default. */
public class EnchantableMaceCommand implements CommandExecutor, TabCompleter {
    private final ElementSMPRefined plugin;

    public EnchantableMaceCommand(ElementSMPRefined plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("element.admin")) {
            sender.sendMessage(Lang.ENCHANTABLE_MACE_YOU_DON_T_HAVE_PERMISSION);
            return true;
        }

        Player target;
        if (args.length == 0) {
            if (!(sender instanceof Player self)) {
                sender.sendMessage(Lang.ENCHANTABLE_MACE_USAGE);
                return true;
            }
            target = self;
        } else if (args.length == 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(Lang.ENCHANTABLE_MACE_PLAYER_NOT_FOUND);
                return true;
            }
        } else {
            sender.sendMessage(Lang.ENCHANTABLE_MACE_USAGE);
            return true;
        }

        ItemStack mace = EnchantableMace.make(plugin);
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(mace);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(item -> target.getWorld().dropItem(target.getLocation(), item));
            target.sendMessage(Lang.ENCHANTABLE_MACE_INVENTORY_FULL);
        }

        target.sendMessage(Lang.ENCHANTABLE_MACE_RECEIVED);
        if (target != sender) {
            sender.sendMessage(Lang.enchantableMaceGiven(target.getName()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("element.admin")) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(p.getName());
            }
        }
        return out;
    }
}
