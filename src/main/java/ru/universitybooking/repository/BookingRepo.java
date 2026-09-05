package ru.universitybooking.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ru.universitybooking.entity.BookingRequest;

@Repository
public interface BookingRepo extends CrudRepository<BookingRequest, Long> {}
