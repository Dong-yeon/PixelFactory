package com.pixelfactory.event.repository;

import com.pixelfactory.event.domain.FactoryEvent;
import com.pixelfactory.event.domain.FactoryEventType;
import com.pixelfactory.event.domain.TargetType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FactoryEventRepository extends JpaRepository<FactoryEvent, Long> {

    List<FactoryEvent> findByOrderByCreatedAtDesc(Pageable pageable);

    List<FactoryEvent> findByWorkOrderIdOrderByCreatedAtDesc(Long workOrderId);

    @Query("""
            select e from FactoryEvent e
            where e.targetType = :targetType
              and e.targetId = :targetId
              and e.eventType = :eventType
              and e.createdAt >= :from
              and e.createdAt < :to
            order by e.createdAt asc
            """)
    List<FactoryEvent> findTargetEventsInWindow(
            @Param("targetType") TargetType targetType,
            @Param("targetId") Long targetId,
            @Param("eventType") FactoryEventType eventType,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    Optional<FactoryEvent> findFirstByTargetTypeAndTargetIdAndEventTypeAndCreatedAtLessThanOrderByCreatedAtDesc(
            TargetType targetType,
            Long targetId,
            FactoryEventType eventType,
            LocalDateTime before
    );
}
