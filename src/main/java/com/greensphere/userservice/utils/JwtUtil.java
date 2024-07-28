package com.greensphere.userservice.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.greensphere.userservice.dto.request.tokenRequest.TokenRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.Date;

@Component
@Slf4j
public class JwtUtil {
    @Value("${jwt.refresh.validity}")
    private int refreshValidity;
    @Value("${jwt.secret}")
    private String jwtSecret;
    @Value("${jwt.validity}")
    private int jwtValidity;

    public String createJwtToken(TokenRequest tokenRequest) {
        return JWT.create()
                .withSubject(tokenRequest.getUsername())
                .withClaim("role", tokenRequest.getRole())
                .withIssuedAt(Date.from(tokenRequest.getNow().atZone(ZoneId.systemDefault()).toInstant()))
                .withIssuer("GreenSphare")
                .withExpiresAt(new Date(System.currentTimeMillis() + jwtValidity * 1000L))
                .sign(Algorithm.HMAC512(jwtSecret.getBytes()));
    }

    public boolean isValidToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC512(jwtSecret.getBytes());
            JWTVerifier verifier = JWT.require(algorithm).build();
            DecodedJWT decodedJWT = verifier.verify(token);

            // Extract the expiration claim
            Long expirationTime = decodedJWT.getClaim("exp").asLong();
            long currentTime = System.currentTimeMillis() / 1000; // Convert to seconds

            if ((expirationTime != null) && (expirationTime >= currentTime)) {
                log.info("isValidToken-> JWT is not expired.");
                return true;
            } else {
                log.info("isValidToken-> JWT is expired.");
            }
        } catch (JWTDecodeException e) {
            log.warn("isValidToken-> Invalid JWT format ");
            // Handle the decoding exception here
        } catch (TokenExpiredException e) {
            log.warn("isValidToken-> Token is expired");
        }
        return false;
    }

    public String createRefreshToken(TokenRequest tokenRequest) {
        return JWT.create()
                .withSubject(tokenRequest.getUsername())
                .withClaim("role", tokenRequest.getRole())
                .withIssuedAt(Date.from(tokenRequest.getNow().atZone(ZoneId.systemDefault()).toInstant()))
                .withIssuer("GreenSphare")
                .withExpiresAt(new Date(System.currentTimeMillis() + refreshValidity * 1000L))
                .sign(Algorithm.HMAC512(jwtSecret.getBytes()));

    }

    public String getUsernameFromToken(String token) {
        DecodedJWT decodedJWT = JWT.decode(token);
        log.info("username  {}", decodedJWT.getSubject());
        return decodedJWT.getSubject();
    }

}
