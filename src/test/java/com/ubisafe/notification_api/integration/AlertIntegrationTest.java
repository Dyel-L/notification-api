package com.ubisafe.notification_api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ubisafe.notification_api.domain.Alert;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

import static com.ubisafe.notification_api.domain.Severity.MEDIUM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for Alert API with Kafka using Testcontainers.
 * Tests the complete flow: REST API -> Kafka -> Consumer verification.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Testcontainers
class AlertIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("apache/kafka:3.8.1"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.producer.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.consumer.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void devePublicarMensagemNoKafkaAoReceberPost() throws Exception {
        Alert alert = Alert.builder()
                .alertType("INTEGRATION_TEST")
                .clientId("client-test123")
                .message("Integration test alert")
                .severity(MEDIUM)
                .source("integration-test")
                .build();


        mockMvc.perform(post("/alerts")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(alert)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        Properties consumerProps = createConsumerProperties();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList("alerts"));

            Alert alertEncontrado = null;
            long timeoutMs = System.currentTimeMillis() + 10000; // 10 segundos

            while (System.currentTimeMillis() < timeoutMs && alertEncontrado == null) {
                var records = consumer.poll(Duration.ofMillis(500));

                for (ConsumerRecord<String, String> record : records) {
                    Alert alertRecebido = objectMapper.readValue(record.value(), Alert.class);

                    if ("INTEGRATION_TEST".equals(alertRecebido.getAlertType())
                            && "Integration test alert".equals(alertRecebido.getMessage())) {
                        alertEncontrado = alertRecebido;
                        break;
                    }
                }
            }

            assertThat(alertEncontrado)
                    .isNotNull()
                    .satisfies(a -> {
                        assertThat(a.getClientId()).isEqualTo("client-test123");
                        assertThat(a.getSeverity()).isEqualTo(MEDIUM);
                        assertThat(a.getSource()).isEqualTo("integration-test");
                        assertThat(a.getAlertType()).isEqualTo("INTEGRATION_TEST");
                        assertThat(a.getId()).isNotNull();
                        assertThat(a.getTimestamp()).isNotNull();
                    });
        }
    }

    @Test
    void deveValidarCamposObrigatorios() throws Exception {
        mockMvc.perform(post("/alerts")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    void devePublicarMultiploAlertsNoKafka() throws Exception {
        Alert alert1 = createAlert("TEST_1", "First alert", "client-1");
        Alert alert2 = createAlert("TEST_2", "Second alert", "client-2");

        mockMvc.perform(post("/alerts")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(alert1)))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/alerts")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(alert2)))
                .andExpect(status().isAccepted());

        Properties consumerProps = createConsumerProperties();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList("alerts"));

            int alertsEncontrados = 0;
            long timeoutMs = System.currentTimeMillis() + 10000;

            while (System.currentTimeMillis() < timeoutMs && alertsEncontrados < 2) {
                var records = consumer.poll(Duration.ofMillis(500));

                for (ConsumerRecord<String, String> record : records) {
                    Alert alert = objectMapper.readValue(record.value(), Alert.class);

                    if (alert.getAlertType().startsWith("TEST_")) {
                        alertsEncontrados++;
                    }
                }
            }

            assertThat(alertsEncontrados).isEqualTo(2);
        }
    }

    private Properties createConsumerProperties() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-consumer-" + System.currentTimeMillis());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100");
        return props;
    }

    private Alert createAlert(String type, String message, String clientId) {
        return Alert.builder()
                .alertType(type)
                .clientId(clientId)
                .message(message)
                .severity(MEDIUM)
                .source("integration-test")
                .build();
    }
}