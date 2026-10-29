package ru.practicum.ewm.event.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import ru.practicum.ewm.category.dto.CategoryDto;
import ru.practicum.ewm.user.dto.UserShortDto;

import java.time.LocalDateTime;

public record EventShortDto(
        Long id,
        String title,
        String annotation,
        CategoryDto category,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime eventDate,
        UserShortDto initiator,
        Boolean paid,
        Long confirmedRequests,
        Long views) {
}
