package ru.universitybooking.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@RedisHash("BookingRequest")
public class BookingRequest {

    @Id
    private Long id;
    private Long userId;
    private int room;
    private LocalDateTime bookingDate;

    @Indexed
    private StatusRequest status;

    private LocalDateTime createDate;

}
