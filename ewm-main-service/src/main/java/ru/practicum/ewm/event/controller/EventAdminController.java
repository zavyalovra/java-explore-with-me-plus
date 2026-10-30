package ru.practicum.ewm.event.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.event.dto.EventFullDto;
import ru.practicum.ewm.event.dto.UpdateEventAdminRequest;
import ru.practicum.ewm.event.model.EventState;
import ru.practicum.ewm.event.service.EventService;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/admin/events")
@RequiredArgsConstructor
public class EventAdminController {

    private final EventService eventService;

    @PatchMapping("/{eventId}")
    public EventFullDto updateEvent(@PathVariable Long eventId, @Valid @RequestBody UpdateEventAdminRequest request) {
        return eventService.updateAdminEvent(eventId, request);
    }

    @GetMapping
    public List<EventFullDto> searchEvents(
            @RequestParam(required = false)
            List<Long> users,

            @RequestParam(required = false)
            List<EventState> states,

            @RequestParam(required = false)
            List<Long> categories,

            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
            LocalDateTime rangeStart,

            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
            LocalDateTime rangeEnd,

            @RequestParam(defaultValue = "0")
            int from,

            @RequestParam(defaultValue = "10")
            int size
    ) {
        return eventService.searchAdminEvents(
                users,
                states,
                categories,
                rangeStart,
                rangeEnd,
                from,
                size
        );
    }
}