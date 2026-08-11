package com.pixelfactory.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * ai-service: cycle 스트림에서 이상을 감지해 anomaly 이벤트를 발행한다.
 *
 * 구독: factory/+/+/cycle
 * 발행: factory/{lineCode}/{equipmentCode}/anomaly  (계약: docs/mqtt-topics.md)
 *
 * oee-service와는 MQTT 계약으로만 통신한다 — 코드/DB 직접 참조 없음(컴포저블 원칙).
 *
 * 환경변수:
 *   MQTT_URL  기본 tcp://localhost:1883
 */
public final class AnomalyDetectorApp implements MqttCallbackExtended {

    private static final String CYCLE_TOPIC_FILTER = "factory/+/+/cycle";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, CycleAnomalyDetector> detectors = new ConcurrentHashMap<>();
    private final MqttClient client;

    private AnomalyDetectorApp(MqttClient client) {
        this.client = client;
    }

    public static void main(String[] args) throws Exception {
        String brokerUrl = env("MQTT_URL", "tcp://localhost:1883");

        MqttClient client = new MqttClient(
                brokerUrl,
                "ai-service-" + System.currentTimeMillis(),
                new MemoryPersistence()
        );
        AnomalyDetectorApp app = new AnomalyDetectorApp(client);
        client.setCallback(app);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        client.connect(options);
        System.out.printf("ai-service connected to %s%n", brokerUrl);

        new CountDownLatch(1).await();
    }

    @Override
    public void connectComplete(boolean reconnect, String serverUri) {
        try {
            client.subscribe(CYCLE_TOPIC_FILTER, 1);
            System.out.printf("Subscribed to %s (reconnect=%s)%n", CYCLE_TOPIC_FILTER, reconnect);
        } catch (MqttException e) {
            System.err.println("Failed to subscribe: " + e.getMessage());
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            String[] parts = topic.split("/");
            if (parts.length != 4) {
                return;
            }
            String lineCode = parts[1];
            String equipmentCode = parts[2];

            JsonNode json = objectMapper.readTree(new String(message.getPayload(), StandardCharsets.UTF_8));
            long cycleTimeMs = json.path("cycleTimeMs").asLong(0);
            boolean defect = json.path("defect").asBoolean(false);
            if (cycleTimeMs <= 0) {
                return;
            }

            detectors.computeIfAbsent(equipmentCode, key -> new CycleAnomalyDetector())
                    .onCycle(cycleTimeMs, defect)
                    .ifPresent(anomaly -> publishAnomaly(lineCode, equipmentCode, anomaly));
        } catch (Exception e) {
            // Never propagate: an exception here would shut down the Paho client connection.
            System.err.println("Failed to handle cycle message on " + topic + ": " + e.getMessage());
        }
    }

    private void publishAnomaly(String lineCode, String equipmentCode, CycleAnomalyDetector.Anomaly anomaly) {
        ObjectNode payload = objectMapper.createObjectNode();

        if (anomaly instanceof CycleAnomalyDetector.CycleTimeSpike spike) {
            payload.put("anomalyType", "CYCLE_TIME_SPIKE");
            payload.put("cycleTimeMs", spike.cycleTimeMs());
            payload.put("mean", Math.round(spike.mean()));
            payload.put("stdDev", Math.round(spike.stdDev()));
            payload.put("zScore", Math.round(spike.zScore() * 10) / 10.0);
        } else if (anomaly instanceof CycleAnomalyDetector.DefectBurst burst) {
            payload.put("anomalyType", "DEFECT_BURST");
            payload.put("defectCount", burst.defectCount());
            payload.put("windowSize", burst.windowSize());
        }
        payload.put("ts", Instant.now().toString());

        String topic = "factory/" + lineCode + "/" + equipmentCode + "/anomaly";
        try {
            MqttMessage message = new MqttMessage(payload.toString().getBytes(StandardCharsets.UTF_8));
            message.setQos(1);
            client.publish(topic, message);
            System.out.printf("[%s] anomaly published: %s%n", equipmentCode, payload);
        } catch (MqttException e) {
            System.err.println("Failed to publish anomaly to " + topic + ": " + e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        System.err.println("MQTT connection lost. Automatic reconnect is enabled. " + cause.getMessage());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // QoS 1 publish acknowledged — nothing to do.
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
