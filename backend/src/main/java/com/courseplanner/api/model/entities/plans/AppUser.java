package com.courseplanner.api.model.entities.plans;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "app_user")
@Getter @Setter @NoArgsConstructor
public class AppUser implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String email;
    private String displayName;

    @Column(unique = true)
    private String casId;

    @Column
    private Instant lastLoginAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private transient List<CoursePlan> plans = new ArrayList<>();

    public static AppUser fromCas(String casId) {
        return fromCas(casId, "example.edu");
    }

    public static AppUser fromCas(String casId, String emailDomain) {
        AppUser u = new AppUser();
        u.casId = casId;
        u.email = casId + "@" + emailDomain;
        u.displayName = casId;
        u.createdAt = Instant.now();
        return u;
    }
}
