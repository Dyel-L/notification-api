package com.ubisafe.alert_processor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ubisafe.alert_processor.domain.Alert;
import com.ubisafe.alert_processor.domain.AlertEntity;
import com.ubisafe.alert_processor.exception.AlertProcessingException;
import com.ubisafe.alert_processor.repository.AlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static com.ubisafe.alert_processor.domain.Severity.HIGH;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertConsumerServiceTest {

    @Mock
    private AlertRepository alertRepository;

    @InjectMocks
    private AlertConsumerService alertConsumerService;

    private String testAlertJson;

    @BeforeEach
    void setUp() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        Alert alert = Alert.builder()
                .id("test-id-123")
                .clientId("client-id-123")
                .alertType("SYSTEM")
                .message("Test alert message")
                .severity(HIGH)
                .source("test-source")
                .timestamp(LocalDateTime.now())
                .build();

        testAlertJson = objectMapper.writeValueAsString(alert);
    }

    @Test
    void consumeAlert_ShouldSaveAlert_WhenValidJson() {
        // Arrange
        when(alertRepository.save(any(AlertEntity.class))).thenReturn(null);

        // Act
        alertConsumerService.consumeAlert(testAlertJson);

        // Assert
        ArgumentCaptor<AlertEntity> captor = ArgumentCaptor.forClass(AlertEntity.class);
        verify(alertRepository, times(1)).save(captor.capture());

        AlertEntity savedEntity = captor.getValue();
        assertEquals("test-id-123", savedEntity.getId());
        assertEquals("SYSTEM", savedEntity.getAlertType());
        assertEquals("Test alert message", savedEntity.getMessage());
        assertEquals(HIGH, savedEntity.getSeverity());
        assertNotNull(savedEntity.getProcessedAt());
    }

    @Test
    void consumeAlert_ShouldThrowException_WhenInvalidJson() {
        // Arrange
        String invalidJson = "{ invalid json }";

        // Act & Assert
        assertThrows(AlertProcessingException.class, () -> alertConsumerService.consumeAlert(invalidJson));

        verify(alertRepository, never()).save(any(AlertEntity.class));
    }

    @Test
    void consumeAlert_ShouldSimulateDelay() {
        // Arrange
        when(alertRepository.save(any(AlertEntity.class))).thenReturn(null);
        long startTime = System.currentTimeMillis();

        // Act
        alertConsumerService.consumeAlert(testAlertJson);
        long endTime = System.currentTimeMillis();

        // Assert
        long duration = endTime - startTime;
        assertTrue(duration >= 500, "Processing should take at least 500ms");
        verify(alertRepository, times(1)).save(any(AlertEntity.class));
    }
}
