package com.mnm.api.global.ai;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * AI 서버 없이 화면 흐름을 돌리기 위한 가짜 구현.
 * - stt: 음성 내용과 상관없이 시연 문장(S1)을 돌려준다.
 * - route: 단어 규칙으로 일지 분류·귀표 끝 4자리·신고서 항목을 뽑는다 (LLM 아님, 정확도 보장 없음).
 * - summary: 명세(REQ-AI-09)가 규칙만으로 만든다고 정한 형식을 그대로 만든다.
 * shortcut: 실제 AI 서버(mnm-ai)가 배포되면 HTTP 구현으로 바꾸고 이 클래스는 테스트용으로만 남긴다.
 */
@Component
public class MockAiClient implements AiClient {

    /** 기능명세서 시연 시나리오 S1 발화 */
    static final String DEMO_STT_TEXT = "어젯밤에 0871번이 송아지 낳았어. 암컷이야.";

    private static final Pattern TAG4 = Pattern.compile("(?<!\\d)(\\d{4})(?!\\d)");
    private static final Pattern HEADS = Pattern.compile("(\\d+)\\s*(두|마리)");
    private static final Pattern FARM_NO = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)");

    /** 앞에 있는 분류가 먼저 걸린다. 출생·폐사·이동이 관찰 단어(먹·사료)보다 우선 */
    private static final Map<String, List<String>> KEYWORDS = new LinkedHashMap<>();

    static {
        KEYWORDS.put("출생", List.of("송아지", "낳", "출생", "분만"));
        KEYWORDS.put("폐사", List.of("폐사", "죽었", "죽어", "죽은"));
        KEYWORDS.put("이동", List.of("팔았", "팔려", "출하", "사 왔", "사왔", "옮겼", "이동", "양도", "양수", "도축"));
        KEYWORDS.put("접종", List.of("접종", "백신", "주사"));
        KEYWORDS.put("소독", List.of("소독"));
        KEYWORDS.put("출입", List.of("다녀갔", "왔다 갔", "방문", "사료차", "수의사", "출입"));
        KEYWORDS.put("사육현황", List.of("사육", "두수", "마리 있"));
        KEYWORDS.put("관찰", List.of("사료", "침", "기침", "설사", "절뚝", "열이", "안 먹", "남겼", "남김", "물집", "혹", "누워"));
    }

    private static final Map<String, String> REPORT_TYPE = Map.of("출생", "birth", "폐사", "death", "이동", "move");

    private static final Map<String, String> SYMPTOM_NAMES = new LinkedHashMap<>();

    static {
        SYMPTOM_NAMES.put("drool", "침 흘림");
        SYMPTOM_NAMES.put("blister", "물집");
        SYMPTOM_NAMES.put("feed", "사료 남김");
        SYMPTOM_NAMES.put("lump", "혹");
    }

    /** 시행규칙 제13조 칸 이름 (mnm-ai rules/report_state_machine FORM6_LABELS, mnm-data form_field 원문) */
    private static final List<String> FORM6_KEYS = List.of(
            "owner_and_location", "species_and_head_count", "condition", "found_date", "reporter", "other");
    private static final List<String> FORM6_LABELS = List.of(
            "1. 신고대상 가축의 소유자등의 성명(축산계열화사업자의 경우에는 회사ㆍ법인명으로 한다) 및 신고대상 가축의 사육장소 또는 발견장소",
            "2. 신고대상 가축의 종류 및 두수",
            "3. 질병명(수의사의 진단을 받지 아니한 때에는 신고자가 추정하는 병명 또는 발견당시의 상태)",
            "4. 죽은 연월일(죽은 연월일이 분명하지 아니한 때에는 발견 연월일)",
            "5. 신고자의 성명 및 주소",
            "6. 그 밖에 가축이 죽거나 병든 원인 등 신고에 관하여 필요한 사항");

    @Override
    public SttResult stt(byte[] audio, String contentType) {
        if (audio == null || audio.length == 0) {
            throw new AiException("STT-FAIL", "빈 음성");
        }
        return new SttResult(DEMO_STT_TEXT, 4.0);
    }

    @Override
    public RouteResult route(RouteRequest request) {
        Set<String> known = request.cows().stream().map(RouteCow::tag4).collect(Collectors.toSet());
        List<RouteItem> items = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String sentence : request.text().split("[.!?\\n]+")) {
            String s = sentence.trim();
            if (s.isEmpty()) {
                continue;
            }
            String category = classify(s);
            String tag4 = find(TAG4, s);
            if (category == null && tag4 == null && !items.isEmpty()) {
                // "암컷이야" 처럼 앞 문장을 이어 말한 것은 앞 항목에 붙인다
                RouteItem last = items.removeLast();
                items.add(new RouteItem(last.category(), last.value() + ". " + s, last.cowTag4(), last.vaccineType(),
                        last.headCount()));
                continue;
            }
            String cowTag4 = tag4 != null && known.contains(tag4) ? tag4 : null;
            if (tag4 != null && cowTag4 == null && !missing.contains("cow")) {
                missing.add("cow");
            }
            String vaccine = "접종".equals(category) ? vaccine(s) : null;
            String heads = find(HEADS, s);
            items.add(new RouteItem(category == null ? "메모" : category, s, cowTag4, vaccine,
                    heads == null ? null : Integer.valueOf(heads)));
        }
        RouteItem reportItem = items.stream().filter(i -> REPORT_TYPE.containsKey(i.category())).findFirst().orElse(null);
        if (reportItem == null) {
            return new RouteResult("diary", items, null, Map.of(), missing);
        }
        String type = REPORT_TYPE.get(reportItem.category());
        String date = relativeDate(request.text(), request.today()).toString();
        Map<String, Object> fields = new LinkedHashMap<>();
        switch (type) {
            case "birth" -> {
                fields.put("mother_tag4", reportItem.cowTag4());
                fields.put("birth_date", date);
                fields.put("sex", sex(request.text()));
            }
            case "death" -> {
                fields.put("cow_tag", reportItem.cowTag4());
                fields.put("death_date", date);
            }
            default -> {
                fields.put("move_type", moveType(request.text()));
                fields.put("cow_tag", reportItem.cowTag4());
                fields.put("move_date", date);
                fields.put("counterpart_farm_no", find(FARM_NO, request.text()));
                fields.put("counterpart_name", null);
            }
        }
        fields.forEach((k, v) -> {
            if (v == null && !"counterpart_name".equals(k) && !missing.contains(k)) {
                missing.add(k);
            }
        });
        return new RouteResult("history_report", items, type, fields, missing);
    }

    @Override
    public SummaryResult summary(SummaryRequest request) {
        LocalDate found = request.foundDate();
        List<String> matched = SYMPTOM_NAMES.entrySet().stream()
                .filter(e -> Boolean.TRUE.equals(request.symptomAnswers().get(e.getKey())))
                .map(Map.Entry::getValue)
                .toList();
        List<SummaryDiary> diary = request.diary() == null ? List.of() : request.diary();
        List<Map<String, Object>> related = diary.stream()
                .filter(d -> request.cow().tag4() != null && request.cow().tag4().equals(d.cowTag4()))
                .filter(d -> !d.date().isAfter(found) && !d.date().isBefore(found.minusDays(6)))
                .sorted(Comparator.comparing(SummaryDiary::date).reversed())
                .map(d -> map("category", d.category(), "value", d.value(), "date", d.date().toString()))
                .toList();

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cow", cowMap(request.cow()));
        card.put("matched_symptoms", matched);
        card.put("diary_related", related);
        card.put("photos", request.photos() == null ? List.of()
                : request.photos().stream().map(p -> Map.<String, Object>of("part", p.part(), "match", p.match())).toList());
        card.put("vaccine", latest(diary, "접종", found));
        card.put("disinfection", latest(diary, "소독", found));
        card.put("visit", latest(diary, "출입", found));

        // mnm-ai agents/receiver_copilot draft_form6 과 같은 규칙: 빈칸은 null + missing
        Map<String, Object> f = request.reportFields();
        String species = str(f.get("species"));
        Object heads = f.get("head_count");
        List<?> symptoms = f.get("symptoms") instanceof List<?> l && !l.isEmpty() ? l : null;
        Map<String, Object> form6 = new LinkedHashMap<>();
        form6.put("owner_and_location", str(f.get("owner_and_location")));
        form6.put("species_and_head_count", species == null || heads == null ? null
                : species + " " + heads + "두" + (request.farmTotalHead() == null ? "" : " (전체 " + request.farmTotalHead() + "두)"));
        form6.put("condition", symptoms == null ? null
                : "발견 당시 상태: " + symptoms.stream().map(Object::toString).collect(Collectors.joining(", ")));
        form6.put("found_date", str(f.get("found_date")));
        form6.put("reporter", str(f.get("reporter")));
        form6.put("other", Objects.toString(str(f.get("other")), ""));
        List<String> missing = form6.entrySet().stream()
                .filter(e -> e.getValue() == null)
                .map(Map.Entry::getKey)
                .toList();
        return new SummaryResult(card, form6, missing, form6Text(form6));
    }

    /** 인터넷 신고서 복사 텍스트. 빈칸은 '(빈칸)', 6호가 비면 '-' (mnm-ai draft_copy_text) */
    private static String form6Text(Map<String, Object> form6) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < FORM6_KEYS.size(); i++) {
            Object v = form6.get(FORM6_KEYS.get(i));
            lines.add(FORM6_LABELS.get(i) + ": " + (v == null ? "(빈칸)" : v.toString().isEmpty() ? "-" : v));
        }
        return String.join("\n", lines);
    }

    private static Map<String, Object> cowMap(SummaryCow cow) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("tag4", cow.tag4());
        map.put("desc", cow.desc());
        map.put("barn", cow.barn());
        return map;
    }

    private static Map<String, Object> latest(List<SummaryDiary> diary, String category, LocalDate found) {
        return diary.stream()
                .filter(d -> category.equals(d.category()) && !d.date().isAfter(found))
                .max(Comparator.comparing(SummaryDiary::date))
                .map(d -> map("date", d.date().toString(), "value", d.value(),
                        "days_ago", ChronoUnit.DAYS.between(d.date(), found)))
                .orElse(null);
    }

    /** 값에 null 이 있어도 되는 순서 있는 Map (Map.of 는 null 을 받지 않는다) */
    private static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static String classify(String sentence) {
        return KEYWORDS.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(sentence::contains))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private static LocalDate relativeDate(String text, LocalDate today) {
        if (text.contains("그저께") || text.contains("그제")) {
            return today.minusDays(2);
        }
        return text.contains("어제") || text.contains("어젯") ? today.minusDays(1) : today;
    }

    private static String sex(String text) {
        if (text.matches(".*(암컷|암소|암송아지).*")) {
            return "암";
        }
        return text.matches(".*(수컷|수소|수송아지).*") ? "수" : null;
    }

    private static String moveType(String text) {
        if (text.matches(".*(사 왔|사왔|양수).*")) {
            return "buy";
        }
        return text.matches(".*(팔았|팔려|출하|도축|양도).*") ? "sell" : "move";
    }

    private static String vaccine(String sentence) {
        if (sentence.contains("구제역")) {
            return "구제역";
        }
        return sentence.contains("럼피스킨") ? "럼피스킨" : null;
    }

    private static String find(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static String str(Object value) {
        return value == null || Objects.toString(value).isBlank() ? null : value.toString();
    }
}
