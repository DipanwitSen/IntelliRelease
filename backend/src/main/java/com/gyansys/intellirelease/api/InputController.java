package com.gyansys.intellirelease.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Format-sniffing for arbitrary pasted or uploaded content (JSON, XML, IDoc,
 * CSV, ...). Not built yet — see {@code PayloadController} for the same gap
 * on the structured payload side.
 */
@RestController
@RequestMapping("/api/v1/inputs")
@Tag(name = "Input Inspection", description = "Format sniffing for pasted/uploaded content — not built yet")
public class InputController {

    @PostMapping("/inspect")
    @Operation(summary = "Not implemented — the format sniffer is not built yet")
    public ResponseEntity<Object> inspect(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
