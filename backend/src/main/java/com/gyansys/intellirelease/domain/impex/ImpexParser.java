package com.gyansys.intellirelease.domain.impex;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns raw ImpEx text into {@link ImpexModel.ImpexBlock}s — pure, static,
 * no Spring, no I/O. Exactly one job: recognise the header/row shape ImpEx
 * scripts always have, never interpret what running the script would do.
 *
 * <h2>What this deliberately does not do</h2>
 * <ul>
 *   <li>Resolve {@code $macro} definitions (e.g. {@code $lang=en}) — a macro
 *       line is skipped, not substituted, so a cell can show a literal
 *       {@code $contentCatalog} token rather than a guessed expansion.</li>
 *   <li>Evaluate embedded Groovy/beanshell scripting blocks.</li>
 *   <li>Understand impex "mode" overrides on individual rows — the header's
 *       mode applies to every row under it, which covers the overwhelming
 *       majority of real ImpEx.</li>
 * </ul>
 * An impex file that only uses these features still parses — the parts this
 * class does not understand are skipped, not guessed at.
 */
final class ImpexParser {

    private ImpexParser() {
    }

    private static final Pattern HEADER =
            Pattern.compile("^(INSERT_UPDATE|UPDATE|REMOVE|INSERT)\\s+([A-Za-z_][\\w.]*)\\s*;(.*)$");

    /** {@code field}, optionally {@code field(qualifier)}, optionally followed by one or more {@code [modifiers]}. */
    private static final Pattern COLUMN =
            Pattern.compile("^([A-Za-z_][\\w]*)\\s*(?:\\(([^)]*)\\))?\\s*((?:\\[[^]]*])*)\\s*$");

    static List<ImpexModel.ImpexBlock> parseFile(String path, String content) {
        List<ImpexModel.ImpexBlock> blocks = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return blocks;
        }

        String[] lines = content.split("\r?\n", -1);

        String mode = null;
        String itemType = null;
        List<ImpexModel.ColumnDef> columns = null;
        List<ImpexModel.ImpexRow> rows = null;
        int headerLineNumber = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();

            if (line.isEmpty() || line.startsWith("#")) {
                flush(blocks, mode, itemType, columns, rows, path, headerLineNumber);
                mode = null;
                itemType = null;
                columns = null;
                rows = null;
                continue;
            }
            if (line.startsWith("$")) {
                // Macro definition — not resolved, and does not close a block on its own.
                continue;
            }

            Matcher header = HEADER.matcher(line);
            if (header.matches()) {
                flush(blocks, mode, itemType, columns, rows, path, headerLineNumber);
                mode = header.group(1);
                itemType = header.group(2);
                columns = parseColumns(header.group(3));
                rows = new ArrayList<>();
                headerLineNumber = i + 1;
                continue;
            }

            if (columns != null) {
                rows.add(parseRow(line, columns));
            }
            // A data-shaped line before any header is malformed input — skipped,
            // not guessed at.
        }
        flush(blocks, mode, itemType, columns, rows, path, headerLineNumber);
        return blocks;
    }

    private static void flush(List<ImpexModel.ImpexBlock> blocks, String mode, String itemType,
                              List<ImpexModel.ColumnDef> columns, List<ImpexModel.ImpexRow> rows,
                              String path, int headerLineNumber) {
        if (mode != null && rows != null && !rows.isEmpty()) {
            blocks.add(new ImpexModel.ImpexBlock(mode, itemType, columns, List.copyOf(rows), path, headerLineNumber));
        }
    }

    private static List<ImpexModel.ColumnDef> parseColumns(String remainder) {
        List<ImpexModel.ColumnDef> columns = new ArrayList<>();
        for (String cell : splitUnescaped(remainder)) {
            String trimmed = cell.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            Matcher match = COLUMN.matcher(trimmed);
            if (match.matches()) {
                String field = match.group(1);
                String qualifier = match.group(2);
                boolean unique = match.group(3) != null && match.group(3).contains("unique=true");
                columns.add(new ImpexModel.ColumnDef(field, qualifier, unique));
            } else {
                columns.add(new ImpexModel.ColumnDef(trimmed, null, false));
            }
        }
        return columns;
    }

    private static ImpexModel.ImpexRow parseRow(String line, List<ImpexModel.ColumnDef> columns) {
        List<String> cells = splitUnescaped(line);

        // Real ImpEx data rows conventionally carry one leading cell before
        // the first declared column — a per-row mode-override slot the
        // header never names (e.g. "; LoyaltyPointsSync" under a
        // single-column header). When the row has exactly one more cell than
        // there are columns, that leading cell is dropped rather than
        // silently shifting every value one column to the left.
        int offset = cells.size() == columns.size() + 1 ? 1 : 0;

        Map<String, String> values = new LinkedHashMap<>();
        List<ImpexModel.ImpexLink> links = new ArrayList<>();
        String uniqueKey = null;
        String firstNonBlank = null;

        for (int i = 0; i < columns.size() && i + offset < cells.size(); i++) {
            ImpexModel.ColumnDef column = columns.get(i);
            String value = cells.get(i + offset).strip();
            if (value.isEmpty()) {
                continue;
            }

            values.put(column.field(), value);
            if (firstNonBlank == null) {
                firstNonBlank = value;
            }
            if (column.unique() && uniqueKey == null) {
                uniqueKey = value;
            }
            if (column.qualifier() != null) {
                List<String> targets = List.of(value.split(",")).stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
                if (!targets.isEmpty()) {
                    links.add(new ImpexModel.ImpexLink(column.field(), targets));
                }
            }
        }

        String key = uniqueKey != null ? uniqueKey : firstNonBlank != null ? firstNonBlank : "(row)";
        return new ImpexModel.ImpexRow(key, values, links);
    }

    /** Splits on {@code ;} that is not preceded by a backslash escape, then unescapes {@code \;} to {@code ;}. */
    private static List<String> splitUnescaped(String line) {
        String[] raw = line.split("(?<!\\\\);", -1);
        List<String> result = new ArrayList<>(raw.length);
        for (String cell : raw) {
            result.add(cell.replace("\\;", ";"));
        }
        return result;
    }
}
