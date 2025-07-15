package pluto.upik.shared.ai.resolver;

import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;
import pluto.upik.shared.ai.data.DTO.GuideResponseDTO;
import pluto.upik.shared.ai.service.AIService;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class AIMutationResolver {

    private final AIService aiService;

    @MutationMapping
    public GuideResponseDTO generateAIGuide(@Argument UUID voteId, @Argument String voteCategory) {
        return aiService.generateAndSaveGuide(voteId, voteCategory);
    }
}