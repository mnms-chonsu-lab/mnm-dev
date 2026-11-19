package com.mnm.api.receiver;

import com.mnm.api.domain.alert.Alert;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ReceiverAlertRepository extends JpaRepository<Alert, Long> {

    /** 아직 열람·피드백하지 않은 알림, 최근순 (미응답 판정, 계산식 ⑥) */
    List<Alert> findByUserIdInAndReadAtIsNullAndFeedbackIsNullOrderByIdDesc(Collection<Long> userIds);

    /** 알림을 한 번이라도 받은 농가 계정 */
    @Query("select distinct a.user.id from Alert a where a.user.id in :userIds")
    List<Long> findUserIdsWithAlerts(@Param("userIds") Collection<Long> userIds);
}
