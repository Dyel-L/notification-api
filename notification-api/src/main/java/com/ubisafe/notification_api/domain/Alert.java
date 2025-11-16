package com.ubisafe.notification_api.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alert {

    private String id;

    @NotBlank(message = "Tipo de alerta é obrigatorio")
    private String type;

    @NotBlank(message = "Mensagem é obrigatoria")
    private String message;

    @NotNull(message = "Severidade é obrigatoria")
    private Severity severity;

    private String source;

    private LocalDateTime timestamp;

    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
