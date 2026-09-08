package ru.universitybooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.core.RedisKeyValueAdapter.EnableKeyspaceEvents;

@SpringBootApplication
@EnableRedisRepositories(enableKeyspaceEvents = EnableKeyspaceEvents.ON_STARTUP)
public class UniversityBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(UniversityBookingApplication.class, args);
    }

}
