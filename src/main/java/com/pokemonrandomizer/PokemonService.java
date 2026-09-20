package com.pokemonrandomizer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PokemonService {

    static final String DEFAULT_LANGUAGE = "en";
    static final Set<String> VALID_LANGUAGES =
            Set.of("en", "fr", "de", "es", "it", "ja", "ko", "zh-hans", "zh-hant");

    private final PokemonDataSource dataSource;
    private final Random random;
    private final ConcurrentHashMap<String, JSONObject> speciesCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, JSONObject> typeCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, JSONObject> formCache = new ConcurrentHashMap<>();
    private volatile List<String> cachedNames;
    private volatile String lastShownSlug;

    public PokemonService(PokemonDataSource dataSource) {
        this(dataSource, new SecureRandom());
    }

    PokemonService(PokemonDataSource dataSource, Random random) {
        this.dataSource = dataSource;
        this.random = random;
    }

    static String normalizeLanguage(String lang) {
        return (lang != null && VALID_LANGUAGES.contains(lang)) ? lang : DEFAULT_LANGUAGE;
    }

    public Pokemon getRandomPokemon(String lang) throws PokemonApiException {
        List<String> names = getCachedNames();
        String slug = pickNameExcluding(names, lastShownSlug);
        Pokemon pokemon = buildLocalizedPokemon(slug, normalizeLanguage(lang));
        lastShownSlug = slug;
        return pokemon;
    }

    public Pokemon getPokemonBySlug(String slug, String lang) throws PokemonApiException {
        return buildLocalizedPokemon(slug, normalizeLanguage(lang));
    }

    private synchronized List<String> getCachedNames() throws PokemonApiException {
        if (cachedNames == null) {
            List<String> names = dataSource.fetchAllPokemonNames();
            if (names.isEmpty()) {
                throw new PokemonApiException("PokeAPI's Pokemon listing was empty");
            }
            cachedNames = names;
        }
        return cachedNames;
    }

    String pickNameExcluding(List<String> names, String excluded) {
        String candidate = names.get(pickRandomIndex(names.size()));
        if (names.size() <= 1 || excluded == null) {
            return candidate;
        }
        while (candidate.equals(excluded)) {
            candidate = names.get(pickRandomIndex(names.size()));
        }
        return candidate;
    }

    int pickRandomIndex(int size) {
        return random.nextInt(size);
    }

    Pokemon buildLocalizedPokemon(String slug, String lang) throws PokemonApiException {
        JSONObject pokemonJson = dataSource.fetchPokemonByName(slug);
        try {
            String imageUrl = null;
            JSONObject sprites = pokemonJson.optJSONObject("sprites");
            if (sprites != null) {
                imageUrl = sprites.optString("front_default", null);
            }

            List<String> typeSlugs = new ArrayList<>();
            JSONArray typesArray = pokemonJson.optJSONArray("types");
            if (typesArray != null) {
                for (int i = 0; i < typesArray.length(); i++) {
                    typeSlugs.add(typesArray.getJSONObject(i).getJSONObject("type").getString("name"));
                }
            }

            String speciesSlug = slug;
            JSONObject species = pokemonJson.optJSONObject("species");
            if (species != null) {
                speciesSlug = species.optString("name", slug);
            }

            String name = localizedDisplayName(slug, speciesSlug, lang);

            List<PokemonType> types = new ArrayList<>();
            for (String typeSlug : typeSlugs) {
                types.add(new PokemonType(typeSlug, localizedTypeName(typeSlug, lang)));
            }

            return new Pokemon(slug, name, imageUrl, types);
        } catch (JSONException e) {
            throw new PokemonApiException("PokeAPI returned an unexpected response shape: " + e.getMessage(), e);
        }
    }

    private String localizedDisplayName(String slug, String speciesSlug, String lang) throws PokemonApiException {
        JSONObject speciesJson = getSpeciesJson(speciesSlug);
        String speciesName = localizedNameFrom(speciesJson.optJSONArray("names"), lang, speciesSlug);

        if (slug.equals(speciesSlug)) {
            return speciesName;
        }

        JSONObject formJson = getFormJson(slug);

        // Prefer pokemon-form's curated "names" array (reliably covers en/fr/de with a
        // natural, official full name); otherwise fall back to "form_names" (reliably covers
        // all 9 supported languages, but is sometimes a full compound name and sometimes just
        // the qualifier depending on form type — see extractQualifier).
        String source = exactLanguageEntry(formJson.optJSONArray("names"), lang);
        if (source == null) {
            source = exactLanguageEntry(formJson.optJSONArray("form_names"), lang);
        }

        String qualifier = (source != null)
                ? extractQualifier(source, speciesName)
                : titleCase(slugQualifier(slug, speciesSlug)); // last resort: no translation at all

        if (qualifier.isBlank()) {
            return speciesName;
        }
        return speciesName + " (" + qualifier + ")";
    }

    /**
     * Given a source string that may either be just a form qualifier (e.g. "Forme d'Alola")
     * or a complete compound name that already includes the species name (e.g.
     * "Mega Charizard X" or the fused Japanese "メガリザードンＸ"), returns just the qualifier
     * portion, always suitable for display as "{species} ({qualifier})".
     */
    private static String extractQualifier(String source, String speciesName) {
        String lowerSource = source.toLowerCase(Locale.ROOT);
        String lowerSpecies = speciesName.toLowerCase(Locale.ROOT);
        int idx = lowerSource.indexOf(lowerSpecies);
        if (idx < 0) {
            return source.trim();
        }
        // Only clean up stray connector characters immediately at the removal seam (e.g. the
        // "-" left behind by "Méga-Dracaufeu X" -> "Méga-" + " X") — never touch punctuation
        // elsewhere in the remaining text, such as a French elision apostrophe in "d'Alola".
        String before = source.substring(0, idx).replaceAll("[\\s\\-]+$", "");
        String after = source.substring(idx + speciesName.length()).replaceAll("^[\\s\\-]+", "");
        String remainder = (before + " " + after).trim().replaceAll("\\s{2,}", " ");
        return remainder.isEmpty() ? source.trim() : remainder;
    }

    private static String slugQualifier(String slug, String speciesSlug) {
        String qualifier = slug.substring(speciesSlug.length());
        return qualifier.startsWith("-") ? qualifier.substring(1) : qualifier;
    }

    private static String exactLanguageEntry(JSONArray names, String lang) {
        if (names == null) {
            return null;
        }
        for (int i = 0; i < names.length(); i++) {
            JSONObject entry = names.getJSONObject(i);
            String entryLang = entry.getJSONObject("language").optString("name", "");
            if (entryLang.equals(lang)) {
                return entry.optString("name", null);
            }
        }
        return null;
    }

    private String localizedTypeName(String typeSlug, String lang) throws PokemonApiException {
        JSONObject typeJson = getTypeJson(typeSlug);
        return localizedNameFrom(typeJson.optJSONArray("names"), lang, typeSlug);
    }

    private static String localizedNameFrom(JSONArray names, String lang, String fallbackSlug) {
        if (names == null) {
            return titleCase(fallbackSlug);
        }
        String englishFallback = null;
        for (int i = 0; i < names.length(); i++) {
            JSONObject entry = names.getJSONObject(i);
            String entryLang = entry.getJSONObject("language").optString("name", "");
            String entryName = entry.optString("name", null);
            if (entryName == null) {
                continue;
            }
            if (entryLang.equals(lang)) {
                return entryName;
            }
            if (entryLang.equals(DEFAULT_LANGUAGE)) {
                englishFallback = entryName;
            }
        }
        return englishFallback != null ? englishFallback : titleCase(fallbackSlug);
    }

    private JSONObject getSpeciesJson(String speciesSlug) throws PokemonApiException {
        JSONObject cached = speciesCache.get(speciesSlug);
        if (cached != null) {
            return cached;
        }
        JSONObject fetched = dataSource.fetchSpeciesByName(speciesSlug);
        speciesCache.put(speciesSlug, fetched);
        return fetched;
    }

    private JSONObject getTypeJson(String typeSlug) throws PokemonApiException {
        JSONObject cached = typeCache.get(typeSlug);
        if (cached != null) {
            return cached;
        }
        JSONObject fetched = dataSource.fetchTypeByName(typeSlug);
        typeCache.put(typeSlug, fetched);
        return fetched;
    }

    private JSONObject getFormJson(String slug) throws PokemonApiException {
        JSONObject cached = formCache.get(slug);
        if (cached != null) {
            return cached;
        }
        JSONObject fetched = dataSource.fetchFormByName(slug);
        formCache.put(slug, fetched);
        return fetched;
    }

    private static String titleCase(String hyphenated) {
        String[] words = hyphenated.split("-");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return result.toString();
    }
}
