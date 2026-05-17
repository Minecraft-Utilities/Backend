package xyz.mcutils.backend.model.token.turnstile;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class TurnstileResponse {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("challenge_ts")
    private String challengeTs;

    @JsonProperty("hostname")
    private String hostname;

    @JsonProperty("error-codes")
    private List<String> errorCodes;

    @JsonProperty("action")
    private String action;

    @JsonProperty("cdata")
    private String cdata;
}