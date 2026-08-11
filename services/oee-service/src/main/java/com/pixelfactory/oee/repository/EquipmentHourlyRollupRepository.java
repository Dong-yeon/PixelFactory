package com.pixelfactory.oee.repository;

import com.pixelfactory.oee.domain.EquipmentHourlyRollup;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquipmentHourlyRollupRepository extends JpaRepository<EquipmentHourlyRollup, Long> {

    boolean existsByEquipmentIdAndHourStart(Long equipmentId, LocalDateTime hourStart);

    @Query("select max(r.hourStart) from EquipmentHourlyRollup r where r.equipmentId = :equipmentId")
    LocalDateTime findMaxHourStart(@Param("equipmentId") Long equipmentId);

    List<EquipmentHourlyRollup> findByEquipmentIdAndHourStartGreaterThanEqualAndHourStartLessThanOrderByHourStartAsc(
            Long equipmentId,
            LocalDateTime from,
            LocalDateTime to
    );
}
