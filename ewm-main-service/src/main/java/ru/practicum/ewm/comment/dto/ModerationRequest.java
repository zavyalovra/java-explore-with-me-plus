package ru.practicum.ewm.comment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ModerationRequest {

    @NotNull
    private ModerationDecision status;

    @Size(max = 500)
    private String moderationReason;
}
