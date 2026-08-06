package com.gyansys.intellirelease.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Field-level mapping sets across integration hops. No mapping catalogue has
 * been imported for this tenant yet.
 */
@RestController
@RequestMapping("/api/v1/mappings")
@Tag(name = "Mappings", description = "Field-level mapping sets — not yet populated")
public class MappingController {

    @GetMapping
    @Operation(summary = "Mapping sets — empty until a mapping catalogue is imported")
    public PageResponse<Object> list(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size) {
        return PageResponse.of(List.of(), 0, page, size);
    }

    @GetMapping("/{setId}/links")
    @Operation(summary = "Mapping links — empty until a mapping catalogue is imported")
    public PageResponse<Object> links(@PathVariable String setId,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size) {
        return PageResponse.of(List.of(), 0, page, size);
    }

    @GetMapping("/{setId}/compare")
    @Operation(summary = "Not implemented — mapping version comparison is not built yet")
    public ResponseEntity<Object> compare(@PathVariable String setId, @RequestParam String base,
                                          @RequestParam String target) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
