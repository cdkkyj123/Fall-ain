package com.sok.fallain.domain.character;

import com.sok.fallain.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "persona_fact", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"character_id", "fact_key"})
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonaFact extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;

    @Column(nullable = false)
    private String factKey;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PersonaFactCategory category;

    @Column(nullable = false)
    private Integer tier;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private Integer unlockDayFrom;

    @Column(nullable = false)
    private Integer unlockDayTo;
}
