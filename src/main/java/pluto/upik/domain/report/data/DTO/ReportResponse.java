package pluto.upik.domain.report.data.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 신고 정보를 클라이언트에 전달하기 위한 DTO 클래스
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportResponse {
    /**
     * 신고한 사용자의 ID
     */
    private UUID userId;
    
    /**
     * 신고 대상의 ID
     */
    private UUID targetId;
    
    /**
     * 신고 사유
     */
    private String reason;

    /**
     * 신고 대상 타입 (guide 또는 vote)
     */
    private String targetType;

    /**
     * 신고 생성 일자
     */
    private LocalDate createdAt;

    /**
     * 신고 대상의 제목 (가이드 제목 또는 투표 질문)
     */
    private String targetTitle;

    /**
     * 신고 대상의 작성자 ID
     */
    private String authorId;

    /**
     * 신고 대상의 작성자 이름
     */
    private String authorName;

    /**
     * 신고 대상의 작성자 프로필 이미지
     */
    private String authorProfileImage;

    /**
     * 신고 대상의 카테고리
     */
    private String category;

    /**
     * 가이드 타입 (targetType이 guide인 경우)
     */
    private String guideType;

    /**
     * 좋아요 수 (targetType이 guide인 경우)
     */
    private Long likeCount;

    /**
     * 재투표 수 (targetType이 guide인 경우)
     */
    private Long revoteCount;
    /**
     * 대상 생성 일자
     */
    private LocalDate targetCreatedAt;

    /**
     * 가이드 내용 또는 투표 옵션들 (targetType에 따라 다름)
     */
    private String content;

    /**
     * 투표 상태 (targetType이 vote인 경우)
     */
    private String status;

    /**
     * DTO의 문자열 표현을 반환하는 메서드
     * 민감한 정보는 마스킹 처리합니다.
     *
     * @return DTO의 문자열 표현
     */
    @Override
    public String toString() {
        return "ReportResponse{" +
                "userId=" + userId +
                ", targetId=" + targetId +
                ", reason='" + (reason != null ? reason.substring(0, Math.min(10, reason.length())) + "..." : null) + '\'' +
                ", targetType='" + targetType + '\'' +
                ", targetTitle='" + (targetTitle != null ? targetTitle.substring(0, Math.min(20, targetTitle.length())) + "..." : null) + '\'' +
                ", authorId='" + authorId + '\'' +
                ", authorName='" + authorName + '\'' +
                ", category='" + category + '\'' +
                ", guideType='" + guideType + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}