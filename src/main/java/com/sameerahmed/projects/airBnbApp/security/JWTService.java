package com.sameerahmed.projects.airBnbApp.security;

import com.sameerahmed.projects.airBnbApp.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Service
public class JWTService {

    /**
     * Distinguishes an access token from a refresh token. Without it both are
     * interchangeable bearer credentials, because they are signed with the same
     * key and carry the same subject — a refresh token in an Authorization
     * header would authenticate for its full lifetime.
     */
    private static final String TOKEN_TYPE_CLAIM = "typ";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    @Value("${jwt.secretKey}")
    private String jwtSecretKey;

    @Value("${jwt.access-token-validity:10m}")
    private Duration accessTokenValidity;

    @Value("${jwt.refresh-token-validity:7d}")
    private Duration refreshTokenValidity;

    private SecretKey getSecretKey(){
        return Keys.hmacShaKeyFor(jwtSecretKey.getBytes(StandardCharsets.UTF_8));
    }

    public Duration getRefreshTokenValidity() {
        return refreshTokenValidity;
    }

    public String generateAccessToken(User user){
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .claim("email", user.getEmail())
                .claim("roles", user.getRoles())
                .issuedAt(new Date())
                .expiration(expiryAfter(accessTokenValidity))
                .signWith(getSecretKey())
                .compact();
    }

    public String generateRefreshToken(User user){
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
                .issuedAt(new Date())
                .expiration(expiryAfter(refreshTokenValidity))
                .signWith(getSecretKey())
                .compact();
    }

    public Long getUserIdFromAccessToken(String token){
        return subjectOf(parseClaims(token, ACCESS_TOKEN_TYPE));
    }

    public Long getUserIdFromRefreshToken(String token){
        return subjectOf(parseClaims(token, REFRESH_TOKEN_TYPE));
    }

    private Date expiryAfter(Duration validity) {
        return new Date(System.currentTimeMillis() + validity.toMillis());
    }

    /**
     * {@code require} makes the type claim part of signature validation rather
     * than an afterthought: a token of the wrong type fails to parse at all,
     * and the resulting MissingClaimException / IncorrectClaimException are both
     * JwtExceptions, so callers handle them like any other invalid token.
     */
    private Claims parseClaims(String token, String expectedType) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .require(TOKEN_TYPE_CLAIM, expectedType)
                .clockSkewSeconds(30)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Long subjectOf(Claims claims) {
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new JwtException("Token subject is not a valid user id", e);
        }
    }
}
