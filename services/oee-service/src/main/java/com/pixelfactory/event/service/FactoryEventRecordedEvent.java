package com.pixelfactory.event.service;

import com.pixelfactory.event.dto.FactoryEventResponse;

/**
 * FactoryEvent가 저장될 때마다 발행되는 애플리케이션 이벤트.
 * 실시간 push 등 부가 동작은 이 이벤트를 구독해서 처리한다 — FactoryEventService가
 * WebSocket/OEE 같은 하위 관심사를 직접 알 필요가 없도록 분리한다.
 */
public record FactoryEventRecordedEvent(FactoryEventResponse event) {
}
