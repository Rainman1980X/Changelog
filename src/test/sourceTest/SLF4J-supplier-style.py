#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Refactor SLF4J supplier-style log chains into parameterized logs WITHOUT suppliers.

What it does
------------
Transforms chains like:

    log.atInfo()
       .setMessage("{}")
       .addArgument(() -> "My Error: "+e+", the parameter="+param)
       .log();

into:

    log.atInfo()
       .setMessage("My Error: {}, the parameter={}")
       .addArgument(e)
       .addArgument(param)
       .log();

Also supports:
- Multiple supplier addArgument(...) in one chain (order preserved)
- Block lambdas: () -> { return expr; } ⇒ () -> expr (internally normalized)
- Extra arguments after a supplier: .addArgument(() -> "Text", e, ctx)
  → appends " {}" placeholders for each extra and emits separate .addArgument(e), .addArgument(ctx)
- Multiline chains (DOTALL), nested parentheses, strings with quotes/escapes, ternaries

Safety rules
------------
- Only transforms chains that contain setMessage("{}") (exactly empty template)
- Non-supplier addArgument(...) calls remain untouched
- A supplier lambda with only a literal (no expressions) still contributes to the message

CLI
---
Usage:
    python3 SLF4J-supplier-style.py /path/to/src --dry-run
    python3 SLF4J-supplier-style.py /path/to/src
    python3 SLF4J-supplier-style.py --self-test
    python3 SLF4J-supplier-style.py --self-test-verbose

Notes:
- No .bak backups are created. Use Git for rollback.
"""

import argparse
import re
from pathlib import Path

# Match a full logging chain:
#   (log|LOGGER|logger).atTrace/Debug/Info/Warn/Error()
#   ... setMessage("{}") ...
#   ... log()
# Non-greedy .*? with DOTALL lets us span multiple lines until the closing .log().
CHAIN_RE = re.compile(
    r"""
    (?P<chain>
      \b(?:log|LOGGER|logger)\s*\.\s*at(?:Trace|Debug|Info|Warn|Error)\s*\(\)   # atXxx()
      .*?                                                                       # any
      \.\s*setMessage\s*\(\s*"\s*\{\}\s*"\s*\)                                  # setMessage("{}")
      .*?                                                                       # any
      \.\s*log\s*\(\s*\)                                                        # .log()
    )
    """,
    re.VERBOSE | re.DOTALL
)

# --- String & expression helpers ------------------------------------------------

STR_LIT_RE = re.compile(r'^\s*("([^"\\]|\\.)*"|\'([^\'\\]|\\.)*\')\s*$')
BLOCK_LAMBDA_RE = re.compile(r'^\s*\{\s*return\s+(?P<expr>.*?);\s*\}\s*$', re.DOTALL)

def unquote_java_literal(token: str) -> str:
    """Return the literal content of a Java string/char literal."""
    if token.startswith('"') and token.endswith('"'):
        return bytes(token[1:-1], "utf-8").decode("unicode_escape")
    if token.startswith("'") and token.endswith("'"):
        return bytes(token[1:-1], "utf-8").decode("unicode_escape")
    return token

def escape_java_string(s: str) -> str:
    """Escape for inclusion in a Java double-quoted string."""
    return s.replace("\\", "\\\\").replace('"', '\\"')

def split_top_level(expr: str, sep: str):
    """
    Split `expr` by a single-character separator at top-level (depth=0), respecting
    strings and parentheses. Returns a list of trimmed parts (empty parts removed).
    """
    parts, buf = [], []
    in_str, esc, quote, depth = False, False, None, 0
    for ch in expr:
        if in_str:
            buf.append(ch)
            if esc:
                esc = False
            elif ch == "\\":
                esc = True
            elif ch == quote:
                in_str, quote = False, None
        else:
            if ch in ('"', "'"):
                in_str, quote = True, ch; buf.append(ch)
            elif ch == '(':
                depth += 1; buf.append(ch)
            elif ch == ')':
                depth = max(0, depth - 1); buf.append(ch)
            elif ch == sep and depth == 0:
                part = "".join(buf).strip()
                if part: parts.append(part)
                buf.clear()
            else:
                buf.append(ch)
    part = "".join(buf).strip()
    if part:
        parts.append(part)
    return parts

def split_top_level_commas(expr: str):
    return split_top_level(expr, ',')

def split_top_level_concat(expr: str):
    return split_top_level(expr, '+')

def normalize_lambda(src: str) -> str:
    """Turn block lambda (() -> { return expr; }) into expression body 'expr'."""
    s = src.strip()
    m = BLOCK_LAMBDA_RE.match(s)
    return m.group("expr").strip() if m else s

def build_msg_and_args_from_lambda(lambda_src: str):
    """
    From a supplier lambda body, produce:
      - message part: literals concatenated with '{}' for non-literals
      - argument expressions: list of non-literal tokens, in order
    """
    expr = normalize_lambda(lambda_src)
    tokens = split_top_level_concat(expr)
    msg_parts, args = [], []
    for t in tokens:
        if STR_LIT_RE.match(t):
            msg_parts.append(unquote_java_literal(t))
        else:
            msg_parts.append("{}")
            # Collapse internal whitespace/newlines inside expression tokens for stable single-line output
            args.append(re.sub(r"\s+", " ", t.strip()))
    return "".join(msg_parts), args

# --- Chain transformation (parser-based) ---------------------------------------

def replace_supplier_calls(chain_text: str):
    """
    Walk through the chain text, find .addArgument(...), and:
    - if first arg is a supplier '() -> ...', turn its lambda body into message & args
    - append " {}" placeholders for any extra args after the supplier
    - emit .addArgument(expr) separately for each lambda-extracted arg and each extra arg
    Returns (new_chain_text, accumulated_message_string)
    """
    s = chain_text
    i = 0
    out = []
    accumulated_msg = ""
    while i < len(s):
        j = s.find(".addArgument(", i)
        if j == -1:
            out.append(s[i:])
            break
        out.append(s[i:j])

        # find matching ')' for this .addArgument(
        k = j + len(".addArgument(")
        depth = 1
        in_str = False
        esc = False
        quote = None
        while k < len(s):
            ch = s[k]
            if in_str:
                if esc: esc = False
                elif ch == "\\": esc = True
                elif ch == quote: in_str = False
                k += 1; continue
            if ch in ('"', "'"):
                in_str = True; quote = ch; k += 1; continue
            if ch == '(':
                depth += 1; k += 1; continue
            if ch == ')':
                depth -= 1; k += 1
                if depth == 0: break
                continue
            k += 1

        # extract arguments inside addArgument(...)
        arglist = s[j+len(".addArgument("):k-1].strip()
        args = split_top_level_commas(arglist)

        replacement = None
        if args and args[0].strip().startswith('() ->'):
            # Supplier case
            lam = args[0].split('->', 1)[1].strip()
            msg_from_lambda, lambda_exprs = build_msg_and_args_from_lambda(lam)

            rest = args[1:]
            if rest:
                # Add " {}" for each extra (prepend one space if not already spacing)
                if msg_from_lambda and not msg_from_lambda.endswith((" ", "\t")):
                    msg_from_lambda += " "
                msg_from_lambda += " ".join(["{}"] * len(rest))

            # Append to accumulated message with spacing between fragments if needed
            if accumulated_msg and not accumulated_msg.endswith((" ", "\t")):
                accumulated_msg += " "
            accumulated_msg += msg_from_lambda

            # Build replacement: separate .addArgument(...) for each
            pieces = []
            for a in lambda_exprs:
                pieces.append(f'.addArgument({a})')
            for a in rest:
                pieces.append(f'.addArgument({a})')
            replacement = "".join(pieces)
        else:
            # Not a supplier: keep original call as-is
            replacement = ".addArgument(" + arglist + ")"

        out.append(replacement)
        i = k

    return "".join(out), accumulated_msg

def transform_chain(chain_text: str) -> str:
    """
    Transform a single chain text. If no supplier found, return original.
    Otherwise, replace setMessage("{}") with the accumulated message.
    """
    new_chain, msg = replace_supplier_calls(chain_text)
    if not msg:
        return chain_text
    # Replace only the first setMessage("{}") occurrence within the chain
    new_chain = re.sub(
        r'(\.\s*setMessage\s*\()\s*"\s*\{\}\s*"\s*(\))',
        lambda m: f'{m.group(1)}"{escape_java_string(msg)}"{m.group(2)}',
        new_chain,
        count=1
    )
    return new_chain

def transform_source(src: str) -> str:
    """Apply transformation to every matched chain in a source file."""
    return CHAIN_RE.sub(lambda m: transform_chain(m.group("chain")), src)

# --- File processing & CLI -----------------------------------------------------

def process_file(path: Path, dry_run: bool) -> bool:
    text = path.read_text(encoding="utf-8", errors="ignore")
    out = transform_source(text)
    if out != text:
        if dry_run:
            return True
        path.write_text(out, encoding="utf-8")
        return True
    return False

# --- Self-tests (including verbose diagnostics) --------------------------------

def _diag_show_diff(before: str, after: str) -> None:
    import difflib
    print("---- BEFORE ----")
    print(before)
    print("---- AFTER  ----")
    print(after)
    print("---- DIFF   ----")
    for line in difflib.unified_diff(before.splitlines(), after.splitlines(),
                                     fromfile="before", tofile="after", lineterm=""):
        print(line)

def run_self_test() -> bool:
    """Smoke tests for core scenarios."""
    def check(before: str, expect_contains: list[str], expect_equal: bool = False):
        after = transform_source(before)
        if expect_equal:
            assert after == before, "Expected no change"
        else:
            for frag in expect_contains:
                assert frag in after, f"Missing fragment: {frag}"
        return before, after

    ok = True
    try:
        # 1) simple expression lambda with two args
        b1 = r'''
        class Demo {
            void test(Exception e, String param) {
                log.atInfo().setMessage("{}").addArgument(() -> "My Error: "+e+", the parameter="+param).log();
            }
        }'''
        check(b1, ['.setMessage("My Error: {}, the parameter={}")', '.addArgument(e)', '.addArgument(param)'])

        # 2) block lambda
        b2 = r'''
        class Demo {
            void t(Exception e, int n) {
                log.atWarn()
                   .setMessage("{}")
                   .addArgument(() -> { return "N="+n+", cause="+e; })
                   .log();
            }
        }'''
        check(b2, ['.setMessage("N={}, cause={}")', '.addArgument(n)', '.addArgument(e)'])

        # 3) multiple suppliers
        b3 = r'''
        class Demo {
            void t(String userId, String file, long size) {
                log.atWarn()
                   .setMessage("{}")
                   .addArgument(() -> "User="+userId+" ")
                   .addArgument(() -> "File:"+file+" size="+size)
                   .log();
            }
        }'''
        check(b3, ['.setMessage("User={} File:{} size={}")',
                   '.addArgument(userId)', '.addArgument(file)', '.addArgument(size)'])

        # 4) mixed non-supplier argument (kept)
        b4 = r'''
        class Demo {
            void t(String id, Throwable cause) {
                log.atError()
                   .setMessage("{}")
                   .addArgument(() -> "ID="+id)
                   .addArgument(cause)
                   .log();
            }
        }'''
        check(b4, ['.setMessage("ID={}")', '.addArgument(id)', '.addArgument(cause)'])

        # 5) only literal lambda (no args) + no rest → message only
        b5 = r'''
        class Demo {
            void t() {
                log.atDebug().setMessage("{}").addArgument(() -> "Static text only").log();
            }
        }'''
        check(b5, ['.setMessage("Static text only")'])

        # 6) literal + one extra arg → add " {}" for extra
        b6 = r'''
        class Demo {
            void t(Exception e) {
                log.atDebug().setMessage("{}").addArgument(() -> "Static text only", e).log();
            }
        }'''
        check(b6, ['.setMessage("Static text only {}")', '.addArgument(e)'])

        # 7) expr + two rest args
        b7 = r'''
        class Demo {
            void t(Exception e, String ctx) {
                log.atDebug().setMessage("{}").addArgument(() -> "Oops: "+e, ctx, e).log();
            }
        }'''
        after_b7 = transform_source(b7)
        assert '.setMessage("Oops: {} {} {}")' in after_b7
        assert after_b7.count('.addArgument(e)') == 2
        assert '.addArgument(ctx)' in after_b7

        # 8) ternary + cause (multiline) — ensure spacing between fragments
        b8 = r'''
        class Demo {
            void t(String id, Throwable cause) {
                log.atError()
                   .setMessage("{}")
                   .addArgument(() -> "ID="+id)
                   .addArgument(() -> "C="+(cause != null
                                            ? cause.getMessage()
                                            : "none"), cause)
                   .log();
            }
        }'''
        after_b8 = transform_source(b8)
        # Expect a space between "ID={}" and the next supplier fragment
        assert '.setMessage("ID={} C={} {}")' in after_b8
        assert '.addArgument(id)' in after_b8
        assert '.addArgument((cause != null ? cause.getMessage() : "none"))' in after_b8
        assert '.addArgument(cause)' in after_b8

    except AssertionError as e:
        print(f"Self-test assertion failed: {e}")
        ok = False
    return ok

def run_self_test_verbose() -> bool:
    """Verbose diagnostics for self-tests with before/after/diff output."""
    cases = []

    cases.append(("simple_two_args", r'''
        class Demo {
            void test(Exception e, String param) {
                log.atInfo().setMessage("{}").addArgument(() -> "My Error: "+e+", the parameter="+param).log();
            }
        }''',
        ['.setMessage("My Error: {}, the parameter={}")', '.addArgument(e)', '.addArgument(param)']))

    cases.append(("block_lambda", r'''
        class Demo {
            void t(Exception e, int n) {
                log.atWarn()
                   .setMessage("{}")
                   .addArgument(() -> { return "N="+n+", cause="+e; })
                   .log();
            }
        }''',
        ['.setMessage("N={}, cause={}")', '.addArgument(n)', '.addArgument(e)']))

    cases.append(("multiple_suppliers", r'''
        class Demo {
            void t(String userId, String file, long size) {
                log.atWarn()
                   .setMessage("{}")
                   .addArgument(() -> "User="+userId+" ")
                   .addArgument(() -> "File:"+file+" size="+size)
                   .log();
            }
        }''',
        ['.setMessage("User={} File:{} size={}")',
         '.addArgument(userId)', '.addArgument(file)', '.addArgument(size)']))

    cases.append(("mixed_non_supplier", r'''
        class Demo {
            void t(String id, Throwable cause) {
                log.atError()
                   .setMessage("{}")
                   .addArgument(() -> "ID="+id)
                   .addArgument(cause)
                   .log();
            }
        }''',
        ['.setMessage("ID={}")', '.addArgument(id)', '.addArgument(cause)']))

    cases.append(("only_literal", r'''
        class Demo {
            void t() {
                log.atDebug().setMessage("{}").addArgument(() -> "Static text only").log();
            }
        }''',
        ['.setMessage("Static text only")']))

    cases.append(("literal_plus_one_rest", r'''
        class Demo {
            void t(Exception e) {
                log.atDebug().setMessage("{}").addArgument(() -> "Static text only", e).log();
            }
        }''',
        ['.setMessage("Static text only {}")', '.addArgument(e)']))

    cases.append(("expr_plus_two_rest", r'''
        class Demo {
            void t(Exception e, String ctx) {
                log.atDebug().setMessage("{}").addArgument(() -> "Oops: "+e, ctx, e).log();
            }
        }''',
        ['.setMessage("Oops: {} {} {}")', '.addArgument(ctx)']))

    cases.append(("ternary_plus_cause_multiline", r'''
        class Demo {
            void t(String id, Throwable cause) {
                log.atError()
                   .setMessage("{}")
                   .addArgument(() -> "ID="+id)
                   .addArgument(() -> "C="+(cause != null
                                            ? cause.getMessage()
                                            : "none"), cause)
                   .log();
            }
        }''',
        ['.setMessage("ID={} C={} {}")',
         '.addArgument(id)',
         '.addArgument((cause != null ? cause.getMessage() : "none"))',
         '.addArgument(cause)']))

    all_ok = True
    for name, before, expects in cases:
        print(f"\n=== CASE: {name} ===")
        after = transform_source(before)
        ok = True
        for frag in expects:
            if frag not in after:
                print(f"❌ Missing fragment: {frag}")
                ok = False
        if not ok:
            _diag_show_diff(before, after)
            all_ok = False
        else:
            print("✅ OK")
    return all_ok

def main():
    ap = argparse.ArgumentParser(description="Refactor SLF4J supplier logs to parameterized logs (no suppliers, no backups).")
    ap.add_argument("root", nargs="?", help="Root directory with Java sources")
    ap.add_argument("--dry-run", action="store_true", help="Report changes without writing")
    ap.add_argument("--self-test", action="store_true", help="Run internal self-test and exit")
    ap.add_argument("--self-test-verbose", action="store_true", help="Run verbose self-test with diffs and exit")
    args = ap.parse_args()

    if args.self_test_verbose:
        raise SystemExit(0 if run_self_test_verbose() else 1)

    if args.self_test:
        if run_self_test():
            print("Self-test passed: all example transformations are correct.")
            raise SystemExit(0)
        else:
            print("Self-test FAILED.")
            raise SystemExit(1)

    if not args.root:
        print("Error: please provide a root directory or use --self-test/--self-test-verbose")
        raise SystemExit(2)

    root = Path(args.root)
    if not root.exists():
        print(f"Error: path not found: {root}")
        raise SystemExit(3)

    files_total = 0
    changed = 0
    for p in root.rglob("*.java"):
        files_total += 1
        try:
            if process_file(p, args.dry_run):
                changed += 1
                print(("[DRY] " if args.dry_run else "") + f"Transformed: {p}")
        except Exception as e:
            print(f"⚠️ Error processing {p}: {e}")

    print(f"\nScanned {files_total} files. {'Would change' if args.dry_run else 'Changed'} {changed} files.")

if __name__ == "__main__":
    main()
