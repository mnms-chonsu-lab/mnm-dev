package com.mnm.api.farmer;

import com.mnm.api.domain.alert.Alert;
import com.mnm.api.domain.farm.FarmInfo;
import com.mnm.api.domain.user.User;
import com.mnm.api.domain.user.UserSettings;
import com.mnm.api.farmer.MeDtos.AlertResponse;
import com.mnm.api.farmer.MeDtos.FarmResponse;
import com.mnm.api.farmer.MeDtos.SettingsRequest;
import com.mnm.api.farmer.MeDtos.SettingsResponse;
import com.mnm.api.global.error.BusinessException;
import com.mnm.api.global.error.ErrorCode;
import com.mnm.api.global.response.PageResponse;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 알림함 (F-14), 내 정보·설정 (F-15, COM-03) */
@Service
@RequiredArgsConstructor
class MeService {

    private final FarmerSupport support;
    private final FarmerAlertRepository alertRepository;
    private final FarmerSettingsRepository settingsRepository;
    private final FarmerUserRepository userRepository;
    private final FarmerCowRepository cowRepository;
    private final FarmerDeliveryRepository deliveryRepository;

    /** FARM-18 알림 목록 (최근순 20건). 알림함을 열면 받은 알림을 읽음으로 표시한다 (홈 미확인 수가 줄어듦) */
    @Transactional
    PageResponse<AlertResponse> alerts(Long userId, String cursor) {
        List<Alert> rows = alertRepository.findByUserIdAndIdLessThanOrderByIdDesc(userId, FarmerSupport.cursor(cursor),
                PageRequest.of(0, FarmerSupport.PAGE_SIZE + 1));
        Instant now = Instant.now();
        PageResponse<AlertResponse> page = FarmerSupport.page(rows, FarmerSupport.PAGE_SIZE, MeService::toResponse,
                Alert::getId);
        rows.stream().limit(FarmerSupport.PAGE_SIZE).forEach(a -> a.markRead(now));
        return page;
    }

    /** FARM-19 확인함 · 문제 없음 */
    @Transactional
    AlertResponse feedback(Long userId, Long alertId, String feedback) {
        Alert alert = alertRepository.findByIdAndUserId(alertId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "알림 없음"));
        alert.giveFeedback(feedback, Instant.now());
        return toResponse(alert);
    }

    /** FARM-20 화면 설정. 저장한 적 없으면 기본값 */
    @Transactional(readOnly = true)
    SettingsResponse settings(Long userId) {
        return toResponse(settingsRepository.findById(userId).orElseGet(() -> UserSettings.defaults(userId)));
    }

    @Transactional
    SettingsResponse updateSettings(Long userId, SettingsRequest request) {
        UserSettings settings = settingsRepository.findById(userId)
                .orElseGet(() -> settingsRepository.save(UserSettings.defaults(userId)));
        settings.change(request.fontStep(), request.highContrast(), request.autoSpeak());
        return toResponse(settings);
    }

    /** FARM-20 농장 정보 */
    @Transactional(readOnly = true)
    FarmResponse farm(Long userId) {
        return toResponse(support.farm(userId));
    }

    /** FARM-20 연락처 바꾸기 (F-P06). 다른 농가 계정과 같은 번호면 막는다. 숫자만 저장 (로그인은 숫자로 비교) */
    @Transactional
    FarmResponse changePhone(Long userId, String phone) {
        FarmInfo farm = support.farm(userId);
        String digits = phone.replaceAll("\\D", "");
        User owner = farm.getOwner();
        if (userRepository.existsByRoleAndPhoneAndIdNot("farmer", digits, owner.getId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "다른 계정에서 쓰는 번호예요. 번호를 다시 확인해 주세요.", "번호 중복");
        }
        owner.changePhone(digits);
        return toResponse(farm);
    }

    private FarmResponse toResponse(FarmInfo farm) {
        return new FarmResponse(farm.getFarmNo(), farm.getName(), farm.getAddress(), farm.getOwner().getName(),
                farm.getOwner().getPhone(), farm.getHeadCount(), farm.getOrg() == null ? null : farm.getOrg().getName(),
                cowRepository.countByFarmIdAndStatus(farm.getId(), "active"), deliveryRepository.countMine(farm.getId()));
    }

    private static SettingsResponse toResponse(UserSettings s) {
        return new SettingsResponse(s.getFontStep(), s.isHighContrast(), s.isAutoSpeak());
    }

    private static AlertResponse toResponse(Alert a) {
        return new AlertResponse(a.getId(), a.getKind(), a.getTitle(), a.getBody(), a.getRefId(), a.getFeedback(),
                a.getReadAt(), a.getCreatedAt());
    }
}
