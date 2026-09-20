package com.pokemonrandomizer;

public class PokemonApiException extends Exception {

    public PokemonApiException(String message) {
        super(message);
    }

    public PokemonApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
