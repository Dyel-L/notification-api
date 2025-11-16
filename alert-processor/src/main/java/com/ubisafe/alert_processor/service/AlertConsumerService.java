package com.ubisafe.alert_processor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ubisafe.alert_processor.domain.Alert;
import com.ubisafe.alert_processor.domain.AlertEntity;
import com.ubisafe.alert_processor.domain.ProcessingStatus;
import com.ubisafe.alert_processor.exception.AlertProcessingException;
import com.ubisafe.alert_processor.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertConsumerService {

    private final AlertRepository alertRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final long PROCESSING_DELAY_MS = 500;

    @KafkaListener(topics = "alerts", groupId = "processor-group")
    @Transactional
    public void consumeAlert(String alertJson) {
        AlertEntity entity = null;
        try {
            log.info("Received alert from Kafka: {}", alertJson);

            // Deserialize alert
            Alert alert = objectMapper.readValue(alertJson, Alert.class);

            // Simulate processing delay
            Thread.sleep(PROCESSING_DELAY_MS);

            // Convert to entity and persist
            entity = AlertEntity.builder()
                    .id(alert.getId())
                    .clientId(alert.getClientId())
                    .alertType(alert.getAlertType())
                    .message(alert.getMessage())
                    .severity(alert.getSeverity())
                    .source(alert.getSource())
                    .timestamp(alert.getTimestamp())
                    .processedAt(LocalDateTime.now())
                    .processingStatus(ProcessingStatus.SUCCESS)
                    .build();

            alertRepository.save(entity);

            log.info("Alert processed and saved successfully: id={}, type={}, severity={}",
                    entity.getId(), entity.getAlertType(), entity.getSeverity());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Processing interrupted for alert: {}", alertJson, e);
            persistFailedAlert(entity, alertJson, "Alert processing was interrupted: " + e.getMessage());
            throw new AlertProcessingException("Alert processing was interrupted", e);
        } catch (Exception e) {
            log.error("Error processing alert: {}", alertJson, e);
            persistFailedAlert(entity, alertJson, "Failed to process alert: " + e.getMessage());
            throw new AlertProcessingException("Failed to process alert", e);
        }
    }

    private void persistFailedAlert(AlertEntity entity, String alertJson, String failureReason) {
        try {
            if (entity == null) {
                Alert alert = objectMapper.readValue(alertJson, Alert.class);
                entity = AlertEntity.builder()
                        .id(alert.getId())
                        .clientId(alert.getClientId())
                        .alertType(alert.getAlertType())
                        .message(alert.getMessage())
                        .severity(alert.getSeverity())
                        .source(alert.getSource())
                        .timestamp(alert.getTimestamp())
                        .processedAt(LocalDateTime.now())
                        .processingStatus(ProcessingStatus.FAILURE)
                        .failureReason(failureReason)
                        .build();
            } else {
                entity.setProcessingStatus(ProcessingStatus.FAILURE);
                entity.setFailureReason(failureReason);
            }
            alertRepository.save(entity);
            log.info("Failed alert saved: id={}, reason={}", entity.getId(), failureReason);
        } catch (Exception e) {
            log.error("Failed to persist error alert: {}", alertJson, e);
        }
    }
}
