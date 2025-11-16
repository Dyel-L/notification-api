package com.ubisafe.alert_processor.domain;

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
    private String clientId;
    private String alertType;
    private String message;
    private Severity severity;
    private String source;
    private LocalDateTime timestamp;
}
