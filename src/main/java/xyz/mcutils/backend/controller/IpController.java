package xyz.mcutils.backend.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import xyz.mcutils.backend.model.response.IpLookupResponse;
import xyz.mcutils.backend.service.MaxMindService;

@RestController
@RequestMapping(value = "/ip")
@Tag(name = "IP Controller", description = "The IP Controller is used to get information about an IP address.")
public class IpController {
    @GetMapping(value = "/{query}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IpLookupResponse> getIpLookup(@Parameter(description = "The IP address to lookup", example = "127.0.0.1") @PathVariable String query) {
        return ResponseEntity.ok().body(MaxMindService.lookupIp(query));
    }
}
