package ru.practicum.ewm.request.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.EventState;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.request.dto.EventRequestStatusUpdateRequest;
import ru.practicum.ewm.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.ewm.request.dto.ParticipationRequestDto;
import ru.practicum.ewm.request.dto.RequestUpdateStatus;
import ru.practicum.ewm.request.mapper.ParticipationRequestMapper;
import ru.practicum.ewm.request.model.ParticipationRequest;
import ru.practicum.ewm.request.model.RequestStatus;
import ru.practicum.ewm.request.repository.ParticipationRequestRepository;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParticipationRequestService {

    private final ParticipationRequestRepository requestRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final ParticipationRequestMapper requestMapper;

    public List<ParticipationRequestDto> getUserRequests(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Пользователь с ID " + userId + " не найден");
        }

        return requestRepository.findByRequester_Id(userId)
                .stream()
                .map(requestMapper::toDto)
                .toList();
    }

    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        ParticipationRequest request = requestRepository
                .findByIdAndRequester_Id(requestId, userId)
                .orElseThrow(() -> new NotFoundException("Заявка с ID " + requestId
                                + " не найдена у пользователя с ID " + userId));

        request.setStatus(RequestStatus.CANCELED);

        return requestMapper.toDto(request);
    }

    @Transactional
    public ParticipationRequestDto create(Long userId, Long eventId) {

        User requester = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с ID " + userId + " не найден"));

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с ID " + eventId + " не найдено"));

        if (event.getInitiator().getId().equals(userId)) {
            throw new ConflictException("Организатор не может подать заявку на участие в собственном событии");
        }

        if (requestRepository.existsByEvent_IdAndRequester_Id(
                eventId, userId
        )) {
            throw new ConflictException("Пользователь уже подал заявку на участие в этом событии");
        }

        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Нельзя подать заявку на участие в неопубликованном событии");
        }

        int limit = event.getParticipantLimit();

        long confirmed = requestRepository.countByEvent_IdAndStatus(
                eventId,
                RequestStatus.CONFIRMED
        );

        if (limit > 0 && confirmed >= limit) {
            throw new ConflictException("Лимит участников события уже достигнут");
        }

        RequestStatus status;

        if (limit == 0 || !event.isRequestModeration()) {
            status = RequestStatus.CONFIRMED;
        } else {
            status = RequestStatus.PENDING;
        }

        ParticipationRequest request = ParticipationRequest.builder()
                .event(event)
                .requester(requester)
                .status(status)
                .build();

        ParticipationRequest saved = requestRepository.saveAndFlush(request);

        return requestMapper.toDto(saved);
    }

    public List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId) {
        eventRepository.findByIdAndInitiator_Id(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Событие с ID " + eventId
                                + " не найдено у пользователя с ID " + userId));

        return requestRepository.findByEvent_Id(eventId)
                .stream()
                .map(requestMapper::toDto)
                .toList();
    }

    @Transactional
    public EventRequestStatusUpdateResult updateEventRequests(
            Long userId,
            Long eventId,
            EventRequestStatusUpdateRequest updateRequest
    ) {
        Event event = eventRepository
                .findByIdAndInitiator_Id(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Событие с ID " + eventId
                                + " не найдено у пользователя с ID " + userId));

        List<Long> requestIds = updateRequest.requestIds()
                .stream()
                .distinct()
                .toList();

        if (requestIds.isEmpty()) {
            return new EventRequestStatusUpdateResult(List.of(), List.of());
        }

        List<ParticipationRequest> requests = requestRepository
                .findByEvent_IdAndIdIn(eventId, requestIds);

        if (requests.size() != requestIds.size()) {
            throw new NotFoundException("Некоторые заявки не найдены или не относятся к событию с ID " + eventId);
        }

        for (ParticipationRequest request : requests) {
            if (request.getStatus() != RequestStatus.PENDING) {
                throw new ConflictException("Изменить можно только заявку со статусом PENDING. "
                                + "ID заявки: " + request.getId());
            }
        }

        List<ParticipationRequestDto> confirmed = new ArrayList<>();
        List<ParticipationRequestDto> rejected = new ArrayList<>();

        if (updateRequest.status() == RequestUpdateStatus.REJECTED) {

            for (ParticipationRequest request : requests) {
                request.setStatus(RequestStatus.REJECTED);
            }

            requestRepository.saveAllAndFlush(requests);

            for (ParticipationRequest request : requests) {
                rejected.add(requestMapper.toDto(request));
            }

        } else {

            int limit = event.getParticipantLimit();

            long confirmedCount = requestRepository.countByEvent_IdAndStatus(eventId, RequestStatus.CONFIRMED);

            if (limit > 0 && confirmedCount + requests.size() > limit) {
                throw new ConflictException("Недостаточно свободных мест для подтверждения заявок");
            }

            for (ParticipationRequest request : requests) {
                request.setStatus(RequestStatus.CONFIRMED);
            }

            requestRepository.saveAllAndFlush(requests);

            for (ParticipationRequest request : requests) {
                confirmed.add(requestMapper.toDto(request));
            }

            if (limit > 0 && confirmedCount + requests.size() == limit) {

                List<ParticipationRequest> pendingRequests =
                        requestRepository.findByEvent_IdAndStatus(
                                eventId,
                                RequestStatus.PENDING
                        );

                for (ParticipationRequest request : pendingRequests) {
                    request.setStatus(RequestStatus.REJECTED);
                }

                requestRepository.saveAllAndFlush(pendingRequests);

                for (ParticipationRequest request : pendingRequests) {
                    rejected.add(requestMapper.toDto(request));
                }
            }
        }

        return new EventRequestStatusUpdateResult(
                confirmed,
                rejected
        );
    }
}
