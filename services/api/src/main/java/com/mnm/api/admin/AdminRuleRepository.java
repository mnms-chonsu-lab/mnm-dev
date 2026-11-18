package com.mnm.api.admin;

import com.mnm.api.domain.guide.BiosecurityRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AdminRuleRepository extends JpaRepository<BiosecurityRule, Long> {

    /** A-S05 문구 표. 분류별 화면 순서대로 (순서 없는 조항 본문은 뒤로) */
    @Query("""
            select r from BiosecurityRule r
            where (:kind is null or r.kind = :kind)
              and (:status is null or r.status = :status)
              and (:disease is null or r.disease = :disease)
            order by r.kind, r.displayOrder asc nulls last, r.id
            """)
    List<BiosecurityRule> search(@Param("kind") String kind, @Param("status") String status,
            @Param("disease") String disease);

    List<BiosecurityRule> findByKindAndStatus(String kind, String status);
}
