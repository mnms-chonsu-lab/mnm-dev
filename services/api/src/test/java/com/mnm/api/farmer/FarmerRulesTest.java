package com.mnm.api.farmer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 계산식 ①~④ 와 신고서 빈칸 규칙 (mnm-ai rules 와 같은 값이어야 한다) */
class FarmerRulesTest {

    private static final Set<LocalDate> NO_HOLIDAYS = Set.of();

    @ParameterizedTest(name = "{0} 발생 → {1} 까지")
    @CsvSource({
            // 화면설계서 예시: 폐사 11/2(월) → 11/9, 이동 11/3(화) → 11/10 (토·일 제외)
            "2026-11-02, 2026-11-09",
            "2026-11-03, 2026-11-10",
            // 금요일 발생: 월~금 5일
            "2026-11-06, 2026-11-13",
            // 토요일 발생: 다음 날(일) 안 셈 → 월~금
            "2026-11-07, 2026-11-13",
            // 일요일 발생 (출생 예시 11/1)
            "2026-11-01, 2026-11-06",
    })
    void 신고_기한은_발생_다음_날부터_토일을_빼고_5일째(LocalDate event, LocalDate due) {
        assertThat(FarmerRules.reportDue(event, NO_HOLIDAYS)).isEqualTo(due);
    }

    @Test
    void 공휴일은_기한_계산에서_빠진다() {
        // 2026-09-24(목)~26(토) 추석 연휴 가정: 9/22(화) 발생 → 23(1), 28(2), 29(3), 30(4), 10/1(5)
        Set<LocalDate> chuseok = Set.of(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 26));
        assertThat(FarmerRules.reportDue(LocalDate.of(2026, 9, 22), chuseok)).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void 귀표_기한은_출생_신고일_30일_뒤() {
        assertThat(FarmerRules.earTagDue(LocalDate.of(2026, 11, 2))).isEqualTo(LocalDate.of(2026, 12, 2));
    }

    @ParameterizedTest(name = "침{0} 물집{1} 사료{2} 혹{3} → 부위 {4}, 안내 {5}")
    @CsvSource({
            "no,  no,  no,  no,  '',               ",
            "yes, no,  no,  no,  mouth,            FMD",
            "no,  yes, no,  no,  mouth|hoof,       FMD",
            "no,  no,  yes, no,  flank,            FMD",
            "no,  no,  no,  yes, flank,            LSD",
            "no,  no,  yes, yes, flank,            LSD",
            "yes, no,  yes, no,  mouth,            FMD",
            "yes, no,  no,  yes, mouth|flank,      FMD",
            "yes, yes, yes, yes, mouth|hoof|flank, FMD",
            "unknown, unknown, unknown, unknown, '', ",
    })
    void 촬영_부위와_질병별_안내_분기(String drool, String blister, String feed, String lump, String parts, String disease) {
        Map<String, String> answers = Map.of("drool", drool, "blister", blister, "feed", feed, "lump", lump);
        List<String> expected = parts.isEmpty() ? List.of() : List.of(parts.split("\\|"));
        assertThat(FarmerRules.requiredParts(answers)).isEqualTo(expected);
        assertThat(FarmerRules.disease(answers)).isEqualTo(disease);
    }

    @Test
    void 공식_증상_일치는_예로_답한_질문_이름() {
        Map<String, String> answers = Map.of("drool", "yes", "blister", "unknown", "feed", "yes", "lump", "no");
        assertThat(FarmerRules.matchedSymptoms(answers)).containsExactly("침 흘림", "사료 남김");
    }

    @Test
    void 신고서_빈칸은_종류별_필수_칸만_보고_첫_칸을_묻는다() {
        Map<String, Object> birth = new java.util.HashMap<>(Map.of("mother_tag4", "0871", "birth_date", "2026-11-01"));
        assertThat(FarmerRules.missing("출생", birth)).containsExactly("sex", "breed");
        assertThat(FarmerRules.question("sex")).isEqualTo("송아지가 암컷이에요, 수컷이에요?");

        assertThat(FarmerRules.missing("양도", Map.of("cow_tag", "0871", "move_date", "2026-11-03")))
                .containsExactly("counterpart_farm_no");
        // 내 농장 다른 축사로 옮기는 이동은 상대 농장이 없다
        assertThat(FarmerRules.missing("이동", Map.of("cow_tag", "0871", "move_date", "2026-11-03"))).isEmpty();
        assertThat(FarmerRules.reportType("move", "sell")).isEqualTo("양도");
        assertThat(FarmerRules.reportType("move", "buy")).isEqualTo("양수");
    }
}
