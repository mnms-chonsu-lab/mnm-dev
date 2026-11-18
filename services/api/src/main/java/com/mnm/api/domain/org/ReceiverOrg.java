package com.mnm.api.domain.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 접수 기관 (축협 · 방역기관 · 시군청). */
@Entity
@Table(name = "receiver_org")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReceiverOrg {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "org_id")
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    /** 축협 | 방역기관 | 시군청 */
    @Column(nullable = false, length = 20)
    private String type;

    /** 관할 법정동 코드(10자리) 목록 */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "region_codes", nullable = false, columnDefinition = "char(10)[]")
    private String[] regionCodes = new String[0];

    @Column(length = 20)
    private String phone;

    /** JSON 문자열: [{inbox_id, type(history|biosecurity), name, phone}] */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String inboxes = "[]";

    /** 기관 추가 (A-S09). inboxes 는 JSON 문자열 */
    public static ReceiverOrg create(String name, String type, String[] regionCodes, String phone, String inboxes) {
        ReceiverOrg org = new ReceiverOrg();
        org.update(name, type, regionCodes, phone, inboxes);
        return org;
    }

    /** 편집 패널 저장 (A-S09). 전체 값을 바꾼다 */
    public void update(String name, String type, String[] regionCodes, String phone, String inboxes) {
        this.name = name;
        this.type = type;
        this.regionCodes = regionCodes;
        this.phone = phone;
        this.inboxes = inboxes;
    }
}
