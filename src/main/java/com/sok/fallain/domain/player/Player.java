package com.sok.fallain.domain.player;

import com.sok.fallain.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "player", uniqueConstraints = {
        @UniqueConstraint(columnNames = "player_id")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Player extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private UUID playerId;

    @Column(nullable = true)
    private String nickname;
}
