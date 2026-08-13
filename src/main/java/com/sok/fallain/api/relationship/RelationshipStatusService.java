package com.sok.fallain.api.relationship;

import com.sok.fallain.api.relationship.dto.MemoryCandidateDto;
import com.sok.fallain.api.relationship.dto.RelationshipStatusResponse;
import com.sok.fallain.common.exception.BusinessException;
import com.sok.fallain.common.exception.ErrorCode;
import com.sok.fallain.domain.conversation.MemoryCandidate;
import com.sok.fallain.domain.conversation.MemoryCandidateRepository;
import com.sok.fallain.domain.conversation.MemoryCandidateState;
import com.sok.fallain.domain.relationship.Freshness;
import com.sok.fallain.domain.relationship.FreshnessCalculator;
import com.sok.fallain.domain.relationship.UserCharacter;
import com.sok.fallain.domain.relationship.UserCharacterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RelationshipStatusService {

    private static final int TURN_BUDGET = 8;

    private final UserCharacterRepository userCharacterRepository;
    private final MemoryCandidateRepository memoryCandidateRepository;

    /**
     * 관계 상태를 조회한다.
     *
     * @param ucId 사용자-캐릭터 ID
     * @return 관계 상태 응답 DTO
     * @throws BusinessException RELATIONSHIP_NOT_FOUND (404)
     */
    public RelationshipStatusResponse getRelationshipStatus(Long ucId) {
        UserCharacter userCharacter = userCharacterRepository.findById(ucId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RELATIONSHIP_NOT_FOUND));

        // stage 계산: intimacy 값에 따라
        String stage = calculateStage(userCharacter.getIntimacy());

        // turnsLeftToday 계산
        int turnsLeftToday = TURN_BUDGET - userCharacter.getTurnsUsedToday();

        // DROPPED 상태의 메모리 후보 조회
        List<MemoryCandidate> droppedCandidates = memoryCandidateRepository
                .findByUserCharacterIdAndState(ucId, MemoryCandidateState.DROPPED);

        // MemoryCandidateDto 목록 생성
        List<MemoryCandidateDto> memoryCandidateDtos = droppedCandidates.stream()
                .map(candidate -> {
                    Freshness freshness = FreshnessCalculator.calculate(
                            userCharacter.getCurrentDay(),
                            candidate.getDroppedOnDay()
                    );
                    return new MemoryCandidateDto(
                            candidate.getFactKey(),
                            freshness.name()
                    );
                })
                .toList();

        return new RelationshipStatusResponse(
                userCharacter.getId(),
                userCharacter.getCurrentDay(),
                userCharacter.getIntimacy(),
                stage,
                userCharacter.getTurnsUsedToday(),
                TURN_BUDGET,
                turnsLeftToday,
                userCharacter.getDayState().name(),
                memoryCandidateDtos
        );
    }

    /**
     * intimacy 값에 따라 stage를 계산한다.
     * STRANGER 0-149 / ACQUAINTED 150-399 / CLOSE 400-699 / TRUSTED 700-899 / LOVED 900+
     */
    private String calculateStage(Integer intimacy) {
        if (intimacy < 150) {
            return "STRANGER";
        } else if (intimacy < 400) {
            return "ACQUAINTED";
        } else if (intimacy < 700) {
            return "CLOSE";
        } else if (intimacy < 900) {
            return "TRUSTED";
        } else {
            return "LOVED";
        }
    }
}
