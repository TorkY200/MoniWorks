package com.example.application.repository;

import com.example.application.domain.KPI;
import com.example.application.domain.KPIValue;
import com.example.application.domain.Period;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KPIValueRepository extends JpaRepository<KPIValue, Long> {

    List<KPIValue> findByKpiOrderByPeriodStartDateDesc(KPI kpi);

    Optional<KPIValue> findByKpiAndPeriod(KPI kpi, Period period);

    @Query("SELECT kv FROM KPIValue kv WHERE kv.kpi = :kpi " +
           "AND kv.period.fiscalYear.id = :fiscalYearId ORDER BY kv.period.startDate")
    List<KPIValue> findByKpiAndFiscalYear(@Param("kpi") KPI kpi,
                                           @Param("fiscalYearId") Long fiscalYearId);

    @Query("SELECT kv FROM KPIValue kv WHERE kv.kpi.company.id = :companyId " +
           "AND kv.period.fiscalYear.id = :fiscalYearId ORDER BY kv.kpi.code, kv.period.startDate")
    List<KPIValue> findByCompanyAndFiscalYear(@Param("companyId") Long companyId,
                                               @Param("fiscalYearId") Long fiscalYearId);

    void deleteByKpi(KPI kpi);
}
