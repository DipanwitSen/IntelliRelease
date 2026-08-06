package com.gyansys.intellirelease.domain.payload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses, validates and compares payloads in whatever format they arrive in.
 *
 * <h2>Format is sniffed, never trusted</h2>
 * A {@code .txt} holding an IDoc is still an IDoc, and a {@code .xml} holding a
 * SOAP fault deserves the fault-aware view. File extensions in an integration
 * landscape are unreliable often enough that trusting them produces confidently
 * wrong parsing — so the extension is at most a tie-breaker.
 *
 * <h2>Why the comparison is structural</h2>
 * A textual diff of two payloads reports reordered elements and reformatted
 * whitespace as differences, which buries the two changes that actually matter:
 * a field that disappeared and a type that changed. This flattens both sides to
 * path/value pairs first, so the diff is about content rather than layout, and
 * marks removals and type changes as breaking because those are what break a
 * consumer.
 */
@Service
public class PayloadAnalyzer {

    /** Rows past this are summarised rather than returned; a 2M-row feed must not become a 2M-row response. */
    private static final int MAX_TABLE_ROWS = 500;

    private final ObjectMapper objectMapper;

    public PayloadAnalyzer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /* ==================================================================
       Format detection
       ================================================================== */

    /**
     * Identifies the format from the content itself.
     *
     * <p>Ordered most-specific first: a SOAP envelope is XML, and an IDoc is
     * XML too, so testing plain XML early would swallow both.
     */
    public String detectFormat(String content, String filename) {
        if (content == null || content.isBlank()) {
            return "TEXT";
        }
        String trimmed = content.strip();
        String head = trimmed.substring(0, Math.min(trimmed.length(), 4000));
        String lower = head.toLowerCase();

        if (lower.contains("<soap:envelope") || lower.contains("<soapenv:envelope")
                || lower.contains("<env:envelope")) {
            return "SOAP_ENVELOPE";
        }
        if (lower.contains("<wsdl:definitions") || lower.contains("<definitions")
                && lower.contains("http://schemas.xmlsoap.org/wsdl/")) {
            return "WSDL";
        }
        if (lower.contains("<edmx:edmx") || lower.contains("<edmx")) {
            return "EDMX";
        }
        if (lower.contains("<xs:schema") || lower.contains("<xsd:schema")) {
            return "XSD";
        }
        if (lower.contains("<idoc") || lower.contains("edi_dc40")) {
            return "IDOC_XML";
        }
        if (trimmed.startsWith("<")) {
            return "XML";
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            // Only claim JSON if it actually parses. A truncated log line
            // starting with '{' is not JSON, and saying it is produces a
            // misleading parse error instead of an honest "this is text".
            return isParsableJson(trimmed) ? "JSON" : "TEXT";
        }
        if (looksLikeImpex(head)) {
            return "IMPEX";
        }
        if (looksLikeProperties(head)) {
            return "PROPERTIES";
        }
        if (looksLikeYaml(head)) {
            return "YAML";
        }
        if (looksLikeStackTrace(head)) {
            return "STACK_TRACE";
        }
        if (detectDelimiter(head) != null) {
            return "CSV";
        }

        // The extension is the last resort, never the first.
        String extension = extensionOf(filename);
        return switch (extension) {
            case "json" -> "JSON";
            case "xml" -> "XML";
            case "csv" -> "CSV";
            case "yaml", "yml" -> "YAML";
            case "properties" -> "PROPERTIES";
            case "log" -> "STACK_TRACE";
            default -> "TEXT";
        };
    }

    /* ==================================================================
       Parsing
       ================================================================== */

    /** Parses content into a normalised tree plus validation findings. */
    public ParsedPayload parse(String content, String filename, String formatHint) {
        String format = formatHint != null && !formatHint.isBlank()
                ? formatHint
                : detectFormat(content, filename);

        List<ValidationIssue> issues = new ArrayList<>();
        PayloadNode tree = null;
        PayloadTable table = null;

        switch (format) {
            case "JSON" -> {
                try {
                    tree = fromJson("$", "root", objectMapper.readTree(content));
                } catch (Exception exception) {
                    issues.add(new ValidationIssue("$", "Not valid JSON: " + exception.getMessage(),
                            "HIGH", null, null, "json.parse"));
                }
            }
            case "CSV", "TSV" -> {
                table = parseDelimited(content);
                issues.addAll(validateTable(table));
            }
            case "XML", "SOAP_ENVELOPE", "WSDL", "XSD", "EDMX", "IDOC_XML" -> {
                issues.addAll(validateXmlWellFormedness(content));
            }
            default -> {
                // TEXT, PROPERTIES, YAML, STACK_TRACE: rendered as-is. No parser
                // means no fabricated tree, which is the honest outcome.
            }
        }

        return new ParsedPayload(format, content, tree, table,
                new PayloadValidation(issues.isEmpty(), null, List.copyOf(issues)));
    }

    /* ==================================================================
       Comparison
       ================================================================== */

    /**
     * Structural diff of two payloads.
     *
     * <p>{@code breaking} marks the differences that break a consumer: a
     * removed field and a changed type. An added field is reported but not
     * breaking, because a tolerant reader ignores it — which is exactly the
     * distinction a release manager needs and a textual diff cannot make.
     */
    public PayloadComparison compare(String leftContent, String rightContent,
                                     String leftLabel, String rightLabel, String formatHint) {

        String leftFormat = formatHint != null ? formatHint : detectFormat(leftContent, leftLabel);
        String rightFormat = formatHint != null ? formatHint : detectFormat(rightContent, rightLabel);

        Map<String, String> left = flatten(leftContent, leftFormat);
        Map<String, String> right = flatten(rightContent, rightFormat);

        List<FieldDifference> differences = new ArrayList<>();
        int unchanged = 0;

        Set<String> allPaths = new LinkedHashSet<>(left.keySet());
        allPaths.addAll(right.keySet());

        for (String path : allPaths) {
            String leftValue = left.get(path);
            String rightValue = right.get(path);

            if (leftValue == null) {
                differences.add(new FieldDifference(path, "ADDED", null, rightValue,
                        null, typeOf(rightValue), false,
                        "Present on the right only. Tolerant consumers ignore unknown fields; strict ones reject them."));
            } else if (rightValue == null) {
                differences.add(new FieldDifference(path, "REMOVED", leftValue, null,
                        typeOf(leftValue), null, true,
                        "Present on the left only. Any consumer that requires this field will now fail."));
            } else if (!leftValue.equals(rightValue)) {
                String leftType = typeOf(leftValue);
                String rightType = typeOf(rightValue);
                boolean typeChanged = !leftType.equals(rightType);
                differences.add(new FieldDifference(path,
                        typeChanged ? "TYPE_CHANGED" : "VALUE_CHANGED",
                        leftValue, rightValue, leftType, rightType, typeChanged,
                        typeChanged
                                ? "The value's type changed, which breaks consumers that parse it."
                                : null));
            } else {
                unchanged++;
            }
        }

        // Breaking differences first, then alphabetically by path — a reviewer
        // should never have to scroll past forty added fields to find the one
        // removal that will take a consumer down.
        differences.sort(Comparator
                .comparing(FieldDifference::breaking, Comparator.reverseOrder())
                .thenComparing(FieldDifference::path));

        long added = differences.stream().filter(d -> "ADDED".equals(d.change())).count();
        long removed = differences.stream().filter(d -> "REMOVED".equals(d.change())).count();
        long typeChanged = differences.stream().filter(d -> "TYPE_CHANGED".equals(d.change())).count();
        long changed = differences.stream().filter(d -> "VALUE_CHANGED".equals(d.change())).count();

        return new PayloadComparison(
                leftLabel == null ? "left" : leftLabel,
                rightLabel == null ? "right" : rightLabel,
                leftLabel == null ? "Left" : leftLabel,
                rightLabel == null ? "Right" : rightLabel,
                List.copyOf(differences),
                differences.isEmpty(),
                new ComparisonSummary((int) added, (int) removed, (int) changed, (int) typeChanged, unchanged));
    }

    /* ==================================================================
       Flattening
       ================================================================== */

    /** Reduces any supported format to path -> value so two of them can be compared. */
    private Map<String, String> flatten(String content, String format) {
        Map<String, String> flat = new LinkedHashMap<>();
        if (content == null || content.isBlank()) {
            return flat;
        }

        switch (format) {
            case "JSON" -> {
                try {
                    flattenJson("$", objectMapper.readTree(content), flat);
                } catch (Exception exception) {
                    flat.put("$", content.strip());
                }
            }
            case "CSV", "TSV" -> {
                PayloadTable table = parseDelimited(content);
                // Keyed by the first column's value so rows are compared by
                // identity rather than by position — a reordered file is not
                // a changed file.
                for (int row = 0; row < table.rows().size(); row++) {
                    List<String> values = table.rows().get(row);
                    String key = values.isEmpty() ? String.valueOf(row) : values.get(0);
                    for (int column = 0; column < values.size() && column < table.headers().size(); column++) {
                        flat.put(key + "." + table.headers().get(column), values.get(column));
                    }
                }
            }
            case "XML", "SOAP_ENVELOPE", "WSDL", "XSD", "EDMX", "IDOC_XML" -> flattenXml(content, flat);
            default -> {
                String[] lines = content.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    flat.put("line[" + (i + 1) + "]", lines[i].strip());
                }
            }
        }
        return flat;
    }

    private void flattenJson(String path, JsonNode node, Map<String, String> out) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry ->
                    flattenJson(path + "." + entry.getKey(), entry.getValue(), out));
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                flattenJson(path + "[" + i + "]", node.get(i), out);
            }
        } else {
            out.put(path, node.asText());
        }
    }

    /**
     * Extracts element paths and text from XML without a full DOM parse.
     *
     * <p>Regex over XML is normally a mistake, and it is bounded here to
     * exactly one job: pairing an element's name with its immediate text so two
     * payloads can be compared. It does not attempt to model namespaces,
     * attributes or mixed content, and well-formedness is checked separately.
     */
    private void flattenXml(String content, Map<String, String> out) {
        var matcher = XML_LEAF.matcher(content);
        Map<String, Integer> seen = new LinkedHashMap<>();

        while (matcher.find()) {
            String name = matcher.group(1);
            String value = matcher.group(2).strip();
            if (value.isEmpty()) {
                continue;
            }
            // Repeated element names get an index so two occurrences do not
            // collapse into one and hide a difference.
            int occurrence = seen.merge(name, 1, Integer::sum);
            out.put(occurrence == 1 ? name : name + "[" + occurrence + "]", value);
        }
    }

    private static final Pattern XML_LEAF =
            Pattern.compile("<([A-Za-z_][\\w.:-]*)(?:\\s[^>]*)?>([^<>]*)</\\1>");

    /* ==================================================================
       Delimited files
       ================================================================== */

    /**
     * Detects the delimiter by consistency across the first few lines rather
     * than by frequency in one.
     *
     * <p>A single header line packed with commas can look comma-delimited even
     * when the file is semicolon-delimited with commas inside values — which is
     * the normal shape of a European price feed. Requiring the same count on
     * several lines eliminates that class of mistake.
     */
    public String detectDelimiter(String content) {
        String[] lines = content.split("\r?\n", 6);
        if (lines.length < 1) {
            return null;
        }

        String best = null;
        int bestCount = 0;

        for (String candidate : new String[]{";", ",", "\t", "|"}) {
            int firstCount = countOccurrences(lines[0], candidate);
            if (firstCount == 0) {
                continue;
            }
            boolean consistent = true;
            for (int i = 1; i < lines.length; i++) {
                if (lines[i].isBlank()) {
                    continue;
                }
                if (countOccurrences(lines[i], candidate) != firstCount) {
                    consistent = false;
                    break;
                }
            }
            if (consistent && firstCount > bestCount) {
                best = candidate;
                bestCount = firstCount;
            }
        }
        return best;
    }

    private PayloadTable parseDelimited(String content) {
        String delimiter = detectDelimiter(content);
        if (delimiter == null) {
            delimiter = ",";
        }

        String[] lines = content.split("\r?\n");
        if (lines.length == 0) {
            return new PayloadTable(delimiter, List.of(), List.of(), false, 0);
        }

        List<String> headers = List.of(lines[0].split(Pattern.quote(delimiter), -1));
        List<List<String>> rows = new ArrayList<>();

        int total = 0;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            total++;
            if (rows.size() < MAX_TABLE_ROWS) {
                rows.add(List.of(lines[i].split(Pattern.quote(delimiter), -1)));
            }
        }

        return new PayloadTable(delimiter, headers, List.copyOf(rows), total > rows.size(), total);
    }

    /**
     * Checks each row has as many cells as the header has columns.
     *
     * <p>This is the single most common defect in a delimited feed and the one
     * that produces "no column found" downstream, so it is worth catching in
     * the viewer rather than at 3am in an import log.
     */
    private List<ValidationIssue> validateTable(PayloadTable table) {
        List<ValidationIssue> issues = new ArrayList<>();
        int expected = table.headers().size();

        for (int i = 0; i < table.rows().size(); i++) {
            int actual = table.rows().get(i).size();
            if (actual != expected) {
                issues.add(new ValidationIssue(
                        "row[" + (i + 1) + "]",
                        "Row has " + actual + " cells but the header declares " + expected
                                + ". Usually an unescaped delimiter inside a value.",
                        "HIGH", i + 2, null, "csv.columnCount"));
            }
            if (issues.size() >= 20) {
                issues.add(new ValidationIssue("$",
                        "Further row-length problems suppressed — fix the delimiter or quoting and re-check.",
                        "INFO", null, null, "csv.columnCount"));
                break;
            }
        }
        return issues;
    }

    /**
     * Cheap well-formedness check: are open and close tags balanced?
     *
     * <p>Deliberately not a validating parser. Building a DocumentBuilder here
     * would mean either accepting external entity resolution — an XXE risk on
     * payloads that arrive from outside — or configuring it defensively for a
     * check this shallow. Balance catches truncation, which is the failure this
     * viewer actually sees.
     */
    private List<ValidationIssue> validateXmlWellFormedness(String content) {
        var open = Pattern.compile("<([A-Za-z_][\\w.:-]*)(?:\\s[^>]*?)?(?<!/)>").matcher(content);
        var close = Pattern.compile("</([A-Za-z_][\\w.:-]*)>").matcher(content);

        Map<String, Integer> counts = new LinkedHashMap<>();
        while (open.find()) {
            counts.merge(open.group(1), 1, Integer::sum);
        }
        while (close.find()) {
            counts.merge(close.group(1), -1, Integer::sum);
        }

        List<ValidationIssue> issues = new ArrayList<>();
        counts.forEach((element, balance) -> {
            if (balance > 0) {
                issues.add(new ValidationIssue(element,
                        balance + " unclosed <" + element + "> element(s). The payload may be truncated.",
                        "HIGH", null, null, "xml.balance"));
            } else if (balance < 0) {
                issues.add(new ValidationIssue(element,
                        Math.abs(balance) + " unmatched </" + element + "> closing tag(s).",
                        "HIGH", null, null, "xml.balance"));
            }
        });
        return issues;
    }

    /* ==================================================================
       Helpers
       ================================================================== */

    private PayloadNode fromJson(String path, String name, JsonNode node) {
        if (node.isObject()) {
            List<PayloadNode> children = new ArrayList<>();
            node.fields().forEachRemaining(entry ->
                    children.add(fromJson(path + "." + entry.getKey(), entry.getKey(), entry.getValue())));
            return new PayloadNode(path, name, "OBJECT", null, "object", List.copyOf(children), null, false);
        }
        if (node.isArray()) {
            List<PayloadNode> children = new ArrayList<>();
            for (int i = 0; i < node.size(); i++) {
                children.add(fromJson(path + "[" + i + "]", name + "[" + i + "]", node.get(i)));
            }
            return new PayloadNode(path, name, "ARRAY", null, "array", List.copyOf(children), null, true);
        }
        return new PayloadNode(path, name, "FIELD", node.asText(), jsonTypeOf(node), List.of(), null, false);
    }

    private boolean isParsableJson(String content) {
        try {
            objectMapper.readTree(content);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private static String jsonTypeOf(JsonNode node) {
        if (node.isTextual()) return "string";
        if (node.isInt() || node.isLong()) return "integer";
        if (node.isNumber()) return "number";
        if (node.isBoolean()) return "boolean";
        if (node.isNull()) return "null";
        return "unknown";
    }

    /** Infers a comparable type from a value's shape, for type-change detection. */
    private static String typeOf(String value) {
        if (value == null) return "null";
        String trimmed = value.strip();
        if (trimmed.isEmpty()) return "empty";
        if (trimmed.matches("-?\\d+")) return "integer";
        if (trimmed.matches("-?\\d*\\.\\d+")) return "decimal";
        if (trimmed.equalsIgnoreCase("true") || trimmed.equalsIgnoreCase("false")) return "boolean";
        if (trimmed.matches("\\d{4}-\\d{2}-\\d{2}([T ].*)?")) return "date";
        return "string";
    }

    private static int countOccurrences(String line, String token) {
        int count = 0;
        int index = 0;
        while ((index = line.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private static String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private static boolean looksLikeImpex(String head) {
        return head.contains("INSERT_UPDATE ") || head.contains("UPDATE ") && head.contains(";");
    }

    private static boolean looksLikeProperties(String head) {
        return head.lines().filter(line -> !line.isBlank() && !line.startsWith("#"))
                .limit(5)
                .allMatch(line -> line.matches("[\\w.$-]+\\s*[=:].*"));
    }

    private static boolean looksLikeYaml(String head) {
        return head.lines().filter(line -> !line.isBlank() && !line.startsWith("#"))
                .limit(5)
                .anyMatch(line -> line.matches("\\s*[\\w.-]+:\\s.*") || line.matches("\\s*-\\s+.*"));
    }

    private static boolean looksLikeStackTrace(String head) {
        return head.contains("\tat ") || head.matches("(?s).*\\n\\s+at [\\w.$]+\\(.*");
    }

    /* ==================================================================
       Wire types
       ================================================================== */

    public record ParsedPayload(String format, String raw, PayloadNode tree,
                                PayloadTable table, PayloadValidation validation) {
    }

    public record PayloadNode(String path, String name, String kind, String value, String dataType,
                              List<PayloadNode> children, Boolean required, boolean repeating) {
    }

    public record PayloadTable(String delimiter, List<String> headers, List<List<String>> rows,
                               boolean truncated, int totalRows) {
    }

    public record PayloadValidation(boolean valid, String schemaApplied, List<ValidationIssue> issues) {
    }

    public record ValidationIssue(String path, String message, String severity,
                                  Integer line, Integer column, String rule) {
    }

    public record PayloadComparison(String leftId, String rightId, String leftLabel, String rightLabel,
                                    List<FieldDifference> differences, boolean identical,
                                    ComparisonSummary summary) {
    }

    public record FieldDifference(String path, String change, String leftValue, String rightValue,
                                  String leftType, String rightType, boolean breaking, String note) {
    }

    public record ComparisonSummary(int added, int removed, int changed, int typeChanged, int unchanged) {
    }
}
