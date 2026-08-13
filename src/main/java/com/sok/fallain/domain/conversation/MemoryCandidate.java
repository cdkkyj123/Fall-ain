package com.sok.fallain.domain.conversation;

import com.sok.fallain.common.entity.BaseEntity;
import com.sok.fallain.domain.relationship.UserCharacter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "memory_candidate", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_character_id", "fact_key"})
}, indexes = {
        @Index(columnList = "user_character_id,state")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemoryCandidate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_character_id", nullable = false)
    private UserCharacter userCharacter;

    @Column(nullable = false)
    private String factKey;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private MemoryCandidateState state;

    @Column(nullable = false)
    private Integer droppedOnDay;
}
