package ru.practicum.ewm.request.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.practicum.ewm.request.model.ParticipationRequest;
import ru.practicum.ewm.request.model.RequestStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ParticipationRequestRepository extends JpaRepository<ParticipationRequest, Long> {

    List<ParticipationRequest> findByRequester_Id(Long requesterId);

    List<ParticipationRequest> findByEvent_Id(Long eventId);

    Optional<ParticipationRequest> findByIdAndRequester_Id(Long requestId, Long requesterId);

    boolean existsByEvent_IdAndRequester_Id(Long eventId, Long requesterId);

    long countByEvent_IdAndStatus(Long eventId, RequestStatus status);

    List<ParticipationRequest> findByEvent_IdAndStatus(Long eventId, RequestStatus status);

    List<ParticipationRequest> findByEvent_IdAndIdIn(Long eventId, Collection<Long> requestIds);
}