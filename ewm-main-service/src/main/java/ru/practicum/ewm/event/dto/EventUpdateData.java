package ru.practicum.ewm.event.dto;

import java.time.LocalDateTime;

public interface EventUpdateData {

    String annotation();

    Long category();

    String description();

    LocalDateTime eventDate();

    LocationDto location();

    Boolean paid();

    Integer participantLimit();

    Boolean requestModeration();

    String title();

}