package com.pixelfactory.event.repository;

import com.pixelfactory.event.domain.FactoryEvent;
import com.pixelfactory.event.domain.FactoryEventType;
import com.pixelfactory.event.domain.TargetType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FactoryEventRepository extends JpaRepository<FactoryEvent, Long> {

    List<FactoryEvent> findByOrderByCreatedAtDesc(Pageable pageable);

    List<FactoryEvent> findByWorkOrderIdOrderByCreatedAtDesc(Long workOrderId);

    // OEE 계산: 조회 창 시작 시점에 대상이 어떤 상태였는지 알기 위한 직전 이벤트 1건.
    Optional<FactoryEvent> findTopByTargetTypeAndTargetIdAndEventTypeAndCreatedAtBeforeOrderByCreatedAtDesc(
            TargetType targetType, Long targetId, FactoryEventType eventType, LocalDateTime before
    );

    // OEE 계산: 조회 창 내의 상태 전이/사이클 이벤트를 시간순으로.
    List<FactoryEvent> findByTargetTypeAndTargetIdAndEventTypeAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(
            TargetType targetType, Long targetId, FactoryEventType eventType, LocalDateTime start, LocalDateTime end
    );
}
