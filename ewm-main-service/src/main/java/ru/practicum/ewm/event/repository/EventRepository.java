package ru.practicum.ewm.event.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.EventState;

import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {
    Page<Event> findByInitiator_Id(Long initiatorId, Pageable pageable);

    Optional<Event> findByIdAndInitiator_Id(Long eventId, Long initiatorId);

    Optional<Event> findByIdAndState(Long eventId, EventState state);
}