package com.ubisafe.notification_api.service;

import com.ubisafe.notification_api.domain.Alert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static com.ubisafe.notification_api.domain.Severity.HIGH;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para o {@link AlertService}.
 * Valida:
 * - Geração de ID e timestamp quando ausentes.
 * - Uso de ID fornecido externamente.
 * - Envio para o tópico Kafka correto.
 * -
 * Usa Mockito para simular o {@link KafkaTemplate} e evitar integração real com Kafka.
 */
@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private AlertService alertService;

    private Alert testAlert;

    @BeforeEach
    void setUp() {
        testAlert = Alert.builder()
                .alertType("SYSTEM")
                .clientId("client-id-123")
                .message("Test alert message")
                .severity(HIGH)
                .source("test-source")
                .build();
    }

    @Test
    void publishAlert_ShouldGenerateIdAndTimestamp_WhenNotProvided() {
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        String alertId = alertService.publishAlert(testAlert);

        assertNotNull(alertId);
        assertNotNull(testAlert.getId());
        assertNotNull(testAlert.getTimestamp());
        verify(kafkaTemplate, times(1)).send(eq("alerts"), anyString(), anyString());
    }

    @Test
    void publishAlert_ShouldUseProvidedId_WhenIdIsProvided() {
        String providedId = "test-id-123";
        testAlert.setId(providedId);
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        String alertId = alertService.publishAlert(testAlert);

        assertEquals(providedId, alertId);
        verify(kafkaTemplate, times(1)).send(eq("alerts"), eq(providedId), anyString());
    }

    @Test
    void publishAlert_ShouldSendToCorrectTopic() {
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        alertService.publishAlert(testAlert);

        verify(kafkaTemplate, times(1)).send(eq("alerts"), anyString(), anyString());
    }
}
