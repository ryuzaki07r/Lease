package com.rahul.Lease.Security;

import com.rahul.Lease.Models.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JWTService {

    public static final String ACCESS_TOKEN = "access";
    public static final String REFRESH_TOKEN = "refresh";
    private static final String TOKEN_TYPE_CLAIM = "type";

    @Value("${jwt.secretKey}")
    private String jwtSecretKey;

    private SecretKey getSecretKey(){
        return Keys.hmacShaKeyFor(jwtSecretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String getAccessToken(User user){
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", user.getRoles().toString())
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()+ 1000*60*10))
                .signWith(getSecretKey())
                .compact();
    }

    public String generateRefreshToken(User user){
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 1000L *60*60*24*30*6 ))
                .signWith(getSecretKey())
                .compact();
    }

    // Rejects tokens of the wrong type, so a refresh token cannot be used as an access token and vice versa
    public Long getUserIdFromToken(String token, String expectedType){
        Claims claims = Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if(!expectedType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))){
            throw new JwtException("Invalid token type, expected " + expectedType + " token");
        }
        return Long.valueOf(claims.getSubject());
    }
}
