# 청양버스 서버 (Spring Boot + MySQL)

앱 ↔ 이 서버 ↔ MySQL 순서로 연결됩니다. 앱은 MySQL에 직접 붙지 않습니다.

## 1. MySQL 준비 (MySQL 8.0.16 이상)

`db/01_schema.sql` → `db/02_seed.sql` 순서로 실행합니다. 01은 `cheongyang_bus` DB를 지우고 새로 만듭니다.

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
| `POST /api/auth/login` `{loginId, password}` | 로그인 | 200 성공 / 401 실패 |
| `GET /api/schedules?origin=청양&dest=대전` | 시간표 (출발 시각 순) | 목록 |
| `GET /api/favorites?loginId=아이디` | 내 즐겨찾기 | 목록 |
| `POST /api/favorites` `{loginId, scheduleId}` | 즐겨찾기 추가 | 200 |
| `DELETE /api/favorites?loginId=아이디&scheduleId=1` | 즐겨찾기 해제 | 200 |

시간표 한 편: `{scheduleId, originRegion, originTerminal, destRegion, destTerminal, departure, arrival, durationText}`
