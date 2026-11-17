package com.mnm.api.global;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 공통 응답 형식 · 전역 예외 처리 · request_id · 스웨거 · CORS 를 실제 설정 그대로 확인한다.
 * DB 없이 띄우기 위해 Hibernate 가 시작할 때 DB 에 접속하지 않도록 한다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:1/none",
        "spring.datasource.username=none",
        "spring.datasource.password=none",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
        "mnm.cors.allowed-origins=https://farmer.example.com",
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class CommonResponseTest {

    private static final String BASE = "/api/farmer/_test";

    @Autowired
    MockMvc mvc;

    @Test
    void 성공_응답은_data_와_request_id_를_snake_case_로_준다() throws Exception {
        MvcResult result = mvc.perform(get(BASE + "/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.farm_name").value("한우농장"))
                .andExpect(jsonPath("$.data.created_at").value("2026-11-17T03:00:00Z"))
                .andReturn();

        String headerId = result.getResponse().getHeader("X-Request-Id");
        assertThat(headerId).isNotBlank();
        assertThat(result.getResponse().getContentAsString()).contains("\"request_id\":\"" + headerId + "\"");
    }

    @Test
    void 요청의_X_Request_Id_는_형식이_맞을_때만_그대로_쓴다() throws Exception {
        mvc.perform(get(BASE + "/ok").header("X-Request-Id", "abcdef0123456789"))
                .andExpect(header().string("X-Request-Id", "abcdef0123456789"))
                .andExpect(jsonPath("$.request_id").value("abcdef0123456789"));

        mvc.perform(get(BASE + "/ok").header("X-Request-Id", "bad id; drop"))
                .andExpect(header().string("X-Request-Id", not("bad id; drop")));
    }

    @Test
    void 본문_검증_실패는_VALID_422_와_칸별_문구() throws Exception {
        mvc.perform(post(BASE + "/form").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ear_tag\":\"\",\"head_count\":0}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALID-422"))
                .andExpect(jsonPath("$.message").value("입력한 내용을 다시 확인해 주세요."))
                .andExpect(jsonPath("$.request_id").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("ear_tag")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("head_count")))
                .andExpect(jsonPath("$.errors[*].message").value(hasItem("귀표 번호를 넣어 주세요.")));
    }

    @Test
    void 잘못된_JSON_과_파라미터_검증_실패도_VALID_422() throws Exception {
        mvc.perform(post(BASE + "/form").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALID-422"))
                .andExpect(jsonPath("$.errors").doesNotExist());

        mvc.perform(get(BASE + "/param").param("size", "0"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void BusinessException_은_해당_코드와_detail() throws Exception {
        mvc.perform(get(BASE + "/business"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT-409"))
                .andExpect(jsonPath("$.message").value("이미 보냈어요."))
                .andExpect(jsonPath("$.detail").value("같은 전달 재요청"));
    }

    @Test
    void DB_제약_위반은_409_이고_SQL_을_노출하지_않는다() throws Exception {
        mvc.perform(get(BASE + "/integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT-409"))
                .andExpect(content().string(not(containsString("insert"))));
    }

    @Test
    void 업로드_용량_초과는_UPLOAD_FAIL() throws Exception {
        mvc.perform(get(BASE + "/upload"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("UPLOAD-FAIL"));
    }

    @Test
    void 처리_못한_예외는_SRV_5XX_이고_원인은_로그에만_남긴다(CapturedOutput output) throws Exception {
        MvcResult result = mvc.perform(get(BASE + "/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("SRV-5XX"))
                .andExpect(jsonPath("$.detail").value(nullValue()))
                .andExpect(content().string(not(containsString("01012345678"))))
                .andReturn();

        String requestId = result.getResponse().getHeader("X-Request-Id");
        assertThat(output).contains("[" + requestId + "]").contains("[SRV-5XX]");
    }

    @Test
    void 없는_경로와_잘못된_메서드() throws Exception {
        mvc.perform(get("/api/farmer/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NF-404"));

        mvc.perform(post(BASE + "/ok"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD-405"))
                .andExpect(header().exists("Allow"));
    }

    @Test
    void 컨트롤러_밖_오류도_같은_형식() throws Exception {
        mvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 401))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-401"))
                .andExpect(jsonPath("$.request_id").isNotEmpty());
    }

    @Test
    void CORS_허용_도메인과_노출_헤더() throws Exception {
        mvc.perform(options(BASE + "/ok")
                        .header("Origin", "https://farmer.example.com")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Idempotency-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://farmer.example.com"));

        mvc.perform(get(BASE + "/ok").header("Origin", "https://farmer.example.com"))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("X-Request-Id")));

        mvc.perform(get(BASE + "/ok").header("Origin", "https://evil.example.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 스웨거에_공통_오류_응답과_Bearer_인증이_보인다() throws Exception {
        mvc.perform(get("/v3/api-docs/farmer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.request_id").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearer.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/farmer/_test/ok'].get.responses['4XX']").exists())
                .andExpect(jsonPath("$.paths['/api/farmer/_test/ok'].get.responses['5XX']").exists())
                .andExpect(jsonPath("$.components.schemas.Item.properties.farm_name").exists());
    }
}
