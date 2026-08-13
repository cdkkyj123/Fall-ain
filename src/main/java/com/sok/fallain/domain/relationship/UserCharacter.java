package com.sok.fallain.domain.relationship;

import com.sok.fallain.common.entity.BaseEntity;
import com.sok.fallain.domain.character.Character;
import com.sok.fallain.domain.player.Player;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_character", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"player_id", "character_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCharacter extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;

    @Column(nullable = false)
    private Integer intimacy;

    @Column(nullable = false)
    private Integer currentDay;

    @Column(nullable = false)
    private Integer turnsUsedToday;

    @Column(nullable = false)
    private Boolean pendingTurn;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DayState dayState;

    @Column(nullable = false)
    private Integer lastTouchedDay;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private RelationshipStatus status;

    @Version
    private Long version;
}
