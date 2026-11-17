package com.mnm.api.domain.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import org.hibernate.annotations.Generated;

/**
 * created_at 이 있는 테이블의 공통 부모.
 * 값은 DB 기본값(now())으로 채우고, 저장 직후 다시 읽어 온다. 서버 시각과 DB 시각이 섞이지 않게 하려는 것.
 * updated_at 은 현재 스키마에 없다. mnm-data 에서 추가되면 여기에 함께 둔다.
 */
@Getter
@MappedSuperclass
public abstract class BaseTimeEntity {

    @Generated
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
