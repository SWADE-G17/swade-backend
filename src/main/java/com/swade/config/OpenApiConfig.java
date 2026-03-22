package com.swade.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SWADE MRI Processing API")
                        .version("1.0")
                        .description("API for uploading NIfTI MRI files and receiving AI model predictions with processed NIfTI output.")
                        .contact(new Contact()
                                .name("SWADE Backend")));
    }
}
