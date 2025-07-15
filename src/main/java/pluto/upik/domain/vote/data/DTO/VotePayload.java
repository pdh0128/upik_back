package pluto.upik.domain.vote.data.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pluto.upik.domain.option.data.model.Option;
import pluto.upik.domain.vote.data.model.Vote;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VotePayload {
    private UUID id;
    private String title;  // 스키마와 일치하도록 question -> title로 변경
    private String category;
    private String finishedAt;
    private String status;
    private int totalResponses;
    private List<OptionWithStatsPayload> options;
    private boolean hasVoted; // 사용자가 투표에 참여했는지 여부

    // 정적 팩토리 메서드 (옵션 통계 없이)
    public static VotePayload fromEntity(Vote vote, List<Option> options) {
        return VotePayload.builder()
                .id(vote.getId())
                .title(vote.getQuestion())  // question 필드를 title로 매핑
                .category(vote.getCategory())
                .finishedAt(vote.getFinishedAt().format(DateTimeFormatter.ISO_LOCAL_DATE))
                .status(vote.getStatus().name())
                .totalResponses(0) // 기본값
                .options(options.stream()
                        .map(option -> new OptionWithStatsPayload(option.getId(), option.getContent(), 0, 0))
                        .toList())
                .hasVoted(false) // 기본값
                .build();
    }

    // 정적 팩토리 메서드 (옵션 통계 포함)
    public static VotePayload fromEntityWithStats(Vote vote, List<Option> options,
                                                 List<OptionWithStatsPayload> optionStats, int totalResponses) {
        return fromEntityWithStats(vote, options, optionStats, totalResponses, false);
    }
    
    // 정적 팩토리 메서드 (옵션 통계와 투표 여부 포함)
    public static VotePayload fromEntityWithStats(Vote vote, List<Option> options,
                                                 List<OptionWithStatsPayload> optionStats, int totalResponses,
                                                 boolean hasVoted) {
        return VotePayload.builder()
                .id(vote.getId())
                .title(vote.getQuestion())
                .category(vote.getCategory())
                .finishedAt(vote.getFinishedAt().format(DateTimeFormatter.ISO_LOCAL_DATE))
                .status(vote.getStatus().name())
                .totalResponses(totalResponses)
                .options(optionStats)
                .hasVoted(hasVoted)
                .build();
    }
}