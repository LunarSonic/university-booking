package ru.universitybooking.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ru.universitybooking.entity.BookingRequest;
import ru.universitybooking.entity.StatusRequest;

import java.util.List;

@Repository
public interface BookingRepo extends CrudRepository<BookingRequest, Long> {
    List<BookingRequest> findByStatus(StatusRequest status);
}
