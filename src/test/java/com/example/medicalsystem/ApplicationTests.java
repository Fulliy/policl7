package com.example.medicalsystem;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "MEDICAL_DB_PASSWORD", matches = ".+")
class ApplicationTests {

    @Test
    void contextLoads() {
    }

}
