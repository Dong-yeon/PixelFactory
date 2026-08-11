package com.pixelfactory.websocket;

import com.pixelfactory.event.service.FactoryEventRecordedEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class FactoryEventPushListener {

    private final SimpMessagingTemplate messagingTemplate;

    public FactoryEventPushListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    // AFTER_COMMIT: 롤백된 이벤트가 대시보드로 새어 나가지 않게 한다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onFactoryEventRecorded(FactoryEventRecordedEvent event) {
        messagingTemplate.convertAndSend("/topic/events", event.event());
    }
}
