package pluto.upik.domain.guide.data.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 키워드 검색 결과 가이드 정보를 클라이언트에 전달하기 위한 DTO 클래스
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class KeywordGuideResponse {
    /**
     * 가이드 ID
     */
    private UUID id;
    
    /**
     * 가이드 제목
     */
    private String title;
    
    /**
     * 검색 키워드
     */
    private String keyword;
    
    /**
     * 가이드 내용
     */
    private String content;
    
    /**
     * 가이드 생성 일자
     */
    private LocalDate createdAt;
    
    /**
     * 가이드 타입
     */
    private String guideType;

    /**
     * 카테고리
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
     * 작성자 이메일
     */
    private String userEmail;

    /**
     * 가이드 내용의 요약된 문자열을 반환합니다.
     *
     * @param maxLength 최대 길이
     * @return 요약된 내용
     */
    public String getContentSummary(int maxLength) {
        if (content == null || content.length() <= maxLength) {
            return content;
}
        return content.substring(0, maxLength) + "...";
    }

    /**
     * 키워드가 포함된 제목에서 키워드를 하이라이트한 문자열을 반환합니다.
     *
     * @return 하이라이트된 제목
     */
    public String getHighlightedTitle() {
        if (title == null || keyword == null || keyword.isEmpty()) {
            return title;
}
        return title.replaceAll("(?i)" + keyword, "<strong>" + keyword + "</strong>");
    }
}
