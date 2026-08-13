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

    /**
     * 되짚기(callback) 성공: DROPPED -> RECALLED 전이.
     */
    public void recall() {
        this.state = MemoryCandidateState.RECALLED;
    }

    /**
     * (재)드랍: state를 DROPPED로, droppedOnDay를 갱신한다. 기존 RECALLED/EXPIRED 후보가
     * 같은 factKey로 다시 드랍되는 경우에도 unique(user_character_id, fact_key) 제약을 지키기
     * 위해 신규 삽입 대신 이 메서드로 기존 레코드를 갱신한다.
     */
    public void markDropped(Integer day) {
        this.state = MemoryCandidateState.DROPPED;
        this.droppedOnDay = day;
    }
}
