package pluto.upik.shared.ai.data.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIRequestDTO {
    private UUID voteId;
    private String voteCategory;
}