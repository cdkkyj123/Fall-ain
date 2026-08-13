package com.sok.fallain.domain.character;

import com.sok.fallain.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "character")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Character extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String persona;

    @Column(nullable = false)
    @Builder.Default
    private Integer arcLengthDays = 30;
}
