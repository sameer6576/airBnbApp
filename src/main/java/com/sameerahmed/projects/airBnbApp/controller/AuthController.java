package com.sameerahmed.projects.airBnbApp.controller;

import com.sameerahmed.projects.airBnbApp.dto.LoginDto;
import com.sameerahmed.projects.airBnbApp.dto.LoginResponseDto;
import com.sameerahmed.projects.airBnbApp.dto.SignUpRequestDto;
import com.sameerahmed.projects.airBnbApp.dto.UserDto;
import com.sameerahmed.projects.airBnbApp.security.AuthService;
import com.sameerahmed.projects.airBnbApp.security.JWTService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth")
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    private final AuthService authService;
    private final JWTService jwtService;

    /**
     * Browsers treat localhost as a secure context, so this can stay true for
     * local development. Set it false only when serving the API over plain HTTP
     * from a non-localhost host — and prefer fixing the transport instead.
     */
    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

    @PostMapping("/signup")
    public ResponseEntity<UserDto> signup(@Valid @RequestBody SignUpRequestDto signUpRequestDto) {
        return new ResponseEntity<>(authService.signUp(signUpRequestDto), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginDto loginDto,
                                                  HttpServletRequest httpServletRequest,
                                                  HttpServletResponse httpServletResponse) {
        String[] tokens = authService.login(loginDto);

        Cookie cookie = refreshTokenCookie(httpServletRequest, tokens[1]);
        cookie.setMaxAge((int) jwtService.getRefreshTokenValidity().toSeconds());
        httpServletResponse.addCookie(cookie);

        return ResponseEntity.ok(new LoginResponseDto(tokens[0]));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDto> refresh(HttpServletRequest request) {
        String refreshToken = readRefreshToken(request)
                .orElseThrow(() -> new AuthenticationServiceException("Refresh token not found inside the cookies"));

        String accessToken = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(new LoginResponseDto(accessToken));
    }

    /**
     * Clears the refresh cookie. Because the tokens are stateless there is no
     * server-side record to revoke, so this is what makes logging out mean
     * anything at all — without it the cookie outlives the session and the next
     * 401 silently mints a new access token.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        Cookie cookie = refreshTokenCookie(request, "");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ResponseEntity.noContent().build();
    }

    private Optional<String> readRefreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> REFRESH_TOKEN_COOKIE.equals(cookie.getName()))
                .findFirst()
                .map(Cookie::getValue);
    }

    /**
     * Scoped to the auth endpoints so the credential is not attached to every
     * other API call. The clearing cookie in logout must match these attributes
     * exactly, or the browser keeps the original.
     */
    private Cookie refreshTokenCookie(HttpServletRequest request, String value) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath(request.getContextPath() + "/auth");
        cookie.setAttribute("SameSite", "Lax");
        return cookie;
    }
}
