package pluto.upik.shared.oauth2jwt.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    private String role;

    private String username;

    private String name;

    private String email;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private double dollar;

    private double won;

    private long streakCount;

    @CreatedDate
    private LocalDateTime recentDate;

    @PrePersist
    public void generateId() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
    }

    @Builder
    public User(String role, String username, String name, String email){
        this.role = role;
        this.username = username;
        this.name = name;
        this.email = email;
        this.dollar = 0;
        this.won = 0;
        this.streakCount = 0;
    }
}