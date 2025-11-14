package com.ubisafe.alert_processor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ubisafe.alert_processor.domain.Alert;
import com.ubisafe.alert_processor.domain.AlertEntity;
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
        try {
            log.info("Received alert from Kafka: {}", alertJson);

            // Deserialize alert
            Alert alert = objectMapper.readValue(alertJson, Alert.class);

            // Simulate processing delay
            Thread.sleep(PROCESSING_DELAY_MS);

            // Convert to entity and persist
            AlertEntity entity = AlertEntity.builder()
                    .id(alert.getId())
                    .type(alert.getType())
                    .message(alert.getMessage())
                    .severity(AlertEntity.Severity.valueOf(alert.getSeverity().name()))
                    .source(alert.getSource())
                    .timestamp(alert.getTimestamp())
                    .processedAt(LocalDateTime.now())
                    .build();

            alertRepository.save(entity);

            log.info("Alert processed and saved: id={}, type={}, severity={}",
                    entity.getId(), entity.getType(), entity.getSeverity());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Processing interrupted for alert: {}", alertJson, e);
            throw new AlertProcessingException("Alert processing was interrupted", e);
        } catch (Exception e) {
            log.error("Error processing alert: {}", alertJson, e);
            throw new AlertProcessingException("Failed to process alert", e);
        }
    }
}

