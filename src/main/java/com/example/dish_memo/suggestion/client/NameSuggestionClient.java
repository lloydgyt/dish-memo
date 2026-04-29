package com.example.dish_memo.suggestion.client;

/**
 * Client boundary for multimodal dish name generation.
 */
public interface NameSuggestionClient {

    /**
     * Generates a dish name from the validated image URL.
     *
     * @param imageUrl validated image URL
     * @return generated model result
     */
    ModelNameSuggestion suggest(String imageUrl);
}
