package com.silenker.parkingrecommendation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ParkingRecommendationApplication {

    public static void main(String[] args) {
        SpringApplication.run(ParkingRecommendationApplication.class, args);
    }

}
