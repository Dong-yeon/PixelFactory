package com.pixelfactory.event;

import com.pixelfactory.event.dto.FactoryEventResponse;

/**
 * FactoryEvent가 영속화될 때 발행되는 내부 스프링 이벤트.
 * WebSocket push 등 후처리는 이 이벤트를 구독한다 (커밋 이후에만 전파).
 */
public record FactoryEventSaved(FactoryEventResponse event) {
}
