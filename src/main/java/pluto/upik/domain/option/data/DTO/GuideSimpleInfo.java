package pluto.upik.domain.option.data.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 간단한 가이드 정보 DTO
 * 가이드의 기본 정보와 작성자 정보를 포함하는 간략화된 DTO입니다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuideSimpleInfo {
    
    /**
     * 가이드 ID
     */
    private UUID id;
    
    /**
     * 가이드 제목
     */
    private String title;
    
    /**
     * 가이드 요약 내용
     */
    private String summary;

    /**
     * 가이드 타입
     */
    private String guideType;

    /**
     * 작성자 ID
     */
    private UUID userId;

    /**
     * 작성자 이름
     */
    private String userName;

    /**
     * 작성자 프로필 이미지 URL
     */
    private String userProfileImage;

    /**
     * 가이드 생성 일자
     */
    private LocalDate createdAt;

    /**
     * 가이드 카테고리
     */
    private String category;

    /**
     * 좋아요 수
     */
    private Long likeCount;

    /**
     * 재투표 수
     */
    private Long revoteCount;
}