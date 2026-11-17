package com.mnm.api.auth;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mnm.api.TestTokens;
import com.mnm.api.domain.farm.FarmInfo;
import com.mnm.api.domain.org.ReceiverOrg;
import com.mnm.api.domain.user.User;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 로그인 · 잠금 · 임시 비밀번호 변경 · 역할별 접근을 실제 보안 설정 그대로 확인한다.
 * DB 없이 띄우고(nodb) 리포지토리·트랜잭션은 가짜로 바꾼다. 엔티티 변경은 메모리의 객체에 그대로 남는다.
 */
@SpringBootTest
@ActiveProfiles("nodb")
@AutoConfigureMockMvc
class AuthApiTest {

    private static final String FARM_NO = "448012";
    private static final String PHONE = "01000000937";
    private static final String TEMP_PW = "Temp-1234x";

    @Autowired
    MockMvc mvc;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    JwtEncoder jwtEncoder;

    @MockitoBean
    AuthUserRepository userRepository;
    @MockitoBean
    AuthFarmRepository farmRepository;
    @MockitoBean
    PlatformTransactionManager transactionManager;

    // ---- 농가 (AUTH-01) ----

    @Test
    void 농가는_농장식별번호와_휴대폰_번호가_맞으면_30일_토큰을_받고_농가_API만_쓴다() throws Exception {
        User farmer = farmer("active");
        String token = bearerOf(loginFarmer(FARM_NO, "010-0000-0937")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("farmer"))
                .andExpect(jsonPath("$.data.must_change_pw").value(false))
                .andExpect(jsonPath("$.data.expires_at").exists()));

        given(userRepository.findById(10L)).willReturn(Optional.of(farmer));
        given(farmRepository.findFirstByOwnerId(10L)).willReturn(Optional.of(farmOf(farmer)));
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.users_id").value(10))
                .andExpect(jsonPath("$.data.farm.farm_no").value(FARM_NO))
                .andExpect(jsonPath("$.data.farm.org_name").value("홍성축협"));

        // 권한이 있으면 없는 경로는 404, 다른 플랫폼은 403
        mvc.perform(get("/api/farmer/nope").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/receiver/nope").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-403"));
        mvc.perform(get("/api/admin/nope").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
    }

    @Test
    void 농가_정보가_틀리면_AUTH_FAIL_5번째에_10분_잠금() throws Exception {
        farmer("active");
        for (int i = 0; i < 4; i++) {
            loginFarmer(FARM_NO, "01099999999")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH-FAIL"));
        }
        loginFarmer(FARM_NO, "01099999999")
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("AUTH-LOCK"))
                .andExpect(jsonPath("$.message").value(containsString("10분")));

        // 잠긴 동안은 맞게 넣어도 잠금
        loginFarmer(FARM_NO, PHONE).andExpect(status().isLocked());
    }

    @Test
    void 없는_농장식별번호는_AUTH_FAIL_형식이_틀리면_VALID_422() throws Exception {
        loginFarmer("499999", PHONE)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-FAIL"));

        loginFarmer("12", "abc")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.hasItems("farm_no", "phone")));
    }

    @Test
    void 정지된_계정은_정보가_맞아도_로그인할_수_없다() throws Exception {
        farmer("suspended");
        loginFarmer(FARM_NO, PHONE)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-403"));
    }

    // ---- 접수자·운영자 (AUTH-02, AUTH-04) ----

    @Test
    void 임시_비밀번호로_로그인하면_비밀번호를_바꾸기_전까지_접수자_API를_못_쓴다() throws Exception {
        User receiver = staff("receiver", "hs-bio", true);
        given(userRepository.findById(20L)).willReturn(Optional.of(receiver));

        String tempToken = bearerOf(loginStaff("hs-bio", TEMP_PW)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.must_change_pw").value(true)));
        mvc.perform(get("/api/receiver/nope").header(HttpHeaders.AUTHORIZATION, tempToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, tempToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.org.name").value("홍성축협"))
                .andExpect(jsonPath("$.data.inbox_id").value("hs-nh-bio"));

        // 규칙 위반 · 임시 비밀번호와 같음 → VALID-422
        changePassword(tempToken, "short1").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("new_password"));
        changePassword(tempToken, TEMP_PW).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("임시 비밀번호와 다른 비밀번호를 정해 주세요."));

        String newToken = bearerOf(changePassword(tempToken, "NewPass123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.must_change_pw").value(false)));
        mvc.perform(get("/api/receiver/nope").header(HttpHeaders.AUTHORIZATION, newToken))
                .andExpect(status().isNotFound());

        // 한 번 바꾼 뒤에는 이 API 로 다시 바꿀 수 없다
        changePassword(newToken, "Other12345").andExpect(status().isForbidden());

        // 새 비밀번호로 로그인
        loginStaff("hs-bio", "NewPass123").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.must_change_pw").value(false));
        loginStaff("hs-bio", TEMP_PW).andExpect(status().isUnauthorized());
    }

    @Test
    void 운영자는_어드민_API만_쓰고_비밀번호를_5번_틀리면_잠긴다() throws Exception {
        staff("admin", "yumin", false);
        String token = bearerOf(loginStaff("yumin", TEMP_PW).andExpect(status().isOk()));
        mvc.perform(get("/api/admin/nope").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/receiver/nope").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());

        for (int i = 0; i < 4; i++) {
            loginStaff("yumin", "wrong").andExpect(jsonPath("$.code").value("AUTH-FAIL"));
        }
        loginStaff("yumin", "wrong").andExpect(jsonPath("$.code").value("AUTH-LOCK"));
    }

    @Test
    void 없는_아이디는_AUTH_FAIL() throws Exception {
        loginStaff("nobody", TEMP_PW)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-FAIL"));
    }

    // ---- 토큰 ----

    @Test
    void 토큰이_없거나_틀리거나_만료되면_AUTH_401() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-401"))
                .andExpect(jsonPath("$.request_id").isNotEmpty());
        mvc.perform(get("/api/farmer/nope").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-401"));

        String expired = TestTokens.bearer(jwtEncoder, 10L, "farmer", false, Duration.ofSeconds(-120));
        mvc.perform(get("/api/farmer/nope").header(HttpHeaders.AUTHORIZATION, expired))
                .andExpect(status().isUnauthorized());

        // 만료된 토큰을 붙인 채 로그인해도 로그인은 된다
        farmer("active");
        mvc.perform(post("/api/auth/farmer/login").header(HttpHeaders.AUTHORIZATION, expired)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"farm_no\":\"" + FARM_NO + "\",\"phone\":\"" + PHONE + "\"}"))
                .andExpect(status().isOk());
    }

    // ---- helpers ----

    private ResultActions loginFarmer(String farmNo, String phone) throws Exception {
        return mvc.perform(post("/api/auth/farmer/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"farm_no\":\"" + farmNo + "\",\"phone\":\"" + phone + "\"}"));
    }

    private ResultActions loginStaff(String loginId, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login_id\":\"" + loginId + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions changePassword(String token, String newPassword) throws Exception {
        return mvc.perform(post("/api/auth/password").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"new_password\":\"" + newPassword + "\"}"));
    }

    private static String bearerOf(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.access_token");
    }

    private User farmer(String status) {
        User user = entity(User.class, Map.of("id", 10L, "role", "farmer", "name", "김한우", "phone", PHONE,
                "status", status));
        given(farmRepository.findByFarmNo(FARM_NO)).willReturn(Optional.of(farmOf(user)));
        return user;
    }

    private User staff(String role, String loginId, boolean mustChangePw) {
        User user = entity(User.class, Map.of("id", 20L, "role", role, "name", "박서연", "loginId", loginId,
                "passwordHash", passwordEncoder.encode(TEMP_PW), "mustChangePw", mustChangePw,
                "org", hongseong(), "inboxId", "hs-nh-bio"));
        given(userRepository.findByLoginId(loginId)).willReturn(Optional.of(user));
        given(userRepository.findById(anyLong())).willReturn(Optional.of(user));
        return user;
    }

    private static FarmInfo farmOf(User owner) {
        return entity(FarmInfo.class, Map.of("id", 1L, "farmNo", FARM_NO, "owner", owner, "name", "김한우 농장",
                "headCount", 42, "org", hongseong()));
    }

    private static ReceiverOrg hongseong() {
        return entity(ReceiverOrg.class, Map.of("id", 1L, "name", "홍성축협", "type", "축협",
                "regionCodes", new String[] {"4480000000"}));
    }

    /** 엔티티는 생성자·setter 를 열지 않으므로 테스트에서만 필드를 직접 채운다 */
    private static <T> T entity(Class<T> type, Map<String, Object> fields) {
        T entity = BeanUtils.instantiateClass(type);
        fields.forEach((name, value) -> ReflectionTestUtils.setField(entity, name, value));
        return entity;
    }
}
