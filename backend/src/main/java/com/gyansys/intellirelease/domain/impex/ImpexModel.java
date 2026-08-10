package com.gyansys.intellirelease.domain.impex;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;

import java.util.List;
import java.util.Map;

/**
 * Wire types for ImpEx content analysis.
 *
 * <p>Same discipline as {@code PayloadAnalyzer}: every count and link traces
 * back to a literal header and a literal row in a literal file, found by
 * pattern, never guessed. ImpEx syntax is generic — the item type named in a
 * block's header can be {@code ContentSlot}, {@code CMSFlexComponent},
 * {@code IntegrationObjectItem}, or any other SAP Commerce type — so nothing
 * here hardcodes a fixed vocabulary of "CMS types". A landscape that only
 * ever touches integration objects in ImpEx gets a breakdown of integration
 * objects; one that touches CMS slots gets a breakdown of CMS slots.
 */
public final class ImpexModel {

    private ImpexModel() {
    }

    /** One INSERT_UPDATE / UPDATE / REMOVE / INSERT header block and its data rows. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ImpexBlock(
            String mode,
            String itemType,
            List<ColumnDef> columns,
            List<ImpexRow> rows,
            String sourceFile,
            int lineNumber
    ) {
    }

    /** One declared column: its field name and, if present, its reference qualifier. */
    public record ColumnDef(String field, String qualifier, boolean unique) {
    }

    /** One data row under a block's header, plus whatever it references. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ImpexRow(
            String key,
            Map<String, String> values,
            List<ImpexLink> links
    ) {
    }

    /**
     * A reference from one row to another item, discovered from a qualifier
     * column such as {@code contentSlot(uid)} or {@code integrationObject(code)}.
     * {@code targetKeys} has more than one entry for collection columns — SAP
     * Commerce's default comma-separated multi-value convention (e.g. a
     * {@code ContentSlot}'s {@code cmsComponents} column).
     */
    public record ImpexLink(String field, List<String> targetKeys) {
    }

    /** One item type's operation tally across every ImpEx file this pull request touched. */
    public record ImpexTypeSummary(
            String itemType,
            int insertUpdateCount,
            int updateCount,
            int removeCount,
            int insertCount,
            int totalCount
    ) {
    }

    public record ImpexOperationCounts(int insertUpdate, int update, int remove, int insert, int total) {
    }

    /**
     * Every item this pull request's ImpEx declares, flattened so the UI can
     * group by type or open one item's links without a second request.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ImpexItemView(
            String itemType,
            String mode,
            String key,
            Map<String, String> fields,
            List<ImpexLink> links,
            String sourceFile
    ) {
    }

    /**
     * The full answer to "what does this pull request's ImpEx actually do?"
     *
     * <p>{@code touched = false} is a real answer — a pull request with no
     * {@code .impex} file gets exactly this, not an empty-looking populated
     * shape. {@code contentUnavailable} is a separate, also-honest outcome: it
     * means ImpEx files were touched but their content could not be fetched
     * (no {@code GITHUB_TOKEN} configured, a deleted/renamed file, an
     * unreachable API) — the UI must not read that silence as "no CMS impact".
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ImpexAnalysis(
            boolean touched,
            boolean contentUnavailable,
            int filesAnalyzed,
            List<String> filesWithoutContent,
            ImpexOperationCounts operationCounts,
            List<ImpexTypeSummary> byType,
            List<ImpexItemView> items,
            ProvenanceClass provenanceClass
    ) {
        public static ImpexAnalysis untouched() {
            return new ImpexAnalysis(false, false, 0, List.of(),
                    new ImpexOperationCounts(0, 0, 0, 0, 0), List.of(), List.of(),
                    ProvenanceClass.DERIVED_FACT);
        }

        public static ImpexAnalysis unavailable(List<String> impexPaths) {
            return new ImpexAnalysis(true, true, 0, List.copyOf(impexPaths),
                    new ImpexOperationCounts(0, 0, 0, 0, 0), List.of(), List.of(),
                    ProvenanceClass.UNKNOWN);
        }
    }
}
