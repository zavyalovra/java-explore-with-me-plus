package ru.practicum.ewm.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiError {

    private String status;

    private String reason;

    private String message;

    private String timestamp;
}
