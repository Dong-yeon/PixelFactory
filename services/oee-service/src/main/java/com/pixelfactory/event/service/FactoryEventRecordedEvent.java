package com.pixelfactory.event.service;

import com.pixelfactory.event.dto.FactoryEventResponse;

/** FactoryEvent 영속화 시 발행되는 인프로세스 이벤트 — WebSocket push 등 후처리용. */
public record FactoryEventRecordedEvent(FactoryEventResponse event) {
}
