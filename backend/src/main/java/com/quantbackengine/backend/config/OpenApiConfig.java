package com.quantbackengine.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI quantBackEngineOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("QuantBackEngine API")
                        .description("Next-generation quantitative backtesting engine for trading strategies")
                        .version("2.0.0")
                        .contact(new Contact()
                                .name("QuantBackEngine Team")
                                .email("support@quantbackengine.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")));
    }
}
