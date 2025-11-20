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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private static final String TOPIC = "alerts";

    public String publishAlert(Alert alert) {
        try {
            if (alert.getId() == null || alert.getId().isEmpty()) {
                alert.setId(UUID.randomUUID().toString());
            }
            if (alert.getTimestamp() == null) {
                alert.setTimestamp(LocalDateTime.now());
            }

            String alertJson = objectMapper.writeValueAsString(alert);

            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(TOPIC, alert.getId(), alertJson);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish alert with id={}: {}", alert.getId(), ex.getMessage());
                } else {
                    log.info("Alert published to Kafka: id={}, partition={}, offset={}",
                            alert.getId(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });

            return alert.getId();
        } catch (JsonProcessingException e) {
            log.error("Error serializing alert: {}", e.getMessage());
            throw new AlertPublishException("Failed to serialize alert", e);
        }
    }
}