package com.mnm.api.admin;

import com.mnm.api.domain.user.User;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AdminUserRepository extends JpaRepository<User, Long> {

    /**
     * A-S02 사용자 목록. 행 = [User, FarmInfo(농가만, 나머지 null)]. 최근에 만든 계정부터.
     * org_id 는 접수자 소속 기관 또는 농가 담당 축협. q 는 이름·아이디·연락처·농장명·농장식별번호 부분 일치
     */
    @Query("""
            select u, f from User u
            left join u.org o
            left join FarmInfo f on f.owner = u
            left join f.org fo
            where (:role is null or u.role = :role)
              and (:status is null or u.status = :status)
              and (:orgId is null or o.id = :orgId or fo.id = :orgId)
              and (:q is null or u.name like :q or u.loginId like :q or u.phone like :q
                   or f.name like :q or f.farmNo like :q)
              and u.id < :cursor
            order by u.id desc
            """)
    List<Object[]> search(@Param("role") String role, @Param("status") String status, @Param("orgId") Long orgId,
            @Param("q") String q, @Param("cursor") Long cursor, Limit limit);

    boolean existsByLoginId(String loginId);

    /** 농가 로그인 키라 휴대폰 번호는 농가끼리 겹치면 안 된다. 하이픈 유무와 상관없이 숫자로 비교 */
    @Query(value = """
            select exists (select 1 from users
                           where role = 'farmer' and users_id <> :excludeId
                             and regexp_replace(phone, '\\D', '', 'g') = :digits)
            """, nativeQuery = true)
    boolean existsFarmerPhone(@Param("digits") String digits, @Param("excludeId") Long excludeId);

    /** A-S09 접수자 수. 행 = [org_id, inbox_id, 인원] */
    @Query("""
            select u.org.id, u.inboxId, count(u) from User u
            where u.role = 'receiver' and u.org is not null
            group by u.org.id, u.inboxId
            """)
    List<Object[]> countReceiversByInbox();
}
