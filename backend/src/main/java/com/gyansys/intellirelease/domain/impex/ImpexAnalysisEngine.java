package com.gyansys.intellirelease.domain.impex;

import com.gyansys.intellirelease.model.enums.ProvenanceClass;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the ImpEx files a pull request touched into one answer: what does it
 * insert, update or remove, on which SAP Commerce types, and how are those
 * rows linked to each other.
 *
 * <p>Deterministic, like every other engine in the philosophy chain — this
 * counts and links what {@link ImpexParser} found, and never infers a
 * relationship the file itself does not declare via a qualifier column.
 */
@Service
public class ImpexAnalysisEngine {

    /**
     * @param fileContents           path -> full file content, for every
     *                               {@code .impex} file whose content could be fetched
     * @param filesWithoutContent    {@code .impex} paths this pull request touched
     *                               but whose content could not be read
     */
    public ImpexModel.ImpexAnalysis analyze(Map<String, String> fileContents, List<String> filesWithoutContent) {
        if (fileContents.isEmpty() && filesWithoutContent.isEmpty()) {
            return ImpexModel.ImpexAnalysis.untouched();
        }
        if (fileContents.isEmpty()) {
            return ImpexModel.ImpexAnalysis.unavailable(filesWithoutContent);
        }

        List<ImpexModel.ImpexItemView> items = new ArrayList<>();
        // itemType -> [insertUpdate, update, remove, insert]
        Map<String, int[]> byType = new LinkedHashMap<>();
        int insertUpdate = 0;
        int update = 0;
        int remove = 0;
        int insert = 0;

        for (Map.Entry<String, String> entry : fileContents.entrySet()) {
            String path = entry.getKey();
            for (ImpexModel.ImpexBlock block : ImpexParser.parseFile(path, entry.getValue())) {
                int[] counts = byType.computeIfAbsent(block.itemType(), key -> new int[4]);
                for (ImpexModel.ImpexRow row : block.rows()) {
                    switch (block.mode()) {
                        case "INSERT_UPDATE" -> {
                            insertUpdate++;
                            counts[0]++;
                        }
                        case "UPDATE" -> {
                            update++;
                            counts[1]++;
                        }
                        case "REMOVE" -> {
                            remove++;
                            counts[2]++;
                        }
                        case "INSERT" -> {
                            insert++;
                            counts[3]++;
                        }
                        default -> {
                            // Unrecognised mode — the header regex only matches the four
                            // known keywords, so this branch is unreachable in practice.
                        }
                    }
                    items.add(new ImpexModel.ImpexItemView(
                            block.itemType(), block.mode(), row.key(), row.values(), row.links(), path));
                }
            }
        }

        List<ImpexModel.ImpexTypeSummary> byTypeSummary = byType.entrySet().stream()
                .map(entry -> {
                    int[] c = entry.getValue();
                    return new ImpexModel.ImpexTypeSummary(entry.getKey(), c[0], c[1], c[2], c[3], c[0] + c[1] + c[2] + c[3]);
                })
                .sorted(Comparator.comparingInt(ImpexModel.ImpexTypeSummary::totalCount).reversed())
                .toList();

        int total = insertUpdate + update + remove + insert;

        return new ImpexModel.ImpexAnalysis(
                true,
                !filesWithoutContent.isEmpty(),
                fileContents.size(),
                List.copyOf(filesWithoutContent),
                new ImpexModel.ImpexOperationCounts(insertUpdate, update, remove, insert, total),
                byTypeSummary,
                items,
                ProvenanceClass.DERIVED_FACT);
    }
}
