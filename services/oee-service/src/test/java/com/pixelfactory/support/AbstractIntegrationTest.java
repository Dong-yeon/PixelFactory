package com.pixelfactory.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * PostgreSQL + Mosquitto를 Testcontainers로 띄우고 Spring 컨텍스트를 그 위에서 부팅한다.
 * MQTT 컨슈머(MqttEventSubscriber)는 앱 기동(ApplicationReadyEvent) 시 이 브로커에
 * 실제로 연결되므로, 테스트가 이 브로커에 발행한 메시지는 프로덕션과 동일한
 * MqttMessageHandler → FactoryEventService → DB 경로를 그대로 탄다.
 *
 * 컨테이너는 static + 수동 start()로 JVM당 1회만 띄운다(싱글톤 컨테이너 패턴) —
 * 서브클래스마다 매번 새로 올리면 각 클래스당 수 초가 추가된다.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("pixelfactory")
                    .withUsername("pixel")
                    .withPassword("pixel");

    protected static final GenericContainer<?> MOSQUITTO =
            new GenericContainer<>("eclipse-mosquitto:2")
                    .withExposedPorts(1883)
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource("mosquitto-test.conf"),
                            "/mosquitto/config/mosquitto.conf"
                    );

    static {
        POSTGRES.start();
        MOSQUITTO.start();
    }

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("mqtt.enabled", () -> "true");
        registry.add("mqtt.broker-url", AbstractIntegrationTest::mosquittoUrl);
    }

    protected static String mosquittoUrl() {
        return "tcp://" + MOSQUITTO.getHost() + ":" + MOSQUITTO.getMappedPort(1883);
    }
}
