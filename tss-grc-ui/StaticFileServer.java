import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.*;
import java.util.Map;

/**
 * Standalone static file server for tss-grc-ui.
 * Serves the frontend on http://localhost:8086
 *
 * Run from the tss-grc-ui/ directory:
 *   java StaticFileServer.java
 */
public class StaticFileServer {

    private static final int PORT = 8086;
    private static final Path ROOT = Path.of(".").toAbsolutePath().normalize();

    private static final Map<String, String> MIME_TYPES = Map.of(
        "html", "text/html; charset=UTF-8",
        "css",  "text/css; charset=UTF-8",
        "js",   "application/javascript; charset=UTF-8",
        "json", "application/json",
        "png",  "image/png",
        "jpg",  "image/jpeg",
        "jpeg", "image/jpeg",
        "svg",  "image/svg+xml",
        "ico",  "image/x-icon"
    );

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new StaticHandler());
        server.start();
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║   TSS GRC UI  →  http://localhost:" + PORT + "/login.html  ║");
        System.out.println("║   Root: " + ROOT + "  ║");
        System.out.println("║   Press Ctrl+C to stop                           ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
    }

    static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            URI uri = exchange.getRequestURI();
            String rawPath = uri.getPath();

            // Default to login.html for root
            if ("/".equals(rawPath) || rawPath.isEmpty()) {
                rawPath = "/login.html";
            }

            // Prevent path traversal
            Path filePath = ROOT.resolve(rawPath.substring(1)).normalize();
            if (!filePath.startsWith(ROOT)) {
                sendError(exchange, 403, "Forbidden");
                return;
            }

            if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                sendError(exchange, 404, "Not Found: " + rawPath);
                return;
            }

            String ext = getExtension(filePath.getFileName().toString());
            String mime = MIME_TYPES.getOrDefault(ext, "application/octet-stream");

            byte[] body = Files.readAllBytes(filePath);
            exchange.getResponseHeaders().set("Content-Type", mime);
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }

        private void sendError(HttpExchange exchange, int code, String msg) throws IOException {
            byte[] body = msg.getBytes();
            exchange.sendResponseHeaders(code, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }

        private String getExtension(String filename) {
            int dot = filename.lastIndexOf('.');
            return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
        }
    }
}
