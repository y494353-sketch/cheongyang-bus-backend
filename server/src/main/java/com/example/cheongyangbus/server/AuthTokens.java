package com.example.cheongyangbus.server;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 로그인할 때 발급하는 토큰. 관리자 API는 이 토큰(X-Auth-Token 헤더)으로 누가 보냈는지 확인합니다.
 * 서버 메모리에만 두므로 서버를 다시 켜면 모두 사라지고, 다시 로그인하면 됩니다.
 */
@Component
public class AuthTokens {

    private final Map<String, String> tokens = new ConcurrentHashMap<>();

    /** 새 토큰 발급 (토큰 → 아이디) */
    public String issue(String loginId) {
        String token = UUID.randomUUID().toString();
        tokens.put(token, loginId);
        return token;
    }

    /** 토큰의 주인 아이디. 모르는 토큰이면 null */
    public String loginId(String token) {
        return token == null ? null : tokens.get(token);
    }

    public void revoke(String token) {
        if (token != null) tokens.remove(token);
    }

    /** 회원이 삭제되면 그 회원의 토큰도 없앰 */
    public void revokeAll(String loginId) {
        tokens.values().removeIf(loginId::equals);
    }
}
