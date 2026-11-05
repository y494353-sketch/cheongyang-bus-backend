# 청양버스 파일별 기능 안내

## 먼저 알아둘 점

GitHub 파일 목록의 커밋 메시지는 파일마다 따로 적는 칸이 아닙니다. 여러 파일을 한 커밋으로 올리면 같은 커밋 제목이 각 파일 옆에 반복됩니다. 이 문서에서 파일별 역할을 확인할 수 있습니다.

앱은 Kotlin + XML이며, 데이터 저장에는 기기 내부 Room DB를 사용합니다. 서버를 통한 기기 간 공지·회원 동기화는 없습니다. 시간표는 관리자가 CSV로 내보낸 뒤 새 APK의 assets에 넣어 배포하는 방식입니다.

## 앱 코드

| 파일 | 기능 |
|---|---|
| app/src/main/java/com/example/cheongyangbus/AccountStore.kt | 시연용 관리자 계정 확인과 관리자 로그인 세션을 저장합니다. 일반 회원 계정은 UserRepository와 Room을 사용합니다. |
| app/src/main/java/com/example/cheongyangbus/AdminActivity.kt | 관리자 메뉴입니다. 시간표 관리, 공지 관리, 회원 관리 화면으로 이동합니다. |
| app/src/main/java/com/example/cheongyangbus/BaseActivity.kt | 화면 공통 시스템 표시줄·화면 여백 처리를 제공하고 뒤로가기 버튼을 연결합니다. |
| app/src/main/java/com/example/cheongyangbus/BusDetailActivity.kt | 선택한 버스의 상세정보를 보여주고 즐겨찾기와 출발 알림을 관리합니다. |
| app/src/main/java/com/example/cheongyangbus/BusResultActivity.kt | 출발지·도착지에 맞는 시간표를 Room에서 조회해 목록으로 표시합니다. |
| app/src/main/java/com/example/cheongyangbus/BusScheduleStore.kt | SharedPreferences 방식의 예전 시간표 저장 코드입니다. 현재 검색·관리 화면은 Room을 사용하며 이 클래스는 이전 구현의 잔여 코드입니다. |
| app/src/main/java/com/example/cheongyangbus/BusSearchActivity.kt | 청양 출발/도착 모드를 적용하고 청양을 고정합니다. 선택 가능한 지역은 Room 시간표의 출발지·도착지에서 가져옵니다. |
| app/src/main/java/com/example/cheongyangbus/BusTime.kt | 화면 사이에 전달하는 버스 한 편의 데이터 모델과 Intent 변환을 담당합니다. |
| app/src/main/java/com/example/cheongyangbus/BusTimeAdapter.kt | 검색 결과와 즐겨찾기 버스를 RecyclerView 항목으로 표시합니다. |
| app/src/main/java/com/example/cheongyangbus/DemoData.kt | 예전 데모 즐겨찾기·알림 샘플과 BusScheduleStore 연결 코드입니다. 현재 주요 검색·즐겨찾기 기능은 Room Repository를 사용합니다. |
| app/src/main/java/com/example/cheongyangbus/FavoriteActivity.kt | Room에 저장된 즐겨찾기 목록을 보여줍니다. |
| app/src/main/java/com/example/cheongyangbus/LoginActivity.kt | 일반 회원은 UserRepository로 로그인하고, 시연용 관리자는 AccountStore로 확인합니다. |
| app/src/main/java/com/example/cheongyangbus/MainActivity.kt | 메인 화면의 즐겨찾기, 내 정보, 공지사항, 청양 출발/도착 버튼을 연결합니다. |
| app/src/main/java/com/example/cheongyangbus/MemberManageActivity.kt | 관리자용 회원 목록 조회와 회원 삭제를 제공합니다. 비밀번호 해시는 화면에 표시하지 않습니다. |
| app/src/main/java/com/example/cheongyangbus/MyPageActivity.kt | 로그인 아이디, 즐겨찾기 이동, 알림 설정, 로그아웃을 제공합니다. |
| app/src/main/java/com/example/cheongyangbus/NoticeActivity.kt | 일반 사용자가 공지 목록을 읽고 공지 제목·내용·등록일을 확인합니다. |
| app/src/main/java/com/example/cheongyangbus/NoticeManageActivity.kt | 관리자가 Room 공지를 등록·수정·삭제합니다. 일반 사용자 공지 화면과 같은 notices 데이터를 사용합니다. |
| app/src/main/java/com/example/cheongyangbus/RegisterActivity.kt | 입력값과 비밀번호 확인을 검사하고 UserRepository를 통해 회원을 Room에 등록합니다. |
| app/src/main/java/com/example/cheongyangbus/ScheduleManageActivity.kt | 관리자가 Room 시간표를 추가·수정·삭제하고 파일 저장 선택기로 전체 시간표를 CSV로 내보냅니다. |
| app/src/main/java/com/example/cheongyangbus/data/Alarm.kt | 출발 알림 Room 테이블·DAO와 알림 예약/취소 및 알림 수신 처리를 담당합니다. |
| app/src/main/java/com/example/cheongyangbus/data/AppDatabase.kt | cheongyang_bus.db Room 데이터베이스와 users, schedules, favorites, alarms, notices 테이블을 정의합니다. DB 열 때 버전이 바뀐 시간표 CSV를 적용하도록 연결합니다. |
| app/src/main/java/com/example/cheongyangbus/data/Favorite.kt | 즐겨찾기 Room Entity·DAO·Repository입니다. |
| app/src/main/java/com/example/cheongyangbus/data/Notice.kt | 공지 Room Entity와 목록·등록·수정·삭제 DAO를 정의합니다. |
| app/src/main/java/com/example/cheongyangbus/data/NoticeRepository.kt | 공지 화면이 Room DAO를 호출하도록 연결하는 Repository입니다. |
| app/src/main/java/com/example/cheongyangbus/data/Prefs.kt | 로그인 아이디, 최근 검색 경로, 출발 알림 켜기/끄기처럼 작은 설정값을 SharedPreferences에 저장합니다. |
| app/src/main/java/com/example/cheongyangbus/data/Schedule.kt | 시간표 Room Entity·DAO, 지역 목록 조회, CSV 파싱/검증, 버전 비교와 schedules 테이블 교체를 담당합니다. |
| app/src/main/java/com/example/cheongyangbus/data/ScheduleRepository.kt | 출발지·도착지 시간표 조회와 버스 상태·소요시간 표시값 변환을 담당합니다. |
| app/src/main/java/com/example/cheongyangbus/data/SeoulTime.kt | 기기 시간대 설정과 무관하게 Asia/Seoul 기준 시각과 날짜를 만듭니다. |
| app/src/main/java/com/example/cheongyangbus/data/UserRepository.kt | Room 회원 가입·로그인·회원 목록·삭제를 처리하고 비밀번호는 PBKDF2 해시로 저장합니다. |

## 앱 데이터

| 파일 | 기능 |
|---|---|
| app/src/main/assets/timetable.csv | 앱에 포함되는 초기/배포 시간표 데이터입니다. CSV 열은 시간표 Entity와 맞춰야 합니다. |
| app/src/main/assets/timetable_version.txt | 번들 CSV의 정수 버전입니다. 시간표를 새로 배포할 때 버전을 올려야 기존 기기에 갱신이 적용됩니다. |

## 화면 XML

| 파일 | 기능 |
|---|---|
| app/src/main/res/layout/activity_admin.xml | 관리자 메뉴 화면의 버튼과 배치입니다. |
| app/src/main/res/layout/activity_bus_detail.xml | 버스 상세, 즐겨찾기, 출발 알림 화면의 배치입니다. |
| app/src/main/res/layout/activity_bus_result.xml | 검색된 시간표 목록 화면의 배치입니다. |
| app/src/main/res/layout/activity_bus_search.xml | 청양 고정 출발/도착 지역 선택 화면의 배치입니다. |
| app/src/main/res/layout/activity_favorite.xml | 즐겨찾기 목록 화면의 배치입니다. |
| app/src/main/res/layout/activity_login.xml | 로그인 화면의 입력창과 버튼 배치입니다. |
| app/src/main/res/layout/activity_main.xml | 메인 화면의 공지사항 버튼, 즐겨찾기·내 정보 버튼, 출발/도착 카드 배치입니다. |
| app/src/main/res/layout/activity_member_manage.xml | 관리자 회원 관리 화면의 배치입니다. |
| app/src/main/res/layout/activity_my_page.xml | 내 정보와 설정 화면의 배치입니다. |
| app/src/main/res/layout/activity_notice.xml | 일반 사용자 공지 목록 화면의 배치입니다. |
| app/src/main/res/layout/activity_notice_manage.xml | 관리자 공지 관리 화면의 배치입니다. |
| app/src/main/res/layout/activity_register.xml | 회원가입 화면의 아이디·비밀번호·비밀번호 확인 입력 배치입니다. |
| app/src/main/res/layout/activity_schedule_manage.xml | 시간표 관리, 추가 및 CSV 내보내기 화면의 배치입니다. |
| app/src/main/res/layout/item_bus_time.xml | RecyclerView에 표시되는 버스 시간표 한 행의 배치입니다. |

## 아이콘·색상·문구

| 파일 | 기능 |
|---|---|
| app/src/main/res/drawable/badge_background.xml | 버스 상태 배지의 배경 모양입니다. |
| app/src/main/res/drawable/circle_background.xml | 원형 아이콘 배경입니다. |
| app/src/main/res/drawable/ic_arrow.xml | 방향 표시 아이콘입니다. |
| app/src/main/res/drawable/ic_back.xml | 뒤로가기 아이콘입니다. |
| app/src/main/res/drawable/ic_bell.xml | 출발 알림 아이콘입니다. |
| app/src/main/res/drawable/ic_bus.xml | 버스 아이콘 및 앱 아이콘에 사용합니다. |
| app/src/main/res/drawable/ic_lock.xml | 청양 고정 지역을 표시하는 자물쇠 아이콘입니다. |
| app/src/main/res/drawable/ic_person.xml | 사용자/내 정보 아이콘입니다. |
| app/src/main/res/drawable/ic_pin.xml | 위치 아이콘입니다. |
| app/src/main/res/drawable/ic_star.xml | 즐겨찾기 아이콘입니다. |
| app/src/main/res/values/colors.xml | 앱의 초록색 계열과 보조 색상을 정의합니다. |
| app/src/main/res/values/strings.xml | 화면 문구, 공지 안내, 버튼 글자, 접근성 설명을 모읍니다. |
| app/src/main/res/values/themes.xml | Material 기반 앱 테마와 공통 위젯 스타일을 정의합니다. |

## 빌드·프로젝트 설정

| 파일 | 기능 |
|---|---|
| app/build.gradle.kts | Android 앱 모듈, Kotlin/KSP, Room·Material·RecyclerView 등 의존성과 SDK 설정입니다. |
| build.gradle.kts | 프로젝트 수준 Gradle 플러그인 버전을 설정합니다. |
| settings.gradle.kts | 플러그인/의존성 저장소와 app 모듈을 등록합니다. |
| gradle.properties | Gradle 실행 옵션을 설정합니다. |
| gradle/wrapper/gradle-wrapper.jar | 프로젝트에 고정된 Gradle 버전을 실행하는 Wrapper 구성 요소입니다. |
| gradle/wrapper/gradle-wrapper.properties | Wrapper가 내려받을 Gradle 버전을 지정합니다. |
| gradlew | macOS/Linux용 Gradle Wrapper 실행 스크립트입니다. |
| gradlew.bat | Windows용 Gradle Wrapper 실행 스크립트입니다. |
| .gitignore | 빌드 결과, 로컬 SDK 경로, IDE 개인 설정 파일을 Git에서 제외합니다. |

## 참고 문서

| 파일 | 기능 |
|---|---|
| 검증결과.md | 프로젝트의 정적 확인 및 확인 범위를 기록합니다. |
| 전체코드.md | 주요 Kotlin/XML 코드와 파일 경로를 모아 둔 참고 문서입니다. |
| README.md | 저장소의 기존 소개 문서입니다. 이번 앱 소스 추가 커밋에서는 변경하지 않았습니다. |
| check.md | 저장소에 이미 있던 연결 확인 문서입니다. 이번 앱 소스 추가 커밋에서는 변경하지 않았습니다. |

## 데이터 범위

회원·시간표·즐겨찾기·출발 알림·공지는 기기 내부 Room DB에 저장됩니다. 시간표 CSV가 더 높은 버전으로 앱에 포함되어 설치/업데이트되면 schedules 테이블만 교체합니다. 공지와 계정은 서버가 없어 다른 기기와 자동 동기화되지 않습니다.
