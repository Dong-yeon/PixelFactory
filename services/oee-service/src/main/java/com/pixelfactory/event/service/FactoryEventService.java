package com.pixelfactory.event.service;

import com.pixelfactory.event.domain.EventSeverity;
import com.pixelfactory.event.domain.FactoryEvent;
import com.pixelfactory.event.domain.FactoryEventType;
import com.pixelfactory.event.domain.SourceType;
import com.pixelfactory.event.domain.TargetType;
import com.pixelfactory.event.dto.FactoryEventCreateRequest;
import com.pixelfactory.event.dto.FactoryEventResponse;
import com.pixelfactory.event.repository.FactoryEventRepository;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FactoryEventService {

    private static final int DEFAULT_RECENT_LIMIT = 30;
    private static final int MAX_RECENT_LIMIT = 100;

    private final FactoryEventRepository factoryEventRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public FactoryEventService(
            FactoryEventRepository factoryEventRepository,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.factoryEventRepository = factoryEventRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    public FactoryEventResponse create(FactoryEventCreateRequest request) {
        FactoryEvent event = new FactoryEvent(
                request.eventType(),
                request.sourceType(),
                request.sourceId(),
                request.targetType(),
                request.targetId(),
                request.workOrderId(),
                request.lotNo(),
                request.severity(),
                request.message(),
                request.payloadJson()
        );

        return publishAndReturn(factoryEventRepository.save(event));
    }

    @Transactional
    public FactoryEventResponse record(
            FactoryEventType eventType,
            SourceType sourceType,
            Long sourceId,
            TargetType targetType,
            Long targetId,
            Long workOrderId,
            String lotNo,
            EventSeverity severity,
            String message,
            String payloadJson
    ) {
        FactoryEvent event = new FactoryEvent(
                eventType,
                sourceType,
                sourceId,
                targetType,
                targetId,
                workOrderId,
                lotNo,
                severity,
                message,
                payloadJson
        );

        return publishAndReturn(factoryEventRepository.save(event));
    }

    // 저장 직후 FactoryEventRecordedEvent를 publish한다. 리스너는 기본적으로
    // @TransactionalEventListener(AFTER_COMMIT)로 구독하므로, 여기서 커밋을 기다리지 않고
    // publish해도 실제 처리(실시간 push 등)는 트랜잭션 커밋 이후에만 일어난다.
    private FactoryEventResponse publishAndReturn(FactoryEvent savedEvent) {
        FactoryEventResponse response = FactoryEventResponse.from(savedEvent);
        applicationEventPublisher.publishEvent(new FactoryEventRecordedEvent(response));
        return response;
    }

    public List<FactoryEventResponse> getRecent(Integer limit) {
        int safeLimit = normalizeLimit(limit);

        return factoryEventRepository.findByOrderByCreatedAtDesc(PageRequest.of(0, safeLimit))
                .stream()
                .map(FactoryEventResponse::from)
                .toList();
    }

    public List<FactoryEventResponse> getByWorkOrder(Long workOrderId) {
        return factoryEventRepository.findByWorkOrderIdOrderByCreatedAtDesc(workOrderId)
                .stream()
                .map(FactoryEventResponse::from)
                .toList();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_RECENT_LIMIT;
        }

        if (limit < 1) {
            return DEFAULT_RECENT_LIMIT;
        }

        return Math.min(limit, MAX_RECENT_LIMIT);
    }
}
