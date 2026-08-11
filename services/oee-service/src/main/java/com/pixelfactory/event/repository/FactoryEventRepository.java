package com.pixelfactory.event.repository;

import com.pixelfactory.event.domain.FactoryEvent;
import com.pixelfactory.event.domain.FactoryEventType;
import com.pixelfactory.event.domain.TargetType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    @Query("""
            select min(e.createdAt) from FactoryEvent e
            where e.targetType = :targetType and e.targetId = :targetId
            """)
    LocalDateTime findMinCreatedAtByTarget(
            @Param("targetType") TargetType targetType,
            @Param("targetId") Long targetId
    );

    long deleteByEventTypeAndCreatedAtBefore(FactoryEventType eventType, LocalDateTime cutoff);

    // 상태 이벤트는 "더 최신 이벤트로 대체된 것"만 지운다 — 설비별 최신 1건이 남아
    // OEE의 "window 직전 상태" 조회가 오래 상태 변화가 없는 설비에서도 깨지지 않는다.
    // (id가 아니라 createdAt 기준 — 백필로 소급 적재된 데이터에서도 시간 순서가 맞도록)
    @Modifying
    @Query("""
            delete from FactoryEvent e
            where e.eventType = :eventType
              and e.createdAt < :cutoff
              and exists (
                  select 1 from FactoryEvent newer
                  where newer.eventType = :eventType
                    and newer.targetType = e.targetType
                    and newer.targetId = e.targetId
                    and newer.createdAt > e.createdAt
              )
            """)
    int deleteOldEventsKeepingLatestPerTarget(
            @Param("eventType") FactoryEventType eventType,
            @Param("cutoff") LocalDateTime cutoff
    );
}
