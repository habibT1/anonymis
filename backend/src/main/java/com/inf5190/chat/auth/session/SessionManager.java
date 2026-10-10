package com.inf5190.chat.auth.session;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Repository;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

@Repository
@PropertySource("classpath:application.properties")
public class SessionManager {

    @Value("${jwt.secret.key.base64}")
    private String secretKeyBase64;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {

        byte[] keyBytes = Base64.getDecoder().decode(secretKeyBase64);


        this.secretKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(keyBytes);
    }


    public String addSession(SessionData authData) {
        long nowMillis = System.currentTimeMillis();
        Date issuedAt = new Date(nowMillis);
        Date expiration = new Date(nowMillis + 2L * 60L * 60L * 1000L);

        String jwt = Jwts.builder()
                .subject(authData.username())
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();

        return jwt;
    }


    public void removeSession(String sessionId) {
        // JWT stateless - pas de gestion de session côté serveur
    }

    public SessionData getSession(String sessionId) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(sessionId)
                    .getPayload();

            String username = claims.getSubject();

            if (username == null || username.isBlank()) {
                return null;
            }

            return new SessionData(username);

        } catch (JwtException e) {
            return null;
        }
    }
}