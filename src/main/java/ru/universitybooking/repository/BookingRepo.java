package ru.universitybooking.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ru.universitybooking.entity.BookingRequest;

@Repository
public interface BookingRepo extends CrudRepository<BookingRequest, Long> {}
