package com.ubisafe.notification_api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ubisafe.notification_api.domain.Alert;
import com.ubisafe.notification_api.exception.AlertPublishException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.ubisafe.notification_api.domain.Severity.HIGH;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para o {@link AlertService} com:
 * - ID determinístico
 * - Deduplicação via serviço Redis mockado
 * - Retorno Map com flags
 */
@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private DeduplicationService deduplicationService;

    private AlertService alertService;

    private Alert testAlert;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        alertService = new AlertService(kafkaTemplate, objectMapper, deduplicationService);
        testAlert = Alert.builder()
                .alertType("SYSTEM")
                .clientId("client-id-123")
                .message("Test alert message")
                .severity(HIGH)
                .source("test-source")
                .build();
    }

    private String expectedDeterministicId(Alert alert) {
        String content = String.format("%s:%s:%s:%s", alert.getClientId(), alert.getAlertType(), alert.getMessage(), alert.getSeverity());
        return UUID.nameUUIDFromBytes(content.getBytes(StandardCharsets.UTF_8)).toString();
    }

    @Test
    void publishAlert_ShouldGenerateDeterministicIdAndTimestamp_WhenNotDuplicate() {
        when(deduplicationService.isDuplicate(anyString())).thenReturn(false);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        Map<String, String> result = alertService.publishAlert(testAlert);

        String expectedId = expectedDeterministicId(testAlert);
        assertEquals(expectedId, result.get("id"));
        assertEquals("ACCEPTED", result.get("status"));
        assertNotNull(testAlert.getTimestamp(), "Timestamp deve ser preenchido se ausente");
        verify(kafkaTemplate, times(1)).send(eq("alerts"), eq(expectedId), anyString());
    }

    @Test
    void publishAlert_ShouldIgnoreProvidedIdAndUseDeterministic_WhenIdPresent() {
        testAlert.setId("id-manual-invalido");
        when(deduplicationService.isDuplicate(anyString())).thenReturn(false);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        Map<String, String> result = alertService.publishAlert(testAlert);
        String expectedId = expectedDeterministicId(testAlert);

        assertEquals(expectedId, result.get("id"));
        assertNotEquals("id-manual-invalido", result.get("id"));
    }

    @Test
    void publishAlert_ShouldSendToKafka_WhenNotDuplicate() {
        when(deduplicationService.isDuplicate(anyString())).thenReturn(false);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        alertService.publishAlert(testAlert);
        verify(kafkaTemplate, times(1)).send(eq("alerts"), anyString(), anyString());
    }

    @Test
    void publishAlert_ShouldSkipKafkaSend_WhenDuplicate() {
        when(deduplicationService.isDuplicate(anyString())).thenReturn(true);

        Map<String, String> result = alertService.publishAlert(testAlert);

        assertEquals("ACCEPTED", result.get("status"));
        assertEquals("Duplicate alert detected within window; not republished", result.get("message"));
        assertNull(testAlert.getTimestamp(), "Timestamp não deve ser preenchido se pular publicação por duplicidade");
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void publishAlert_ShouldReturnDuplicateOnSecondCallSequentially() {
        when(deduplicationService.isDuplicate(anyString())).thenReturn(false, true); // primeira false, segunda true
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        Map<String, String> first = alertService.publishAlert(testAlert);
        Map<String, String> second = alertService.publishAlert(testAlert);

        verify(kafkaTemplate, times(1)).send(eq("alerts"), anyString(), anyString());
    }

    @Test
    void publishAlert_ShouldThrowAlertPublishException_OnSerializationFailure() throws Exception {
        ObjectMapper spyMapper = spy(objectMapper);
        alertService = new AlertService(kafkaTemplate, spyMapper, deduplicationService);
        when(deduplicationService.isDuplicate(anyString())).thenReturn(false);
        doThrow(new JsonProcessingException("boom") { }).when(spyMapper).writeValueAsString(any());

        AlertPublishException ex = assertThrows(AlertPublishException.class, () -> alertService.publishAlert(testAlert));
        assertTrue(ex.getMessage().contains("Failed to serialize alert"));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void publishAlert_ShouldReturnMapEvenIfKafkaSendFails() {
        when(deduplicationService.isDuplicate(anyString())).thenReturn(false);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenThrow(new RuntimeException("Kafka down"));

        Map<String, String> result = alertService.publishAlert(testAlert);
        assertEquals("ACCEPTED", result.get("status"));
        assertEquals("Alert accepted but Kafka publish failed", result.get("message"));
        assertEquals("true", result.get("kafkaError"));
        assertNotNull(result.get("id"));
        assertNotNull(testAlert.getTimestamp());
        verify(kafkaTemplate, times(1)).send(eq("alerts"), anyString(), anyString());
    }

    @Test
    void publishAlert_ShouldNotContainKafkaError_WhenSendSucceeds() {
        when(deduplicationService.isDuplicate(anyString())).thenReturn(false);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        Map<String, String> result = alertService.publishAlert(testAlert);
        assertEquals("ACCEPTED", result.get("status"));
        assertEquals("Alert received and queued for processing", result.get("message"));
        assertFalse(result.containsKey("kafkaError"));
        verify(kafkaTemplate, times(1)).send(eq("alerts"), anyString(), anyString());
    }
}
