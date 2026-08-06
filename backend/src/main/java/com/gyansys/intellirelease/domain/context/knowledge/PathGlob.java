package com.gyansys.intellirelease.domain.context.knowledge;

import java.util.regex.Pattern;

/**
 * Glob matching for changed-file paths, with gitignore/Ant semantics.
 *
 * <p>Extracted from {@link SapCommerceKnowledgeBase} once the integration
 * catalogue needed the same matching. Two independent implementations of
 * "does this path match this pattern?" is exactly the kind of duplication that
 * drifts: one gets a fix, the other does not, and the two knowledge bases
 * quietly start disagreeing about the same file.
 *
 * <p>Hand-rolled rather than delegated to {@code java.nio.file.PathMatcher},
 * because the JDK's {@code **} does not match zero directory segments
 * (confirmed empirically — see {@code SapCommerceKnowledgeBaseTest}). That
 * silently misses exactly the shallow paths these patterns are written to
 * catch: a root {@code manifest.json}, a file sitting directly under
 * {@code resources/}.
 *
 * <p>Semantics:
 * <ul>
 *   <li>{@code *} matches within one path segment</li>
 *   <li>{@code **} matches zero or more whole segments, including zero when it
 *       sits between two slashes or leads the pattern</li>
 *   <li>{@code ?} matches one non-separator character</li>
 *   <li>{@code {a,b,c}} matches any one of the alternatives</li>
 * </ul>
 *
 * <p>Brace alternation exists because the integration catalogue needs patterns
 * like {@code **}{@code /*Order*{Converter,Populator,Contributor}*.java}.
 * Writing those as three rules each would triple the catalogue and make it
 * possible for one of the three to drift.
 */
public final class PathGlob {

    private PathGlob() {
    }

    /** Normalises separators and strips a leading slash so patterns can be written relative. */
    public static String normalise(String rawPath) {
        if (rawPath == null) {
            return "";
        }
        String path = rawPath.replace('\\', '/').trim();
        return path.startsWith("/") ? path.substring(1) : path;
    }

    /** Compiles one glob into an anchored regex. */
    public static Pattern compile(String glob) {
        StringBuilder regex = new StringBuilder();
        int i = 0;
        int length = glob.length();

        while (i < length) {
            char current = glob.charAt(i);

            if (current == '*' && i + 1 < length && glob.charAt(i + 1) == '*') {
                boolean slashBefore = regex.length() == 0 || regex.charAt(regex.length() - 1) == '/';
                int after = i + 2;
                boolean slashAfter = after < length && glob.charAt(after) == '/';
                boolean endOfPattern = after == length;

                if (slashBefore && slashAfter) {
                    // "X/**/Y" or a leading "**/Y": zero or more whole directories.
                    regex.append("(?:.*/)?");
                    i = after + 1;
                    continue;
                }
                if (slashBefore && endOfPattern) {
                    // Trailing "X/**": X optionally followed by anything.
                    if (regex.length() > 0) {
                        regex.setLength(regex.length() - 1);
                    }
                    regex.append("(?:/.*)?");
                    i = after;
                    continue;
                }
                // "**" not cleanly delimited by slashes on both sides: match anything.
                regex.append(".*");
                i = after;
                continue;
            }

            if (current == '*') {
                regex.append("[^/]*");
                i++;
                continue;
            }
            if (current == '?') {
                regex.append("[^/]");
                i++;
                continue;
            }

            // Brace alternation: {a,b,c} -> (?:a|b|c). Only treated as
            // alternation when a closing brace actually follows on the same
            // pattern; an unmatched '{' falls through and is escaped as a
            // literal, so a malformed pattern degrades to "matches nothing"
            // rather than throwing at startup.
            if (current == '{') {
                int close = glob.indexOf('}', i);
                if (close > i) {
                    String[] alternatives = glob.substring(i + 1, close).split(",", -1);
                    regex.append("(?:");
                    for (int a = 0; a < alternatives.length; a++) {
                        if (a > 0) {
                            regex.append('|');
                        }
                        regex.append(Pattern.quote(alternatives[a]));
                    }
                    regex.append(')');
                    i = close + 1;
                    continue;
                }
            }

            if ("\\.[]{}()+-^$|".indexOf(current) >= 0) {
                regex.append('\\').append(current);
                i++;
                continue;
            }
            regex.append(current);
            i++;
        }

        return Pattern.compile("^" + regex + "$");
    }

    /** Convenience for one-off checks; compile once and reuse for hot paths. */
    public static boolean matches(String glob, String rawPath) {
        return compile(glob).matcher(normalise(rawPath)).matches();
    }
}
