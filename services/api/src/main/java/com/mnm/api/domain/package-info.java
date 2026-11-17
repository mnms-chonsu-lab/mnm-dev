/**
 * DB 테이블과 1:1로 대응하는 엔티티. 농가 앱 · 접수자 웹 · 어드민이 함께 쓴다.
 * 기준: mnm-data/migrations/0001_init_operational.sql. DB 구조는 mnm-data에서만 바꾸고, 바뀌면 여기 엔티티를 맞춘다.
 * 값 목록이 정해진 문자열 컬럼(role, status 등)은 주석에 허용값을 적어 두었다.
 */
package com.mnm.api.domain;
