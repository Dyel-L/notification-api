package com.ubisafe.notification_api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ubisafe.notification_api.domain.Alert;
import com.ubisafe.notification_api.exception.AlertPublishException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final DeduplicationService deduplicationService;
    private static final String TOPIC = "alerts";

    public Map<String, String> publishAlert(Alert alert) {
        try {
            String contentHash = generateAlertHash(alert);
            alert.setId(contentHash);

            boolean duplicate = deduplicationService.isDuplicate(contentHash);
            if (duplicate) {
                log.info("Duplicate alert skipped. id={}", contentHash);
                return Map.of(
                        "id", contentHash,
                        "status", "ACCEPTED",
                        "message", "Duplicate alert detected within window; not republished"
                );
            }

            if (alert.getTimestamp() == null) {
                alert.setTimestamp(LocalDateTime.now());
            }

            String alertJson = objectMapper.writeValueAsString(alert);

            try {
                CompletableFuture<SendResult<String, String>> future =
                        kafkaTemplate.send(TOPIC, alert.getId(), alertJson);

                future.whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish alert (async) id={}: {}", alert.getId(), ex.getMessage());
                    } else {
                        log.info("Alert published to Kafka: id={}, partition={}, offset={}",
                                alert.getId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
            } catch (Exception sendEx) {
                log.error("Immediate Kafka send failure for id={}: {}", alert.getId(), sendEx.getMessage());
                return Map.of(
                        "id", contentHash,
                        "status", "ACCEPTED",
                        "message", "Alert accepted but Kafka publish failed",
                        "kafkaError", "true"
                );
            }

            return Map.of(
                    "id", contentHash,
                    "status", "ACCEPTED",
                    "message", "Alert received and queued for processing"
            );
        } catch (JsonProcessingException e) {
            log.error("Error serializing alert: {}", e.getMessage());
            throw new AlertPublishException("Failed to serialize alert", e);
        }
    }

    private String generateAlertHash(Alert alert) {
        String content = String.format("%s:%s:%s:%s",
                alert.getClientId(),
                alert.getAlertType(),
                alert.getMessage(),
                alert.getSeverity());

        return UUID.nameUUIDFromBytes(content.getBytes()).toString();
    }
}
