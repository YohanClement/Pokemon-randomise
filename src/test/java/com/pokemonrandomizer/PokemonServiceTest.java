package com.pokemonrandomizer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PokemonServiceTest {

    private static JSONObject pokemonJson(String slug, String speciesSlug, String imageUrl, String... typeSlugs) {
        JSONObject sprites = new JSONObject();
        if (imageUrl != null) {
            sprites.put("front_default", imageUrl);
        }
        JSONArray types = new JSONArray();
        int slot = 1;
        for (String typeSlug : typeSlugs) {
            types.put(new JSONObject().put("slot", slot++).put("type", new JSONObject().put("name", typeSlug)));
        }
        return new JSONObject()
                .put("name", slug)
                .put("species", new JSONObject().put("name", speciesSlug))
                .put("sprites", sprites)
                .put("types", types);
    }

    private static JSONArray namesArray(Map<String, String> byLang) {
        JSONArray arr = new JSONArray();
        byLang.forEach((lang, name) ->
                arr.put(new JSONObject().put("name", name).put("language", new JSONObject().put("name", lang))));
        return arr;
    }

    private static JSONObject namesJson(Map<String, String> byLang) {
        return new JSONObject().put("names", namesArray(byLang));
    }

    /** Builds a pokemon-form fixture: curated "names" (full names) and "form_names" (descriptors/full names). */
    private static JSONObject formJson(Map<String, String> curatedNames, Map<String, String> formNames) {
        return new JSONObject()
                .put("names", namesArray(curatedNames))
                .put("form_names", namesArray(formNames));
    }

    /** A data source backed by explicit per-slug fixtures for pokemon/species/type/form responses. */
    private static class FakeDataSource implements PokemonDataSource {
        private final List<String> names;
        private final Map<String, JSONObject> pokemonBySlug;
        private final Map<String, JSONObject> speciesBySlug;
        private final Map<String, JSONObject> typeBySlug;
        private final Map<String, JSONObject> formBySlug;
        private final PokemonApiException failure;

        FakeDataSource(List<String> names, Map<String, JSONObject> pokemonBySlug,
                        Map<String, JSONObject> speciesBySlug, Map<String, JSONObject> typeBySlug) {
            this(names, pokemonBySlug, speciesBySlug, typeBySlug, Map.of());
        }

        FakeDataSource(List<String> names, Map<String, JSONObject> pokemonBySlug,
                        Map<String, JSONObject> speciesBySlug, Map<String, JSONObject> typeBySlug,
                        Map<String, JSONObject> formBySlug) {
            this.names = names;
            this.pokemonBySlug = pokemonBySlug;
            this.speciesBySlug = speciesBySlug;
            this.typeBySlug = typeBySlug;
            this.formBySlug = formBySlug;
            this.failure = null;
        }

        FakeDataSource(PokemonApiException failure) {
            this.names = List.of("bulbasaur");
            this.pokemonBySlug = Map.of();
            this.speciesBySlug = Map.of();
            this.typeBySlug = Map.of();
            this.formBySlug = Map.of();
            this.failure = failure;
        }

        @Override
        public List<String> fetchAllPokemonNames() {
            return names;
        }

        @Override
        public JSONObject fetchPokemonByName(String name) throws PokemonApiException {
            if (failure != null) {
                throw failure;
            }
            JSONObject json = pokemonBySlug.get(name);
            if (json == null) {
                throw new PokemonApiException("no fixture for pokemon: " + name);
            }
            return json;
        }

        @Override
        public JSONObject fetchSpeciesByName(String speciesSlug) throws PokemonApiException {
            JSONObject json = speciesBySlug.get(speciesSlug);
            if (json == null) {
                throw new PokemonApiException("no fixture for species: " + speciesSlug);
            }
            return json;
        }

        @Override
        public JSONObject fetchTypeByName(String typeSlug) throws PokemonApiException {
            JSONObject json = typeBySlug.get(typeSlug);
            if (json == null) {
                throw new PokemonApiException("no fixture for type: " + typeSlug);
            }
            return json;
        }

        @Override
        public JSONObject fetchFormByName(String formSlug) throws PokemonApiException {
            JSONObject json = formBySlug.get(formSlug);
            if (json == null) {
                throw new PokemonApiException("no fixture for form: " + formSlug);
            }
            return json;
        }
    }

    /** A minimal data source that echoes back whatever slug/name it's asked for, for repeat-avoidance tests. */
    private static class EchoDataSource implements PokemonDataSource {
        private final List<String> names;
        final AtomicInteger speciesCalls = new AtomicInteger();
        final AtomicInteger typeCalls = new AtomicInteger();

        EchoDataSource(List<String> names) {
            this.names = names;
        }

        @Override
        public List<String> fetchAllPokemonNames() {
            return names;
        }

        @Override
        public JSONObject fetchPokemonByName(String name) {
            return pokemonJson(name, name, null);
        }

        @Override
        public JSONObject fetchSpeciesByName(String speciesSlug) {
            speciesCalls.incrementAndGet();
            return namesJson(Map.of("en", speciesSlug));
        }

        @Override
        public JSONObject fetchTypeByName(String typeSlug) {
            typeCalls.incrementAndGet();
            return namesJson(Map.of("en", typeSlug));
        }

        @Override
        public JSONObject fetchFormByName(String formSlug) {
            // Never called: every EchoDataSource pokemon has slug == speciesSlug (no variants).
            return formJson(Map.of(), Map.of());
        }
    }

    @Test
    void pickRandomIndex_staysWithinListBounds() {
        PokemonService service = new PokemonService(new EchoDataSource(List.of("a")), new Random(42));
        int size = 1351; // matches PokeAPI's real listing size at time of writing
        for (int i = 0; i < 1000; i++) {
            int index = service.pickRandomIndex(size);
            assertTrue(index >= 0 && index < size, "index " + index + " out of range [0, " + size + ")");
        }
    }

    @Test
    void normalizeLanguage_acceptsKnownCodes_andDefaultsOtherwiseToEnglish() {
        assertEquals("fr", PokemonService.normalizeLanguage("fr"));
        assertEquals("en", PokemonService.normalizeLanguage(null));
        assertEquals("en", PokemonService.normalizeLanguage("xx-not-a-language"));
    }

    @Test
    void buildLocalizedPokemon_producesLocalizedNameAndTypes() throws PokemonApiException {
        Map<String, JSONObject> pokemon = Map.of(
                "pikachu", pokemonJson("pikachu", "pikachu", "https://example.test/pikachu.png", "electric"));
        Map<String, JSONObject> species = Map.of(
                "pikachu", namesJson(Map.of("en", "Pikachu", "fr", "Pikachu")));
        Map<String, JSONObject> types = Map.of(
                "electric", namesJson(Map.of("en", "Electric", "fr", "Électrik")));

        PokemonService service = new PokemonService(
                new FakeDataSource(List.of("pikachu"), pokemon, species, types));

        Pokemon result = service.buildLocalizedPokemon("pikachu", "fr");

        assertEquals("pikachu", result.slug());
        assertEquals("Pikachu", result.name());
        assertEquals("https://example.test/pikachu.png", result.imageUrl());
        assertEquals(List.of(new PokemonType("electric", "Électrik")), result.types());
    }

    @Test
    void buildLocalizedPokemon_fallsBackToEnglish_whenTranslationMissing() throws PokemonApiException {
        Map<String, JSONObject> pokemon = Map.of(
                "ditto", pokemonJson("ditto", "ditto", null, "normal"));
        Map<String, JSONObject> species = Map.of(
                "ditto", namesJson(Map.of("en", "Ditto"))); // no "fr" entry
        Map<String, JSONObject> types = Map.of(
                "normal", namesJson(Map.of("en", "Normal"))); // no "fr" entry

        PokemonService service = new PokemonService(
                new FakeDataSource(List.of("ditto"), pokemon, species, types));

        Pokemon result = service.buildLocalizedPokemon("ditto", "fr");

        assertEquals("Ditto", result.name());
        assertEquals(List.of(new PokemonType("normal", "Normal")), result.types());
    }

    @Test
    void buildLocalizedPokemon_missingImageAndNoTypes_producesNameWithNullImageAndEmptyTypes()
            throws PokemonApiException {
        Map<String, JSONObject> pokemon = Map.of(
                "missingno", pokemonJson("missingno", "missingno", null));
        Map<String, JSONObject> species = Map.of(
                "missingno", namesJson(Map.of("en", "MissingNo.")));

        PokemonService service = new PokemonService(
                new FakeDataSource(List.of("missingno"), pokemon, species, Map.of()));

        Pokemon result = service.buildLocalizedPokemon("missingno", "en");

        assertEquals("MissingNo.", result.name());
        assertNull(result.imageUrl());
        assertTrue(result.types().isEmpty());
    }

    // --- Variant/form translation (pokemon-form endpoint) ---

    @Test
    void variantForm_extractsQualifierFromCuratedFormName_whenPokemonFormNamesHasTheLanguage()
            throws PokemonApiException {
        Map<String, JSONObject> pokemon = Map.of(
                "charizard-mega-x", pokemonJson("charizard-mega-x", "charizard", null, "fire", "dragon"));
        Map<String, JSONObject> species = Map.of(
                "charizard", namesJson(Map.of("en", "Charizard", "fr", "Dracaufeu")));
        Map<String, JSONObject> types = new HashMap<>();
        types.put("fire", namesJson(Map.of("en", "Fire", "fr", "Feu")));
        types.put("dragon", namesJson(Map.of("en", "Dragon", "fr", "Dragon")));
        Map<String, JSONObject> forms = Map.of(
                "charizard-mega-x", formJson(
                        Map.of("fr", "Méga-Dracaufeu X", "de", "Mega-Glurak X"), // curated "names"
                        Map.of("fr", "Méga-Dracaufeu X", "ja", "メガリザードンＸ"))); // "form_names"

        PokemonService service = new PokemonService(
                new FakeDataSource(List.of("charizard-mega-x"), pokemon, species, types, forms));

        Pokemon result = service.buildLocalizedPokemon("charizard-mega-x", "fr");

        // "Méga-Dracaufeu X" minus the species name "Dracaufeu" leaves the qualifier "Méga X",
        // always shown in parentheses after the species name — never the fused form as-is.
        assertEquals("Dracaufeu (Méga X)", result.name());
    }

    @Test
    void variantForm_extractsQualifierFromFormNamesDescriptor_whenItAlreadyContainsTheSpeciesName()
            throws PokemonApiException {
        Map<String, JSONObject> pokemon = Map.of(
                "charizard-mega-x", pokemonJson("charizard-mega-x", "charizard", null));
        Map<String, JSONObject> species = Map.of(
                "charizard", namesJson(Map.of("en", "Charizard", "ja", "リザードン")));
        Map<String, JSONObject> forms = Map.of(
                // No curated "names" entry for ja, but "form_names" already has the full compound name.
                "charizard-mega-x", formJson(Map.of(), Map.of("ja", "メガリザードンＸ")));

        PokemonService service = new PokemonService(
                new FakeDataSource(List.of("charizard-mega-x"), pokemon, species, Map.of(), forms));

        Pokemon result = service.buildLocalizedPokemon("charizard-mega-x", "ja");

        assertEquals("リザードン (メガ Ｘ)", result.name());
    }

    @Test
    void variantForm_combinesSpeciesNameWithFormNamesDescriptor_whenItIsJustAQualifier()
            throws PokemonApiException {
        Map<String, JSONObject> pokemon = Map.of(
                "raichu-alola", pokemonJson("raichu-alola", "raichu", null));
        Map<String, JSONObject> species = Map.of(
                "raichu", namesJson(Map.of("en", "Raichu", "fr", "Raichu")));
        Map<String, JSONObject> forms = Map.of(
                // "form_names" fr entry is just the qualifier, doesn't contain "Raichu".
                "raichu-alola", formJson(Map.of(), Map.of("fr", "Forme d’Alola")));

        PokemonService service = new PokemonService(
                new FakeDataSource(List.of("raichu-alola"), pokemon, species, Map.of(), forms));

        Pokemon result = service.buildLocalizedPokemon("raichu-alola", "fr");

        assertEquals("Raichu (Forme d’Alola)", result.name());
    }

    @Test
    void variantForm_preservesApostrophesElsewhereInTheQualifier_whenSpeciesNameIsAtTheStart()
            throws PokemonApiException {
        Map<String, JSONObject> pokemon = Map.of(
                "raichu-alola", pokemonJson("raichu-alola", "raichu", null));
        Map<String, JSONObject> species = Map.of(
                "raichu", namesJson(Map.of("en", "Raichu", "fr", "Raichu")));
        Map<String, JSONObject> forms = Map.of(
                // Curated "names" fr entry starts with the species name itself, followed by an
                // elision apostrophe ("d'Alola") that must survive extraction untouched.
                "raichu-alola", formJson(Map.of("fr", "Raichu d’Alola"), Map.of()));

        PokemonService service = new PokemonService(
                new FakeDataSource(List.of("raichu-alola"), pokemon, species, Map.of(), forms));

        Pokemon result = service.buildLocalizedPokemon("raichu-alola", "fr");

        assertEquals("Raichu (d’Alola)", result.name());
    }

    @Test
    void variantForm_fallsBackToEnglishSlugQualifier_whenNoFormTranslationExistsAtAll()
            throws PokemonApiException {
        Map<String, JSONObject> pokemon = Map.of(
                "charizard-mega-x", pokemonJson("charizard-mega-x", "charizard", null));
        Map<String, JSONObject> species = Map.of(
                "charizard", namesJson(Map.of("en", "Charizard", "fr", "Dracaufeu")));
        Map<String, JSONObject> forms = Map.of(
                "charizard-mega-x", formJson(Map.of(), Map.of())); // no data at all

        PokemonService service = new PokemonService(
                new FakeDataSource(List.of("charizard-mega-x"), pokemon, species, Map.of(), forms));

        Pokemon result = service.buildLocalizedPokemon("charizard-mega-x", "fr");

        assertEquals("Dracaufeu (Mega X)", result.name());
    }

    @Test
    void getRandomPokemon_clientFailure_surfacesAsHandledException() {
        PokemonApiException failure = new PokemonApiException("network down");
        PokemonService service = new PokemonService(new FakeDataSource(failure));

        assertThrows(PokemonApiException.class, () -> service.getRandomPokemon("en"));
    }

    @Test
    void getRandomPokemon_usesEveryNameInTheList_neverAnOutOfRangeGuess() throws PokemonApiException {
        Map<String, JSONObject> pokemon = Map.of("eevee", pokemonJson("eevee", "eevee", null));
        Map<String, JSONObject> species = Map.of("eevee", namesJson(Map.of("en", "Eevee")));
        PokemonService service = new PokemonService(
                new FakeDataSource(List.of("eevee"), pokemon, species, Map.of()), new Random(1));

        Pokemon result = service.getRandomPokemon("en");

        assertEquals("eevee", result.slug());
        assertEquals("Eevee", result.name());
    }

    @Test
    void getRandomPokemon_neverRepeatsTheImmediatelyPreviousPick() throws PokemonApiException {
        PokemonService service = new PokemonService(
                new EchoDataSource(List.of("bulbasaur", "charmander", "squirtle")), new Random(7));

        String previous = null;
        for (int i = 0; i < 200; i++) {
            Pokemon pokemon = service.getRandomPokemon("en");
            if (previous != null) {
                assertNotEquals(previous, pokemon.slug(), "repeated on iteration " + i);
            }
            previous = pokemon.slug();
        }
    }

    @Test
    void pickNameExcluding_withOnlyOneName_stillReturnsItWithoutLooping() {
        PokemonService service = new PokemonService(new EchoDataSource(List.of("ditto")), new Random(3));

        String result = service.pickNameExcluding(List.of("ditto"), "ditto");

        assertEquals("ditto", result);
    }

    @Test
    void getRandomPokemon_withOnlyOnePokemonAvailable_stillReturnsItAcrossCalls() throws PokemonApiException {
        PokemonService service = new PokemonService(new EchoDataSource(List.of("ditto")));

        Pokemon first = service.getRandomPokemon("en");
        Pokemon second = service.getRandomPokemon("en");

        assertEquals("ditto", first.slug());
        assertEquals("ditto", second.slug());
    }

    @Test
    void getPokemonBySlug_reusesCachedSpeciesAndTypeData_acrossRepeatedCalls() throws PokemonApiException {
        EchoDataSource dataSource = new EchoDataSource(List.of("pikachu"));
        PokemonService service = new PokemonService(dataSource);

        service.getPokemonBySlug("pikachu", "en");
        service.getPokemonBySlug("pikachu", "fr");
        service.getPokemonBySlug("pikachu", "de");

        assertEquals(1, dataSource.speciesCalls.get(),
                "species data should be fetched once and cached, not once per language switch");
    }
}
