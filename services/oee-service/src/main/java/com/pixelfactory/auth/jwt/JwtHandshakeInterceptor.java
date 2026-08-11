package com.pixelfactory.auth.jwt;

import io.jsonwebtoken.JwtException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * WebSocket(STOMP) 핸드셰이크 인증. 브라우저 네이티브 WebSocket/SockJS는 커스텀 헤더를 못
 * 실어 보내므로 REST와 달리 Authorization 헤더 대신 쿼리 파라미터(?token=)로 JWT를 받는다.
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final String TOKEN_PARAM = "token";
    private static final String USERNAME_ATTRIBUTE = "username";

    private final JwtTokenProvider jwtTokenProvider;

    public JwtHandshakeInterceptor(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        String token = extractToken(request);

        if (!StringUtils.hasText(token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            jwtTokenProvider.validateToken(token);
            attributes.put(USERNAME_ATTRIBUTE, jwtTokenProvider.getUsername(token));
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        // no-op
    }

    private String extractToken(ServerHttpRequest request) {
        String query = request.getURI().getQuery();
        if (query == null) {
            return null;
        }

        for (String param : query.split("&")) {
            int separatorIndex = param.indexOf('=');
            if (separatorIndex <= 0) {
                continue;
            }
            String key = param.substring(0, separatorIndex);
            if (TOKEN_PARAM.equals(key)) {
                return URLDecoder.decode(param.substring(separatorIndex + 1), StandardCharsets.UTF_8);
            }
        }

        return null;
    }
}
