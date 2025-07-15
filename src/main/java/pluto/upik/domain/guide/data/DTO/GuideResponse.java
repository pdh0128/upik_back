package pluto.upik.domain.guide.data.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 가이드 정보를 클라이언트에 전달하기 위한 DTO 클래스
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class GuideResponse {
    /**
     * 가이드 ID
     */
    private UUID id;
    
    /**
     * 가이드 제목
     */
    private String title;
    
    /**
     * 가이드 내용
     */
    private String content;
    
    /**
     * 가이드 생성 일자
     */
    private LocalDate createdAt;
    
    /**
     * 좋아요 수
     */
    private Integer like;  // likeCount에서 like로 변경, Long에서 Integer로 변경
    
    /**
     * 관련 투표 ID
     */
    private UUID voteId;
    
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
}