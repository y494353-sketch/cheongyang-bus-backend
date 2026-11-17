package com.example.cheongyangbus.server;

import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 앱이 부르는 API. 테이블은 db/01_schema.sql 기준입니다.
 *
 * POST   /api/auth/signup   {loginId, password}     201 가입 / 409 아이디 중복
 * POST   /api/auth/login    {loginId, password}     200 {loginId, role, token} / 401 실패
 * POST   /api/auth/logout   (X-Auth-Token)          토큰 폐기
 * GET    /api/regions?origin=청양 | ?dest=청양       시간표가 있는 지역 목록
 * GET    /api/schedules?origin=청양&dest=대전        시간표 목록
 * GET    /api/notices                               공지사항 목록 (최신순)
 * GET    /api/favorites?loginId=아이디               즐겨찾기 목록
 * POST   /api/favorites     {loginId, scheduleId}   즐겨찾기 추가
 * DELETE /api/favorites?loginId=아이디&scheduleId=1  즐겨찾기 해제
 *
 * 관리자용 API는 AdminController 에 있습니다.
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final JdbcTemplate db;
    private final AuthTokens tokens;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public ApiController(JdbcTemplate db, AuthTokens tokens) {
        this.db = db;
        this.tokens = tokens;
    }

    public record Account(String loginId, String password) {}

    public record FavoriteRequest(String loginId, int scheduleId) {}

    // ----------------------------------------------------------------- 회원

    @PostMapping("/auth/signup")
    public ResponseEntity<Void> signup(@RequestBody Account a) {
        if (isBlank(a.loginId()) || isBlank(a.password()) || a.loginId().length() > 30) {
            return ResponseEntity.badRequest().build();
        }
        try {
            // 비밀번호는 BCrypt 해시만 저장, 표시 이름은 아이디로 시작
            db.update("INSERT INTO users (login_id, password_hash, nickname) VALUES (?, ?, ?)",
                    a.loginId(), encoder.encode(a.password()), a.loginId());
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (DuplicateKeyException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Account a) {
        List<Map<String, Object>> user = db.queryForList(
                "SELECT password_hash AS hash, role FROM users WHERE login_id = ?", a.loginId());
        boolean ok = !user.isEmpty() && a.password() != null
                && encoder.matches(a.password(), (String) user.get(0).get("hash"));
        if (!ok) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        // role: USER 일반 회원 / ADMIN 관리자. token 은 관리자 API를 부를 때 씀
        return ResponseEntity.ok(Map.of(
                "loginId", a.loginId(),
                "role", user.get(0).get("role"),
                "token", tokens.issue(a.loginId())));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
        tokens.revoke(token);
        return ResponseEntity.ok().build();
    }

    // ----------------------------------------------------------------- 시간표

    /** 시간표 한 편을 앱에 보내는 모양 (v_schedule 뷰에서 읽음) */
    private static final String SCHEDULE_COLUMNS = """
            v.schedule_id     AS scheduleId,
            v.origin_region   AS originRegion,
            v.origin_terminal AS originTerminal,
            v.dest_region     AS destRegion,
            v.dest_terminal   AS destTerminal,
            TIME_FORMAT(v.departure_time, '%H:%i') AS departure,
            TIME_FORMAT(v.arrival_time,   '%H:%i') AS arrival,
            v.duration_text   AS durationText
            """;

    /** 버스 조회 화면의 지역 목록. 시간표가 있는 지역만 (관리자가 노선·시간표를 추가하면 바로 나타남) */
    @GetMapping("/regions")
    public List<String> regions(@RequestParam(required = false) String origin,
                                @RequestParam(required = false) String dest) {
        // origin=청양 → 청양에서 갈 수 있는 도착 지역 / dest=청양 → 청양으로 오는 출발 지역
        String column = origin != null ? "v.dest_region" : "v.origin_region";
        String where = origin != null ? "v.origin_region = ?" : "v.dest_region = ?";
        return db.queryForList("SELECT g.region_name FROM region g WHERE g.region_name IN ("
                + "SELECT " + column + " FROM v_schedule v WHERE " + where + " AND v.is_active = TRUE)"
                + " ORDER BY g.sort_order, g.region_id",
                String.class, origin != null ? origin : dest);
    }

    @GetMapping("/schedules")
    public List<Map<String, Object>> schedules(@RequestParam String origin, @RequestParam String dest) {
        return db.queryForList("SELECT " + SCHEDULE_COLUMNS + """
                FROM v_schedule v
                WHERE v.origin_region = ? AND v.dest_region = ? AND v.is_active = TRUE
                ORDER BY v.departure_time
                """, origin, dest);
    }

    // ----------------------------------------------------------------- 공지사항

    @GetMapping("/notices")
    public List<Map<String, Object>> notices() {
        return db.queryForList("""
                SELECT notice_id AS noticeId, title, content,
                       DATE_FORMAT(created_at, '%Y.%m.%d %H:%i') AS createdAt
                FROM notice
                ORDER BY created_at DESC, notice_id DESC
                """);
    }

    // ----------------------------------------------------------------- 즐겨찾기

    @GetMapping("/favorites")
    public List<Map<String, Object>> favorites(@RequestParam String loginId) {
        return db.queryForList("SELECT " + SCHEDULE_COLUMNS + """
                FROM favorite f
                JOIN users u      ON u.user_id = f.user_id
                JOIN v_schedule v ON v.schedule_id = f.schedule_id
                WHERE u.login_id = ?
                ORDER BY v.departure_time
                """, loginId);
    }

    @PostMapping("/favorites")
    public ResponseEntity<Void> addFavorite(@RequestBody FavoriteRequest r) {
        // 이미 있으면 그대로 둠
        db.update("""
                INSERT INTO favorite (user_id, schedule_id)
                SELECT u.user_id, ? FROM users u WHERE u.login_id = ?
                ON DUPLICATE KEY UPDATE favorite_id = favorite_id
                """, r.scheduleId(), r.loginId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/favorites")
    public ResponseEntity<Void> removeFavorite(@RequestParam String loginId, @RequestParam int scheduleId) {
        db.update("""
                DELETE f FROM favorite f
                JOIN users u ON u.user_id = f.user_id
                WHERE u.login_id = ? AND f.schedule_id = ?
                """, loginId, scheduleId);
        return ResponseEntity.ok().build();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
