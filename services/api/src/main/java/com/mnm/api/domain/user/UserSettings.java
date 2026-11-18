package com.mnm.api.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 화면 설정. 기본 키가 users.users_id와 같다. */
@Entity
@Table(name = "user_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSettings {

    @Id
    @Column(name = "users_id")
    private Long userId;

    /** 글자 크기 단계 0~4 */
    @Column(name = "font_step", nullable = false)
    private short fontStep = 1;

    @Column(name = "high_contrast", nullable = false)
    private boolean highContrast;

    @Column(name = "auto_speak", nullable = false)
    private boolean autoSpeak;

    /** 설정을 저장한 적 없는 계정의 기본값 (글자 보통, 고대비·자동 읽기 끔) */
    public static UserSettings defaults(Long userId) {
        UserSettings settings = new UserSettings();
        settings.userId = userId;
        return settings;
    }

    /** null 인 값은 그대로 둔다 */
    public void change(Short fontStep, Boolean highContrast, Boolean autoSpeak) {
        if (fontStep != null) {
            this.fontStep = fontStep;
        }
        if (highContrast != null) {
            this.highContrast = highContrast;
        }
        if (autoSpeak != null) {
            this.autoSpeak = autoSpeak;
        }
    }
}
