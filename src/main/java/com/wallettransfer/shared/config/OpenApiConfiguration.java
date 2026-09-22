package com.wallettransfer.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI walletTransferOpenApi() {
        Info info = new Info()
                .title("Wallet and Money Transfer API")
                .version("v1")
                .description("Modular-monolith API. Business endpoints are introduced incrementally.");
        return new OpenAPI().info(info);
    }
}
