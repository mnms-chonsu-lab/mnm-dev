package com.mnm.api.global.ai;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 서버 → AI 서비스 호출 (docs/api/ai-internal.yaml, AI-INT-*). 요청·응답 이름은 그 명세를 따른다.
 * 실패하면 {@link AiException}을 던지고, 부르는 쪽이 명세의 대체 경로(글쓰기·선택형 입력·원본 기록 표시)로 진행한다.
 * shortcut: 지금 구현은 {@link MockAiClient}뿐이다. AI 서버(mnm-ai)가 배포되면 AI_BASE_URL 로 호출하는 구현으로 바꾼다.
 */
public interface AiClient {

    /** AI-INT-02 /ai/stt: 음성 → 문장 */
    SttResult stt(byte[] audio, String contentType);

    /** AI-INT-01 /ai/route: 문장 → 의도 + 일지 항목 + 신고서 항목 */
    RouteResult route(RouteRequest request);

    /** AI-INT-08 /ai/summary: 승인된 소 상태 기록 → 접수자용 한 장 요약 + 신고서 6개 항목 (규칙만 사용) */
    SummaryResult summary(SummaryRequest request);

    record SttResult(String text, double durationSec) {
    }

    /** 이름·전화번호는 넣지 않는다 */
    record RouteRequest(String text, LocalDate today, List<RouteCow> cows) {
    }

    record RouteCow(String tag4, String desc, String barn) {
    }

    /**
     * intent: diary | history_report | symptom. reportType: birth | death | move | null.
     * reportFields 는 신고서 종류별 항목(birth: mother_tag4·birth_date·sex / death: cow_tag·death_date /
     * move: move_type·cow_tag·move_date·counterpart_farm_no·counterpart_name). 날짜는 "YYYY-MM-DD" 문자열.
     */
    record RouteResult(String intent, List<RouteItem> items, String reportType, Map<String, Object> reportFields,
            List<String> missing) {
    }

    /** category: 사육현황 | 접종 | 소독 | 관찰 | 출입 | 출생 | 폐사 | 이동 | 메모. cowTag4 는 소 목록에 없으면 null */
    record RouteItem(String category, String value, String cowTag4, String vaccineType, Integer headCount) {
    }

    /**
     * symptomAnswers 키: drool · blister · lump · feed (예 = true).
     * reportFields 키 (mnm-ai 의심 신고 상태 머신): owner_and_location · species · head_count · symptoms(문장 목록) ·
     * found_date · reporter · other. 빈칸은 null 로 보내고, 응답의 form6 에서도 null + missing 이 된다.
     */
    record SummaryRequest(String recordId, LocalDate foundDate, SummaryCow cow, Map<String, Boolean> symptomAnswers,
            List<SummaryPhoto> photos, List<SummaryDiary> diary, Map<String, Object> reportFields,
            Integer farmTotalHead) {
    }

    record SummaryCow(String tag4, String desc, String barn) {
    }

    /** part: mouth | udder | hoof | flank */
    record SummaryPhoto(String part, boolean match) {
    }

    /** category 는 일지 분류 10개('신고' 포함). 개체 기록이 아니면 cowTag4 = null */
    record SummaryDiary(String cowTag4, String category, String value, LocalDate date) {
    }

    /** card·form6 은 접수자 웹이 그대로 보여주는 JSON. form6Text 는 인터넷 신고서 복사용 (빈칸은 '(빈칸)') */
    record SummaryResult(Map<String, Object> card, Map<String, Object> form6, List<String> missing,
            String form6Text) {
    }

    /** AI-INT-03 /ai/part-check: 사진 주소(서명 URL) + 요청 부위(mouth | udder | hoof | flank) → 일치 여부·화질 */
    PartCheckResult partCheck(String imageUrl, String requestedPart);

    /** AI-INT-04 /ai/behavior: 영상 주소(서명 URL) → 프레임별 서기·눕기·먹기 */
    BehaviorResult behavior(String videoUrl);

    /** predictedPart 는 로그·어드민용 (농가 화면에 내보내지 않는다) */
    record PartCheckResult(String requestedPart, String predictedPart, boolean match, double score, Quality quality,
            String modelVersion, boolean fallback) {
    }

    /** brightness: 평균 밝기 0~255 */
    record Quality(boolean dark, boolean blurry, double brightness) {
    }

    /** frames: 검출된 소 1마리(박스 1개)당 1줄. mainLabel 은 검출이 없으면 null */
    record BehaviorResult(List<BehaviorFrame> frames, String mainLabel, String modelVersion) {
    }

    /** t: 영상 안 시각(초). label: stand | lie | eat */
    record BehaviorFrame(double t, String label, double confidence) {
    }
}
