package org.example.weekttwelveassignment;


import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    private LocalDateTime expiresAt;

    protected RefreshToken() { }

    public RefreshToken(String token, AppUser user, LocalDateTime expiresAt) {
        this.token = token;
        this.user = user;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public Long getId() { return id; }
    public String getToken() { return token; }
    public AppUser getUser() { return user; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}