package pluto.upik.shared.oauth2jwt.dto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import pluto.upik.shared.oauth2jwt.entity.User;
import pluto.upik.shared.oauth2jwt.repository.UserRepository;

import java.util.*;

@Slf4j
public class CustomOAuth2User implements OAuth2User {

    private final UserDTO userDTO;
    private final UserRepository userRepository;
    private final User user;

    // JWT 토큰용 생성자
    public CustomOAuth2User(UserDTO userDTO, UserRepository userRepository) {
        this.userDTO = userDTO;
        this.userRepository = userRepository;
        // JWT에서는 user 객체를 나중에 조회
        this.user = findUserByUsername(userDTO.getUsername());
    }

    private User findUserByUsername(String username) {
        try {
            return userRepository.findByUsername(username).orElse(null);
        } catch (Exception e) {
            log.warn("Failed to find user by username: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public Map<String, Object> getAttributes() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("username", userDTO.getUsername());
        attributes.put("name", userDTO.getName());
        attributes.put("role", userDTO.getRole());
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        // ★★★ ROLE_ 접두사 추가 ★★★
        authorities.add(new SimpleGrantedAuthority(userDTO.getRole()));
        return authorities;
    }

    @Override
    public String getName() {
        return userDTO.getName();
    }

    public String getUsername() {
        return userDTO.getUsername();
    }

    public String getRole() {
        return userDTO.getRole();
    }

    public Optional<User> getUser() {
        if (user != null) {
            return Optional.of(user);
        }
        return userRepository.findByUsername(userDTO.getUsername());
    }
}