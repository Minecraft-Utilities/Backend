package xyz.mcutils.backend.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.mcutils.backend.model.domain.IpLookup;
import xyz.mcutils.backend.service.MaxMindService;

@RestController
@RequestMapping(value = "/ips")
@Tag(name = "IP Controller", description = "The IP Controller is used to get information about an IP address.")
public class IpController {

    private final MaxMindService maxMindService;

    public IpController(MaxMindService maxMindService) {
        this.maxMindService = maxMindService;
    }

    @GetMapping(value = "/{query}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IpLookup> getIpLookup(
            @Parameter(
                    description = "The IP address to lookup",
                    example = "127.0.0.1"
            ) @PathVariable String query
    ) {
        return ResponseEntity.ok()
                .body(maxMindService.lookupIp(query));
    }
}
