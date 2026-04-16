package com.hotel.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HotelAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(HotelAiApplication.class, args);
    }
}
