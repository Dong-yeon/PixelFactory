FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN sh ./gradlew --no-daemon installDist

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/install/ai-service ./
# MQTT_URL 환경변수로 브로커 지정 (기본 tcp://localhost:1883)
ENTRYPOINT ["bin/ai-service"]
