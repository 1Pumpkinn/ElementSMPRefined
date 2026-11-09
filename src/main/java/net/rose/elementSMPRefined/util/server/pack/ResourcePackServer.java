package net.rose.elementSMPRefined.util.server.pack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tiny built-in HTTP server that hands out the resource pack at {@link #PATH}. Players' clients
 * download the pack from here, so no external file host is needed.
 */
public final class ResourcePackServer {
    public static final String PATH = "/pack.zip";

    private final HttpServer server;
    private final ExecutorService executor;
    private final ResourcePackArchive archive;

    public ResourcePackServer(String bindAddress, int port, ResourcePackArchive archive)
            throws IOException {
        this.archive = archive;
        InetSocketAddress address =
                bindAddress.isBlank()
                        ? new InetSocketAddress(port)
                        : new InetSocketAddress(bindAddress, port);
        this.server = HttpServer.create(address, 0);
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.server.setExecutor(executor);
        this.server.createContext(PATH, this::handle);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String method = exchange.getRequestMethod();
            boolean head = method.equalsIgnoreCase("HEAD");
            if (!head && !method.equalsIgnoreCase("GET")) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            byte[] bytes = archive.bytes();
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            if (head) {
                exchange.getResponseHeaders().set("Content-Length", String.valueOf(bytes.length));
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
