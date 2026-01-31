package xyz.mcutils.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import xyz.mcutils.backend.model.response.HealthResponse;

@Controller
@RequestMapping(value = "/")
public class HealthController {

    @GetMapping(value = "/health")
    public ResponseEntity<HealthResponse> home() {
        return ResponseEntity.ok(new HealthResponse("OK"));
    }
}
