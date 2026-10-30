package ru.practicum.ewm.compilation.model;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "compilation_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompilationEvent {

    @EmbeddedId
    private CompilationEventId id;
}
