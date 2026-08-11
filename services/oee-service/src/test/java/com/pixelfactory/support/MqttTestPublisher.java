package com.pixelfactory.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * simulator/ai-service를 흉내 내 테스트 브로커에 직접 발행하는 헬퍼.
 * 실제 Paho 클라이언트를 쓰므로 oee-service가 받는 메시지는 프로덕션과 동일하다.
 */
public final class MqttTestPublisher implements AutoCloseable {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MqttClient client;

    public MqttTestPublisher(String brokerUrl) {
        try {
            this.client = new MqttClient(brokerUrl, "test-publisher-" + System.nanoTime(), new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            client.connect(options);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to connect test MQTT publisher to " + brokerUrl, e);
        }
    }

    public void publishStatus(String lineCode, String equipmentCode, String status) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("status", status);
        payload.put("ts", Instant.now().toString());
        publish(lineCode, equipmentCode, "status", payload);
    }

    public void publishCycle(String lineCode, String equipmentCode, long cycleTimeMs, boolean defect) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("cycleTimeMs", cycleTimeMs);
        payload.put("defect", defect);
        payload.put("ts", Instant.now().toString());
        publish(lineCode, equipmentCode, "cycle", payload);
    }

    public void publishAnomaly(String lineCode, String equipmentCode, String anomalyType) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("anomalyType", anomalyType);
        payload.put("ts", Instant.now().toString());
        publish(lineCode, equipmentCode, "anomaly", payload);
    }

    private void publish(String lineCode, String equipmentCode, String kind, ObjectNode payload) {
        try {
            MqttMessage message = new MqttMessage(payload.toString().getBytes(StandardCharsets.UTF_8));
            message.setQos(1);
            client.publish("factory/" + lineCode + "/" + equipmentCode + "/" + kind, message);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish MQTT test message", e);
        }
    }

    @Override
    public void close() {
        try {
            client.disconnect();
            client.close();
        } catch (Exception ignored) {
            // best-effort cleanup
        }
    }
}
