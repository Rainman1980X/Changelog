#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from __future__ import annotations

import argparse
import datetime as _dt
import html
import json
import re
import sys
from pathlib import Path
from typing import Dict, List, Optional

class Finding:
    __slots__ = ('file', 'line', 'kind', 'message', 'snippet')
    def __init__(self, file: str, line: int, kind: str, message: str, snippet: str):
        self.file = file
        self.line = line
        self.kind = kind
        self.message = message
        self.snippet = snippet
    def as_dict(self) -> Dict:
        return {'file': self.file, 'line': self.line, 'kind': self.kind, 'message': self.message, 'snippet': self.snippet}

class ScanResult:
    def __init__(self) -> None:
        self.findings: List[Finding] = []
        self.suggestions: Dict[str, List[Dict[str, str]]] = {}
        self.totals: Dict[str, int] = {}
    def add_finding(self, f: Finding) -> None:
        self.findings.append(f)
    def add_suggestion(self, file_path: Path, before: str, after: str, note: str) -> None:
        self.suggestions.setdefault(str(file_path), []).append({'before': before, 'after': after, 'note': note})
    def finalize(self) -> None:
        totals: Dict[str, int] = {}
        for f in self.findings:
            totals[f.kind] = totals.get(f.kind, 0) + 1
        self.totals = totals

IMPORT_LOG4J1 = re.compile(r'^\s*import\s+org\.apache\.log4j\.(\w+)\s*;', re.MULTILINE)
IMPORT_LOG4J2 = re.compile(r'^\s*import\s+org\.apache\.logging\.log4j\.(\w+)\s*;', re.MULTILINE)
IMPORT_SLF4J  = re.compile(r'^\s*import\s+org\.slf4j\.(\w+)\s*;', re.MULTILINE)
LOGGER_DECL_LOG4J1 = re.compile(r'Logger\s+(\w+)\s*=\s*Logger\.getLogger\(([^)]+)\)')
LOGGER_DECL_LOG4J2 = re.compile(r'Logger\s+(\w+)\s*=\s*LogManager\.getLogger\(([^)]*)\)')
LOGGER_DECL_SLF4J  = re.compile(r'Logger\s+(\w+)\s*=\s*LoggerFactory\.getLogger\(([^)]+)\)')
IS_DEBUG_ENABLED = re.compile(r'\.\s*isDebugEnabled\s*\(\s*\)')
CONCAT_LOGGING   = re.compile(r'\.\s*(debug|info|warn|error|trace|fatal)\s*\((?P<msg>".*?"|\w+)(\s*\+\s*.+)+\)')
EXCEPTION_LOG_NO_CAUSE = re.compile(r'\.\s*(error|warn)\s*\(\s*"[^"]*"\s*\+\s*e(\.|\.getMessage\(\))?\s*\)')
MDC_LOG4J1 = re.compile(r'\borg\.apache\.log4j\.(MDC|NDC)\b')
MDC_CALLS  = re.compile(r'\b(MDC|NDC)\.(put|push|pop|remove)\b')

def scan_java_file(path: Path, sr: ScanResult) -> None:
    text = path.read_text(encoding='utf-8', errors='ignore')
    lines = text.splitlines()
    for m in IMPORT_LOG4J1.finditer(text):
        ln = text[:m.start()].count('\n') + 1
        sr.add_finding(Finding(str(path), ln, 'log4j1-import', f'Import uses log4j 1.x: {m.group(0).strip()}', m.group(0).strip()))
    for m in IMPORT_LOG4J2.finditer(text):
        ln = text[:m.start()].count('\n') + 1
        sr.add_finding(Finding(str(path), ln, 'log4j2-import', f'Import uses log4j 2.x API: {m.group(0).strip()}', m.group(0).strip()))
    for m in IMPORT_SLF4J.finditer(text):
        ln = text[:m.start()].count('\n') + 1
        sr.add_finding(Finding(str(path), ln, 'slf4j-import', f'Import uses SLF4J API: {m.group(0).strip()}', m.group(0).strip()))
    for m in LOGGER_DECL_LOG4J1.finditer(text):
        ln = text[:m.start()].count('\n') + 1
        sr.add_finding(Finding(str(path), ln, 'logger-decl-log4j1', f'Logger declared with log4j1: {m.group(0).strip()}', m.group(0).strip()))
        before = m.group(0)
        after = f'Logger {m.group(1)} = LoggerFactory.getLogger({m.group(2)});'
        sr.add_suggestion(path, before, after, 'Replace log4j1 logger declaration with SLF4J.')
    for m in LOGGER_DECL_LOG4J2.finditer(text):
        ln = text[:m.start()].count('\n') + 1
        sr.add_finding(Finding(str(path), ln, 'logger-decl-log4j2', f'Logger declared with log4j2 API: {m.group(0).strip()}', m.group(0).strip()))
        before = m.group(0)
        arg = m.group(2) if m.group(2).strip() else f'{path.stem}.class'
        after = f'Logger {m.group(1)} = LoggerFactory.getLogger({arg});'
        sr.add_suggestion(path, before, after, 'Replace log4j2 API logger with SLF4J to unify API.')
    for m in LOGGER_DECL_SLF4J.finditer(text):
        ln = text[:m.start()].count('\n') + 1
        sr.add_finding(Finding(str(path), ln, 'logger-decl-slf4j', f'Logger already SLF4J: {m.group(0).strip()}', m.group(0).strip()))
    for m in IS_DEBUG_ENABLED.finditer(text):
        ln = text[:m.start()].count('\n') + 1
        sr.add_finding(Finding(str(path), ln, 'isDebugEnabled', 'Guard detected; consider parameterized logging to avoid string concat cost.', lines[ln-1].strip()))
    for m in CONCAT_LOGGING.finditer(text):
        ln = text[:m.start()].count('\n') + 1
        raw = lines[ln-1].strip()
        sr.add_finding(Finding(str(path), ln, 'concat-logging', 'String concatenation in logging; migrate to parameterized logging.', raw))
        sr.add_suggestion(path, raw, '// TODO: Replace with parameterized placeholders, e.g. log.info("...", arg1, arg2)', 'Manual check required for parameter binding.')
    for m in EXCEPTION_LOG_NO_CAUSE.finditer(text):
        ln = text[:m.start()].count('\n') + 1
        before = lines[ln-1].strip()
        sr.add_finding(Finding(str(path), ln, 'exception-without-cause', 'Logging exception as string; pass the throwable as last argument.', before))
        after = re.sub(r'"\s*\+\s*e(\.getMessage\(\))?', '", e', before)
        sr.add_suggestion(path, before, after, 'Pass Throwable as last parameter to preserve stack trace.')
    if MDC_LOG4J1.search(text) or MDC_CALLS.search(text):
        for i, line in enumerate(lines, start=1):
            if 'MDC.' in line or 'NDC.' in line:
                sr.add_finding(Finding(str(path), i, 'mdc-ndc', 'MDC/NDC usage found; migrate to SLF4J MDC or Log4j2 ThreadContext.', line.strip()))
                sr.add_suggestion(path, line.strip(), line.replace('NDC.', 'ThreadContext.').replace('MDC.', 'MDC.'), 'Replace NDC with ThreadContext; prefer org.slf4j.MDC or org.apache.logging.log4j.ThreadContext. Manual mapping required.')

def scan_config_file(path: Path, sr: ScanResult) -> None:
    text = path.read_text(encoding='utf-8', errors='ignore')
    if path.suffix in ('.properties', '.xml'):
        if 'log4j.rootLogger' in text or 'org.apache.log4j' in text or '<log4j:' in text or 'log4j.appender' in text:
            sr.add_finding(Finding(str(path), 1, 'log4j1-config', 'Log4j 1.x configuration file detected.', ''))
        if '<Configuration' in text and 'log4j' in text:
            sr.add_finding(Finding(str(path), 1, 'log4j2-config', 'Log4j 2.x configuration detected.', ''))

def scan_repo(repo_root: Path) -> ScanResult:
    sr = ScanResult()
    for path in sorted(repo_root.rglob('*')):
        if path.is_file():
            if path.suffix == '.java':
                scan_java_file(path, sr)
            elif path.suffix in ('.properties', '.xml') and 'log4j' in path.name.lower():
                scan_config_file(path, sr)
    sr.finalize()
    return sr

def render_html(result: ScanResult) -> str:
    def esc(s: str) -> str:
        return html.escape(s, quote=False)
    by_file: Dict[str, List[Finding]] = {}
    for f in result.findings:
        by_file.setdefault(f.file, []).append(f)
    out = []
    out.append('<!doctype html>\n<html>\n<head>\n<meta charset="utf-8">\n<title>Logging Migration Scan Report</title>\n<meta name="viewport" content="width=device-width, initial-scale=1">\n<style>')
    out.append('body { font-family: system-ui, -apple-system, Segoe UI, Roboto, Arial, sans-serif; margin: 2rem; }')
    out.append('h1, h2, h3 { margin: 0.5rem 0; }')
    out.append('.summary { display: grid; grid-template-columns: repeat(auto-fill,minmax(220px,1fr)); gap: 1rem; }')
    out.append('.card { border: 1px solid #ddd; border-radius: 10px; padding: 1rem; box-shadow: 0 1px 4px rgba(0,0,0,0.05);}')
    out.append('table { border-collapse: collapse; width: 100%; }')
    out.append('th, td { border: 1px solid #eee; padding: 6px 8px; font-size: 0.9rem; }')
    out.append('th { background: #fafafa; text-align: left; }')
    out.append('.code { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; background: #f9f9f9; padding: 6px; border-radius: 6px; }')
    out.append('.finding-kind { font-size: 0.8rem; padding: 2px 6px; border-radius: 6px; background: #eef; display: inline-block; }')
    out.append('.diff { white-space: pre-wrap; background: #0a0a0a; color: #eee; padding: 8px; border-radius: 6px; }')
    out.append('.diff .minus { color: #ff8a8a; }')
    out.append('.diff .plus { color: #8aff8a; }')
    out.append('small { color: #555; }')
    out.append('footer { margin-top: 2rem; color: #666; font-size: 0.9rem; }')
    out.append('</style>\n</head>\n<body>')
    out.append('<h1>Logging Migration Scan Report</h1>')
    out.append('<p><small>Generated: ' + _dt.datetime.utcnow().isoformat() + 'Z</small></p>')
    out.append('<h2>Summary</h2><div class="summary">')
    for k, v in sorted(result.totals.items(), key=lambda kv: (-kv[1], kv[0])):
        out.append('<div class="card"><h3>' + esc(k) + '</h3><p><strong>' + str(v) + '</strong> occurrences</p></div>')
    out.append('</div>')
    out.append('<h2>Findings by file</h2>')
    for file in sorted(by_file.keys()):
        findings = by_file[file]
        out.append('<h3>' + esc(file) + '</h3>')
        out.append('<table><thead><tr><th>Line</th><th>Kind</th><th>Message</th><th>Code</th></tr></thead><tbody>')
        for f in findings:
            out.append('<tr><td>' + str(f.line) + '</td><td><span class="finding-kind">' + esc(f.kind) + '</span></td><td>' + esc(f.message) + '</td><td><code class="code">' + esc(f.snippet) + '</code></td></tr>')
        out.append('</tbody></table>')
        suggs = result.suggestions.get(file, [])
        if suggs:
            out.append('<h4>Suggested changes (dry-run)</h4>')
            for s in suggs:
                before = esc(s['before']); after = esc(s['after']); note = esc(s['note'])
                out.append('<div class="card"><p><strong>Note:</strong> ' + note + '</p><div class="diff"><span class="minus">- ' + before + '</span>\n<span class="plus">+ ' + after + '</span></div></div>')
    out.append('<footer>Log migration target: SLF4J API with Log4j 2 backend. Use OpenRewrite for safe bulk edits.</footer>')
    out.append('</body></html>')
    return '\n'.join(out)

def make_demo(root: Path) -> Path:
    files = {
        root / 'module-a' / 'src' / 'main' / 'java' / 'com' / 'acme' / 'legacy' / 'LegacyService.java': """
            package com.acme.legacy;

            import org.apache.log4j.Logger;
            import org.apache.log4j.Level;
            import org.apache.log4j.MDC;
            import org.apache.log4j.NDC;

            public class LegacyService {
                private static final Logger LOG = Logger.getLogger(LegacyService.class);

                public void process(String id) {
                    MDC.put("reqId", id);
                    NDC.push("tenant-42");
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Start process for id=" + id);
                    }
                    try {
                        LOG.info("Working on " + id);
                    } catch (Exception e) {
                        LOG.error("Failed for " + id + ": " + e.getMessage());
                    } finally {
                        NDC.pop();
                        MDC.remove("reqId");
                    }
                }
            }
        """ ,
        root / 'module-a' / 'src' / 'main' / 'resources' / 'log4j.properties': """
            # log4j 1.x properties
            log4j.rootLogger=INFO, stdout
            log4j.appender.stdout=org.apache.log4j.ConsoleAppender
            log4j.appender.stdout.layout=org.apache.log4j.PatternLayout
            log4j.appender.stdout.layout.ConversionPattern=%d{ISO8601} %-5p %c - %m%n
        """ ,
        root / 'module-b' / 'src' / 'main' / 'java' / 'com' / 'acme' / 'modern' / 'ModernService.java': """
            package com.acme.modern;

            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;

            public class ModernService {
                private static final Logger log = LoggerFactory.getLogger(ModernService.class);
                public void go(int n) {
                    log.info("n = {}", n);
                    try {
                        // ...
                    } catch (RuntimeException e) {
                        log.error("boom", e);
                    }
                }
            }
        """ ,
        root / 'module-c' / 'src' / 'main' / 'java' / 'com' / 'acme' / 'v2' / 'V2Service.java': """
            package com.acme.v2;

            import org.apache.logging.log4j.LogManager;
            import org.apache.logging.log4j.Logger;

            public class V2Service {
                private static final Logger logger = LogManager.getLogger(V2Service.class);
                public void run() {
                    logger.warn("running v2...");
                }
            }
        """ ,
    }
    for p, c in files.items():
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text('\n'.join(line[12:] for line in c.strip('\n').splitlines()) + '\n', encoding='utf-8')
    return root

def main(argv: Optional[List[str]] = None) -> int:
    ap = argparse.ArgumentParser(description='Scan Java repo for logging migration hints (SLF4J + Log4j2).')
    ap.add_argument('--root', type=Path, help='Path to repository root to scan')
    ap.add_argument('--out', type=Path, required=True, help='Output directory for reports')
    ap.add_argument('--json', type=str, default='scan-results.json', help='JSON filename (default: scan-results.json)')
    ap.add_argument('--html', type=str, default='index.html', help='HTML filename (default: index.html)')
    ap.add_argument('--summary', action='store_true', help='Print summary table to stdout')
    ap.add_argument('--fail-on', action='append', default=[], help='Finding kind that triggers non-zero exit (repeatable)')
    ap.add_argument('--make-demo', action='store_true', help='Create a demo monorepo and scan it (ignores --root)')
    args = ap.parse_args(argv)

    out_dir: Path = args.out
    out_dir.mkdir(parents=True, exist_ok=True)

    if args.make_demo:
        repo_root = out_dir / 'demo-repo'
        repo_root.mkdir(parents=True, exist_ok=True)
        repo_root = make_demo(repo_root)
    else:
        if not args.root or not args.root.exists():
            print('ERROR: --root must exist (or use --make-demo).', file=sys.stderr)
            return 2
        repo_root = args.root

    result = scan_repo(repo_root)

    payload = {
        'generated_at': _dt.datetime.utcnow().isoformat() + 'Z',
        'root': str(repo_root),
        'totals': result.totals,
        'findings': [f.as_dict() for f in result.findings],
        'suggestions': result.suggestions,
    }
    json_path = out_dir / args.json
    json_path.write_text(json.dumps(payload, indent=2), encoding='utf-8')

    html_report = render_html(result)
    html_path = out_dir / args.html
    html_path.write_text(html_report, encoding='utf-8')

    if args.summary:
        print('== Log Migration Scanner Summary ==')
        for k, v in sorted(result.totals.items(), key=lambda kv: (-kv[1], kv[0])):
            print(f'{k:28s} {v}')
        print(f'Findings total: {len(result.findings)}')
        print(f'HTML: {html_path}')
        print(f'JSON: {json_path}')

    failing = set(args.fail_on or [])
    if failing:
        hit = any(f.kind in failing for f in result.findings)
        return 1 if hit else 0
    return 0

if __name__ == '__main__':
    sys.exit(main())
