package com.gyansys.intellirelease.model.enums;

/** How a file was touched in a pull request, as reported by the Git provider. */
public enum ChangeType {

    ADDED,
    MODIFIED,
    REMOVED,
    RENAMED,
    UNKNOWN;

    /** Maps GitHub's lower-case file status onto the enum without guessing. */
    public static ChangeType fromGitHubStatus(String status) {
        if (status == null) {
            return UNKNOWN;
        }
        return switch (status.toLowerCase()) {
            case "added" -> ADDED;
            case "modified", "changed" -> MODIFIED;
            case "removed", "deleted" -> REMOVED;
            case "renamed" -> RENAMED;
            default -> UNKNOWN;
        };
    }
}
