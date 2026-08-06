package com.gyansys.intellirelease.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * End-to-end integration flows. Honestly empty — see {@link IntegrationController}:
 * no flow catalogue has been imported for this tenant yet.
 */
@RestController
@RequestMapping("/api/v1/flows")
@Tag(name = "Flows", description = "End-to-end integration flows — not yet populated")
public class FlowController {

    @GetMapping
    @Operation(summary = "Integration flows — empty until a flow catalogue is imported")
    public List<Object> list(@RequestParam(required = false) String q) {
        return List.of();
    }

    @GetMapping("/{id}")
    @Operation(summary = "One flow — not found until a flow catalogue is imported")
    public ResponseEntity<Object> get(@PathVariable String id) {
        return ResponseEntity.notFound().build();
    }
}
