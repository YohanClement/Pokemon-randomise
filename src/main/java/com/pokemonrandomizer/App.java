package com.pokemonrandomizer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class App {

    private static final int PORT = 8080;
    private static final String RANDOM_POKEMON_PATH = "/api/random-pokemon";
    private static final String POKEMON_BY_SLUG_PREFIX = "/api/pokemon/";

    public static void main(String[] args) throws IOException {
        PokemonService service = new PokemonService(new PokemonClient());

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext(RANDOM_POKEMON_PATH, new RandomPokemonHandler(service));
        server.createContext(POKEMON_BY_SLUG_PREFIX, new PokemonBySlugHandler(service));
        server.createContext("/", new StaticFileHandler());
        server.setExecutor(null);
        server.start();

        System.out.println("Pokemon Randomizer listening on http://localhost:" + PORT);
    }

    private static String queryParam(HttpExchange exchange, String key) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String pairKey = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            if (pairKey.equals(key)) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static JSONObject toJson(Pokemon pokemon) {
        JSONObject json = new JSONObject();
        json.put("slug", pokemon.slug());
        json.put("name", pokemon.name());
        if (pokemon.imageUrl() != null) {
            json.put("imageUrl", pokemon.imageUrl());
        }
        JSONArray types = new JSONArray();
        for (PokemonType type : pokemon.types()) {
            types.put(new JSONObject().put("slug", type.slug()).put("name", type.name()));
        }
        json.put("types", types);
        return json;
    }

    private static void writeJson(HttpExchange exchange, int status, JSONObject body) throws IOException {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void writeError(HttpExchange exchange, String logContext, PokemonApiException e)
            throws IOException {
        System.err.println("[" + logContext + "] request failed: " + e.getMessage());
        if (e.getCause() != null) {
            e.getCause().printStackTrace();
        }
        JSONObject error = new JSONObject().put("error", "Couldn't reach the Pokemon service — please try again.");
        writeJson(exchange, 502, error);
    }

    private static class RandomPokemonHandler implements HttpHandler {
        private final PokemonService service;

        RandomPokemonHandler(PokemonService service) {
            this.service = service;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }

            try {
                Pokemon pokemon = service.getRandomPokemon(queryParam(exchange, "lang"));
                writeJson(exchange, 200, toJson(pokemon));
            } catch (PokemonApiException e) {
                writeError(exchange, "random-pokemon", e);
            }
        }
    }

    private static class PokemonBySlugHandler implements HttpHandler {
        private final PokemonService service;

        PokemonBySlugHandler(PokemonService service) {
            this.service = service;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String slug = path.substring(POKEMON_BY_SLUG_PREFIX.length());
            if (slug.isBlank()) {
                exchange.sendResponseHeaders(400, -1);
                exchange.close();
                return;
            }

            try {
                Pokemon pokemon = service.getPokemonBySlug(slug, queryParam(exchange, "lang"));
                writeJson(exchange, 200, toJson(pokemon));
            } catch (PokemonApiException e) {
                writeError(exchange, "pokemon-by-slug", e);
            }
        }
    }

    private static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }
            if (path.contains("..")) {
                exchange.sendResponseHeaders(400, -1);
                exchange.close();
                return;
            }

            String resourcePath = "static" + path;
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (in == null) {
                    byte[] body = "Not found".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(404, body.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(body);
                    }
                    return;
                }

                byte[] body = in.readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", contentTypeFor(path));
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            }
        }

        private static String contentTypeFor(String path) {
            if (path.endsWith(".html")) return "text/html; charset=utf-8";
            if (path.endsWith(".css")) return "text/css; charset=utf-8";
            if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
            return "application/octet-stream";
        }
    }
}
