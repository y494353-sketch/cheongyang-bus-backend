# 청양버스 서버 (Spring Boot + MySQL)

앱 ↔ 이 서버 ↔ MySQL 순서로 연결됩니다. 앱은 MySQL에 직접 붙지 않습니다.

## 1. MySQL 준비 (MySQL 8.0.16 이상)

`db/01_schema.sql` → `db/02_seed.sql` 순서로 실행합니다. 01은 `cheongyang_bus` DB를 지우고 새로 만듭니다.
02에는 관리자 계정(아이디 `admin` / 비밀번호 `admin1234`)이 들어 있습니다.

```bash
mysql -u root -p --default-character-set=utf8mb4 < db/01_schema.sql
mysql -u root -p --default-character-set=utf8mb4 < db/02_seed.sql
```

서버 전용 계정을 만들어 root 대신 씁니다.

```sql
CREATE USER 'cybus'@'localhost' IDENTIFIED BY '여기에_비밀번호';
GRANT SELECT, INSERT, UPDATE, DELETE ON cheongyang_bus.* TO 'cybus'@'localhost';
```

## 2. 서버 실행 (JDK 17 이상)

DB 비밀번호는 코드에 넣지 않고 환경변수 `DB_PASSWORD`로 넘깁니다.

```bash
cd server
chmod +x gradlew                      # zip으로 받으면 실행 권한이 빠져 있음
DB_PASSWORD=여기에_비밀번호 ./gradlew bootRun
```

Windows:

```bat
cd server
set DB_PASSWORD=여기에_비밀번호
gradlew.bat bootRun
```

계속 띄워 둘 때는 `./gradlew bootJar` 후 `java -jar build/libs/cheongyangbus-server-1.0.jar`.
MySQL이 다른 컴퓨터에 있으면 `DB_URL`, 계정 이름이 다르면 `DB_USER` 환경변수도 넘깁니다.

확인: 브라우저에서 `http://서버주소:8080/api/schedules?origin=청양&dest=대전` → 시간표 JSON이 나오면 정상.
(서버 방화벽에서 8080 포트를 열어야 휴대폰이 접속할 수 있습니다.)

## 3. 앱 연결

`app/src/main/java/com/example/cheongyangbus/data/Api.kt`의 `BASE_URL`을 `http://서버주소:8080`으로 바꿉니다.

## API

| 요청 | 내용 | 응답 |
|---|---|---|
| `POST /api/auth/signup` `{loginId, password}` | 회원가입 (BCrypt 해시 저장) | 201 가입 / 409 아이디 중복 |
| `POST /api/auth/login` `{loginId, password}` | 로그인 | 200 `{loginId, role, token}` / 401 실패 |
| `POST /api/auth/logout` | 로그아웃 (토큰 폐기) | 200 |
| `GET /api/regions?origin=청양` 또는 `?dest=청양` | 시간표가 있는 지역 목록 | 목록 |
| `GET /api/schedules?origin=청양&dest=대전` | 시간표 (출발 시각 순) | 목록 |
| `GET /api/notices` | 공지사항 (최신순) | 목록 |
| `GET /api/favorites?loginId=아이디` | 내 즐겨찾기 | 목록 |
| `POST /api/favorites` `{loginId, scheduleId}` | 즐겨찾기 추가 | 200 |
| `DELETE /api/favorites?loginId=아이디&scheduleId=1` | 즐겨찾기 해제 | 200 |

시간표 한 편: `{scheduleId, originRegion, originTerminal, destRegion, destTerminal, departure, arrival, durationText}`

### 관리자 API

로그인 응답의 `token`을 `X-Auth-Token` 헤더로 보내야 하고, 관리자(`users.role = 'ADMIN'`)가 아니면 401입니다.
토큰은 서버 메모리에만 있어서 서버를 다시 켜면 관리자도 다시 로그인해야 합니다.
실패하면 `{"message": "이유"}`를 같이 보냅니다.

| 요청 | 내용 | 응답 |
|---|---|---|
| `GET /api/admin/routes` | 노선 목록 (노선별 시간표 수 포함) | 목록 |
| `POST /api/admin/routes` `{originRegion, originTerminal, destRegion, destTerminal}` | 노선 추가 (새 지역·터미널은 함께 생성, 터미널을 비우면 지역 이름 사용) | 201 / 400 / 409 이미 있음 |
| `DELETE /api/admin/routes/{id}` | 노선 삭제 | 200 / 409 시간표가 있음 |
| `GET /api/admin/schedules` | 전체 시간표 | 목록 |
| `POST /api/admin/schedules` `{routeId, departure, arrival}` | 시간표 추가 (HH:mm 검증) | 201 / 400 / 409 같은 출발 시각 |
| `PUT /api/admin/schedules/{id}` `{routeId, departure, arrival}` | 시간표 수정 | 200 / 400 / 404 / 409 |
| `DELETE /api/admin/schedules/{id}` | 시간표 삭제 (그 편의 즐겨찾기도 삭제) | 200 |
| `POST /api/admin/notices` `{title, content}` | 공지 등록 | 201 / 400 |
| `PUT /api/admin/notices/{id}` `{title, content}` | 공지 수정 | 200 / 400 / 404 |
| `DELETE /api/admin/notices/{id}` | 공지 삭제 | 200 |
| `GET /api/admin/users` | 회원 목록 (비밀번호 해시 제외) | 목록 |
| `DELETE /api/admin/users/{loginId}` | 회원 삭제 (즐겨찾기도 삭제, 관리자는 불가) | 200 / 400 / 404 |
