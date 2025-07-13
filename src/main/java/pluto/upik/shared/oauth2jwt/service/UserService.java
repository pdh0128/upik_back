package pluto.upik.shared.oauth2jwt.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import pluto.upik.shared.oauth2jwt.dto.CustomOAuth2User;

@Service
public class UserService {

    /**
     * 현재 인증된 사용자 정보를 가져오는 메서드
     */
    public CustomOAuth2User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            return (CustomOAuth2User) authentication.getPrincipal();
        }

        return null; // 인증되지 않은 사용자
    }

    /**
     * 현재 사용자가 인증되었는지 확인하는 메서드
     */
    public boolean isAuthenticated() {
        return getCurrentUser() != null;
    }
}