package com.mnm.api.receiver;

import com.mnm.api.domain.alert.Alert;
import com.mnm.api.global.error.BusinessException;
import com.mnm.api.global.error.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/** 접수자 쪽 규칙 코드 (상태 흐름, 처리 결과, 미응답, 연락 우선순위, 신고서 6개 항목). 판정이 아니라 기록값 그대로 쓴다 */
final class ReceiverRules {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    static final String NEW = "new";
    static final String CHECKING = "checking";
    static final String DONE = "done";

    /** 처리 결과 (R-P04 4개 중 1개 필수). 코드는 제안값, receiver_check.result 표준 목록 확정 전 */
    static final Map<String, String> RESULTS = linked(
            "inspection_request", "방역기관 검사 요청",
            "vet_guide", "수의사 진료 안내",
            "observe_guide", "경과 관찰 안내",
            "not_applicable", "공식 증상 해당 없음");
    /** 이력 신고 초안을 축협이 대신 신고함 (RCV-10) */
    static final String PROXY_REPORTED = "proxy_reported";

    /** 계산식 ⑥ 미응답: 알림 발송 후 48시간 동안 열람·피드백 없음 (제안값) */
    static final Duration UNANSWERED_AFTER = Duration.ofHours(48);
    static final int DUE_SOON_DAYS = 7;
    static final int SMALL_FARM_HEADS = 50;

    static final List<String> SYMPTOM_KEYS = List.of("drool", "blister", "feed", "lump");
    static final Map<String, String> SYMPTOM_NAMES = Map.of(
            "drool", "침 흘림", "blister", "물집", "feed", "사료 남김", "lump", "혹");
    static final Map<String, String> DISEASE_NAMES = Map.of("FMD", "구제역", "LSD", "럼피스킨");

    /** 신고서 6개 항목 (시행규칙 제13조). 키는 AI-INT-08 form6 기준 (소유자 성명과 사육장소는 한 칸) */
    static final Map<String, String> FORM6_LABELS = linked(
            "owner_and_location", "소유자 성명 · 사육장소 또는 발견장소",
            "species_and_head_count", "가축의 종류 및 두수",
            "condition", "질병명(추정 병명 또는 발견 당시 상태)",
            "found_date", "죽은 연월일(불분명 시 발견 연월일)",
            "reporter", "신고자 성명 및 주소");

    private ReceiverRules() {
    }

    static LocalDate today() {
        return LocalDate.now(KST);
    }

    /** 날짜 경계는 한국 시간 자정 */
    static Instant startOf(LocalDate date) {
        return date.atStartOfDay(KST).toInstant();
    }

    /**
     * 상태 흐름: 새로 도착 → 확인 중 → 처리 완료, 처리 완료 → 다시 열기(확인 중).
     * 이력 신고 초안은 완료를 RCV-10(대신 신고 마침)으로만 하고, 완료 뒤에는 다시 열지 않는다.
     */
    static void checkTransition(String current, String next, boolean history) {
        boolean ok = switch (next) {
            case CHECKING -> NEW.equals(current) || !history && DONE.equals(current);
            case DONE -> !history && CHECKING.equals(current);
            default -> false;
        };
        if (!ok) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "바꿀 수 없는 상태: " + current + " → " + next);
        }
    }

    /** 계산식 ② 공식 증상 일치 항목 (answers 의 "yes"). 판정 아님 */
    static List<String> matchedSymptoms(Map<String, Object> answers) {
        return SYMPTOM_KEYS.stream().filter(k -> yes(answers, k)).map(SYMPTOM_NAMES::get).toList();
    }

    /** 계산식 ④ 질병별 지침 분기: 혹만 "예"(침·물집은 아니요) → LSD, 그 외 → FMD. 모두 아니요면 null */
    static String disease(Map<String, Object> answers) {
        if (SYMPTOM_KEYS.stream().noneMatch(k -> yes(answers, k))) {
            return null;
        }
        return yes(answers, "lump") && !yes(answers, "drool") && !yes(answers, "blister") ? "LSD" : "FMD";
    }

    /**
     * 농가 알림 응답 상태. unread 는 이 농가의 안 읽은 알림.
     * unanswered(48시간 넘게 안 읽음) | waiting(보낸 지 48시간 안 됨) | seen(모두 확인함) | null(받은 알림 없음)
     */
    static String alertState(List<Alert> unread, boolean anyAlert, Instant now) {
        if (unread.stream().anyMatch(a -> isUnanswered(a, now))) {
            return "unanswered";
        }
        if (!unread.isEmpty()) {
            return "waiting";
        }
        return anyAlert ? "seen" : null;
    }

    static boolean isUnanswered(Alert alert, Instant now) {
        return alert.getReadAt() == null && alert.getFeedback() == null && alert.getCreatedAt() != null
                && !alert.getCreatedAt().isAfter(now.minus(UNANSWERED_AFTER));
    }

    /** 계산식 ⑩ 연락 우선순위: 미응답 → 기한 남은 일수 적은 순 → 두수 적은 순 (값이 없으면 뒤로) */
    static <T> Comparator<T> contactPriority(Predicate<T> unanswered, Function<T, Long> dDay,
            Function<T, Integer> headCount) {
        return Comparator.<T, Boolean>comparing(t -> !unanswered.test(t))
                .thenComparing(dDay, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(headCount, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    /** 010-1234-0937 → 010-****-0937 */
    static String maskPhone(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        return digits.length() < 8 ? "****" : digits.substring(0, 3) + "-****-" + digits.substring(digits.length() - 4);
    }

    /** [항목 전체 복사]용 줄바꿈 구분 텍스트. 빈칸은 '(빈칸)' */
    static String form6Text(Map<String, Object> form6) {
        StringBuilder text = new StringBuilder();
        FORM6_LABELS.forEach((key, label) -> {
            Object value = form6.get(key);
            text.append(label).append(": ")
                    .append(value == null || value.toString().isBlank() ? "(빈칸)" : value).append('\n');
        });
        return text.toString().stripTrailing();
    }

    private static boolean yes(Map<String, Object> answers, String key) {
        return "yes".equals(answers.get(key));
    }

    private static Map<String, String> linked(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
