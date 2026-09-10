package ru.universitybooking.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Data
@NoArgsConstructor
@AllArgsConstructor
@RedisHash("BasketItem")
public class BasketItem {
    @Id
    private Long id;

    @Indexed
    private Long userId;

    private Long serviceId;

    private int room;
    private LocalDateTime bookingDate;
    private LocalDateTime addedAt;

    @TimeToLive(unit = TimeUnit.SECONDS)
    private Long ttl;
}
