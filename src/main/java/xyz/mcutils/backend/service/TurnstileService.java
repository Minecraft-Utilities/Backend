package xyz.mcutils.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import xyz.mcutils.backend.common.WebRequest;
import xyz.mcutils.backend.model.token.turnstile.TurnstileResponse;

@Service
public class TurnstileService {
    private static final String API_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final WebRequest webRequest;

    @Value("${mc-utils.turnstile-key}")
    private String secretKey;

    public TurnstileService(WebRequest webRequest) {
        this.webRequest = webRequest;
    }

    public TurnstileResponse validateToken(String token, String remoteIp) {
        if (secretKey == null) {
            throw new IllegalStateException("Turnstile secret key has not been set");
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("secret", secretKey);
        params.add("response", token);
        if (remoteIp != null) {
            params.add("remoteip", remoteIp);
        }
        ResponseEntity<TurnstileResponse> response = webRequest.request(API_URL).post(params).asResponse(TurnstileResponse.class);
        return response.getBody();
    }
}
