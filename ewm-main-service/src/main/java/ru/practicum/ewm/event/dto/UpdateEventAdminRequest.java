package ru.practicum.ewm.event.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record UpdateEventAdminRequest(

        @Size(min = 20, max = 2000)
        @Pattern(regexp = "(?s).*\\S.*")
        String annotation,

        @Positive
        Long category,

        @Size(min = 20, max = 7000)
        @Pattern(regexp = "(?s).*\\S.*")
        String description,

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime eventDate,

        @Valid
        LocationDto location,

        Boolean paid,

        @PositiveOrZero
        Integer participantLimit,

        Boolean requestModeration,

        AdminEventStateAction stateAction,

        @Size(min = 3, max = 120)
        @Pattern(regexp = "(?s).*\\S.*")
        String title

) {
}