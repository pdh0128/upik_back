package pluto.upik.domain.vote.resolver;

import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import pluto.upik.domain.vote.data.DTO.VoteDetailPayload;
import pluto.upik.domain.vote.data.DTO.VotePayload;
import pluto.upik.domain.vote.service.VoteServiceUpdated;

import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class VoteQueryResolverUpdated {

    private final VoteServiceUpdated voteService;
    
    // 더미 사용자 ID
    private static final UUID DUMMY_USER_ID = UUID.fromString("e49207e8-471a-11f0-937c-42010a800003");

    @SchemaMapping(typeName = "VoteQuery", field = "getAllVotes")
    public List<VotePayload> getAllVotes() {
        // 목 데이터로 더미 사용자 ID 사용
        return voteService.getAllVotes(DUMMY_USER_ID);
    }

    @SchemaMapping(typeName = "VoteQuery", field = "getVoteById")
    public VoteDetailPayload getVoteById(@Argument String id) {
        // 목 데이터로 더미 사용자 ID 사용
        return voteService.getVoteById(UUID.fromString(id), DUMMY_USER_ID);
    }

    @SchemaMapping(typeName = "VoteQuery", field = "getMostPopularOpenVote")
    public List<VotePayload> getMostPopularOpenVote() {
        // 인기 있는 투표 3개를 반환
        return voteService.getMostPopularOpenVote();
    }

    @SchemaMapping(typeName = "VoteQuery", field = "getLeastPopularOpenVote")
    public VotePayload getLeastPopularOpenVote() {
        // 항상 투표하지 않은 것으로 표시 (VoteService에서 처리)
        return voteService.getLeastPopularOpenVote();
    }
    
    /**
     * 현재 사용자가 생성한 투표 목록을 조회합니다.
     * 더미 사용자 ID를 사용합니다.
     *
     * @return 현재 사용자가 생성한 투표 목록
     */
    @SchemaMapping(typeName = "VoteQuery", field = "getMyVotes")
    public List<VotePayload> getMyVotes() {
        // 더미 사용자 ID를 사용하여 사용자가 생성한 투표 목록 조회
        return voteService.getMyVotes();
    }
}