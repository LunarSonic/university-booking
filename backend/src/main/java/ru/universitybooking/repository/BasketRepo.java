package ru.universitybooking.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ru.universitybooking.entity.BasketItem;

import java.util.List;

@Repository
public interface BasketRepo extends CrudRepository<BasketItem, Long> {
    List<BasketItem> findByUserId(Long id);
}
