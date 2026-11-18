package com.mnm.api.farmer;

import com.mnm.api.domain.guide.BiosecurityRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 지식베이스는 게시(published)된 문구만 읽는다 */
interface FarmerRuleRepository extends JpaRepository<BiosecurityRule, Long> {

    List<BiosecurityRule> findByKindAndStatusOrderByDisplayOrderAscIdAsc(String kind, String status);
}
