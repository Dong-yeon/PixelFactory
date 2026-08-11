# Railway/컨테이너 배포용 — Gradle wrapper로 bootJar 빌드 후 JRE 이미지에서 실행.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
# Windows 체크아웃은 gradlew 실행 비트가 없을 수 있어 sh로 직접 실행한다.
RUN sh ./gradlew --no-daemon bootJar

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENV SPRING_PROFILES_ACTIVE=dev
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
