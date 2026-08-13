package com.sok.fallain.domain.conversation;

import com.sok.fallain.common.entity.BaseEntity;
import com.sok.fallain.domain.relationship.UserCharacter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "message", indexes = {
        @Index(columnList = "user_character_id,day,turn_index")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_character_id", nullable = false)
    private UserCharacter userCharacter;

    @Column(nullable = false)
    private Integer day;

    @Column(nullable = false)
    private Integer turnIndex;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String droppedFactKeysJson;
}
