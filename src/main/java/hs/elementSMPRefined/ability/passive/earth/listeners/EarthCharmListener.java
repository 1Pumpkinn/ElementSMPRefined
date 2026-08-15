package hs.elementSMPRefined.ability.passive.earth.listeners;//package hs.elementSMPRefined.ability.passive.earth.listeners;
//
//import hs.elementSMPRefined.ElementSMPRefined;
//import hs.elementSMPRefined.ability.passive.earth.EarthElement;
//import hs.elementSMPRefined.managers.ElementManager;
//import org.bukkit.plugin.java.JavaPlugin;
//import hs.elementSMPRefined.util.bukkit.MetadataHelper;
//import org.bukkit.ChatColor;
//import org.bukkit.entity.*;
//import org.bukkit.event.EventHandler;
//import org.bukkit.event.Listener;
//import org.bukkit.event.entity.EntityDamageByEntityEvent;
//import org.bukkit.metadata.FixedMetadataValue;
//
//public class EarthCharmListener implements Listener {
//    private final ElementManager elements;
//    private final ElementSMPRefined plugin;
//    private final MetadataHelper metadataHelper;
//
//    public EarthCharmListener(ElementManager elements, ElementSMPRefined plugin) {
//        this.elements = elements;
//        this.plugin = (ElementSMPRefined) plugin;
//        this.metadataHelper = plugin.getMetadataHelper();
//    }
//
//    @EventHandler
//    public void onPunch(EntityDamageByEntityEvent e) {
//        if (!(e.getDamager() instanceof Player p)) return;
//        if (!(e.getEntity() instanceof Mob mob)) return;
//
//        // Use safe metadata access
//        long until = metadataHelper.getLong(p, EarthElement.META_CHARM_NEXT_UNTIL, 0);
//        if (until == 0 || System.currentTimeMillis() > until) return;
//
//        // Check if mob can be charmed (prevent boss mobs)
//        if (mob instanceof Wither || mob instanceof EnderDragon || mob instanceof Warden) {
//            p.sendMessage(ChatColor.RED + "This creature cannot be charmed!");
//            e.setCancelled(true);
//            return;
//        }
//
//        // Consume the ability
//        metadataHelper.remove(p, EarthElement.META_CHARM_NEXT_UNTIL);
//
//        long expire = System.currentTimeMillis() + 30_000L;
//        mob.setMetadata("earth_charmed_owner", new FixedMetadataValue(plugin, p.getUniqueId().toString()));
//        mob.setMetadata("earth_charmed_until", new FixedMetadataValue(plugin, expire));
//
//        p.sendMessage(ChatColor.GREEN + "Mob charmed! It will follow you for 30s.");
//        e.setCancelled(true);
//    }
//}
