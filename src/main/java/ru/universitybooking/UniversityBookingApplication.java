package ru.universitybooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.core.RedisKeyValueAdapter.EnableKeyspaceEvents;
import ru.universitybooking.repository.BasketRepo;
import ru.universitybooking.repository.BookingRepo;
import ru.universitybooking.repository.UniversityServiceRepo;

@SpringBootApplication
@EnableJpaRepositories(basePackageClasses = {BookingRepo.class, UniversityServiceRepo.class})
@EnableRedisRepositories(basePackageClasses = BasketRepo.class, enableKeyspaceEvents = EnableKeyspaceEvents.ON_STARTUP)
public class UniversityBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(UniversityBookingApplication.class, args);
    }

}
