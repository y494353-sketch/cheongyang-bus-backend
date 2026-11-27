# cheongyang-bus-backend
청양지역 시외버스 출발·도착시간 확인 및 출발시간 안내 백엔드


## 커뮤니티 테스트

커뮤니티가 들어간 Android 프로젝트는 `CheongyangBus/`에서 열고, 백엔드는 `server/`에서 실행합니다.

기존 `cheongyang_bus` DB에는 `db/03_community.sql`, `db/04_community_comments.sql`을 순서대로 적용한 뒤 서버를 재시작합니다. 처음 설정하는 경우에는 `01_schema.sql`과 `02_seed.sql`을 먼저 실행합니다. `01_schema.sql`은 기존 DB를 삭제하므로 사용 중인 DB에 다시 실행하지 마세요.

여러 기기에서 글과 댓글을 공유하려면 같은 백엔드 주소를 사용해야 합니다. 작성자 정보가 저장되기 전에 만든 글은 글쓴이 알림을 보낼 수 없습니다.
