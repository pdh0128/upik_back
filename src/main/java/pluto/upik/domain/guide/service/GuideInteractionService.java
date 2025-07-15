package pluto.upik.domain.guide.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import pluto.upik.domain.guide.data.model.Guide;
import pluto.upik.domain.guide.data.model.GuideAndUser;
import pluto.upik.domain.guide.data.model.GuideAndUserId;
import pluto.upik.domain.guide.repository.GuideAndUserRepository;
import pluto.upik.domain.guide.repository.GuideRepository;
import pluto.upik.domain.report.data.model.Report;
import pluto.upik.domain.report.repository.ReportRepository;
import pluto.upik.domain.user.repository.UserRepository;
import pluto.upik.shared.exception.BusinessException;
import pluto.upik.shared.exception.ResourceNotFoundException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 가이드 상호작용(좋아요, 신고 등) 관련 비즈니스 로직을 처리하는 서비스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GuideInteractionService implements GuideInteractionServiceInterface {

    private final GuideRepository guideRepository;
    private final ReportRepository reportRepository;
    private final GuideAndUserRepository guideAndUserRepository;
    private final UserRepository userRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean toggleLikeGuide(UUID userId, UUID guideId) {
        log.info("가이드 좋아요 토글 요청 시작 - userId: {}, guideId: {}", userId, guideId);
        
        // 사용자 존재 확인
        if (!userRepository.existsById(userId)) {
            log.warn("가이드 좋아요 토글 실패 - 사용자 없음 (userId: {})", userId);
            throw new ResourceNotFoundException("User not found: " + userId);
        }
        
        // 가이드 존재 확인
        if (!guideRepository.existsById(guideId)) {
            log.warn("가이드 좋아요 토글 실패 - 가이드 없음 (guideId: {})", guideId);
            throw new ResourceNotFoundException("Guide not found: " + guideId);
        }

        GuideAndUserId id = new GuideAndUserId(userId, guideId);
        try {
            // 이미 좋아요 했는지 확인
            if (guideAndUserRepository.existsById(id)) {
                // 좋아요 취소
                guideAndUserRepository.deleteById(id);
                guideRepository.decrementLikeCount(guideId);
                log.info("가이드 좋아요 취소 완료 - userId: {}, guideId: {}", userId, guideId);
                return false;
            } else {
                // 좋아요 추가 - Builder 패턴 사용
                GuideAndUser entity = GuideAndUser.builder()
                    .id(id)
                        .build();
                guideAndUserRepository.save(entity);
                guideRepository.incrementLikeCount(guideId);
                log.info("가이드 좋아요 추가 완료 - userId: {}, guideId: {}", userId, guideId);
                return true;
            }
        } catch (DataIntegrityViolationException e) {
            log.error("가이드 좋아요 토글 중 데이터 무결성 위반 - userId: {}, guideId: {}, error: {}", userId, guideId, e.getMessage(), e);
            throw new BusinessException("Data integrity violation: " + e.getMessage());
        } catch (Exception e) {
            log.error("가이드 좋아요 토글 중 알 수 없는 오류 - userId: {}, guideId: {}, error: {}", userId, guideId, e.getMessage(), e);
            throw new BusinessException("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean toggleReportAndRevote(UUID guideId, UUID userId, String reason) {
        log.info("가이드 재투표 신고 토글 요청 시작 - userId: {}, guideId: {}, reason: {}", userId, guideId, reason);

        // 사용자 존재 확인
        if (!userRepository.existsById(userId)) {
            log.warn("가이드 재투표 신고 토글 실패 - 사용자 없음 (userId: {})", userId);
            throw new ResourceNotFoundException("User not found: " + userId);
        }

        // 가이드 존재 확인
        if (!guideRepository.existsById(guideId)) {
            log.warn("가이드 재투표 신고 토글 실패 - 가이드 없음 (guideId: {})", guideId);
            throw new ResourceNotFoundException("Guide not found: " + guideId);
        }

        try {
            // 이미 신고했는지 확인
            boolean exists = reportRepository.existsByUserIdAndTargetId(userId, guideId);
            if (exists) {
                // 신고 취소
                reportRepository.deleteByUserIdAndTargetId(userId, guideId);
                guideRepository.decrementRevoteCount(guideId);
                log.info("가이드 재투표 신고 취소 완료 - userId: {}, guideId: {}", userId, guideId);
                return false;
            } else {
                // 신고 추가 - Builder 패턴 사용
                Report report = Report.builder()
                        .userId(userId)
                        .targetId(guideId)
                        .reason(reason)
                        .createdAt(LocalDate.now())
                        .build();
                reportRepository.save(report);
                guideRepository.incrementRevoteCount(guideId);
                log.info("가이드 재투표 신고 추가 완료 - userId: {}, guideId: {}, reason: {}", userId, guideId, reason);
                return true;
            }
        } catch (DataIntegrityViolationException e) {
            log.error("가이드 재투표 신고 토글 중 데이터 무결성 위반 - userId: {}, guideId: {}, reason: {}, error: {}",
                    userId, guideId, reason, e.getMessage(), e);
            throw new BusinessException("데이터 무결성 위반: " + e.getMessage());
        } catch (Exception e) {
            log.error("가이드 재투표 신고 토글 중 알 수 없는 오류 - userId: {}, guideId: {}, reason: {}, error: {}",
                    userId, guideId, reason, e.getMessage(), e);
            throw new BusinessException("알 수 없는 오류가 발생했습니다.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Guide> getUserCreatedGuides(UUID userId) {
        log.info("사용자가 생성한 가이드 조회 요청 시작 - userId: {}", userId);
        
        // 사용자 존재 확인
        if (!userRepository.existsById(userId)) {
            log.warn("사용자가 생성한 가이드 조회 실패 - 사용자 없음 (userId: {})", userId);
            throw new ResourceNotFoundException("User not found: " + userId);
        }
        
        try {
            // 사용자가 생성한 가이드 조회
            List<Guide> userGuides = guideRepository.findGuidesByUserId(userId);
            log.info("사용자가 생성한 가이드 조회 완료 - userId: {}, 가이드 수: {}", userId, userGuides.size());
            return userGuides;
        } catch (Exception e) {
            log.error("사용자가 생성한 가이드 조회 중 오류 발생 - userId: {}, error: {}", userId, e.getMessage(), e);
            throw new BusinessException("사용자가 생성한 가이드 조회 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
}