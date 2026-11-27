package com.example.cheongyangbus.server;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 API. 모든 요청에 로그인 때 받은 토큰을 X-Auth-Token 헤더로 보내야 하고,
 * 토큰 주인이 관리자(users.role = 'ADMIN')가 아니면 401 을 돌려줍니다.
 * 실패 이유는 {"message": "..."} 로 보냅니다.
 *
 * 노선   GET /api/admin/routes · POST /api/admin/routes · DELETE /api/admin/routes/{id} (사용 중지)
 * 시간표 GET /api/admin/schedules · POST /api/admin/schedules · PUT·DELETE /api/admin/schedules/{id} (삭제 = 사용 중지)
 * 공지   POST /api/admin/notices · PUT·DELETE /api/admin/notices/{id}
 * 회원   GET /api/admin/users · DELETE /api/admin/users/{loginId}
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final String TOKEN = "X-Auth-Token";
    private static final Pattern TIME = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");

    private final JdbcTemplate db;
    private final AuthTokens tokens;

    public AdminController(JdbcTemplate db, AuthTokens tokens) {
        this.db = db;
        this.tokens = tokens;
    }

    public record RouteRequest(String originRegion, String originTerminal, String destRegion, String destTerminal) {}

    public record ScheduleRequest(int routeId, String departure, String arrival) {}

    public record NoticeRequest(String title, String content) {}

    // ----------------------------------------------------------------- 노선

    @GetMapping("/routes")
    public ResponseEntity<?> routes(@RequestHeader(value = TOKEN, required = false) String token) {
        if (!isAdmin(token)) return unauthorized();
        return ResponseEntity.ok(db.queryForList("""
                SELECT r.route_id AS routeId,
                       oreg.region_name AS originRegion, ot.terminal_name AS originTerminal,
                       dreg.region_name AS destRegion,   dt.terminal_name AS destTerminal,
                       (SELECT COUNT(*) FROM schedule s WHERE s.route_id = r.route_id AND s.is_active = TRUE) AS scheduleCount
                FROM route r
                JOIN terminal ot   ON ot.terminal_id = r.origin_terminal_id
                JOIN region   oreg ON oreg.region_id = ot.region_id
                JOIN terminal dt   ON dt.terminal_id = r.dest_terminal_id
                JOIN region   dreg ON dreg.region_id = dt.region_id
                WHERE r.is_active = TRUE
                ORDER BY oreg.sort_order, dreg.sort_order, r.route_id
                """));
    }

    /** 노선 추가. 처음 보는 지역·터미널은 함께 만듭니다. 터미널을 비우면 지역 이름을 터미널 이름으로 씁니다. */
    @PostMapping("/routes")
    @Transactional
    public ResponseEntity<?> addRoute(@RequestHeader(value = TOKEN, required = false) String token,
                                      @RequestBody RouteRequest r) {
        if (!isAdmin(token)) return unauthorized();
        String originRegion = trim(r.originRegion());
        String destRegion = trim(r.destRegion());
        if (originRegion.isEmpty() || destRegion.isEmpty() || originRegion.length() > 20 || destRegion.length() > 20) {
            return badRequest("출발 지역과 도착 지역을 20자 이내로 입력하세요.");
        }
        String originTerminal = trim(r.originTerminal()).isEmpty() ? originRegion : trim(r.originTerminal());
        String destTerminal = trim(r.destTerminal()).isEmpty() ? destRegion : trim(r.destTerminal());
        if (originTerminal.length() > 50 || destTerminal.length() > 50) {
            return badRequest("터미널 이름은 50자 이내로 입력하세요.");
        }
        if (originTerminal.equals(destTerminal)) {
            return badRequest("출발과 도착이 같습니다.");
        }
        Integer origin = terminalId(regionId(originRegion), originTerminal);
        Integer dest = terminalId(regionId(destRegion), destTerminal);
        if (origin == null || dest == null) {
            return badRequest("같은 이름의 터미널이 다른 지역에 이미 있습니다.");
        }
        // 삭제(사용 중지)했던 노선을 다시 추가하면 다시 사용 (시간표·즐겨찾기도 다시 정상 표시)
        int revived = db.update(
                "UPDATE route SET is_active = TRUE WHERE origin_terminal_id = ? AND dest_terminal_id = ? AND is_active = FALSE",
                origin, dest);
        if (revived > 0) return ResponseEntity.status(HttpStatus.CREATED).build();
        try {
            db.update("INSERT INTO route (origin_terminal_id, dest_terminal_id) VALUES (?, ?)", origin, dest);
        } catch (DuplicateKeyException e) {
            return conflict("이미 있는 노선입니다.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * 노선 삭제 = 사용 중지 (DB에서 지우지 않음).
     * 그 노선의 시간표는 검색·관리 목록에서 빠지고, 사용자 즐겨찾기에는 '운행 폐지'로 연하게 남습니다.
     */
    @DeleteMapping("/routes/{id}")
    public ResponseEntity<?> deleteRoute(@RequestHeader(value = TOKEN, required = false) String token,
                                         @PathVariable int id) {
        if (!isAdmin(token)) return unauthorized();
        int rows = db.update("UPDATE route SET is_active = FALSE WHERE route_id = ? AND is_active = TRUE", id);
        return rows > 0 ? ResponseEntity.ok().build() : notFound("없는 노선입니다.");
    }

    // ----------------------------------------------------------------- 시간표

    @GetMapping("/schedules")
    public ResponseEntity<?> schedules(@RequestHeader(value = TOKEN, required = false) String token) {
        if (!isAdmin(token)) return unauthorized();
        return ResponseEntity.ok(db.queryForList("""
                SELECT v.schedule_id     AS scheduleId,
                       v.route_id        AS routeId,
                       v.origin_region   AS originRegion,
                       v.origin_terminal AS originTerminal,
                       v.dest_region     AS destRegion,
                       v.dest_terminal   AS destTerminal,
                       TIME_FORMAT(v.departure_time, '%H:%i') AS departure,
                       TIME_FORMAT(v.arrival_time,   '%H:%i') AS arrival,
                       v.duration_text   AS durationText
                FROM v_schedule v
                WHERE v.is_active = TRUE
                ORDER BY v.route_id, v.departure_time
                """));
    }

    @PostMapping("/schedules")
    public ResponseEntity<?> addSchedule(@RequestHeader(value = TOKEN, required = false) String token,
                                         @RequestBody ScheduleRequest s) {
        if (!isAdmin(token)) return unauthorized();
        String error = checkSchedule(s);
        if (error != null) return badRequest(error);
        // 삭제(사용 중지)했던 같은 노선·같은 출발 시각의 편이 있으면 그 편을 다시 사용 (즐겨찾기도 다시 정상 표시)
        int revived = db.update("""
                UPDATE schedule SET is_active = TRUE, arrival_time = ?
                WHERE route_id = ? AND departure_time = ? AND day_type = 'DAILY' AND is_active = FALSE
                """, s.arrival(), s.routeId(), s.departure());
        if (revived > 0) return ResponseEntity.status(HttpStatus.CREATED).build();
        try {
            db.update("INSERT INTO schedule (route_id, departure_time, arrival_time) VALUES (?, ?, ?)",
                    s.routeId(), s.departure(), s.arrival());
        } catch (DuplicateKeyException e) {
            return conflict("같은 노선에 같은 출발 시각이 이미 있습니다.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/schedules/{id}")
    public ResponseEntity<?> updateSchedule(@RequestHeader(value = TOKEN, required = false) String token,
                                            @PathVariable int id, @RequestBody ScheduleRequest s) {
        if (!isAdmin(token)) return unauthorized();
        String error = checkSchedule(s);
        if (error != null) return badRequest(error);
        try {
            int rows = db.update(
                    "UPDATE schedule SET route_id = ?, departure_time = ?, arrival_time = ? WHERE schedule_id = ? AND is_active = TRUE",
                    s.routeId(), s.departure(), s.arrival(), id);
            if (rows == 0) return notFound("없는 시간표입니다.");
        } catch (DuplicateKeyException e) {
            return conflict("같은 노선에 같은 출발 시각이 이미 있습니다. (삭제한 편이면 시간표 추가로 같은 시각을 넣으면 다시 사용됩니다)");
        }
        return ResponseEntity.ok().build();
    }

    /**
     * 시간표 삭제 = 사용 중지 (DB에서 지우지 않음).
     * 검색·관리 목록에서는 빠지고, 이 편을 즐겨찾기한 사용자에게는 '운행 폐지'로 연하게 남습니다.
     */
    @DeleteMapping("/schedules/{id}")
    public ResponseEntity<?> deleteSchedule(@RequestHeader(value = TOKEN, required = false) String token,
                                            @PathVariable int id) {
        if (!isAdmin(token)) return unauthorized();
        int rows = db.update("UPDATE schedule SET is_active = FALSE WHERE schedule_id = ? AND is_active = TRUE", id);
        return rows > 0 ? ResponseEntity.ok().build() : notFound("없는 시간표입니다.");
    }

    // ----------------------------------------------------------------- 공지사항 (목록은 ApiController /api/notices)

    @PostMapping("/notices")
    public ResponseEntity<?> addNotice(@RequestHeader(value = TOKEN, required = false) String token,
                                       @RequestBody NoticeRequest n) {
        if (!isAdmin(token)) return unauthorized();
        String error = checkNotice(n);
        if (error != null) return badRequest(error);
        db.update("INSERT INTO notice (title, content) VALUES (?, ?)", trim(n.title()), trim(n.content()));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/notices/{id}")
    public ResponseEntity<?> updateNotice(@RequestHeader(value = TOKEN, required = false) String token,
                                          @PathVariable int id, @RequestBody NoticeRequest n) {
        if (!isAdmin(token)) return unauthorized();
        String error = checkNotice(n);
        if (error != null) return badRequest(error);
        int rows = db.update("UPDATE notice SET title = ?, content = ? WHERE notice_id = ?",
                trim(n.title()), trim(n.content()), id);
        return rows > 0 ? ResponseEntity.ok().build() : notFound("없는 공지사항입니다.");
    }

    @DeleteMapping("/notices/{id}")
    public ResponseEntity<?> deleteNotice(@RequestHeader(value = TOKEN, required = false) String token,
                                          @PathVariable int id) {
        if (!isAdmin(token)) return unauthorized();
        db.update("DELETE FROM notice WHERE notice_id = ?", id);
        return ResponseEntity.ok().build();
    }

    // ----------------------------------------------------------------- 회원

    @GetMapping("/users")
    public ResponseEntity<?> users(@RequestHeader(value = TOKEN, required = false) String token) {
        if (!isAdmin(token)) return unauthorized();
        // 비밀번호 해시는 보내지 않음
        return ResponseEntity.ok(db.queryForList("""
                SELECT login_id AS loginId, role,
                       DATE_FORMAT(created_at, '%Y.%m.%d %H:%i') AS createdAt
                FROM users
                ORDER BY role, created_at DESC, user_id DESC
                """));
    }

    /** 회원 삭제. 즐겨찾기도 함께 지워집니다. 관리자 계정은 지울 수 없습니다. */
    @DeleteMapping("/users/{loginId}")
    public ResponseEntity<?> deleteUser(@RequestHeader(value = TOKEN, required = false) String token,
                                        @PathVariable String loginId) {
        if (!isAdmin(token)) return unauthorized();
        List<String> role = db.queryForList("SELECT role FROM users WHERE login_id = ?", String.class, loginId);
        if (role.isEmpty()) return notFound("없는 회원입니다.");
        if ("ADMIN".equals(role.get(0))) return badRequest("관리자 계정은 삭제할 수 없습니다.");
        db.update("DELETE FROM users WHERE login_id = ?", loginId);
        tokens.revokeAll(loginId);
        return ResponseEntity.ok().build();
    }

    // ----------------------------------------------------------------- 공통

    /** 토큰 주인이 지금도 관리자인지 DB에서 확인 */
    private boolean isAdmin(String token) {
        String loginId = tokens.loginId(token);
        if (loginId == null) return false;
        List<String> role = db.queryForList("SELECT role FROM users WHERE login_id = ?", String.class, loginId);
        return !role.isEmpty() && "ADMIN".equals(role.get(0));
    }

    private String checkSchedule(ScheduleRequest s) {
        if (s.departure() == null || s.arrival() == null
                || !TIME.matcher(s.departure()).matches() || !TIME.matcher(s.arrival()).matches()) {
            return "시간은 09:05 처럼 HH:mm 형식으로 입력하세요.";
        }
        if (s.departure().equals(s.arrival())) return "출발 시각과 도착 시각이 같습니다.";
        Integer route = db.queryForObject(
                "SELECT COUNT(*) FROM route WHERE route_id = ? AND is_active = TRUE", Integer.class, s.routeId());
        if (route == null || route == 0) return "없는 노선입니다.";
        return null;
    }

    private String checkNotice(NoticeRequest n) {
        if (trim(n.title()).isEmpty() || trim(n.content()).isEmpty()) return "제목과 내용을 입력하세요.";
        if (trim(n.title()).length() > 100) return "제목은 100자 이내로 입력하세요.";
        return null;
    }

    /** 지역 번호. 없으면 목록 맨 뒤 순서로 새로 만듦 */
    private int regionId(String name) {
        List<Integer> ids = db.queryForList("SELECT region_id FROM region WHERE region_name = ?", Integer.class, name);
        if (!ids.isEmpty()) return ids.get(0);
        db.update("INSERT INTO region (region_name, sort_order) SELECT ?, COALESCE(MAX(sort_order), 0) + 1 FROM region", name);
        return db.queryForObject("SELECT region_id FROM region WHERE region_name = ?", Integer.class, name);
    }

    /** 터미널 번호. 없으면 새로 만들고, 같은 이름이 다른 지역에 있으면 null */
    private Integer terminalId(int regionId, String name) {
        List<Map<String, Object>> found = db.queryForList(
                "SELECT terminal_id AS id, region_id AS regionId FROM terminal WHERE terminal_name = ?", name);
        if (!found.isEmpty()) {
            Map<String, Object> t = found.get(0);
            return ((Number) t.get("regionId")).intValue() == regionId ? ((Number) t.get("id")).intValue() : null;
        }
        try {
            db.update("INSERT INTO terminal (region_id, terminal_name) VALUES (?, ?)", regionId, name);
        } catch (DataIntegrityViolationException e) {
            return null;
        }
        return db.queryForObject("SELECT terminal_id FROM terminal WHERE terminal_name = ?", Integer.class, name);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "관리자 로그인이 필요합니다."));
    }

    private static ResponseEntity<?> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    private static ResponseEntity<?> conflict(String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", message));
    }

    private static ResponseEntity<?> notFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", message));
    }
}
