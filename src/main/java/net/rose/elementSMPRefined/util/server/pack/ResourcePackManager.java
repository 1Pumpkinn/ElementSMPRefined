package net.rose.elementSMPRefined.util.server.pack;

import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.rose.elementSMPRefined.ElementSMPRefined;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Serves the resource pack bundled in the plugin jar and applies it to players when they join, so
 * the pack never has to be uploaded or linked by hand. See {@code resource_pack} in config.yml.
 */
public final class ResourcePackManager implements Listener {
    private static final UUID PACK_ID =
            UUID.nameUUIDFromBytes("elementsmprefined:resource-pack".getBytes(StandardCharsets.UTF_8));

    private final ElementSMPRefined plugin;
    private final File jarFile;

    private ResourcePackArchive archive;
    private ResourcePackServer server;
    private ResourcePackRequest request;
    private URI url;

    public ResourcePackManager(ElementSMPRefined plugin, File jarFile) {
        this.plugin = plugin;
        this.jarFile = jarFile;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void start() {
        stop();

        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("resource_pack.enabled", true)) {
            plugin.getLogger().info("Resource pack hosting is disabled in config.yml.");
            return;
        }

        String host = cleanHost(config.getString("resource_pack.host", "localhost"));
        int port = config.getInt("resource_pack.port", 8080);
        String bindAddress = config.getString("resource_pack.bind_address", "0.0.0.0");
        boolean required = config.getBoolean("resource_pack.required", true);
        String prompt = config.getString("resource_pack.prompt", "");

        if (host.isEmpty() || port < 1 || port > 65535) {
            plugin.getLogger()
                    .warning(
                            "resource_pack.host / resource_pack.port in config.yml are invalid, so"
                                    + " the resource pack will not be sent.");
            return;
        }

        try {
            archive = ResourcePackArchive.fromJar(jarFile);
            server = new ResourcePackServer(bindAddress, port, archive);
            server.start();
            url = URI.create("http://" + host + ":" + port + ResourcePackServer.PATH);

            ResourcePackRequest.Builder builder =
                    ResourcePackRequest.resourcePackRequest()
                            .packs(
                                    ResourcePackInfo.resourcePackInfo()
                                            .id(PACK_ID)
                                            .uri(url)
                                            .hash(archive.sha1())
                                            .build())
                            .required(required)
                            .replace(true);
            if (prompt != null && !prompt.isBlank()) {
                builder.prompt(Component.text(prompt));
            }
            request = builder.build();

            plugin.getLogger()
                    .info(
                            "Resource pack ready: "
                                    + archive.fileCount()
                                    + " files, "
                                    + archive.bytes().length / 1024
                                    + " KB, sha1 "
                                    + archive.sha1()
                                    + ", download at "
                                    + url);
        } catch (Exception e) {
            plugin.getLogger()
                    .log(
                            Level.WARNING,
                            "Could not start the resource pack server on port "
                                    + port
                                    + " - is the port already in use? The pack will not be sent.",
                            e);
            stop();
        }
    }

    public void stop() {
        if (server != null) {
            server.stop();
        }
        server = null;
        archive = null;
        request = null;
        url = null;
    }

    public void reload() {
        start();
        sendToAll();
    }

    public boolean isRunning() {
        return request != null;
    }

    public URI getUrl() {
        return url;
    }

    public String getSha1() {
        return archive == null ? null : archive.sha1();
    }

    public int getSizeBytes() {
        return archive == null ? 0 : archive.bytes().length;
    }

    public void send(Player player) {
        if (request != null) {
            player.sendResourcePacks(request);
        }
    }

    public void sendToAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            send(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        send(event.getPlayer());
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) {
            return;
        }
        switch (event.getStatus()) {
            case FAILED_DOWNLOAD, INVALID_URL ->
                    plugin.getLogger()
                            .warning(
                                    event.getPlayer().getName()
                                            + " could not download the resource pack from "
                                            + url
                                            + " - check that resource_pack.host is reachable from"
                                            + " the player and the port is open.");
            case FAILED_RELOAD ->
                    plugin.getLogger()
                            .warning(
                                    event.getPlayer().getName()
                                            + " downloaded the resource pack but it failed to"
                                            + " load.");
            default -> {}
        }
    }

    private static String cleanHost(String raw) {
        String host = raw == null ? "" : raw.trim();
        host = host.replaceFirst("^(?i)https?://", "");
        while (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        return host;
    }
}
