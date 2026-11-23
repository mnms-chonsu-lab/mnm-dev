package com.mnm.api.admin;

import com.mnm.api.domain.ops.EtlJobRun;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AdminEtlRepository extends JpaRepository<EtlJobRun, Long> {

    /** 작업별 실행 기록 최근순 */
    @Query("select e from EtlJobRun e where e.job = :job and e.id < :cursor order by e.id desc")
    List<EtlJobRun> runs(@Param("job") String job, @Param("cursor") Long cursor, Limit limit);

    /** 작업별 마지막 성공 */
    @Query("""
            select e from EtlJobRun e
            where e.status = 'success'
              and e.startedAt = (select max(e2.startedAt) from EtlJobRun e2 where e2.job = e.job and e2.status = 'success')
            """)
    List<EtlJobRun> lastSuccesses();

    /** 요청됐거나 실행 중인 작업 이름 */
    @Query("select distinct e.job from EtlJobRun e where e.status in ('requested', 'running')")
    List<String> busyJobs();

    boolean existsByJobAndStatusIn(String job, List<String> statuses);
}
