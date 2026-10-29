package ru.practicum.ewm.compilation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class CompilationEventId implements Serializable {

    @Column(name = "compilation_id")
    private Long compilationId;

    @Column(name = "event_id")
    private Long eventId;
}
