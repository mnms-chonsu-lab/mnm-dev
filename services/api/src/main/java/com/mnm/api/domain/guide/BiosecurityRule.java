package com.mnm.api.domain.guide;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 공식 지침 지식베이스. text는 공식 자료 원문만 담는다.
 * embedding 컬럼(pgvector)은 AI 쪽에서만 쓰므로 매핑하지 않는다.
 */
@Entity
@Table(name = "biosecurity_rule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BiosecurityRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "biosecurity_rule_id")
    private Long id;

    /** question | photo_guide | action | form_field | summary | clause */
    @Column(nullable = false, length = 20)
    private String kind;

    @Column(length = 50)
    private String disease;

    /** suspect | after_report | regional. null = 단계 없음 */
    @Column(length = 20)
    private String stage;

    @Column(name = "text", nullable = false, columnDefinition = "text")
    private String text;

    @Column(name = "source_doc", nullable = false, length = 200)
    private String sourceDoc;

    @Column(length = 200)
    private String clause;

    @Column(name = "revised_at")
    private LocalDate revisedAt;

    /** draft | review | published. published만 화면에 쓴다 */
    @Column(nullable = false, length = 20)
    private String status = "draft";

    @Column(length = 50)
    private String reviewer;

    /** 문구 버전 번호 (낙관적 잠금용 아님) */
    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "example_image", length = 500)
    private String exampleImage;

    @Column(name = "display_order")
    private Integer displayOrder;

    /** JSON 문자열: "예"일 때 촬영할 부위 코드 목록 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "yes_required_parts")
    private String yesRequiredParts;

    @Column(name = "part_code", length = 30)
    private String partCode;

    @Column(name = "photo_part", length = 30)
    private String photoPart;
}
