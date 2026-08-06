/**
 * Minimal syntax highlighting for the payload and code viewers.
 *
 * Hand-rolled rather than pulling in a highlighter library for two reasons:
 * the formats that matter here are few (JSON, XML/SOAP/WSDL/XSD/EDMX, CSV,
 * properties, logs), and a published Artifact-style CSP forbids fetching a
 * grammar bundle at runtime. ~150 lines beats a 200KB dependency.
 *
 * SECURITY: input is HTML-escaped *before* any markup is inserted, and every
 * pattern below only ever wraps already-escaped text. Payloads come from
 * customer systems and must be treated as hostile — a viewer that interpolated
 * raw payload text into innerHTML would be a stored-XSS vector reachable by
 * anyone who can push a message through an interface.
 */

export type HighlightLanguage = 'json' | 'xml' | 'csv' | 'properties' | 'yaml' | 'log' | 'sql' | 'text';

export function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/** Maps a payload format or a filename to a highlighter. */
export function languageFor(format: string | null | undefined, filename?: string | null): HighlightLanguage {
  const key = (format ?? '').toUpperCase();

  if (key.includes('JSON') || key === 'OPENAPI' || key === 'SWAGGER' || key === 'AVRO') return 'json';
  if (['XML', 'SOAP_ENVELOPE', 'WSDL', 'XSD', 'EDMX', 'IDOC_XML', 'IMPEX_XML'].includes(key)) return 'xml';
  if (['CSV', 'TSV', 'FIXED_WIDTH', 'EXCEL'].includes(key)) return 'csv';
  if (key === 'PROPERTIES') return 'properties';
  if (key === 'YAML') return 'yaml';
  if (key === 'SQL') return 'sql';
  if (key === 'LOG' || key === 'STACK_TRACE') return 'log';

  const extension = filename?.split('.').pop()?.toLowerCase();
  switch (extension) {
    case 'json': return 'json';
    case 'xml': case 'wsdl': case 'xsd': case 'edmx': case 'xslt': case 'xsl': return 'xml';
    case 'csv': case 'tsv': return 'csv';
    case 'properties': return 'properties';
    case 'yaml': case 'yml': return 'yaml';
    case 'sql': return 'sql';
    case 'log': return 'log';
    default: return 'text';
  }
}

/** Escapes, then wraps tokens. Returns HTML safe to bind with innerHTML. */
export function highlight(source: string, language: HighlightLanguage): string {
  const escaped = escapeHtml(source);

  switch (language) {
    case 'json': return highlightJson(escaped);
    case 'xml': return highlightXml(escaped);
    case 'csv': return highlightCsv(escaped);
    case 'properties': return highlightProperties(escaped);
    case 'yaml': return highlightYaml(escaped);
    case 'sql': return highlightSql(escaped);
    case 'log': return highlightLog(escaped);
    default: return escaped;
  }
}

function highlightJson(escaped: string): string {
  return escaped
    // Keys first: a quoted string followed by a colon. Doing this before the
    // generic string rule is what stops every key being painted as a value.
    .replace(/(&quot;(?:[^&]|&(?!quot;))*?&quot;)(\s*:)/g, '<span class="t-key">$1</span>$2')
    .replace(/:(\s*)(&quot;(?:[^&]|&(?!quot;))*?&quot;)/g, ':$1<span class="t-string">$2</span>')
    .replace(/\b(true|false)\b/g, '<span class="t-boolean">$1</span>')
    .replace(/\bnull\b/g, '<span class="t-null">null</span>')
    .replace(/(:\s*)(-?\d+\.?\d*(?:[eE][+-]?\d+)?)/g, '$1<span class="t-number">$2</span>');
}

function highlightXml(escaped: string): string {
  return escaped
    .replace(/(&lt;!--[\s\S]*?--&gt;)/g, '<span class="t-comment">$1</span>')
    .replace(/(&lt;\?[\s\S]*?\?&gt;)/g, '<span class="t-comment">$1</span>')
    // Tag names, including a namespace prefix.
    .replace(/(&lt;\/?)([\w.-]+:)?([\w.-]+)/g, '$1<span class="t-tag">$2$3</span>')
    .replace(/([\w.-]+:)?([\w.-]+)(=)(&quot;.*?&quot;)/g,
      '<span class="t-attr">$1$2</span>$3<span class="t-string">$4</span>');
}

function highlightCsv(escaped: string): string {
  const lines = escaped.split('\n');
  if (!lines.length) {
    return escaped;
  }
  // Only the header row is coloured. Painting every delimiter turns a wide CSV
  // into confetti and makes the columns harder to scan, not easier.
  const [header, ...rest] = lines;
  return [`<span class="t-key">${header}</span>`, ...rest].join('\n');
}

function highlightProperties(escaped: string): string {
  return escaped
    .replace(/^([#!].*)$/gm, '<span class="t-comment">$1</span>')
    .replace(/^([\w.$-]+)(\s*[=:]\s*)(.*)$/gm,
      '<span class="t-key">$1</span>$2<span class="t-string">$3</span>');
}

function highlightYaml(escaped: string): string {
  return escaped
    .replace(/^(\s*#.*)$/gm, '<span class="t-comment">$1</span>')
    .replace(/^(\s*-?\s*)([\w.$-]+)(:)/gm, '$1<span class="t-key">$2</span>$3')
    .replace(/:\s(&quot;.*?&quot;|&#39;.*?&#39;)/g, ': <span class="t-string">$1</span>')
    .replace(/\b(true|false|null|~)\b/g, '<span class="t-boolean">$1</span>');
}

const SQL_KEYWORDS =
  'SELECT|FROM|WHERE|JOIN|LEFT|RIGHT|INNER|OUTER|ON|GROUP BY|ORDER BY|HAVING|INSERT|INTO|VALUES|UPDATE|SET|DELETE|CREATE|TABLE|ALTER|DROP|INDEX|AND|OR|NOT|NULL|AS|DISTINCT|LIMIT|OFFSET|UNION|CASE|WHEN|THEN|ELSE|END';

function highlightSql(escaped: string): string {
  return escaped
    .replace(/(--.*)$/gm, '<span class="t-comment">$1</span>')
    .replace(new RegExp(`\\b(${SQL_KEYWORDS})\\b`, 'gi'), '<span class="t-tag">$1</span>')
    .replace(/(&#39;.*?&#39;)/g, '<span class="t-string">$1</span>')
    .replace(/\b(\d+)\b/g, '<span class="t-number">$1</span>');
}

function highlightLog(escaped: string): string {
  return escaped
    .replace(/\b(ERROR|SEVERE|FATAL)\b/g, '<span class="t-boolean">$1</span>')
    .replace(/\b(WARN|WARNING)\b/g, '<span class="t-number">$1</span>')
    .replace(/\b(INFO|DEBUG|TRACE)\b/g, '<span class="t-comment">$1</span>')
    // Fully-qualified Java class names and the `at …` frames of a stack trace.
    .replace(/\b((?:[a-z][\w$]*\.){2,}[A-Z][\w$]*)/g, '<span class="t-tag">$1</span>')
    .replace(/(\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}[.,]?\d*)/g, '<span class="t-attr">$1</span>');
}

/**
 * Wraps every case-insensitive occurrence of `term` in a `<mark>`.
 *
 * Runs over already-highlighted HTML, so it deliberately skips anything inside
 * a tag — otherwise searching for "span" or "class" would corrupt the markup.
 */
export function markMatches(html: string, term: string): string {
  const needle = term.trim();
  if (!needle) {
    return html;
  }

  const pattern = new RegExp(escapeRegex(escapeHtml(needle)), 'gi');
  let result = '';
  let insideTag = false;
  let buffer = '';

  for (const character of html) {
    if (character === '<') {
      result += buffer.replace(pattern, (match) => `<mark>${match}</mark>`);
      buffer = '';
      insideTag = true;
      result += character;
      continue;
    }
    if (character === '>' && insideTag) {
      insideTag = false;
      result += character;
      continue;
    }
    if (insideTag) {
      result += character;
    } else {
      buffer += character;
    }
  }

  return result + buffer.replace(pattern, (match) => `<mark>${match}</mark>`);
}

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/** Best-effort pretty printing. Returns the input unchanged if it cannot parse. */
export function prettyPrint(source: string, language: HighlightLanguage): string {
  try {
    if (language === 'json') {
      return JSON.stringify(JSON.parse(source), null, 2);
    }
    if (language === 'xml') {
      return prettyXml(source);
    }
  } catch {
    // Malformed input is exactly when a user most needs to see the raw text.
  }
  return source;
}

function prettyXml(source: string): string {
  const collapsed = source.replace(/>\s*</g, '><').trim();
  let depth = 0;

  return collapsed
    .replace(/</g, '\n<')
    .split('\n')
    .filter((line) => line.trim().length > 0)
    .map((line) => {
      const trimmed = line.trim();
      if (/^<\/[^>]+>/.test(trimmed)) {
        depth = Math.max(0, depth - 1);
      }
      const indented = '  '.repeat(depth) + trimmed;
      // Opening tags that are neither self-closing, a declaration, nor
      // immediately closed on the same line push the next line in.
      const opens = /^<[^!?/][^>]*[^/]>$/.test(trimmed) || /^<[^!?/][^>]*>$/.test(trimmed);
      const closesInline = /^<([^\s>/]+)[^>]*>.*<\/\1>$/.test(trimmed);
      if (opens && !closesInline) {
        depth++;
      }
      return indented;
    })
    .join('\n');
}
