package com.gyansys.intellirelease.domain.context;

import java.util.Locale;

/**
 * A changed file's path, pre-split so that {@link CapabilityLibrary} rules read
 * like the sentences a Hybris engineer would say out loud.
 *
 * <p>Matching is case-insensitive and separator-normalised. Everything here is
 * pure string work — no file is ever opened, and no content is ever read.
 */
public record PathFacts(
        String path,
        String lowerPath,
        String fileName,
        String lowerFileName,
        String extension
) {

    public static PathFacts of(String rawPath) {
        String normalised = rawPath.replace('\\', '/').trim();
        int lastSlash = normalised.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? normalised.substring(lastSlash + 1) : normalised;
        int lastDot = fileName.lastIndexOf('.');
        String extension = lastDot > 0 ? fileName.substring(lastDot + 1).toLowerCase(Locale.ROOT) : "";
        return new PathFacts(
                normalised,
                normalised.toLowerCase(Locale.ROOT),
                fileName,
                fileName.toLowerCase(Locale.ROOT),
                extension
        );
    }

    public boolean fileNameIs(String candidate) {
        return lowerFileName.equals(candidate.toLowerCase(Locale.ROOT));
    }

    public boolean fileNameEndsWith(String suffix) {
        return lowerFileName.endsWith(suffix.toLowerCase(Locale.ROOT));
    }

    public boolean fileNameStartsWith(String prefix) {
        return lowerFileName.startsWith(prefix.toLowerCase(Locale.ROOT));
    }

    public boolean fileNameContains(String fragment) {
        return lowerFileName.contains(fragment.toLowerCase(Locale.ROOT));
    }

    public boolean pathContains(String fragment) {
        return lowerPath.contains(fragment.toLowerCase(Locale.ROOT));
    }

    /** True when {@code segment} appears as a whole directory in the path. */
    public boolean inDirectory(String segment) {
        String needle = segment.toLowerCase(Locale.ROOT);
        return lowerPath.contains("/" + needle + "/") || lowerPath.startsWith(needle + "/");
    }

    public boolean extensionIs(String candidate) {
        return extension.equals(candidate.toLowerCase(Locale.ROOT));
    }

    public boolean extensionIsAnyOf(String... candidates) {
        for (String candidate : candidates) {
            if (extensionIs(candidate)) {
                return true;
            }
        }
        return false;
    }
}
