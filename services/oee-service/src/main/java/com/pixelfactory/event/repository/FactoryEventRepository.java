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

    List<FactoryEvent> findByEventTypeAndTargetTypeAndTargetIdAndCreatedAtBetweenOrderByCreatedAtAsc(
            FactoryEventType eventType,
            TargetType targetType,
            Long targetId,
            LocalDateTime from,
            LocalDateTime to
    );

    Optional<FactoryEvent> findFirstByEventTypeAndTargetTypeAndTargetIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
            FactoryEventType eventType,
            TargetType targetType,
            Long targetId,
            LocalDateTime at
    );
}
