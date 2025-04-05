package xyz.mcutils.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import xyz.mcutils.backend.model.player.Cape;
import xyz.mcutils.backend.model.response.CapesResponse;
import xyz.mcutils.backend.service.CapeService;

@Controller
@RequestMapping(value = "/capes")
public class CapesController {
    private final CapeService capeService;

    @Autowired
    public CapesController(CapeService capeService) {
        this.capeService = capeService;
    }

    @GetMapping
    public ResponseEntity<CapesResponse> getCapes() {
        return ResponseEntity.ok(new CapesResponse(capeService.getAllCapes().toArray(Cape[]::new)));
    }
}
