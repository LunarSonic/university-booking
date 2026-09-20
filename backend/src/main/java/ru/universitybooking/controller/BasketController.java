package ru.universitybooking.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.universitybooking.dto.BasketItemRequest;
import ru.universitybooking.dto.BasketItemResponse;
import ru.universitybooking.dto.BookingDto;
import ru.universitybooking.service.BasketService;

import java.util.List;

@RestController
@RequestMapping("/basket")
public class BasketController {
    private final BasketService basketService;

    public BasketController(BasketService basketService) {
        this.basketService = basketService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BasketItemResponse createBasket(@RequestBody @Valid BasketItemRequest dto) {
        return basketService.addItem(dto);
    }

    @GetMapping("/user/{userId}")
    @ResponseStatus(HttpStatus.OK)
    public List<BasketItemResponse> getBasket(@PathVariable Long userId) {
        return basketService.getBaskets(userId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBasket(@PathVariable Long id) {
        basketService.deleteItem(id);
    }

    @PostMapping("/{id}/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingDto checkout(@PathVariable Long id) {
        return basketService.checkout(id);
    }
}
