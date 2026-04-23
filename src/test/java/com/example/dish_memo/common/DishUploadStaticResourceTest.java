package com.example.dish_memo.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig
@ContextConfiguration(classes = DishUploadStaticResourceTest.TestConfig.class)
@TestPropertySource(properties = "app.upload.base-dir=target/test-uploads/static-resources")
class DishUploadStaticResourceTest {
    private static final Path UPLOAD_BASE_DIR = Path.of("target/test-uploads/static-resources");
    private static final byte[] PNG_BYTES = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47,
            0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D,
            0x49, 0x48, 0x44, 0x52
    };

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private DishUploadStaticResourceFilter dishUploadStaticResourceFilter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(dishUploadStaticResourceFilter)
                .build();

        Files.createDirectories(UPLOAD_BASE_DIR.resolve("dish"));
        Files.write(UPLOAD_BASE_DIR.resolve("dish/img_1.png"), PNG_BYTES);
        Files.writeString(UPLOAD_BASE_DIR.resolve("dish/readme.txt"), "not an image");
        Files.write(UPLOAD_BASE_DIR.resolve("secret.png"), PNG_BYTES);
    }

    @Test
    void dishImageRequestReturnsBytesAndMimeType() throws Exception {
        mockMvc.perform(get("/uploads/dish/img_1.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG_BYTES));
    }

    @Test
    void nonImageExtensionReturnsNotFound() throws Exception {
        mockMvc.perform(get("/uploads/dish/readme.txt"))
                .andExpect(status().isNotFound());
    }

    @Test
    void executableExtensionReturnsNotFound() throws Exception {
        mockMvc.perform(get("/uploads/dish/shell.jsp"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rawTraversalIsBlockedBeforeResourceLookup() throws Exception {
        mockMvc.perform(get("/uploads/dish/../secret.png"))
                .andExpect(status().isNotFound());
    }

    @Test
    void encodedTraversalIsBlockedBeforeResourceLookup() throws Exception {
        mockMvc.perform(get("/uploads/dish/%2e%2e/secret.png"))
                .andExpect(status().isNotFound());
    }

    @Test
    void doubleEncodedTraversalIsBlockedBeforeResourceLookup() throws Exception {
        mockMvc.perform(get("/uploads/dish/%252e%252e%252fsecret.png"))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadPathsOutsideDishAreNotExposed() throws Exception {
        mockMvc.perform(get("/uploads/secret.png"))
                .andExpect(status().isNotFound());
    }

    @Configuration
    @EnableWebMvc
    static class TestConfig {
        /**
         * Supplies the API user interceptor required by the MVC configuration under test.
         *
         * @return user context interceptor
         */
        @Bean
        UserContextInterceptor userContextInterceptor() {
            return new UserContextInterceptor();
        }

        /**
         * Registers the production MVC static resource configuration.
         *
         * @param userContextInterceptor user identity interceptor
         * @return web configuration
         */
        @Bean
        WebConfig webConfig(UserContextInterceptor userContextInterceptor) {
            return new WebConfig(userContextInterceptor, UPLOAD_BASE_DIR.toString());
        }

        /**
         * Registers the production static resource request guard.
         *
         * @return dish upload static resource filter
         */
        @Bean
        DishUploadStaticResourceFilter dishUploadStaticResourceFilter() {
            return new DishUploadStaticResourceFilter();
        }
    }
}
