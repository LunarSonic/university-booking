package ru.universitybooking.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.universitybooking.dto.BasketItemRequest;
import ru.universitybooking.dto.BasketItemResponse;
import ru.universitybooking.dto.BookingDto;
import ru.universitybooking.entity.BasketItem;
import ru.universitybooking.exception.BasketItemNotFoundException;
import ru.universitybooking.repository.BasketRepo;
import ru.universitybooking.repository.UniversityServiceRepo;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BasketService {

    private final BasketRepo basketRepo;
    private final StringRedisTemplate stringRedisTemplate;
    private final BookingService bookingService;
    private final UniversityServiceRepo universityServiceRepo;

    @Value("${basket.ttl-seconds}")
    private Long ttlSeconds;

    public BasketService(BasketRepo basketRepo, StringRedisTemplate stringRedisTemplate, BookingService bookingService, UniversityServiceRepo universityServiceRepo) {
        this.basketRepo = basketRepo;
        this.stringRedisTemplate = stringRedisTemplate;
        this.bookingService = bookingService;
        this.universityServiceRepo = universityServiceRepo;
    }

    public BasketItemResponse addItem(BasketItemRequest dto) {
        if (!universityServiceRepo.existsById(dto.serviceId())) {
            throw new IllegalArgumentException("Услуга с id " + dto.serviceId() + " не найдена");
        }

        Long id = stringRedisTemplate.opsForValue().increment("BasketItem:sequence");
        if (id == null) {
            throw new IllegalStateException("Не удалось сгенерировать ID: Redis недоступен");
        }

        BasketItem item = new BasketItem();
        item.setId(id);
        item.setUserId(dto.userId());
        item.setServiceId(dto.serviceId());
        item.setRoom(dto.room());
        item.setAddedAt(LocalDateTime.now());
        item.setBookingDate(dto.bookingDate());
        item.setTtl(ttlSeconds);
        BasketItem saved = basketRepo.save(item);
        return toDto(saved);
    }

    public void deleteItem(Long id) {
        if (!basketRepo.existsById(id)) {
            throw new BasketItemNotFoundException("Элемент корзины с id " + id + " не найден");
        }
        basketRepo.deleteById(id);
    }

    public List<BasketItemResponse> getBaskets(Long userId) {
        return basketRepo.findByUserId(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public BookingDto checkout(Long basketItemId) {
        BasketItem item = basketRepo.findById(basketItemId)
                .orElseThrow(() -> new BasketItemNotFoundException("Cannot find basket item with id " + basketItemId));
        basketRepo.deleteById(basketItemId);
        BookingDto bookingDto = new BookingDto(null, item.getUserId(), item.getServiceId(), item.getRoom(), item.getBookingDate(), null, null);
        BookingDto created = bookingService.createBooking(bookingDto);
        return created;
    }

    private BasketItemResponse toDto(BasketItem item) {
        return new BasketItemResponse(
                item.getId(),
                item.getUserId(),
                item.getServiceId(),
                item.getRoom(),
                item.getBookingDate(),
                item.getAddedAt()
        );
    }
}
