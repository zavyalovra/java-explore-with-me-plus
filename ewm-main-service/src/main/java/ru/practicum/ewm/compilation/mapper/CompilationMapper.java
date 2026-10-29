package ru.practicum.ewm.compilation.mapper;

import org.springframework.stereotype.Component;
import ru.practicum.ewm.compilation.dto.CompilationDto;
import ru.practicum.ewm.compilation.dto.NewCompilationDto;
import ru.practicum.ewm.compilation.model.Compilation;

import java.util.List;

@Component
public class CompilationMapper {

    public Compilation toEntity(NewCompilationDto newCompilationDto) {
        return Compilation.builder()
                .title(newCompilationDto.getTitle())
                .pinned(Boolean.TRUE.equals(newCompilationDto.getPinned()))
                .build();
    }

    public CompilationDto toDto(Compilation compilation) {
        return new CompilationDto(
                compilation.getId(),
                compilation.getTitle(),
                compilation.isPinned(),
                List.of());
    }
}
