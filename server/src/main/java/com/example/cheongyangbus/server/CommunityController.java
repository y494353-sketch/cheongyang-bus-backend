package com.example.cheongyangbus.server;

import java.util.Map;
import java.util.LinkedHashSet;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/community")
public class CommunityController {
    private static final String TOKEN = "X-Auth-Token";
    private static final Set<String> CATEGORIES = Set.of("QUESTION", "GENERAL", "INFO");

    private final JdbcTemplate db;
    private final AuthTokens tokens;

    public CommunityController(JdbcTemplate db, AuthTokens tokens) {
        this.db = db;
        this.tokens = tokens;
    }

    @GetMapping("/posts")
    public ResponseEntity<?> list(@RequestHeader(value = TOKEN, required = false) String token,
                                  @RequestParam(value = "category", required = false) String category) {
        if (tokens.loginId(token) == null) return error(401, "다시 로그인해 주세요.");
        String select = "SELECT post_id, category, title, (SELECT COUNT(*) FROM community_comment c WHERE c.post_id = community_post.post_id) AS comment_count FROM community_post";
        if (category == null) {
            return ResponseEntity.ok(db.queryForList(select + " ORDER BY post_id DESC"));
        }
        if (!CATEGORIES.contains(category)) return error(400, "올바른 말머리를 선택해 주세요.");
        return ResponseEntity.ok(db.queryForList(select + " WHERE category = ? ORDER BY post_id DESC", category));
    }

    @PostMapping("/posts")
    public ResponseEntity<?> create(@RequestHeader(value = TOKEN, required = false) String token,
                                    @RequestBody NewPost post) {
        if (tokens.loginId(token) == null) return error(401, "다시 로그인해 주세요.");
        if (post.category() == null || !CATEGORIES.contains(post.category())) {
            return error(400, "올바른 말머리를 선택해 주세요.");
        }
        String title = post.title() == null ? "" : post.title().strip();
        String content = post.content() == null ? "" : post.content().strip();
        if (title.isEmpty() || title.length() > 120 || content.isEmpty() || content.length() > 5000) {
            return error(400, "제목은 1~120자, 본문은 1~5000자로 입력해 주세요.");
        }
        db.update("INSERT INTO community_post (category, title, content, author_id) VALUES (?, ?, ?, ?)",
                post.category(), title, content, tokens.loginId(token));
        return ResponseEntity.status(201).build();
    }

    @GetMapping("/posts/{postId}")
    public ResponseEntity<?> detail(@RequestHeader(value = TOKEN, required = false) String token,
                                    @PathVariable("postId") long postId) {
        if (tokens.loginId(token) == null) return error(401, "다시 로그인해 주세요.");
        var posts = db.queryForList("SELECT post_id, category, title, content FROM community_post WHERE post_id = ?", postId);
        if (posts.isEmpty()) return error(404, "글을 찾을 수 없어요.");
        var comments = db.queryForList("""
                SELECT comment_id, parent_id, author_id, content
                FROM community_comment WHERE post_id = ? ORDER BY comment_id
                """, postId);
        return ResponseEntity.ok(Map.of("post", posts.get(0), "comments", comments));
    }

    @Transactional
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<?> comment(@RequestHeader(value = TOKEN, required = false) String token,
                                     @PathVariable("postId") long postId,
                                     @RequestBody NewComment request) {
        String author = tokens.loginId(token);
        if (author == null) return error(401, "다시 로그인해 주세요.");
        String content = request.content() == null ? "" : request.content().strip();
        if (content.isEmpty() || content.length() > 2000) return error(400, "댓글은 1~2000자로 입력해 주세요.");

        var postAuthors = db.queryForList("SELECT author_id FROM community_post WHERE post_id = ?", String.class, postId);
        if (postAuthors.isEmpty()) return error(404, "글을 찾을 수 없어요.");
        Set<String> recipients = new LinkedHashSet<>(postAuthors);
        if (request.parentId() != null) {
            var parents = db.queryForList("SELECT author_id FROM community_comment WHERE post_id = ? AND comment_id = ?",
                    String.class, postId, request.parentId());
            if (parents.isEmpty()) return error(400, "답글을 달 댓글을 찾을 수 없어요.");
            recipients.add(parents.get(0));
        }
        recipients.remove(null);
        recipients.remove(author);

        var key = new GeneratedKeyHolder();
        db.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO community_comment (post_id, parent_id, author_id, content) VALUES (?, ?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, postId);
            statement.setObject(2, request.parentId());
            statement.setString(3, author);
            statement.setString(4, content);
            return statement;
        }, key);
        long commentId = java.util.Objects.requireNonNull(key.getKey()).longValue();
        for (String recipient : recipients) {
            db.update("INSERT INTO community_notification (recipient, comment_id) VALUES (?, ?)", recipient, commentId);
        }
        return ResponseEntity.status(201).build();
    }

    @GetMapping("/notifications")
    public ResponseEntity<?> notifications(@RequestHeader(value = TOKEN, required = false) String token,
                                           @RequestParam(value = "after", defaultValue = "0") long after) {
        String recipient = tokens.loginId(token);
        if (recipient == null) return error(401, "다시 로그인해 주세요.");
        return ResponseEntity.ok(db.queryForList("""
                SELECT n.notification_id, p.post_id, p.title, c.parent_id
                FROM community_notification n
                JOIN community_comment c ON c.comment_id = n.comment_id
                JOIN community_post p ON p.post_id = c.post_id
                WHERE n.recipient = ? AND n.notification_id > ?
                ORDER BY n.notification_id LIMIT 100
                """, recipient, after));
    }

    public record NewComment(String content, Long parentId) {}

    private ResponseEntity<?> error(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }

    public record NewPost(String category, String title, String content) {}
}
