package com.senetontine.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// Un utilisateur au sein d'une tontine donnee (avec son role et son ordre de passage)
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"tontine_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TontineMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tontine_id")
    private Tontine tontine;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role = MemberRole.MEMBER;

    // position dans le tour de rotation (1, 2, 3...)
    private Integer turnOrder;


    @Column(nullable = false)
    private Instant joinedAt = Instant.now();
}
