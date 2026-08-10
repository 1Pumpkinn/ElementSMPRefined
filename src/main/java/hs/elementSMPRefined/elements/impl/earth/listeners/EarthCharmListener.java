package hs.elementSMPRefined.elements.impl.earth.listeners;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.elements.impl.earth.EarthElement;
import hs.elementSMPRefined.managers.ElementManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.metadata.FixedMetadataValue;

public class EarthCharmListener implements Listener {
    private final ElementManager elements;
    private final ElementSMPRefined plugin;

    public EarthCharmListener(ElementManager elements, ElementSMPRefined plugin) {
        this.elements = elements;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPunch(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!(e.getEntity() instanceof Mob mob)) return;
        if (!p.hasMetadata(EarthElement.META_CHARM_NEXT_UNTIL)) return;

        long until = p.getMetadata(EarthElement.META_CHARM_NEXT_UNTIL).get(0).asLong();
        if (System.currentTimeMillis() > until) return;

        // Check if mob can be charmed (prevent boss mobs)
        if (mob instanceof Wither || mob instanceof EnderDragon || mob instanceof Warden) {
            p.sendMessage(ChatColor.RED + "This creature cannot be charmed!");
            e.setCancelled(true);
            return;
        }

        // Consume the ability
        p.removeMetadata(EarthElement.META_CHARM_NEXT_UNTIL, plugin);

        long expire = System.currentTimeMillis() + 30_000L;
        mob.setMetadata("earth_charmed_owner", new FixedMetadataValue(plugin, p.getUniqueId().toString()));
        mob.setMetadata("earth_charmed_until", new FixedMetadataValue(plugin, expire));

        p.sendMessage(ChatColor.GREEN + "Mob charmed! It will follow you for 30s.");
        e.setCancelled(true);
    }
}
