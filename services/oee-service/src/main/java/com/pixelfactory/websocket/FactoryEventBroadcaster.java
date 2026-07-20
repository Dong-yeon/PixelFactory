package com.pixelfactory.websocket;

import com.pixelfactory.event.FactoryEventSaved;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class FactoryEventBroadcaster {

    private final StreamWebSocketHandler streamWebSocketHandler;

    public FactoryEventBroadcaster(StreamWebSocketHandler streamWebSocketHandler) {
        this.streamWebSocketHandler = streamWebSocketHandler;
    }

    // AFTER_COMMIT: 롤백된 이벤트가 대시보드로 새어 나가지 않게 한다.
    @TransactionalEventListener
    public void onFactoryEventSaved(FactoryEventSaved saved) {
        streamWebSocketHandler.broadcast("event", saved.event());
    }
}
