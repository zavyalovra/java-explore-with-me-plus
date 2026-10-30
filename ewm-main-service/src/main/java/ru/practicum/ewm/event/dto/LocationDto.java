package ru.practicum.ewm.event.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.AllArgsConstructor;

@Data
@AllArgsConstructor
public class LocationDto {

    @NotNull
    private Double lat;

    @NotNull
    private Double lon;
}
