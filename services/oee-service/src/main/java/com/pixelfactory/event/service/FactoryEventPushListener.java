package com.pixelfactory.event.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 이벤트 타임라인용 실시간 feed. 모든 FactoryEvent를 그대로 /topic/events로 broadcast한다
 * (공통 코어 — 특정 이벤트 타입에 대한 판단 없이 그대로 흘려보냄).
 */
@Component
public class FactoryEventPushListener {

    private static final String EVENTS_TOPIC = "/topic/events";

    private final SimpMessagingTemplate messagingTemplate;

    public FactoryEventPushListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    // 트랜잭션 커밋 이후에만 push한다 — 롤백된 이벤트가 대시보드에 노출되는 것을 막는다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFactoryEventRecorded(FactoryEventRecordedEvent event) {
        messagingTemplate.convertAndSend(EVENTS_TOPIC, event.event());
    }
}
