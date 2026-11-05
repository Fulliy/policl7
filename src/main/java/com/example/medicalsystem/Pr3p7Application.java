package com.example.medicalsystem;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.example.medicalsystem.config.SchedulingProperties;

@SpringBootApplication
@EnableConfigurationProperties(SchedulingProperties.class)
public class Pr3p7Application {
    public static void main(String[] args) {
        SpringApplication.run(Pr3p7Application.class, args);
    }
}
