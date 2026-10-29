package ru.practicum.ewm.event.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.event.dto.EventFullDto;
import ru.practicum.ewm.event.dto.EventShortDto;
import ru.practicum.ewm.event.model.EventSort;
import ru.practicum.ewm.event.service.EventService;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventPublicController {

    private final EventService eventService;

    @GetMapping("/{id}")
    public EventFullDto getEventById(@PathVariable Long id, HttpServletRequest request) {
        return eventService.getPublicEventById(id, request);
    }

    @GetMapping
    public List<EventShortDto> searchEvents(
            @RequestParam(required = false)
            String text,

            @RequestParam(required = false)
            List<Long> categories,

            @RequestParam(required = false)
            Boolean paid,

            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
            LocalDateTime rangeStart,

            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
            LocalDateTime rangeEnd,

            @RequestParam(defaultValue = "false")
            boolean onlyAvailable,

            @RequestParam(defaultValue = "EVENT_DATE")
            EventSort sort,

            @RequestParam(defaultValue = "0")
            int from,

            @RequestParam(defaultValue = "10")
            int size,

            HttpServletRequest request
    ) {
        return eventService.searchPublicEvents(
                text,
                categories,
                paid,
                rangeStart,
                rangeEnd,
                onlyAvailable,
                sort,
                from,
                size,
                request
        );
    }
}