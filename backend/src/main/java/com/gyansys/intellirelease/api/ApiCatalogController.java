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
 * The API catalogue (REST/SOAP/OData/OCC surfaces). No catalogue has been
 * imported for this tenant yet — this is deliberately distinct from
 * {@code SapCommerceKnowledgeBase}'s {@code API_LAYER}/{@code OCC_API}
 * classification, which tags individual changed files, not a maintained
 * inventory of API contracts.
 */
@RestController
@RequestMapping("/api/v1/api-catalog")
@Tag(name = "API Catalogue", description = "REST/SOAP/OData/OCC API inventory — not yet populated")
public class ApiCatalogController {

    @GetMapping
    @Operation(summary = "API definitions — empty until an API catalogue is imported")
    public PageResponse<Object> list(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size) {
        return PageResponse.of(List.of(), 0, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One API definition — not found until an API catalogue is imported")
    public ResponseEntity<Object> get(@PathVariable String id) {
        return ResponseEntity.notFound().build();
    }
}
