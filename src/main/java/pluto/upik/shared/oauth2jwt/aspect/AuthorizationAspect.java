package pluto.upik.shared.oauth2jwt.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import pluto.upik.shared.oauth2jwt.annotation.RequireAuth;
import pluto.upik.shared.oauth2jwt.annotation.RequireRole;
import pluto.upik.shared.oauth2jwt.dto.CustomOAuth2User;
import pluto.upik.shared.oauth2jwt.service.UserService;

import java.util.Map;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuthorizationAspect {

    private final UserService userService;

    @Around("@annotation(requireAuth)")
    public Object checkAuthentication(ProceedingJoinPoint joinPoint, RequireAuth requireAuth) throws Throwable {
        if (!userService.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", requireAuth.message(),
                    "code", "UNAUTHORIZED",
                    "isAuthenticated", false
            ));
        }

        return joinPoint.proceed();
    }

    @Around("@annotation(requireRole)")
    public Object checkRole(ProceedingJoinPoint joinPoint, RequireRole requireRole) throws Throwable {
        CustomOAuth2User currentUser = userService.getCurrentUser();

        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "로그인이 필요합니다",
                    "code", "UNAUTHORIZED",
                    "isAuthenticated", false
            ));
        }

        String requiredRole = "ROLE_" + requireRole.value().toUpperCase();
        if (!currentUser.getAuthorities().stream().anyMatch(auth -> auth.getAuthority().equals(requiredRole))) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", requireRole.message(),
                    "code", "ACCESS_DENIED",
                    "requiredRole", requireRole.value(),
                    "currentRole", currentUser.getRole().replace("ROLE_", ""),
                    "isAuthenticated", true
            ));
        }

        return joinPoint.proceed();
    }
}