package ru.practicum.ewm.stats.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EndpointHitDto {

    private Long id;

    @NotBlank
    @Size(max = 255, message = "не должен быть длиннее 255 символов")
    private String app;

    @NotBlank
    @Size(max = 512, message = "не должен быть длиннее 512 символов")
    private String uri;

    @NotBlank
    @Size(max = 45, message = "не должен быть длиннее 45 символов")
    private String ip;

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
}
