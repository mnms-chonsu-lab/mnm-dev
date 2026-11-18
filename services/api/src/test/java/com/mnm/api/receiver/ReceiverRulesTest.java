package com.mnm.api.receiver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mnm.api.domain.alert.Alert;
import com.mnm.api.global.error.BusinessException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class ReceiverRulesTest {

    @Test
    void 상태_흐름은_확인_시작_처리_완료_다시_열기만_된다() {
        assertThatCode(() -> ReceiverRules.checkTransition("new", "checking", false)).doesNotThrowAnyException();
        assertThatCode(() -> ReceiverRules.checkTransition("checking", "done", false)).doesNotThrowAnyException();
        assertThatCode(() -> ReceiverRules.checkTransition("done", "checking", false)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ReceiverRules.checkTransition("new", "done", false)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ReceiverRules.checkTransition("checking", "checking", false))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ReceiverRules.checkTransition("checking", "new", false))
                .isInstanceOf(BusinessException.class);
        // 이력 신고 초안: 완료는 RCV-10 으로만, 완료 뒤 다시 열기 없음
        assertThatCode(() -> ReceiverRules.checkTransition("new", "checking", true)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ReceiverRules.checkTransition("checking", "done", true))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ReceiverRules.checkTransition("done", "checking", true))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 질병_분기와_일치_증상은_예로_답한_것만_센다() {
        assertThat(ReceiverRules.disease(Map.of("lump", "yes", "drool", "no"))).isEqualTo("LSD");
        assertThat(ReceiverRules.disease(Map.of("lump", "yes", "drool", "yes"))).isEqualTo("FMD");
        assertThat(ReceiverRules.disease(Map.of("feed", "unknown"))).isNull();
        assertThat(ReceiverRules.matchedSymptoms(Map.of("blister", "yes", "feed", "unknown"))).containsExactly("물집");
    }

    @Test
    void 미응답은_48시간_넘게_안_읽은_알림이다() {
        Instant now = Instant.now();
        Alert fresh = alert(now.minus(Duration.ofHours(47)));
        Alert old = alert(now.minus(Duration.ofHours(48)));
        assertThat(ReceiverRules.alertState(List.of(fresh), true, now)).isEqualTo("waiting");
        assertThat(ReceiverRules.alertState(List.of(fresh, old), true, now)).isEqualTo("unanswered");
        assertThat(ReceiverRules.alertState(List.of(), true, now)).isEqualTo("seen");
        assertThat(ReceiverRules.alertState(List.of(), false, now)).isNull();
    }

    @Test
    void 연락_우선순위는_미응답_기한_두수_순이고_값이_없으면_뒤로() {
        record Row(String name, boolean unanswered, Long dDay, Integer heads) {
        }
        List<Row> rows = new ArrayList<>(List.of(
                new Row("기한 없음", false, null, 5),
                new Row("큰 농장", false, 2L, 100),
                new Row("미응답", true, null, 80),
                new Row("작은 농장", false, 2L, 9)));
        rows.sort(ReceiverRules.contactPriority(Row::unanswered, Row::dDay, Row::heads));
        assertThat(rows).extracting(Row::name).containsExactly("미응답", "작은 농장", "큰 농장", "기한 없음");
    }

    @Test
    void 번호_가리기와_6개_항목_복사_텍스트() {
        assertThat(ReceiverRules.maskPhone("010-1111-2210")).isEqualTo("010-****-2210");
        assertThat(ReceiverRules.maskPhone("123")).isEqualTo("****");
        Map<String, Object> form6 = new LinkedHashMap<>();
        form6.put("owner_and_location", "김한우 · 홍북읍");
        form6.put("condition", " ");
        String text = ReceiverRules.form6Text(form6);
        assertThat(text.lines()).hasSize(5);
        assertThat(text).startsWith("소유자 성명 · 사육장소 또는 발견장소: 김한우 · 홍북읍")
                .contains("질병명(추정 병명 또는 발견 당시 상태): (빈칸)");
    }

    private static Alert alert(Instant createdAt) {
        Alert alert = BeanUtils.instantiateClass(Alert.class);
        ReflectionTestUtils.setField(alert, "createdAt", createdAt);
        return alert;
    }
}
