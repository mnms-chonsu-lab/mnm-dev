package com.mnm.api.farmer;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 틀리면 안 되는 판단을 코드로 처리하는 규칙 (산출값 요구사항 계산식 ①~④, REQ-AI-04~06). LLM 을 쓰지 않는다.
 * mnm-ai rules/(deadline · disease_branch · report_state_machine)와 같은 규칙·값을 옮겼다. mnm-ai 내부 API 서버는
 * /ai/* 만 열고 /rules/* 는 열지 않으므로(mnm-ai#7) 서버에서 계산한다. mnm-ai 규칙이 바뀌면 여기도 같이 고친다.
 */
final class FarmerRules {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * 증상 질문 답변 키·이름. 질문 순서: 침 → 물집 → 사료 → 혹.
     * mnm-ai rules/disease_branch 와 같은 값 (DB 주석의 nodule 대신 AI 입력 형식의 lump 를 쓴다)
     */
    static final List<String> SYMPTOM_KEYS = List.of("drool", "blister", "feed", "lump");
    static final Map<String, String> SYMPTOM_NAMES = Map.of(
            "drool", "침 흘림", "blister", "물집", "feed", "사료 남김", "lump", "혹");

    /** 촬영 부위 코드 (mnm-ai rules · mnm-data biosecurity_rule.part_code) */
    static final String MOUTH = "mouth";
    static final String HOOF = "hoof";
    static final String FLANK = "flank";

    /** 신고서 종류 (history_report.type) */
    static final List<String> REPORT_TYPES = List.of("출생", "폐사", "양도", "양수", "이동");

    private FarmerRules() {
    }

    static LocalDate today() {
        return LocalDate.now(KST);
    }

    /** ① 신고 기한: 발생일 다음 날부터 토요일·일요일·공휴일을 빼고 5일째 되는 날 */
    static LocalDate reportDue(LocalDate eventDate, Set<LocalDate> holidays) {
        LocalDate day = eventDate;
        int counted = 0;
        while (counted < 5) {
            day = day.plusDays(1);
            boolean weekend = day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
            if (!weekend && !holidays.contains(day)) {
                counted++;
            }
        }
        return day;
    }

    /** ① 귀표 기한: 출생 신고일 + 30일 */
    static LocalDate earTagDue(LocalDate birthReportDate) {
        return birthReportDate.plusDays(30);
    }

    /** ② 공식 증상 일치 = "예"로 답한 질문의 증상 이름 (판정 아님) */
    static List<String> matchedSymptoms(Map<String, String> answers) {
        return SYMPTOM_KEYS.stream().filter(k -> yes(answers, k)).map(SYMPTOM_NAMES::get).toList();
    }

    /** ③ 촬영 부위: 침 → 입 / 물집 → 입 + 발굽 / 혹 → 몸 옆면 / 사료만 → 몸 옆면 / 모두 아니요 → 없음 */
    static List<String> requiredParts(Map<String, String> answers) {
        Set<String> parts = new LinkedHashSet<>();
        if (yes(answers, "drool") || yes(answers, "blister")) {
            parts.add(MOUTH);
        }
        if (yes(answers, "blister")) {
            parts.add(HOOF);
        }
        if (yes(answers, "lump") || (yes(answers, "feed") && parts.isEmpty())) {
            parts.add(FLANK);
        }
        return List.copyOf(parts);
    }

    /** ④ 질병별 안내: 모두 아니요 → null(안내 없음) / 혹 "예"이고 침·물집 아님 → LSD(럼피스킨) / 그 외 → FMD(구제역) */
    static String disease(Map<String, String> answers) {
        if (SYMPTOM_KEYS.stream().noneMatch(k -> yes(answers, k))) {
            return null;
        }
        return yes(answers, "lump") && !yes(answers, "drool") && !yes(answers, "blister") ? "LSD" : "FMD";
    }

    /**
     * 신고서 상태 머신: 종류별 필수 칸 (REQ-AI-05, F-S04Q). 출생은 mnm-ai rules/report_state_machine 과 같다
     * (송아지 귀표는 출생 신고 후 위탁기관이 부여하므로 선택). 폐사·양도·양수·이동은 mnm-ai 에 없어 화면설계서 F-S04Q 기준.
     */
    static List<String> requiredFields(String type) {
        return switch (type) {
            case "출생" -> List.of("mother_tag4", "birth_date", "sex", "breed");
            case "폐사" -> List.of("cow_tag", "death_date");
            case "양도", "양수" -> List.of("cow_tag", "move_date", "counterpart_farm_no");
            default -> List.of("cow_tag", "move_date");
        };
    }

    /** 신고서 발생일 칸 */
    static String eventDateField(String type) {
        return switch (type) {
            case "출생" -> "birth_date";
            case "폐사" -> "death_date";
            default -> "move_date";
        };
    }

    /** 빈칸 목록. 값이 없거나 비어 있으면 빈칸 */
    static List<String> missing(String type, Map<String, Object> fields) {
        List<String> missing = new ArrayList<>();
        for (String key : requiredFields(type)) {
            Object value = fields.get(key);
            if (value == null || value.toString().isBlank()) {
                missing.add(key);
            }
        }
        return missing;
    }

    /** 빈칸마다 한 문장 질문 (해요체). 다음 질문은 빈칸 목록의 첫 칸 */
    static String question(String field) {
        return switch (field) {
            case "mother_tag4" -> "어미 소 귀표 번호 끝 4자리를 말씀해 주세요.";
            case "birth_date" -> "송아지가 태어난 날이 언제예요?";
            case "sex" -> "송아지가 암컷이에요, 수컷이에요?";
            case "breed" -> "송아지 품종을 알려 주세요. 예를 들면 한우예요.";
            case "cow_tag" -> "어느 소인가요? 귀표 끝 4자리를 알려 주세요.";
            case "death_date" -> "소가 죽은 날을 알려 주세요.";
            case "move_date" -> "소를 보내거나 받은 날을 알려 주세요.";
            case "counterpart_farm_no" -> "상대 농장식별번호를 알려 주세요.";
            default -> null;
        };
    }

    /** 라우터 신고 종류(birth|death|move + move_type) → history_report.type */
    static String reportType(String routeType, Object moveType) {
        return switch (routeType) {
            case "birth" -> "출생";
            case "death" -> "폐사";
            default -> "sell".equals(moveType) ? "양도" : "buy".equals(moveType) ? "양수" : "이동";
        };
    }

    /** 기한 할 일·알림에 쓰는 제목 */
    static String taskTitle(String type) {
        return switch (type) {
            case "출생" -> "송아지 출생 신고";
            case "폐사" -> "소 폐사 신고";
            case "양도" -> "소 양도 신고";
            case "양수" -> "소 양수 신고";
            default -> "소 이동 신고";
        };
    }

    private static boolean yes(Map<String, String> answers, String key) {
        return "yes".equals(answers.get(key));
    }
}
