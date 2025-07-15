package pluto.upik.domain.guide.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pluto.upik.domain.guide.data.DTO.GuideDetailResponse;
import pluto.upik.domain.guide.data.DTO.GuideResponse;
import pluto.upik.domain.guide.data.model.Guide;
import pluto.upik.domain.guide.repository.GuideRepository;
import pluto.upik.shared.exception.BusinessException;
import pluto.upik.shared.exception.ResourceNotFoundException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 가이드 조회 관련 비즈니스 로직을 처리하는 서비스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuideQueryService implements GuideQueryServiceInterface {

    private final GuideRepository guideRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<GuideResponse> findByCategory(String category) {
        log.info("findByCategory called with category: {}", category);

        try {
            List<Guide> guides = guideRepository.findAllByCategory(category);
            log.info("카테고리별 가이드 조회 결과 - category: {}, 조회된 가이드 수: {}", category, guides.size());

            if (guides == null || guides.isEmpty()) {
                log.warn("카테고리별 가이드 조회 실패 - 가이드 없음 (category: {})", category);
                throw new ResourceNotFoundException("카테고리에 해당하는 가이드가 없습니다: " + category);
            }

            List<GuideResponse> responses = guides.stream()
                    .map(guide -> GuideResponse.builder()
                            .id(guide.getId())
                            .title(guide.getTitle())
                            .content(guide.getContent())
                            .createdAt(guide.getCreatedAt())
                            .like(guide.getLike() != null ? guide.getLike().intValue() : 0) // likeCount → like로 변경 및 null 체크 추가
                            .voteId(guide.getVote() != null ? guide.getVote().getId() : null) // 투표 ID 추가
                            .build())
                    .collect(Collectors.toList());

            log.info("Number of guides found: {}", responses.size());
            return responses;
        } catch (ResourceNotFoundException e) {
            throw e; // 위에서 던진 거 그대로 전달
        } catch (Exception e) {
            log.error("가이드 조회 중 예외 발생 - category: {}, error: {}", category, e.getMessage(), e);
            throw new BusinessException("가이드 조회 중 오류가 발생했습니다.");
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public GuideDetailResponse findGuideById(UUID guideId) {
        log.info("가이드 상세 조회 요청 시작 - guideId: {}", guideId);
        
        try {
            Guide guide = guideRepository.findById(guideId)
                    .orElseThrow(() -> {
                        log.warn("가이드 상세 조회 실패 - 가이드 없음 (guideId: {})", guideId);
                        return new ResourceNotFoundException("해당 ID의 가이드를 찾을 수 없습니다: " + guideId);
                    });
            
            GuideDetailResponse response = GuideDetailResponse.builder()
                    .id(guide.getId())
                    .title(guide.getTitle())
                    .content(guide.getContent())
                    .createdAt(guide.getCreatedAt())
                    .category(guide.getCategory())
                    .guideType(guide.getGuideType())
                    .likeCount(guide.getLike() != null ? guide.getLike().intValue() : 0) // null 체크 추가
                    .revoteCount(guide.getRevoteCount() != null ? guide.getRevoteCount().intValue() : 0) // null 체크 추가
                    .voteId(guide.getVote() != null ? guide.getVote().getId() : null)
                    .build();
            
            log.info("가이드 상세 조회 완료 - guideId: {}", guideId);
            return response;
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("가이드 상세 조회 중 예외 발생 - guideId: {}, error: {}", guideId, e.getMessage(), e);
            throw new BusinessException("가이드 상세 조회 중 오류가 발생했습니다.");
        }
    }
}