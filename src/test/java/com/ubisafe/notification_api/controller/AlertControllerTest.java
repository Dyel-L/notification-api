package com.ubisafe.notification_api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ubisafe.notification_api.domain.Alert;
import com.ubisafe.notification_api.service.AlertService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static com.ubisafe.notification_api.domain.Severity.HIGH;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


/**
 * Testes de camada web (MVC) para o {@code AlertController}.
 * Valida o endpoint de criação de alertas quanto a:
 * - Sucesso (202 Accepted) quando os dados são válidos.
 * - Erros de validação (400 Bad Request) quando campos obrigatórios estão ausentes.
 *
 */
@WebMvcTest(AlertController.class)
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AlertService alertService;

    @Test
    void createAlert_ShouldReturn202_WhenValidAlert() throws Exception {
        Alert alert = Alert.builder()
                .alertType("SYSTEM")
                .clientId("client-id-123")
                .message("Test alert")
                .severity(HIGH)
                .source("test")
                .build();

        when(alertService.publishAlert(any(Alert.class))).thenReturn("test-id-123");

        mockMvc.perform(post("/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alert)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value("test-id-123"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void createAlert_ShouldReturn400_WhenMissingType() throws Exception {
        Alert alert = Alert.builder()
                .message("Test alert")
                .severity(HIGH)
                .build();

        mockMvc.perform(post("/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alert)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAlert_ShouldReturn400_WhenMissingMessage() throws Exception {
        Alert alert = Alert.builder()
                .alertType("SYSTEM")
                .clientId("client-id-123")
                .severity(HIGH)
                .build();

        mockMvc.perform(post("/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alert)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAlert_ShouldReturn400_WhenMissingSeverity() throws Exception {
        Alert alert = Alert.builder()
                .alertType("SYSTEM")
                .clientId("client-id-123")
                .message("Test alert")
                .build();

        mockMvc.perform(post("/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alert)))
                .andExpect(status().isBadRequest());
    }
}

