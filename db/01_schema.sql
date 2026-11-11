-- =====================================================================
--  청양버스 (CheongyangBus) 데이터베이스 스키마
-- ---------------------------------------------------------------------
--  대상 DBMS : MySQL 8.0.16 이상 (MariaDB 10.5 이상과도 호환되는 문법)
--  문자셋    : utf8mb4 (한글)
--  실행 순서 : 01_schema.sql → 02_seed.sql
--
--  테이블 9개
--    [버스 노선·시간 데이터]  region, terminal, route, schedule
--    [운행 상태·지연 정보]    operation_status
--    [사용자·즐겨찾기·알림]   users, favorite, alarm, search_history
--  뷰 1개
--    v_schedule : 출발·도착 시각과 예상 소요시간을 한 번에 읽는 조회용 뷰
--
--  지금 서버(server/)가 쓰는 것: region, terminal, route, schedule, v_schedule, users, favorite
-- =====================================================================

-- 주의: 처음부터 다시 만들 때 쓰는 스크립트입니다. 기존 데이터가 모두 지워집니다.
DROP DATABASE IF EXISTS cheongyang_bus;
CREATE DATABASE cheongyang_bus
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
USE cheongyang_bus;
SET NAMES utf8mb4;


-- ---------------------------------------------------------------------
-- 1. region : 지역 (앱의 출발지·도착지 선택 목록)
-- ---------------------------------------------------------------------
CREATE TABLE region (
  region_id    INT          NOT NULL AUTO_INCREMENT COMMENT '지역 번호',
  region_name  VARCHAR(20)  NOT NULL                COMMENT '지역명 (청양, 대전 ...)',
  sort_order   INT          NOT NULL DEFAULT 0      COMMENT '목록 표시 순서',
  PRIMARY KEY (region_id),
  UNIQUE KEY uk_region_name (region_name)
) ENGINE = InnoDB COMMENT = '지역';


-- ---------------------------------------------------------------------
-- 2. terminal : 터미널(정류장). 한 지역에 여러 개일 수 있음 (대전 = 서대전·유성·동대전)
-- ---------------------------------------------------------------------
CREATE TABLE terminal (
  terminal_id    INT          NOT NULL AUTO_INCREMENT COMMENT '터미널 번호',
  region_id      INT          NOT NULL                COMMENT '소속 지역',
  terminal_name  VARCHAR(50)  NOT NULL                COMMENT '터미널 이름',
  PRIMARY KEY (terminal_id),
  UNIQUE KEY uk_terminal_name (terminal_name),
  KEY idx_terminal_region (region_id),
  CONSTRAINT fk_terminal_region FOREIGN KEY (region_id) REFERENCES region (region_id)
) ENGINE = InnoDB COMMENT = '터미널';


-- ---------------------------------------------------------------------
-- 3. route : 노선(구간) = 출발 터미널 → 도착 터미널
--    경유 노선(청양→공주→대전)도 승차권·시간표처럼 구간별로 저장
-- ---------------------------------------------------------------------
CREATE TABLE route (
  route_id            INT  NOT NULL AUTO_INCREMENT COMMENT '노선 번호',
  origin_terminal_id  INT  NOT NULL                COMMENT '출발 터미널',
  dest_terminal_id    INT  NOT NULL                COMMENT '도착 터미널',
  PRIMARY KEY (route_id),
  UNIQUE KEY uk_route (origin_terminal_id, dest_terminal_id),
  KEY idx_route_dest (dest_terminal_id),
  CONSTRAINT fk_route_origin FOREIGN KEY (origin_terminal_id) REFERENCES terminal (terminal_id),
  CONSTRAINT fk_route_dest   FOREIGN KEY (dest_terminal_id)   REFERENCES terminal (terminal_id),
  CONSTRAINT ck_route_diff   CHECK (origin_terminal_id <> dest_terminal_id)
) ENGINE = InnoDB COMMENT = '노선(구간)';


-- ---------------------------------------------------------------------
-- 4. schedule : 운행 시간표. 1행 = 1편
--    예상 소요시간은 저장하지 않고 (도착 - 출발)로 계산 → v_schedule 참고
-- ---------------------------------------------------------------------
CREATE TABLE schedule (
  schedule_id     INT          NOT NULL AUTO_INCREMENT COMMENT '운행편 번호',
  route_id        INT          NOT NULL                COMMENT '노선',
  departure_time  TIME         NOT NULL                COMMENT '출발 시각',
  arrival_time    TIME         NOT NULL                COMMENT '도착 예정 시각',
  bus_grade       VARCHAR(10)  NOT NULL DEFAULT '일반'  COMMENT '등급: 일반 / 우등',
  day_type        VARCHAR(10)  NOT NULL DEFAULT 'DAILY' COMMENT '운행 요일: DAILY 매일 / WEEKDAY 평일 / WEEKEND 주말',
  is_active       BOOLEAN      NOT NULL DEFAULT TRUE    COMMENT '현재 운행 여부 (시간표 개편으로 없어진 편은 FALSE)',
  PRIMARY KEY (schedule_id),
  UNIQUE KEY uk_schedule (route_id, departure_time, day_type),
  CONSTRAINT fk_schedule_route FOREIGN KEY (route_id) REFERENCES route (route_id),
  CONSTRAINT ck_schedule_grade CHECK (bus_grade IN ('일반', '우등')),
  CONSTRAINT ck_schedule_day   CHECK (day_type IN ('DAILY', 'WEEKDAY', 'WEEKEND'))
) ENGINE = InnoDB COMMENT = '운행 시간표';


-- ---------------------------------------------------------------------
-- 5. operation_status : 날짜별 운행 상태 (지연·결행)
--    행이 없으면 정상 운행. '운행예정/운행중/운행종료'는 현재 시각으로 계산
-- ---------------------------------------------------------------------
CREATE TABLE operation_status (
  status_id     INT           NOT NULL AUTO_INCREMENT COMMENT '번호',
  schedule_id   INT           NOT NULL                COMMENT '운행편',
  service_date  DATE          NOT NULL                COMMENT '운행 날짜',
  status        VARCHAR(10)   NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL 정상 / DELAYED 지연 / CANCELED 결행',
  delay_min     INT           NOT NULL DEFAULT 0      COMMENT '지연 시간(분)',
  reason        VARCHAR(100)  NULL                    COMMENT '지연·결행 사유',
  updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '마지막 수정 시각',
  PRIMARY KEY (status_id),
  UNIQUE KEY uk_operation_status (schedule_id, service_date),
  CONSTRAINT fk_status_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (schedule_id) ON DELETE CASCADE,
  CONSTRAINT ck_status_value    CHECK (status IN ('NORMAL', 'DELAYED', 'CANCELED')),
  CONSTRAINT ck_status_delay    CHECK (delay_min >= 0)
) ENGINE = InnoDB COMMENT = '운행 상태(지연·결행)';


-- ---------------------------------------------------------------------
-- 6. users : 사용자 (로그인 화면의 아이디·비밀번호)
--    비밀번호는 백엔드에서 BCrypt로 해시한 값만 저장 (평문 저장 금지)
-- ---------------------------------------------------------------------
CREATE TABLE users (
  user_id        INT           NOT NULL AUTO_INCREMENT COMMENT '사용자 번호',
  login_id       VARCHAR(30)   NOT NULL                COMMENT '아이디',
  password_hash  VARCHAR(255)  NOT NULL                COMMENT '비밀번호 해시 (BCrypt)',
  nickname       VARCHAR(30)   NOT NULL                COMMENT '표시 이름 (내 정보 화면)',
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '가입 시각',
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_users_login_id (login_id)
) ENGINE = InnoDB COMMENT = '사용자';


-- ---------------------------------------------------------------------
-- 7. favorite : 즐겨찾기 (상세 화면의 '즐겨찾기 추가')
--    현재 앱은 노선이 아니라 '특정 시간의 편'을 저장하므로 schedule 기준
-- ---------------------------------------------------------------------
CREATE TABLE favorite (
  favorite_id  INT       NOT NULL AUTO_INCREMENT COMMENT '번호',
  user_id      INT       NOT NULL                COMMENT '사용자',
  schedule_id  INT       NOT NULL                COMMENT '운행편',
  created_at   DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '추가 시각',
  PRIMARY KEY (favorite_id),
  UNIQUE KEY uk_favorite (user_id, schedule_id),
  KEY idx_favorite_schedule (schedule_id),
  CONSTRAINT fk_favorite_user     FOREIGN KEY (user_id)     REFERENCES users (user_id)        ON DELETE CASCADE,
  CONSTRAINT fk_favorite_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (schedule_id) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT = '즐겨찾기';


-- ---------------------------------------------------------------------
-- 8. alarm : 출발 알림 (상세 화면의 '출발 알림 설정' = 출발 10분 전)
--    실제 알림은 앱이 알림 시각을 받아 기기에서 예약 (AlarmManager 등)
-- ---------------------------------------------------------------------
CREATE TABLE alarm (
  alarm_id        INT       NOT NULL AUTO_INCREMENT COMMENT '번호',
  user_id         INT       NOT NULL                COMMENT '사용자',
  schedule_id     INT       NOT NULL                COMMENT '운행편',
  service_date    DATE      NOT NULL                COMMENT '탑승 날짜',
  minutes_before  INT       NOT NULL DEFAULT 10     COMMENT '출발 몇 분 전에 알릴지',
  created_at      DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '설정 시각',
  PRIMARY KEY (alarm_id),
  UNIQUE KEY uk_alarm (user_id, schedule_id, service_date),
  KEY idx_alarm_schedule (schedule_id),
  CONSTRAINT fk_alarm_user     FOREIGN KEY (user_id)     REFERENCES users (user_id)        ON DELETE CASCADE,
  CONSTRAINT fk_alarm_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (schedule_id) ON DELETE CASCADE,
  CONSTRAINT ck_alarm_minutes  CHECK (minutes_before BETWEEN 1 AND 120)
) ENGINE = InnoDB COMMENT = '출발 알림';


-- ---------------------------------------------------------------------
-- 9. search_history : 최근 검색 (메인 화면의 '최근 검색')
-- ---------------------------------------------------------------------
CREATE TABLE search_history (
  history_id        INT       NOT NULL AUTO_INCREMENT COMMENT '번호',
  user_id           INT       NOT NULL                COMMENT '사용자',
  origin_region_id  INT       NOT NULL                COMMENT '출발 지역',
  dest_region_id    INT       NOT NULL                COMMENT '도착 지역',
  searched_at       DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '검색 시각',
  PRIMARY KEY (history_id),
  KEY idx_history_user_time (user_id, searched_at),
  KEY idx_history_origin (origin_region_id),
  KEY idx_history_dest (dest_region_id),
  CONSTRAINT fk_history_user   FOREIGN KEY (user_id)          REFERENCES users (user_id) ON DELETE CASCADE,
  CONSTRAINT fk_history_origin FOREIGN KEY (origin_region_id) REFERENCES region (region_id),
  CONSTRAINT fk_history_dest   FOREIGN KEY (dest_region_id)   REFERENCES region (region_id)
) ENGINE = InnoDB COMMENT = '최근 검색 기록';


-- ---------------------------------------------------------------------
-- 조회용 뷰 v_schedule
--   지역·터미널 이름, 출발·도착 시각, 예상 소요시간(분/문자열)을 한 행으로.
--   백엔드의 '출발·도착시간 조회', '예상 소요시간 조회'는 이 뷰에서 SELECT.
--   소요시간은 자정을 넘기는 편(23:30→00:45)도 맞도록 하루(86400초)를 더한 뒤
--   MOD(..., 86400)로 계산. 중간값이 음수가 되지 않게 '도착 + 86400 - 출발' 순서.
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_schedule AS
SELECT
  x.*,
  CONCAT_WS(' ',
    IF(x.duration_min >= 60,     CONCAT(x.duration_min DIV 60, '시간'), NULL),
    IF(x.duration_min MOD 60 > 0, CONCAT(x.duration_min MOD 60, '분'),  NULL)
  ) AS duration_text
FROM (
  SELECT
    s.schedule_id,
    r.route_id,
    oreg.region_name  AS origin_region,
    ot.terminal_name  AS origin_terminal,
    dreg.region_name  AS dest_region,
    dt.terminal_name  AS dest_terminal,
    s.departure_time,
    s.arrival_time,
    MOD(TIME_TO_SEC(s.arrival_time) + 86400 - TIME_TO_SEC(s.departure_time), 86400) DIV 60 AS duration_min,
    s.bus_grade,
    s.day_type,
    s.is_active
  FROM schedule s
  JOIN route    r    ON r.route_id     = s.route_id
  JOIN terminal ot   ON ot.terminal_id = r.origin_terminal_id
  JOIN region   oreg ON oreg.region_id = ot.region_id
  JOIN terminal dt   ON dt.terminal_id = r.dest_terminal_id
  JOIN region   dreg ON dreg.region_id = dt.region_id
) AS x;
