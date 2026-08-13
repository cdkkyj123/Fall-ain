package com.sok.fallain.domain.conversation;

import com.sok.fallain.common.entity.BaseEntity;
import com.sok.fallain.domain.relationship.UserCharacter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "day_summary")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DaySummary extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_character_id", nullable = false)
    private UserCharacter userCharacter;

    @Column(nullable = false)
    private Integer day;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summaryText;

    @Column(nullable = false)
    private Integer intimacyDelta;

    @Column(nullable = true)
    @Enumerated(EnumType.STRING)
    private DaySummaryClosedReason closedReason;
}
