package com.mnm.api.global.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mnm.api.global.ai.AiClient.RouteCow;
import com.mnm.api.global.ai.AiClient.RouteRequest;
import com.mnm.api.global.ai.AiClient.SummaryCow;
import com.mnm.api.global.ai.AiClient.SummaryRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/** 가짜 AI 서버(JDK HttpServer)에 붙여 요청 형식·토큰·오류 코드·재시도를 확인한다 */
class HttpAiClientTest {

    private final ObjectMapper mapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();

    private HttpServer server;
    private HttpAiClient client;
    /** 받은 요청: 경로, 토큰, Content-Type, 본문 */
    private final List<String[]> received = new ArrayList<>();
    /** 차례로 돌려줄 응답: 상태 코드, 본문 */
    private final Deque<Object[]> replies = new ArrayDeque<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        client = new HttpAiClient("http://127.0.0.1:" + server.getAddress().getPort() + "/", "secret", mapper);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        received.add(new String[] {ex.getRequestURI().getPath(), ex.getRequestHeaders().getFirst("X-Internal-Token"),
                ex.getRequestHeaders().getFirst("Content-Type"),
                new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)});
        Object[] reply = replies.poll();
        byte[] body = ((String) reply[1]).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders((int) reply[0], body.length);
        ex.getResponseBody().write(body);
        ex.close();
    }

    private void reply(int status, String body) {
        replies.add(new Object[] {status, body});
    }

    @Test
    void 음성은_토큰과_함께_파일로_보내고_아이폰_녹음은_m4a_로_이름을_붙인다() {
        reply(200, "{\"text\":\"0871번 송아지 낳았어\",\"duration_sec\":3.5}");

        AiClient.SttResult result = client.stt("abc".getBytes(), "audio/mp4;codecs=mp4a");

        assertThat(result.text()).isEqualTo("0871번 송아지 낳았어");
        assertThat(result.durationSec()).isEqualTo(3.5);
        String[] req = received.getFirst();
        assertThat(req[0]).isEqualTo("/ai/stt");
        assertThat(req[1]).isEqualTo("secret");
        assertThat(req[2]).startsWith("multipart/form-data; boundary=");
        assertThat(req[3]).contains("name=\"file\"; filename=\"voice.m4a\"", "Content-Type: audio/mp4", "abc");
    }

    @Test
    void 기록_정리는_소_목록을_farm_context_로_감싸고_모르는_응답_칸은_무시한다() throws Exception {
        reply(200, """
                {"intent":"history_report","items":[{"category":"출생","value":"0871번 송아지","cow_tag4":"0871",
                "cow_tag":"900000000871","vaccine_type":null,"head_count":null}],"report_type":"birth",
                "report_fields":{"mother_tag4":"0871","birth_date":"2026-11-25","sex":"암"},"missing":[],
                "reports":[],"notice":null}""");

        AiClient.RouteResult result = client.route(new RouteRequest("송아지 낳았어", LocalDate.of(2026, 11, 26),
                List.of(new RouteCow("0871", null, "1", "900000000871"))));

        assertThat(result.reportType()).isEqualTo("birth");
        assertThat(result.items().getFirst().cowTag()).isEqualTo("900000000871");
        assertThat(result.reportFields()).containsEntry("sex", "암");
        @SuppressWarnings("unchecked")
        Map<String, Object> sent = mapper.readValue(received.getFirst()[3], Map.class);
        assertThat(sent.keySet()).containsExactly("text", "today", "farm_context");
        assertThat(sent.get("today")).isEqualTo("2026-11-26");
        assertThat(received.getFirst()[3]).contains("\"cows\":[{\"tag4\":\"0871\",\"desc\":null,\"barn\":\"1\",\"tag\":\"900000000871\"}]");
    }

    @Test
    void AI_오류는_본문의_코드로_바꾸고_입력_오류는_다시_부르지_않는다() {
        reply(422, "{\"code\":\"STT-FAIL\",\"message\":\"무음\",\"detail\":{\"reason\":\"silence\"}}");

        assertThatThrownBy(() -> client.stt("x".getBytes(), "audio/webm"))
                .isInstanceOfSatisfying(AiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("STT-FAIL");
                    assertThat(e.getMessage()).contains("silence");
                });
        assertThat(received).hasSize(1);
        assertThat(received.getFirst()[3]).contains("filename=\"voice.webm\"");
    }

    @Test
    void 바로_온_503은_한_번_다시_부른다() {
        reply(503, "{\"code\":\"LLM-FAIL\",\"message\":\"api\",\"detail\":{\"reason\":\"api\"}}");
        reply(200, "{\"intent\":\"diary\",\"items\":[],\"report_type\":null,\"report_fields\":{},\"missing\":[]}");

        assertThat(client.route(new RouteRequest("소독함", LocalDate.of(2026, 11, 26), List.of())).intent())
                .isEqualTo("diary");
        assertThat(received).hasSize(2);
    }

    @Test
    void 부위_확인은_503이어도_다시_부르지_않는다() {
        reply(503, "{\"code\":\"PART-FAIL\",\"message\":\"x\",\"detail\":{\"reason\":\"not_loaded\"}}");

        assertThatThrownBy(() -> client.partCheck("https://kr.object.ncloudstorage.com/a.jpg", "mouth"))
                .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.getCode()).isEqualTo("PART-FAIL"));
        assertThat(received).hasSize(1);
    }

    @Test
    void 근거_없는_방역_안내는_RAG_NONE() {
        reply(404, "{\"code\":\"RAG-NONE\",\"message\":\"근거 없음\",\"detail\":{\"reason\":\"no_rule\",\"fallback\":true}}");

        assertThatThrownBy(() -> client.guide("FMD", "after_report", null))
                .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.getCode()).isEqualTo("RAG-NONE"));
        assertThat(received.getFirst()[3]).isEqualTo("{\"disease\":\"FMD\",\"stage\":\"after_report\",\"query\":null}");
    }

    @Test
    void 소를_안_고른_기록은_빈_번호로_보내고_요약에서는_null_로_되돌린다() {
        reply(200, """
                {"card":{"cow":{"tag4":"","desc":null,"barn":"2"},"matched_symptoms":[]},
                "form6":{"found_date":"2026-11-26"},"missing":["reporter"],"form6_text":"..."}""");

        AiClient.SummaryResult result = client.summary(new SummaryRequest("7", LocalDate.of(2026, 11, 26),
                new SummaryCow(null, null, "2"), Map.of("drool", true, "blister", false, "lump", false, "feed", false),
                List.of(), List.of(), Map.of(), 0));

        assertThat(received.getFirst()[3]).contains("\"cow\":{\"tag4\":\"\"", "\"farm_total_head\":null");
        @SuppressWarnings("unchecked")
        Map<String, Object> cow = (Map<String, Object>) result.card().get("cow");
        assertThat(cow).containsEntry("tag4", null).containsEntry("barn", "2");
        assertThat(result.missing()).containsExactly("reporter");
        assertThat(result.form6Text()).isEqualTo("...");
    }

    @Test
    void AI_서버에_연결이_안_되면_기능별_코드() throws IOException {
        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }
        HttpAiClient down = new HttpAiClient("http://127.0.0.1:" + closedPort, "secret", mapper);

        assertThatThrownBy(() -> down.behavior("https://kr.object.ncloudstorage.com/a.mp4"))
                .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.getCode()).isEqualTo("BEHAVIOR-FAIL"));
    }
}
