package pluto.upik.domain.vote.data.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoteDetailPayload {
    private UUID id;
    private String title;
    private String category;
    private String status;
    private String createdBy;
    private String finishedAt;
    private int totalResponses;
    private List<OptionWithStatsPayload> options;
    private boolean hasVoted; // 사용자가 투표에 참여했는지 여부
}