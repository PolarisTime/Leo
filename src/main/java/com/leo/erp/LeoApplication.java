package com.leo.erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LeoApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeoApplication.class, args);
    }
}
