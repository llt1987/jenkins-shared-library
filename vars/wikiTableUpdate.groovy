// vars/wikiTableUpdate.groovy
//
// Update a single cell in a MediaWiki table by row key and column number,
// and print diagnostic "sample keys" from the target column (Patch 2).
//
// Typical use: update column 3 ("Nova Version") for the row whose
// "Client Name" (col 1) is "CIMB MY".
//
// Example (from a Jenkinsfile):
//
//   @Library('mw-lib') _
//   pipeline {
//     agent any
//     stages {
//       stage('Update Nova Version (col 3) for CIMB MY') {
//         steps {
//           wikiTableUpdate(
//             wikiApi:       'https://novawiki.novastp.com/wiki/novadoc/api.php',
//             pageTitle:     'YourPageTitleHere',
//             keyColumn:     1,                      // match on column 1 (Client Name)
//             keyValue:      'CIMB MY',              // row key to match
//             targetColumn:  3,                      // update the 3rd column
//             newValue:      '[[NOVA7-00-01-164]]',  // wikitext to write
//             credentialsId: 'mediawiki-bot-creds',  // Username: RealUser@BotName ; Password: Bot Password
//             assertLevel:   'user',
//             markBot:       false,
//             dryRun:        false,
//             diag:          true                    // Patch 2 diagnostics (default true)
//           )
//         }
//       }
//     }
//   }

def call(Map cfg = [:]) {
  // -------- Required inputs --------
  String wikiApi      = (cfg.wikiApi   ?: env.WIKI_API ?: '').trim()
  String pageTitle    = (cfg.pageTitle ?: '').trim()

  // Which row to change (1-based indices)
  int    keyColumn    = (cfg.containsKey('keyColumn')    ? cfg.keyColumn    : 1) as int
  String keyValue     = (cfg.keyValue ?: '').trim()   // e.g., "CIMB MY"
  int    targetColumn = (cfg.containsKey('targetColumn') ? cfg.targetColumn : 3) as int
  String newValue     = (cfg.newValue ?: '').trim()   // e.g., "[[NOVA7-00-01-164]]"

  // Optional table selector (used if the page has multiple tables)
  // Default tries to match:  ! Client Name !! Stack Name !! Nova Version
  String headerRegex  = (cfg.headerRegex ?: '(?i)\\!\\s*Client\\s*Name\\s*\\!\\!\\s*Stack\\s*Name\\s*\\!\\!\\s*Nova\\s*Version').trim()

  // -------- Auth / behavior --------
  String credentialsId = (cfg.credentialsId ?: 'mediawiki-bot-creds').trim()
  String lgdomain      = (cfg.lgdomain ?: '').trim()
  String assertLevel   = (cfg.assertLevel ?: 'user').trim()     // 'user' or 'bot'
  boolean markBot      = (cfg.containsKey('markBot') ? cfg.markBot : false) as boolean
  String editSummary   = (cfg.editSummary ?: "Update column ${targetColumn} for '${keyValue}'").trim()
  boolean dryRun       = (cfg.containsKey('dryRun') ? cfg.dryRun : false) as boolean

  // -------- Patch 2: diagnostics switch (default true) --------
  boolean diag         = (cfg.containsKey('diag') ? cfg.diag : true) as boolean

  if (!wikiApi)   error "wikiTableUpdate: 'wikiApi' is required."
  if (!pageTitle) error "wikiTableUpdate: 'pageTitle' is required."
  if (!keyValue)  error "wikiTableUpdate: 'keyValue' (the row key) is required."
  if (targetColumn < 1) error "wikiTableUpdate: 'targetColumn' must be >= 1."
  if (keyColumn    < 1) error "wikiTableUpdate: 'keyColumn' must be >= 1."

  withCredentials([usernamePassword(credentialsId: credentialsId,
    usernameVariable: 'WIKI_USER', passwordVariable: 'WIKI_PASS')]) {

    withEnv([
      "WIKI_API=${wikiApi}",
      "PAGE_TITLE=${pageTitle}",
      "KEY_COL=${keyColumn}",
      "KEY_VALUE=${keyValue}",
      "TARGET_COL=${targetColumn}",
      "NEW_VALUE=${newValue}",
      "HEADER_REGEX=${headerRegex}",   // always provided from Groovy
      "ASSERT_LEVEL=${assertLevel}",
      "LGDOMAIN=${lgdomain}",
      "MARK_BOT=${markBot}",
      "DRY_RUN=${dryRun}",
      "EDIT_SUMMARY=${editSummary}",
      "TABLE_DIAG=${diag}"             // Patch 2 diagnostics toggle
    ]) {
      sh '''#!/bin/sh
set -eu

need() { command -v "$1" >/dev/null 2>&1 || { echo "Missing: $1" >&2; exit 2; }; }
need curl; need python3

tmpdir="$(mktemp -d)"
cookiejar="$tmpdir/cookies.txt"
_curl() { curl -fSL --retry 2 --retry-delay 2 -b "$cookiejar" -c "$cookiejar" "$@"; }

echo "==> 1) Get login token"
login_token=$(
  _curl -sG "$WIKI_API" \
    --data-urlencode action=query \
    --data-urlencode meta=tokens \
    --data-urlencode type=login \
    --data-urlencode format=json \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["query"]["tokens"]["logintoken"])'
)

echo "==> 2) Login"
args="
  --data-urlencode action=login
  --data-urlencode format=json
  --data-urlencode lgname=${WIKI_USER}
  --data-urlencode lgpassword=${WIKI_PASS}
  --data-urlencode lgtoken=${login_token}
"
[ -n "${LGDOMAIN:-}" ] && args="$args --data-urlencode lgdomain=${LGDOMAIN}"
_curl -s -X POST "$WIKI_API" $args >/dev/null

echo "==> 3) Verify session"
who_json=$(_curl -sG "$WIKI_API" \
  --data-urlencode action=query \
  --data-urlencode meta=userinfo \
  --data-urlencode format=json)
who=$(printf %s "$who_json" | python3 -c 'import sys,json; j=json.load(sys.stdin); print(j.get("query",{}).get("userinfo",{}).get("name",""))')
if [ -z "$who" ] || printf %s "$who" | grep -Eq "^[0-9]+(\\.[0-9]+){3}$"; then
  echo "ERROR: Still anonymous; use HTTPS and verify credentials." >&2; exit 2
fi
echo "    Logged in as: $who"

echo "==> 4) CSRF token"
csrf_token=$(_curl -sG "$WIKI_API" \
  --data-urlencode action=query \
  --data-urlencode meta=tokens \
  --data-urlencode format=json \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["query"]["tokens"]["csrftoken"])'
)

echo "==> 5) Read page"
page_json=$(_curl -sG "$WIKI_API" \
  --data-urlencode action=query \
  --data-urlencode prop=revisions \
  --data-urlencode rvslots=main \
  --data-urlencode 'rvprop=content|timestamp' \
  --data-urlencode "titles=${PAGE_TITLE}" \
  --data-urlencode format=json \
  --data-urlencode formatversion=2
)

[ -z "${page_json:-}" ] && { echo "Empty response for '${PAGE_TITLE}'" >&2; exit 2; }

export PAGE_JSON="$page_json" KEY_COL TARGET_COL KEY_VALUE NEW_VALUE HEADER_REGEX TABLE_DIAG

old_text=$(
  python3 - <<'PY'
import os, json
d=json.loads(os.environ['PAGE_JSON'])
try:
    rev=d["query"]["pages"][0]["revisions"][0]
    content=rev.get("slots",{}).get("main",{}).get("content","")
    print(content)
except Exception:
    print("", end="")
PY
)

basets=$(
  python3 - <<'PY'
import os, json
d=json.loads(os.environ['PAGE_JSON'])
try:
    print(d["query"]["pages"][0]["revisions"][0].get("timestamp",""))
except Exception:
    print("", end="")
PY
)

if [ -z "${basets:-}" ]; then
  echo "ERROR: No basetimestamp found."; exit 2
fi

echo "==> 6) Update table (column ${TARGET_COL}) for key '${KEY_VALUE}'"
export OLD_TEXT="$old_text"
new_text=$(
  python3 - <<'PY'
import os, re, sys

text   = os.environ.get('OLD_TEXT','')
keycol = int(os.environ.get('KEY_COL','1'))
tgtcol = int(os.environ.get('TARGET_COL','3'))
keyval = os.environ.get('KEY_VALUE','').strip()
newval = os.environ.get('NEW_VALUE','').strip()
hdrx   = os.environ.get('HEADER_REGEX','').strip()     # provided from Groovy
diag   = os.environ.get('TABLE_DIAG','true').lower() in ('1','true','yes')

if not text or not keyval or tgtcol < 1 or keycol < 1:
    print(text); sys.exit(0)

# ---- Find tables delimited by {| ... |} ----
tables = []
pos = 0
while True:
    i = text.find('{|', pos)
    if i < 0: break
    j = text.find('|}', i)
    if j < 0: break
    tables.append((i, j+2))
    pos = j+2

def display(cell: str) -> str:
    """What a human sees: strip bold/italics; for [[Page|Text]] prefer Text."""
    s = cell.strip()
    if s.startswith('[[') and s.endswith(']]'):
        inner = s[2:-2]
        if '|' in inner:
            return inner.split('|', 1)[1]
        return inner
    return re.sub("''+", "", s).strip()

updated = False

# Precompiled pattern for row separators (no raw strings; double-escaped backslashes)
row_sep_pattern = '(?m)^[|]-\\s.*\\n?

for a, b in tables:
    tbl = text[a:b]
    header_ok = (not hdrx) or bool(re.search(hdrx, tbl))

    # ---- Patch 2: diagnostics (print sample keys from the match column) ----
    if diag:
        sample_keys = []
        for ch in re.split(row_sep_pattern, tbl):
            body = ch.replace('\\r\\n|', ' || ').replace('\\n|', ' || ').strip()
            if not body or body.startswith('!'):
                continue
            if body.startswith('|'):
                body = body[1:].lstrip()
            cells = [c.strip() for c in body.split(' || ')]
            if len(cells) >= keycol:
                sample_keys.append(display(cells[keycol-1]))
            if len(sample_keys) >= 5:
                break
        print(f"[#] Table candidate header_match={header_ok} sample keys (col {keycol}): {sample_keys}", file=sys.stderr)

    if not header_ok:
        continue

    # ---- Split rows on lines that start with "|-" (keep separators separately) ----
    row_seps   = re.findall(row_sep_pattern, tbl)
    row_chunks = re.split  (row_sep_pattern, tbl)

    rebuilt_rows = []

    for chunk in row_chunks:
        if not chunk.strip():
            rebuilt_rows.append(chunk)
            continue

        # Coalesce continuations: turn line-leading '|' into ' || ' splits
        body = chunk.replace('\\r\\n|', ' || ').replace('\\n|', ' || ')
        data = body.strip()

        # Header row starts with '!' — keep as-is
        if data.startswith('!'):
            rebuilt_rows.append(chunk)
            continue

        # Drop a single leading '|' for data rows
        if data.startswith('|'):
            data = data[1:].lstrip()

        cells = [c.strip() for c in data.split(' || ')]

        if len(cells) >= max(keycol, tgtcol):
            key = display(cells[keycol - 1])
            if key == keyval:
                cells[tgtcol - 1] = newval
                updated = True

        new_row = '| ' + ' || '.join(cells) + '\\n'
        rebuilt_rows.append(new_row)

    # ---- Re‑interleave with their "|-" separators ----
    out = []
    for i, row in enumerate(rebuilt_rows):
        out.append(row)
        if i < len(row_seps):
            out.append(row_seps[i])

    newtbl = ''.join(out)

    if updated:
        text = text[:a] + newtbl + text[b:]
        break

print(text)
PY
)

if [ "x$OLD_TEXT" = "x$new_text" ]; then
  echo "==> No change (row not found, or content already up to date)."
  rm -rf "$tmpdir"; exit 0
fi

if [ "${DRY_RUN:-false}" = "true" ]; then
  echo "==> DRY RUN — skipping save."
  rm -rf "$tmpdir"; exit 0
fi

echo "==> 7) Save edit"
startts=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
ASSERT_OPT="--data-urlencode assert=${ASSERT_LEVEL}"
BOT_OPT=""
[ "${MARK_BOT}" = "true" ] && BOT_OPT="--data-urlencode bot=true"

_curl -s -X POST "$WIKI_API" \
  --data-urlencode action=edit \
  --data-urlencode format=json \
  --data-urlencode "title=${PAGE_TITLE}" \
  --data-urlencode "text=${new_text}" \
  --data-urlencode "summary=${EDIT_SUMMARY}" \
  $ASSERT_OPT $BOT_OPT \
  --data-urlencode "basetimestamp=${basets}" \
  --data-urlencode "starttimestamp=${startts}" \
  --data-urlencode "token=${csrf_token}" \
| python3 -c 'import sys,json; print(json.dumps(json.load(sys.stdin), indent=2))'

rm -rf "$tmpdir"
'''
    }
  }
}