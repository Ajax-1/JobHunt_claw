package io.github.ajax1.campusradar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class CampusRadarApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusRadarApplication.class, args);
    }
}
