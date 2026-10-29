package ru.practicum.ewm.compilation.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCompilationRequest {

    @Size(min = 1, max = 50)
    private String title;

    private Boolean pinned;

    private Set<Long> events;
}
