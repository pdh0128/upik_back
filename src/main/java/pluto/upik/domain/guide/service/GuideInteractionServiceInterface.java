package pluto.upik.domain.guide.service;

import pluto.upik.domain.guide.data.model.Guide;

import java.util.List;
import java.util.UUID;

/**
 * 가이드 상호작용(좋아요, 신고 등) 관련 비즈니스 로직을 처리하는 서비스 인터페이스
 */
public interface GuideInteractionServiceInterface {

    /**
     * 특정 사용자가 특정 가이드에 좋아요를 토글합니다.
     * 이미 좋아요 되어있으면 좋아요 취소(삭제) 후 카운트 감소
     * 좋아요 없으면 저장 후 카운트 증가
     *
     * @param userId 사용자 ID
     * @param guideId 가이드 ID
     * @return 좋아요가 추가되었으면 true, 취소되었으면 false
     * @throws pluto.upik.shared.exception.ResourceNotFoundException 사용자나 가이드가 존재하지 않을 경우 발생
     * @throws pluto.upik.shared.exception.BusinessException 처리 중 오류 발생 시
     */
    boolean toggleLikeGuide(UUID userId, UUID guideId);

    /**
     * 특정 사용자가 특정 가이드에 대해 재투표 신고를 토글합니다.
     * 이미 신고 되어있으면 신고 취소(삭제) 후 revote count 감소
     * 신고 안되어 있으면 저장 후 revote count 증가
     *
     * @param guideId 가이드 ID
     * @param userId 사용자 ID
     * @param reason 신고 사유
     * @return 신고가 추가되었으면 true, 취소되었으면 false
     * @throws pluto.upik.shared.exception.ResourceNotFoundException 사용자나 가이드가 존재하지 않을 경우 발생
     * @throws pluto.upik.shared.exception.BusinessException 처리 중 오류 발생 시
     */
    boolean toggleReportAndRevote(UUID guideId, UUID userId, String reason);
    
    /**
     * 특정 사용자가 생성한 가이드 목록을 조회합니다.
     *
     * @param userId 사용자 ID
     * @return 사용자가 생성한 가이드 목록
     * @throws pluto.upik.shared.exception.ResourceNotFoundException 사용자가 존재하지 않을 경우 발생
     * @throws pluto.upik.shared.exception.BusinessException 처리 중 오류 발생 시
     */
    List<Guide> getUserCreatedGuides(UUID userId);
}