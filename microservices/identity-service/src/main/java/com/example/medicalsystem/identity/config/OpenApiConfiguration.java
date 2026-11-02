package com.example.medicalsystem.identity.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "Identity Service API", version = "1.0",
        description = "Регистрация, профиль пользователя и административное управление"))
public class OpenApiConfiguration { }
