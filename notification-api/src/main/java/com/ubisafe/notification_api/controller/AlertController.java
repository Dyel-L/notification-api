package com.ubisafe.notification_api.controller;

import com.ubisafe.notification_api.domain.Alert;
import com.ubisafe.notification_api.service.AlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @PostMapping
    public ResponseEntity<Map<String, String>> createAlert(@Valid @RequestBody Alert alert) {
        log.info("Received alert: type={}, severity={}", alert.getType(), alert.getSeverity());

        String alertId = alertService.publishAlert(alert);

        log.info("Alert published successfully with id={}", alertId);
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(Map.of(
                        "id", alertId,
                        "status", "ACCEPTED",
                        "message", "Alert received and queued for processing"
                ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
