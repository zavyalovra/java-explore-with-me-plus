package ru.practicum.ewm.compilation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.practicum.ewm.event.dto.EventShortDto;

import java.util.List;

@Getter
@Builder
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class CompilationDto {

    private Long id;

    private String title;

    private Boolean pinned;

    private List<EventShortDto> events;
}
