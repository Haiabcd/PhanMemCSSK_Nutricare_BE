package com.hn.nutricarebe.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hn.nutricarebe.dto.response.DailyWaterTotalDto;
import com.hn.nutricarebe.entity.WaterLog;

public interface WaterLogRepository extends JpaRepository<WaterLog, UUID> {
    @Query("SELECT COALESCE(SUM(w.amountMl), 0) " + "FROM WaterLog w " + "WHERE w.user.id = :userId AND w.date = :date")
    int sumAmountByUserAndDate(@Param("userId") UUID userId, @Param("date") LocalDate date);

    @Query(
            """
select new com.hn.nutricarebe.dto.response.DailyWaterTotalDto(
	wl.date,
	coalesce(sum(wl.amountMl), 0)
)
from WaterLog wl
where wl.user.id = :userId
and wl.date between :start and :end
group by wl.date
order by wl.date asc
""")
    List<DailyWaterTotalDto> sumDailyByUserAndDateBetween(
            @Param("userId") UUID userId, @Param("start") LocalDate start, @Param("end") LocalDate end);
}
