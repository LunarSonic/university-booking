package ru.universitybooking.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import ru.universitybooking.dto.BasketItemRequest;
import ru.universitybooking.dto.BasketItemResponse;
import ru.universitybooking.entity.BasketItem;
import ru.universitybooking.repository.BasketRepo;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BasketService {

    private final BasketRepo basketRepo;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${basket.ttl-seconds}")
    private Long ttlSeconds;

    public BasketService(BasketRepo basketRepo, StringRedisTemplate stringRedisTemplate) {
        this.basketRepo = basketRepo;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public BasketItemResponse addItem(BasketItemRequest dto) {
        BasketItem item = new BasketItem();
        item.setId(stringRedisTemplate.opsForValue().increment("BasketItem:sequence"));
        item.setUserId(dto.userId());
        item.setRoom(dto.room());
        item.setAddedAt(LocalDateTime.now());
        item.setBookingDate(dto.bookingDate());
        item.setTtl(ttlSeconds);
        BasketItem saved = basketRepo.save(item);
        return toDto(saved);
    }

    public void deleteItem(Long id) {
        basketRepo.deleteById(id);
    }

    public List<BasketItemResponse> getBaskets(Long userId) {
        return basketRepo.findByUserId(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private BasketItemResponse toDto(BasketItem item) {
        return new BasketItemResponse(
                item.getId(),
                item.getUserId(),
                item.getRoom(),
                item.getBookingDate(),
                item.getAddedAt()
        );
    }
}
