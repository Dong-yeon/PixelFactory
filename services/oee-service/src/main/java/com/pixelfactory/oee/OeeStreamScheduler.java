package com.pixelfactory.oee;

import com.pixelfactory.websocket.StreamWebSocketHandler;
import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 접속 중인 대시보드가 있을 때만 최근 60분 윈도우의 OEE 스냅샷을 주기 push한다.
 */
@Component
public class OeeStreamScheduler {

    private static final int WINDOW_MINUTES = 60;

    private final OeeCalculationService oeeCalculationService;
    private final StreamWebSocketHandler streamWebSocketHandler;

    public OeeStreamScheduler(
            OeeCalculationService oeeCalculationService,
            StreamWebSocketHandler streamWebSocketHandler
    ) {
        this.oeeCalculationService = oeeCalculationService;
        this.streamWebSocketHandler = streamWebSocketHandler;
    }

    @Scheduled(fixedDelayString = "${oee.push-interval-ms:5000}")
    public void pushOeeSnapshots() {
        if (!streamWebSocketHandler.hasActiveSessions()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        streamWebSocketHandler.broadcast("oee", oeeCalculationService.snapshotAll(now.minusMinutes(WINDOW_MINUTES), now));
    }
}
