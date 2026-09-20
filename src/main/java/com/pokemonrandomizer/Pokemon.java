package com.pokemonrandomizer;

import java.util.List;

public record Pokemon(String slug, String name, String imageUrl, List<PokemonType> types) {
}
