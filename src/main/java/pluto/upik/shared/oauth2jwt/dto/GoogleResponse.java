package pluto.upik.shared.oauth2jwt.dto;

import java.util.HashMap;
import java.util.Map;

public class GoogleResponse implements OAuth2Response{

    private final Map<String, Object> attribute;

    public GoogleResponse(Map<String, Object> attribute) {
        this.attribute = attribute != null ? attribute : new HashMap<>();
    }

    @Override
    public String getProvider() {

        return "google";
    }

    @Override
    public String getProviderId() {

        return attribute.get("sub") != null ? attribute.get("sub").toString() : "";
    }

    @Override
    public String getEmail() {

        return attribute.get("email") != null ? attribute.get("email").toString() : "";
    }

    @Override
    public String getName() {

        return attribute.get("name") != null ? attribute.get("name").toString() : "";
    }
}