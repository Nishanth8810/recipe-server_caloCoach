package springboot.microservices.recipesservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient

public class RecipesServicesApplication {
    public static void main(String[] args) {
        SpringApplication.run(RecipesServicesApplication.class);
    }
}