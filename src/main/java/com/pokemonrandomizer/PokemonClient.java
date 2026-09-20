package com.pokemonrandomizer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class PokemonClient implements PokemonDataSource {

    private static final String BASE_URL = "https://pokeapi.co/api/v2";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;

    public PokemonClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    @Override
    public List<String> fetchAllPokemonNames() throws PokemonApiException {
        // PokeAPI's numeric Pokemon IDs are not contiguous (alternate forms/variants
        // live at IDs 10000+), so the only reliable way to pick a uniformly random,
        // always-valid Pokemon is to enumerate every entry's name via this listing
        // endpoint (fetched once, cached by PokemonService) rather than guessing a
        // random numeric ID in [1, count].
        JSONObject body = get(BASE_URL + "/pokemon?limit=100000&offset=0");
        try {
            JSONArray results = body.getJSONArray("results");
            List<String> names = new ArrayList<>(results.length());
            for (int i = 0; i < results.length(); i++) {
                names.add(results.getJSONObject(i).getString("name"));
            }
            return names;
        } catch (JSONException e) {
            throw new PokemonApiException("PokeAPI listing response had an unexpected shape: " + e.getMessage(), e);
        }
    }

    @Override
    public JSONObject fetchPokemonByName(String name) throws PokemonApiException {
        return get(BASE_URL + "/pokemon/" + name);
    }

    @Override
    public JSONObject fetchSpeciesByName(String speciesSlug) throws PokemonApiException {
        return get(BASE_URL + "/pokemon-species/" + speciesSlug);
    }

    @Override
    public JSONObject fetchTypeByName(String typeSlug) throws PokemonApiException {
        return get(BASE_URL + "/type/" + typeSlug);
    }

    @Override
    public JSONObject fetchFormByName(String formSlug) throws PokemonApiException {
        return get(BASE_URL + "/pokemon-form/" + formSlug);
    }

    private JSONObject get(String url) throws PokemonApiException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PokemonApiException("PokeAPI returned status " + response.statusCode() + " for " + url);
            }
            return new JSONObject(response.body());
        } catch (IOException e) {
            throw new PokemonApiException("Network error calling PokeAPI: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PokemonApiException("Request to PokeAPI was interrupted", e);
        } catch (JSONException e) {
            throw new PokemonApiException("PokeAPI returned an unexpected response shape: " + e.getMessage(), e);
        }
    }
}
