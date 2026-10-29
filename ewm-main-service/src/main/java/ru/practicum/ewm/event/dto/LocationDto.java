package ru.practicum.ewm.event.dto;

import jakarta.validation.constraints.NotNull;

public record LocationDto(@NotNull Double lat, @NotNull Double lon) {
}
