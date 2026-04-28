package com.example.dish_memo.suggestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Configuration for dish name suggestion model calls and image URL validation.
 */
@ConfigurationProperties(prefix = "dish-memo.suggestion")
public class SuggestionProperties {
    private final Bailian bailian = new Bailian();
    private List<String> imageUrlAllowedHosts = List.of();

    /**
     * Returns Bailian model settings.
     *
     * @return Bailian settings
     */
    public Bailian getBailian() {
        return bailian;
    }

    /**
     * Returns exact or parent domains allowed for image URL input.
     *
     * @return image URL allowlist
     */
    public List<String> getImageUrlAllowedHosts() {
        return imageUrlAllowedHosts;
    }

    /**
     * Sets exact or parent domains allowed for image URL input.
     *
     * @param imageUrlAllowedHosts image URL allowlist
     */
    public void setImageUrlAllowedHosts(List<String> imageUrlAllowedHosts) {
        this.imageUrlAllowedHosts = imageUrlAllowedHosts == null ? List.of() : imageUrlAllowedHosts;
    }

    /**
     * Bailian multimodal model settings.
     */
    public static class Bailian {
        private String apiKey;
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        private String model = "qwen3.6-flash";
        private Duration connectTimeout = Duration.ofSeconds(3);
        private Duration readTimeout = Duration.ofSeconds(15);

        /**
         * Returns the Bailian API key.
         *
         * @return API key
         */
        public String getApiKey() {
            return apiKey;
        }

        /**
         * Sets the Bailian API key.
         *
         * @param apiKey API key
         */
        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        /**
         * Returns the OpenAI-compatible Bailian base URL.
         *
         * @return base URL
         */
        public String getBaseUrl() {
            return baseUrl;
        }

        /**
         * Sets the OpenAI-compatible Bailian base URL.
         *
         * @param baseUrl base URL
         */
        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        /**
         * Returns the model name used for image and text input.
         *
         * @return model name
         */
        public String getModel() {
            return model;
        }

        /**
         * Sets the model name used for image and text input.
         *
         * @param model model name
         */
        public void setModel(String model) {
            this.model = model;
        }

        /**
         * Returns the HTTP connect timeout.
         *
         * @return connect timeout
         */
        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        /**
         * Sets the HTTP connect timeout.
         *
         * @param connectTimeout connect timeout
         */
        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        /**
         * Returns the HTTP read timeout.
         *
         * @return read timeout
         */
        public Duration getReadTimeout() {
            return readTimeout;
        }

        /**
         * Sets the HTTP read timeout.
         *
         * @param readTimeout read timeout
         */
        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }
    }
}
