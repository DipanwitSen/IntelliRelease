package com.gyansys.intellirelease.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gyansys.intellirelease.domain.context.knowledge.ArtifactTypeDefinition;
import com.gyansys.intellirelease.domain.context.knowledge.SapCommerceKnowledgeBase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * The Knowledge Base, sourced directly from {@code sap_context.json} — the
 * same curated SAP Commerce artifact taxonomy that drives file
 * classification. This is real, hand-curated domain knowledge (provenance
 * RULE_OUTPUT), not a placeholder: every article here is one artifact type
 * the Context Engine already knows how to recognise in a diff.
 */
@RestController
@RequestMapping("/api/v1/knowledge")
@Tag(name = "Knowledge Base", description = "SAP Commerce artifact taxonomy, sourced from the classification knowledge base")
public class KnowledgeController {

    private final SapCommerceKnowledgeBase knowledgeBase;

    public KnowledgeController(SapCommerceKnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Article(String id, String title, String category, String summary, String body,
                          List<String> tags, List<String> appliesTo, List<Object> references,
                          String updatedAt, String provenance) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GlossaryTerm(String term, String definition, List<String> aliases, String category) {
    }

    @GetMapping("/articles")
    @Operation(summary = "One article per curated SAP Commerce artifact type")
    public PageResponse<Article> articles(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "50") int size,
                                          @RequestParam(required = false) String q,
                                          @RequestParam(required = false) String tag) {
        List<Article> all = knowledgeBase.artifactTypes().entrySet().stream()
                .filter(entry -> matchesQuery(entry, q))
                .filter(entry -> tag == null || tag.isBlank()
                        || entry.getValue().businessCapability().stream().anyMatch(cap -> cap.equalsIgnoreCase(tag)))
                .map(entry -> toArticle(entry.getKey(), entry.getValue()))
                .sorted((a, b) -> a.title().compareToIgnoreCase(b.title()))
                .toList();

        int safeSize = size <= 0 ? 50 : Math.min(size, 200);
        int fromIndex = Math.min(Math.max(page, 0) * safeSize, all.size());
        int toIndex = Math.min(fromIndex + safeSize, all.size());
        return PageResponse.of(all.subList(fromIndex, toIndex), all.size(), page, safeSize);
    }

    @GetMapping("/articles/{id}")
    @Operation(summary = "One artifact type's full curated entry")
    public ResponseEntity<Article> article(@PathVariable String id) {
        return knowledgeBase.artifactType(id)
                .map(definition -> ResponseEntity.ok(toArticle(id, definition)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/glossary")
    @Operation(summary = "Every curated artifact type as a glossary term")
    public List<GlossaryTerm> glossary(@RequestParam(required = false) String q) {
        return knowledgeBase.artifactTypes().entrySet().stream()
                .filter(entry -> q == null || q.isBlank()
                        || entry.getValue().displayName().toLowerCase().contains(q.toLowerCase())
                        || entry.getKey().toLowerCase().contains(q.toLowerCase()))
                .map(entry -> new GlossaryTerm(
                        entry.getValue().displayName(),
                        entry.getValue().generalizedMeaning() != null
                                ? entry.getValue().generalizedMeaning() : entry.getValue().generalRole(),
                        entry.getValue().aliasOf() == null ? List.of() : List.of(entry.getValue().aliasOf()),
                        entry.getValue().layer()))
                .sorted((a, b) -> a.term().compareToIgnoreCase(b.term()))
                .toList();
    }

    private boolean matchesQuery(Map.Entry<String, ArtifactTypeDefinition> entry, String q) {
        if (q == null || q.isBlank()) {
            return true;
        }
        String needle = q.toLowerCase();
        return entry.getKey().toLowerCase().contains(needle)
                || entry.getValue().displayName().toLowerCase().contains(needle)
                || (entry.getValue().generalRole() != null && entry.getValue().generalRole().toLowerCase().contains(needle));
    }

    private Article toArticle(String id, ArtifactTypeDefinition definition) {
        StringBuilder body = new StringBuilder();
        if (definition.generalizedMeaning() != null) {
            body.append(definition.generalizedMeaning()).append("\n\n");
        }
        if (!definition.potentialImpact().isEmpty()) {
            body.append("**Potential impact:**\n");
            definition.potentialImpact().forEach(item -> body.append("- ").append(item).append('\n'));
            body.append('\n');
        }
        if (!definition.regressionAreas().isEmpty()) {
            body.append("**Suggested regression scope:**\n");
            definition.regressionAreas().forEach(item -> body.append("- ").append(item).append('\n'));
        }

        return new Article(id, definition.displayName(), "COMMERCE_BEST_PRACTICE", definition.generalRole(),
                body.toString(), definition.businessCapability(), List.of(definition.layer()), List.of(), null,
                "RULE_OUTPUT");
    }
}
