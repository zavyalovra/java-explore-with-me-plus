package ru.practicum.ewm.event.repository;

import org.springframework.data.jpa.domain.Specification;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.EventState;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

public final class EventSpecifications {

    private EventSpecifications() {
    }

    public static Specification<Event> forAdmin(
            List<Long> users,
            List<EventState> states,
            List<Long> categories,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd
    ) {
        Specification<Event> spec = (root, query, cb) -> cb.conjunction();

        if (users != null && !users.isEmpty()) {
            spec = spec.and((root, query, cb) ->
                    root.get("initiator").get("id").in(users)
            );
        }

        if (states != null && !states.isEmpty()) {
            spec = spec.and((root, query, cb) ->
                    root.get("state").in(states)
            );
        }

        if (categories != null && !categories.isEmpty()) {
            spec = spec.and((root, query, cb) ->
                    root.get("category").get("id").in(categories)
            );
        }

        if (rangeStart != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(
                            root.get("eventDate"),
                            rangeStart
                    )
            );
        }

        if (rangeEnd != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(
                            root.get("eventDate"),
                            rangeEnd
                    )
            );
        }

        return spec;
    }

    public static Specification<Event> forPublic(
            String text,
            List<Long> categories,
            Boolean paid,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd
    ) {
        Specification<Event> spec = (root, query, cb) ->
                cb.equal(root.get("state"), EventState.PUBLISHED);

        if (text != null && !text.isBlank()) {
            String escaped = text.toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");

            String pattern = "%" + escaped + "%";

            spec = spec.and((root, query, cb) ->
                    cb.or(
                            cb.like(
                                    cb.lower(root.<String>get("annotation")),
                                    pattern,
                                    '\\'
                            ),
                            cb.like(
                                    cb.lower(root.<String>get("description")),
                                    pattern,
                                    '\\'
                            )
                    )
            );
        }

        if (categories != null && !categories.isEmpty()) {
            spec = spec.and((root, query, cb) ->
                    root.get("category").get("id").in(categories)
            );
        }

        if (paid != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("paid"), paid)
            );
        }

        if (rangeStart != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(
                            root.get("eventDate"),
                            rangeStart
                    )
            );
        }

        if (rangeEnd != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(
                            root.get("eventDate"),
                            rangeEnd
                    )
            );
        }

        return spec;
    }
}