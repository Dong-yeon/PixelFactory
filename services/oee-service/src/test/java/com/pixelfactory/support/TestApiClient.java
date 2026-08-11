package com.pixelfactory.support;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;

/**
 * TestRestTemplate에 로그인 토큰을 자동으로 실어 보내는 얇은 래퍼.
 * REST 계약(ApiResponse 봉투)을 그대로 타는 통합 테스트 전용 —
 * 서비스 빈을 직접 호출하지 않고 실제 HTTP를 통해 검증한다.
 *
 * data는 ApiResponse<T>의 T를 Jackson이 raw Map/List/Number로 역직렬화한 것 —
 * 테스트에서는 캐스팅해서 필드를 꺼내 쓴다.
 */
public final class TestApiClient {

    public record ApiEnvelope(boolean success, Object data, Object error) {
    }

    private final TestRestTemplate restTemplate;
    private final String baseUrl;
    private String token;

    public TestApiClient(TestRestTemplate restTemplate, int port) {
        this.restTemplate = restTemplate;
        this.baseUrl = "http://localhost:" + port;
    }

    public TestApiClient loginAs(String username) {
        record LoginRequest(String username, String password) {
        }

        var response = restTemplate.postForEntity(
                baseUrl + "/api/auth/login",
                new LoginRequest(username, "password"),
                ApiEnvelope.class
        );
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Login failed for " + username + ": " + response);
        }
        this.token = (String) asMap(response.getBody().data()).get("accessToken");
        return this;
    }

    public ApiEnvelope get(String path) {
        return exchange(HttpMethod.GET, path, null);
    }

    public ApiEnvelope post(String path, Object body) {
        return exchange(HttpMethod.POST, path, body);
    }

    public ApiEnvelope patch(String path, Object body) {
        return exchange(HttpMethod.PATCH, path, body);
    }

    public HttpStatusCode statusOf(HttpMethod method, String path, Object body) {
        HttpHeaders headers = authHeaders();
        return restTemplate.exchange(baseUrl + path, method, new HttpEntity<>(body, headers), String.class)
                .getStatusCode();
    }

    public Long equipmentId(String equipmentCode) {
        return equipmentField(equipmentCode, eq -> ((Number) eq.get("id")).longValue());
    }

    public String equipmentStatus(String equipmentCode) {
        return equipmentField(equipmentCode, eq -> (String) eq.get("status"));
    }

    private <T> T equipmentField(String equipmentCode, java.util.function.Function<java.util.Map<String, Object>, T> extractor) {
        return asList(get("/api/equipments").data()).stream()
                .filter(eq -> equipmentCode.equals(eq.get("equipmentCode")))
                .findFirst()
                .map(extractor)
                .orElseThrow(() -> new IllegalStateException("Equipment not found: " + equipmentCode));
    }

    @SuppressWarnings("unchecked")
    public static java.util.Map<String, Object> asMap(Object value) {
        return (java.util.Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    public static java.util.List<java.util.Map<String, Object>> asList(Object value) {
        return (java.util.List<java.util.Map<String, Object>>) value;
    }

    private ApiEnvelope exchange(HttpMethod method, String path, Object body) {
        HttpHeaders headers = authHeaders();
        var response = restTemplate.exchange(
                baseUrl + path, method, new HttpEntity<>(body, headers), ApiEnvelope.class
        );
        if (response.getBody() == null) {
            throw new IllegalStateException(method + " " + path + " returned no body: " + response);
        }
        return response.getBody();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }
}
