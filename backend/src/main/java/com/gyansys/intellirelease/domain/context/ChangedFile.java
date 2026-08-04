package com.gyansys.intellirelease.domain.context;

import com.gyansys.intellirelease.model.enums.ChangeType;

/**
 * One file touched by a pull request, exactly as the Git provider reported it.
 *
 * <p>Provenance: FACT. Nothing here is derived — the path, the status and the
 * line counts came from GitHub. Meaning is added later, by
 * {@link SAPCommerceContextEngine}, and stored separately.
 *
 * <p>Note what is absent: file <em>content</em>. The Context Engine classifies
 * on paths, and the AI service never receives source code at all.
 */
public record ChangedFile(
        String path,
        ChangeType changeType,
        int additions,
        int deletions
) {

    public ChangedFile {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Changed file path must not be blank");
        }
        if (changeType == null) {
            changeType = ChangeType.UNKNOWN;
        }
    }

    public static ChangedFile of(String path) {
        return new ChangedFile(path, ChangeType.MODIFIED, 0, 0);
    }

    public int totalLinesTouched() {
        return additions + deletions;
    }
}
