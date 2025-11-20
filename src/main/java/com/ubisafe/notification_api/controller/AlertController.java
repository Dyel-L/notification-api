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
        log.info("Received alert: type={}, severity={}", alert.getAlertType(), alert.getSeverity());

        Map<String, String> result = alertService.publishAlert(alert);

        log.info("Alert processed id={}, duplicate={}", result.get("id"), result.get("duplicate"));
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(result);
    }
}
