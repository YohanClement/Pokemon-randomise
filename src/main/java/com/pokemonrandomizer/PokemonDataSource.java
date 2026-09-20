package com.pokemonrandomizer;

import org.json.JSONObject;

import java.util.List;

/**
 * Abstraction over the PokeAPI calls PokemonService needs, so the service's
 * random-selection and parsing logic can be unit-tested with a fake implementation
 * instead of a real network call (constitution: Testability of Core Logic).
 */
public interface PokemonDataSource {

    List<String> fetchAllPokemonNames() throws PokemonApiException;

    JSONObject fetchPokemonByName(String name) throws PokemonApiException;

    JSONObject fetchSpeciesByName(String speciesSlug) throws PokemonApiException;

    JSONObject fetchTypeByName(String typeSlug) throws PokemonApiException;

    JSONObject fetchFormByName(String formSlug) throws PokemonApiException;
}
