package com.mnm.api.global.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mnm.api.global.ai.AiClient.RouteCow;
import com.mnm.api.global.ai.AiClient.RouteRequest;
import com.mnm.api.global.ai.AiClient.RouteResult;
import com.mnm.api.global.ai.AiClient.SummaryCow;
import com.mnm.api.global.ai.AiClient.SummaryDiary;
import com.mnm.api.global.ai.AiClient.SummaryRequest;
import com.mnm.api.global.ai.AiClient.SummaryResult;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 가짜 AI 가 시연 흐름(S1·S2)에 쓸 만한 값을 명세 형식대로 내는지 */
class MockAiClientTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    private final MockAiClient client = new MockAiClient();

    @Test
    void S1_출생_발화는_신고서_항목과_어제_날짜로_정리된다() {
        RouteResult r = client.route(new RouteRequest(MockAiClient.DEMO_STT_TEXT, TODAY,
                List.of(new RouteCow("0871", null, "1번 축사", "002123450871"))));

        assertThat(r.intent()).isEqualTo("history_report");
        assertThat(r.reportType()).isEqualTo("birth");
        assertThat(r.reportFields()).containsEntry("mother_tag4", "0871")
                .containsEntry("birth_date", "2026-11-01")
                .containsEntry("sex", "암");
        assertThat(r.missing()).isEmpty();
        // "암컷이야" 는 앞 문장에 붙는다
        assertThat(r.items()).hasSize(1);
        assertThat(r.items().getFirst().category()).isEqualTo("출생");
    }

    @Test
    void 일지_발화는_분류별_항목으로_나뉘고_목록에_없는_번호는_빈칸() {
        RouteResult r = client.route(new RouteRequest("아침에 2번 축사 소독했어. 9999번이 사료를 좀 남겼어.", TODAY,
                List.of(new RouteCow("1234", null, null, null))));

        assertThat(r.intent()).isEqualTo("diary");
        assertThat(r.items()).extracting(AiClient.RouteItem::category).containsExactly("소독", "관찰");
        assertThat(r.items().get(1).cowTag4()).isNull();
        assertThat(r.missing()).containsExactly("cow");
    }

    @Test
    void 끝_4자리가_겹치면_4자리로는_정하지_않고_12자리로는_정한다() {
        List<RouteCow> cows = List.of(new RouteCow("0871", null, "1번 축사", "900000000871"),
                new RouteCow("0871", null, "2번 축사", "900000010871"));

        RouteResult four = client.route(new RouteRequest("0871번이 사료를 남겼어", TODAY, cows));
        assertThat(four.items().getFirst().cowTag4()).isNull();
        assertThat(four.items().getFirst().cowTag()).isNull();
        assertThat(four.missing()).containsExactly("cow");

        RouteResult full = client.route(new RouteRequest("900000010871번이 사료를 남겼어", TODAY, cows));
        assertThat(full.items().getFirst().cowTag4()).isEqualTo("0871");
        assertThat(full.items().getFirst().cowTag()).isEqualTo("900000010871");
        assertThat(full.missing()).isEmpty();
    }

    @Test
    void 빈_음성은_STT_FAIL() {
        assertThatThrownBy(() -> client.stt(new byte[0], "audio/webm"))
                .isInstanceOf(AiException.class)
                .extracting("code").isEqualTo("STT-FAIL");
    }

    @Test
    void 요약은_빈칸을_null_과_빈칸_표시로_두고_질병명_칸은_발견_당시_상태() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("owner_and_location", "김한우 · 충남 홍성군 홍성읍 2번 축사");
        fields.put("species", "한우");
        fields.put("head_count", 1);
        fields.put("symptoms", List.of("침 흘림", "사료 남김"));
        fields.put("found_date", "2026-11-02");
        fields.put("reporter", null);
        SummaryResult s = client.summary(new SummaryRequest("7", TODAY, new SummaryCow("1234", null, "2번 축사"),
                Map.of("drool", true, "blister", false, "feed", true, "lump", false), List.of(),
                List.of(new SummaryDiary("1234", "관찰", "사료 남김", LocalDate.of(2026, 10, 30)),
                        new SummaryDiary(null, "접종", "구제역 백신", LocalDate.of(2026, 10, 8))),
                fields, 42));

        assertThat(s.form6()).containsEntry("species_and_head_count", "한우 1두 (전체 42두)")
                .containsEntry("condition", "발견 당시 상태: 침 흘림, 사료 남김")
                .containsEntry("reporter", null);
        assertThat(s.missing()).containsExactly("reporter");
        assertThat(s.form6Text()).contains("5. 신고자의 성명 및 주소: (빈칸)").contains("6. 그 밖에").endsWith(": -");
        assertThat(s.card()).containsKey("vaccine");
        assertThat((List<?>) s.card().get("diary_related")).hasSize(1);
    }
}
