package com.mnm.api.farmer;

import com.mnm.api.domain.farm.FarmInfo;
import com.mnm.api.domain.publicdata.DisinfectionFacility;
import com.mnm.api.domain.publicdata.RegionCodeMap;
import com.mnm.api.farmer.HomeDtos.FacilityResponse;
import com.mnm.api.farmer.HomeDtos.HomeResponse;
import com.mnm.api.farmer.HomeDtos.RegionOutbreak;
import com.mnm.api.farmer.HomeDtos.TaskResponse;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 홈 요약 (F-02), 지역 발생·가까운 거점소독시설 (F-12) */
@Service
@RequiredArgsConstructor
class HomeService {

    static final int OUTBREAK_DAYS = 30;
    /** 공공데이터 질병명 접두어 (lknts_nm) */
    static final String FMD = "구제역";
    static final String LSD = "럼피스킨";

    private final FarmerSupport support;
    private final FarmerTaskRepository taskRepository;
    private final FarmerAlertRepository alertRepository;
    private final FarmerOutbreakRepository outbreakRepository;
    private final FarmerRegionRepository regionRepository;
    private final FarmerFacilityRepository facilityRepository;

    /** FARM-05 */
    @Transactional(readOnly = true)
    HomeResponse home(Long userId) {
        FarmInfo farm = support.farm(userId);
        LocalDate today = FarmerRules.today();
        List<TaskResponse> tasks = taskRepository.findByFarmIdAndDoneAtIsNullOrderByDueDateAscIdAsc(farm.getId()).stream()
                .map(t -> new TaskResponse(t.getId(), t.getKind(), t.getTitle(), t.getDueDate(),
                        ChronoUnit.DAYS.between(today, t.getDueDate()), t.getRefId()))
                .toList();
        return new HomeResponse(farm.getOwner().getName(), tasks.size(), tasks,
                alertRepository.countByUserIdAndReadAtIsNull(userId), outbreaks(farm));
    }

    /** FARM-17 우리 지역 발생 현황 */
    @Transactional(readOnly = true)
    RegionOutbreak region(Long userId) {
        return outbreaks(support.farm(userId));
    }

    /** FARM-17 가장 가까운 거점소독시설. 농장 위경도가 없거나 시설 정보가 없으면 null */
    @Transactional(readOnly = true)
    FacilityResponse nearestFacility(Long userId) {
        FarmInfo farm = support.farm(userId);
        if (farm.getLat() == null || farm.getLng() == null) {
            return null;
        }
        double lat = farm.getLat().doubleValue();
        double lng = farm.getLng().doubleValue();
        // shortcut: 전국 시설(수백 건)을 읽어 서버에서 거리를 잰다. 시설이 수천 건이 되면 DB 거리 계산으로 바꾼다
        record Near(DisinfectionFacility f, double km) {
        }
        return facilityRepository.findByLatIsNotNullAndLngIsNotNull().stream()
                .map(f -> new Near(f, distanceKm(lat, lng, f.getLat().doubleValue(), f.getLng().doubleValue())))
                .min(Comparator.comparingDouble(Near::km))
                .map(n -> new FacilityResponse(n.f().getName(), n.f().getAddress(), Math.round(n.km() * 10) / 10.0,
                        n.f().getOperatingHours(), n.f().getPhone()))
                .orElse(null);
    }

    /** 농장 법정동 앞 5자리(시군구)로 센다. 농장 지역 코드가 없으면 0건 + 기준 시각 */
    private RegionOutbreak outbreaks(FarmInfo farm) {
        var baseAt = outbreakRepository.lastCollectedAt();
        String code = farm.getRegionCode();
        if (code == null || code.length() < 5) {
            return new RegionOutbreak(null, 0, 0, baseAt);
        }
        String sigungu = code.substring(0, 5);
        LocalDate since = FarmerRules.today().minusDays(OUTBREAK_DAYS);
        String name = regionRepository.findById(sigungu + "00000").map(RegionCodeMap::getSigunguName).orElse(null);
        return new RegionOutbreak(name, outbreakRepository.countRecent(sigungu, FMD, since),
                outbreakRepository.countRecent(sigungu, LSD, since), baseAt);
    }

    /** 하버사인 직선거리 (계산식 ⑦) */
    static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLng / 2), 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
