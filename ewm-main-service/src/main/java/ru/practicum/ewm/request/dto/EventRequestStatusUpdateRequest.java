package ru.practicum.ewm.request.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record EventRequestStatusUpdateRequest(
        @NotNull
        List<@NotNull @Positive Long> requestIds,

        @NotNull
        RequestUpdateStatus status
) {
}