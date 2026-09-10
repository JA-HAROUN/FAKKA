package com.oae.fakka.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fakkaOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Fakka API")
                .version("v1")
                .description("Group expense management backend. "
                        + "Every failing request returns the shared ErrorResponse shape: "
                        + "{timestamp, status, error, message, path}."));
    }
}
