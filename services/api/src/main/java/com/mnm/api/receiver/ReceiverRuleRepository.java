package com.mnm.api.receiver;

import com.mnm.api.domain.guide.BiosecurityRule;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 지식베이스는 게시(published)된 문구만 읽는다 */
interface ReceiverRuleRepository extends JpaRepository<BiosecurityRule, Long> {

    List<BiosecurityRule> findByKindAndStatusOrderByDisplayOrderAscIdAsc(String kind, String status);

    List<BiosecurityRule> findByKindInAndStatusOrderByDisplayOrderAscIdAsc(Collection<String> kinds, String status);
}
