package ru.universitybooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class UniversityBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(UniversityBookingApplication.class, args);
    }

}
