package pluto.upik.shared.oauth2jwt.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pluto.upik.shared.oauth2jwt.dto.CustomOAuth2User;
import pluto.upik.shared.oauth2jwt.dto.UserDTO;
import pluto.upik.shared.oauth2jwt.repository.RefreshTokenRepository;
import pluto.upik.shared.oauth2jwt.repository.UserRepository;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JWTFilter extends OncePerRequestFilter {

    private final JWTUtil jwtUtil;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        // ★★★ 1. 스킵할 경로 체크 ★★★
        String requestURI = request.getRequestURI();
        if (shouldSkipFilter(requestURI)) {
            filterChain.doFilter(request, response);
            return;
        }

        // ★★★ 2. Access Token 추출 (헤더 우선, 쿠키 보조) ★★★
        String accessToken = extractAccessToken(request);

        // ★★★ 3. Access Token 처리 ★★★
        if (accessToken != null) {
            if (jwtUtil.isExpired(accessToken)) {
                // 만료된 경우 Refresh Token으로 갱신 시도
                if (handleExpiredAccessToken(request, response)) {
                    log.debug("Access token refreshed successfully for: {}", requestURI);
                } else {
                    log.debug("Failed to refresh access token for: {}", requestURI);
                }
            } else {
                // 유효한 토큰으로 인증 설정
                if (validateAndSetAuthentication(accessToken)) {
                    log.debug("Authentication set successfully for: {}", requestURI);
                }
            }
        } else {
            log.debug("No access token found for: {}", requestURI);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * ★★★ Access Token 추출 (헤더 우선, 쿠키 보조) ★★★
     */
    private String extractAccessToken(HttpServletRequest request) {
        // 1. Authorization 헤더에서 먼저 확인
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }

        // 2. 헤더에 없으면 쿠키에서 확인
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("Authorization".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }

    /**
     * ★★★ JWT 검증을 스킵할 경로들 (인증이 불필요한 경로만!) ★★★
     */
    private boolean shouldSkipFilter(String requestURI) {
        return requestURI.startsWith("/oauth2/") ||
                requestURI.startsWith("/login/") ||
                requestURI.equals("/auth/reissue") ||
                requestURI.startsWith("/static/") ||
                requestURI.startsWith("/css/") ||
                requestURI.startsWith("/js/") ||
                requestURI.startsWith("/images/") ||
                requestURI.equals("/favicon.ico") ||
                requestURI.equals("/error") ||
                requestURI.equals("/api/my");
    }

    /**
     * ★★★ 만료된 Access Token 처리 ★★★
     */
    private boolean handleExpiredAccessToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractRefreshTokenFromCookie(request);

        if (refreshToken == null || jwtUtil.isExpired(refreshToken)) {
            return false;
        }

        if (!"refresh".equals(jwtUtil.getCategory(refreshToken))) {
            return false;
        }

        if (!refreshTokenRepository.existsByToken(refreshToken)) {
            return false;
        }

        try {
            String username = jwtUtil.getUsername(refreshToken);
            String role = jwtUtil.getRole(refreshToken);
            String newAccessToken = jwtUtil.createJwt("access", username, role, 900000L); // 15분

            // ✅ JavaScript에서 접근 가능한 쿠키로 설정
            ResponseCookie cookie = ResponseCookie.from("Authorization", newAccessToken)
                    .path("/")
                    .maxAge(15 * 60) // 15분
                    .httpOnly(false) // ✅ JavaScript 접근 허용
                    .secure(false) // 개발환경
                    .sameSite("Lax")
                    .build();

            response.addHeader("Set-Cookie", cookie.toString());

            return validateAndSetAuthentication(newAccessToken);

        } catch (Exception e) {
            log.warn("Failed to refresh access token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * ★★★ Refresh Token 쿠키 추출 ★★★
     */
    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("refreshToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * ★★★ 토큰 검증 및 인증 설정 ★★★
     */
    private boolean validateAndSetAuthentication(String accessToken) {
        try {
            if (!"access".equals(jwtUtil.getCategory(accessToken))) {
                return false;
            }

            String username = jwtUtil.getUsername(accessToken);
            String role = jwtUtil.getRole(accessToken);

            if (username == null || role == null) {
                return false;
            }

            // 삭제된 사용자 체크
            if ("ROLE_DELETED".equals(role)) {
                log.warn("Deleted user attempted to access: {}", username);
                return false;
            }

            // DB에서 name 조회
            String name = username;
            try {
                Optional<String> nameOpt = userRepository.findNameByUsername(username);
                if (nameOpt.isPresent() && !nameOpt.get().trim().isEmpty()) {
                    name = nameOpt.get();
                }
            } catch (Exception e) {
                log.warn("Failed to fetch name from DB: {}", e.getMessage());
            }

            UserDTO userDTO = UserDTO.builder()
                    .username(username)
                    .name(name)
                    .role(role)
                    .build();

            CustomOAuth2User customOAuth2User = new CustomOAuth2User(userDTO, userRepository);
            Authentication authToken = new UsernamePasswordAuthenticationToken(
                    customOAuth2User, null, customOAuth2User.getAuthorities());

            SecurityContextHolder.getContext().setAuthentication(authToken);
            return true;

        } catch (Exception e) {
            log.warn("Token validation error: {}", e.getMessage());
            return false;
        }
    }
}