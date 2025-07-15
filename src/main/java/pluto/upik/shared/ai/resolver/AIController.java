package pluto.upik.shared.ai.resolver;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pluto.upik.domain.vote.repository.VoteRepository;
import pluto.upik.shared.ai.data.DTO.GuideResponseDTO;
import pluto.upik.shared.ai.service.AIService;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AIController {

    private final AIService aiService;
    private final VoteRepository voteRepository;

    @PostMapping("/AI")
    public GuideResponseDTO ai(@RequestBody UUID vote_id, String vote_category) {
        return aiService.generateAndSaveGuide(vote_id,vote_category);
    }
}
