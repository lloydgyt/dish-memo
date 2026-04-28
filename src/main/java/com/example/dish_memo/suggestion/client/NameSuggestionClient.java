package com.example.dish_memo.suggestion.client;

/**
 * Client boundary for multimodal dish name generation.
 */
public interface NameSuggestionClient {

    /**
     * Generates a dish name from image URL and prompt input.
     *
     * @param imageUrl validated image URL
     * @param prompt user prompt or service default
     * @return generated model result
     */
    ModelNameSuggestion suggest(String imageUrl, String prompt);
}
