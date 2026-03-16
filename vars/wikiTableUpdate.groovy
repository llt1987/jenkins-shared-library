// vars/wikiTableUpdate.groovy
def call(Map cfg = [:]) {
  // -------- Required inputs --------
  String wikiApi      = (cfg.wikiApi ?: env.WIKI_API ?: '').trim()
  String pageTitle    = (cfg.pageTitle ?: '').trim()

  // Which row to change (1-based indices)
  int    keyColumn    = (cfg.containsKey('keyColumn') ? cfg.keyColumn : 1) as int
  String keyValue     = (cfg.keyValue ?: '').trim()   // e.g., "CIMB MY"
  int    targetColumn = (cfg.containsKey('targetColumn') ? cfg.targetColumn : 3) as int
  String newValue     = (cfg.newValue ?: '').trim()   // wikitext put into column 3, e.g., "[[NOVA7-00-01-164]]"

  // Optional table selector (used if the page has multiple tables)
  String headerRegex  = (cfg.headerRegex ?: '(?i)\\!\\s*Client\\s*Name\\s*\\!\\!\\s*Stack\\s*Name\\s*\\!\\!\\s*Nova\\s*Version').trim()

  // -------- Auth / behavior --------
  String credentialsId = (cfg.credentialsId ?: 'mediawiki-bot-creds').trim()
  String lgdomain      = (cfg.lgdomain ?: '').trim()
  String assertLevel   = (cfg.assertLevel ?: 'user').trim()     // 'user' or 'bot'
  boolean markBot      = (cfg.containsKey('markBot') ? cfg.markBot : false) as boolean
  String editSummary   = (cfg.editSummary ?: "Update column ${targetColumn} for '${keyValue}'").trim()
  boolean dryRun       = (cfg.containsKey('dryRun') ? cfg.dryRun : false) as boolean

  if (!wikiApi)   error "wikiTableUpdate: 'wikiApi' is required."
  if (!pageTitle) error "wikiTableUpdate: 'pageTitle' is required."
  if (!keyValue)  error "wikiTableUpdate: 'keyValue' (the row key) is required."
  if (targetColumn < 1) error "wikiTableUpdate: 'targetColumn' must be >= 1."

  withCredentials([usernamePassword(credentialsId: credentialsId,
    usernameVariable: 'WIKI_USER', passwordVariable: 'WIKI_PASS')]) {

    withEnv([
      "WIKI_API=${wikiApi}",
      "PAGE_TITLE=${pageTitle}",
      "KEY_COL=${keyColumn}",
      "KEY_VALUE=${keyValue}",
      "TARGET_COL=${targetColumn}",
      "NEW_VALUE=${newValue}",
      "HEADER_REGEX=${headerRegex}",
      "ASSERT_LEVEL=${assertLevel}",
      "LGDOMAIN=${lgdomain}",
      "MARK_BOT=${markBot}",
      "DRY_RUN=${dryRun}",
      "EDIT_SUMMARY=${editSummary}"
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
)  # API:Login token [5](https://en.wikipedia.org/wiki/Help:Creating_a_bot)

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

echo "==> 4) CSRF token"
csrf_token=$(_curl -sG "$WIKI_API" \
  --data-urlencode action=query \
  --data-urlencode meta=tokens \
  --data-urlencode format=json \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["query"]["tokens"]["csrftoken"])'
)  # meta=tokens (csrf) [3](https://github.com/martyav/MediaWiki-Action-API-Code-Samples)

echo "==> 5) Read page"
page_json=$(_curl -sG "$WIKI_API" \
  --data-urlencode action=query \
  --data-urlencode prop=revisions \
  --data-urlencode rvslots=main \
  --data-urlencode 'rvprop=content|timestamp' \
  --data-urlencode "titles=${PAGE_TITLE}" \
  --data-urlencode format=json \
  --data-urlencode formatversion=2
)  # query+revisions (content|timestamp) [1](https://stackoverflow.com/questions/67163259/use-withcredentialsusernamepassword-in-jenkins-pipeline-library)

[ -z "${page_json:-}" ] && { echo "Empty response for '${PAGE_TITLE}'" >&2; exit 2; }

export PAGE_JSON="$page_json" KEY_COL TARGET_COL KEY_VALUE NEW_VALUE HEADER_REGEX

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
hdrx   = os.environ.get('HEADER_REGEX', '(?i)!\\s*Client\\s*Name\\s*!!\\s*Stack\\s*Name\\s*!!\\s*Nova\\s*Version')

if not keyval or tgtcol < 1 or keycol < 1:
    print(text); sys.exit(0)

# Locate the table by header (best-effort)
tables = []
start = 0
while True:
    i = text.find('{|', start)
    if i < 0: break
    j = text.find('|}', i)
    if j < 0: break
    tables.append((i, j+2))
    start = j+2

def display(s):
    s = s.strip()
    # Extract display text for [[Page|Text]] or [[Page]]
    if s.startswith('[[') and s.endswith(']]'):
        inner = s[2:-2]
        parts = inner.split('|', 1)
        return parts[1] if len(parts)==2 else parts[0]
    return re.sub(r"''+", "", s).strip()  # drop italics/bold

updated = False
for (a,b) in tables:
    tbl = text[a:b]
    # Check header row
    if not re.search(hdrx, tbl):
        continue

    # Split rows by "\n|-" markers; keep the separators
    parts = re.split(r'(\\n\\|-.*\\n)', tbl)
    rebuilt = []
    for idx,chunk in enumerate(parts):
        if idx%2 == 1:
            # row separator (e.g., "\n|- ...\n")
            rebuilt.append(chunk)
        else:
            # a block of one or more rows or header text
            lines = chunk.splitlines(keepends=True)
            buf = []
            rowbuf = []
            inrow = False
            def flush_row():
                nonlocal buf, rowbuf, updated
                if not rowbuf:
                    buf.extend([])
                    return
                row = ''.join(rowbuf)
                # Identify data rows that start with '|' (not header '!')
                if re.search(r'\\n\\|', '\\n'+row) or row.lstrip().startswith('|'):
                    # Merge continued lines and split cells: first cell starts with '|' and others with '||'
                    merged = row.replace('\\n|', '||').replace('\\n', '')
                    if '||' in merged:
                        cells = merged.split('||')
                        # Normalize first cell leading '|' if present
                        if cells[0].startswith('|'): cells[0]=cells[0][1:]
                        # Trim spaces
                        cells = [c.strip() for c in cells]
                        # Only process if enough columns
                        if len(cells) >= max(keycol, tgtcol):
                            key = display(cells[keycol-1])
                            if key == keyval:
                                cells[tgtcol-1] = newval
                                updated = True
                        # Rebuild the row
                        newrow = '| ' + (' || '.join(cells)) + '\\n'
                        buf.append(newrow)
                        rowbuf.clear(); return
                # default: keep as-is
                buf.append(row)
                rowbuf.clear()

            for ln in lines:
                # Row content may be on multiple lines; collect until next "|-" or end of chunk
                if ln.startswith('|-'):
                    # hit a separator inside this chunk unexpectedly; keep as-is
                    flush_row()
                    buf.append(ln); 
                else:
                    rowbuf.append(ln)
            flush_row()
            rebuilt.append(''.join(buf))

    newtbl = ''.join(rebuilt)
    if updated:
        text = text[:a] + newtbl + text[b:]
        break

print(text)
PY
)

if [ "x$OLD_TEXT" = "x$new_text" ]; then
  echo "==> No change (row not found or already up to date)."
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
# action=edit is POST-only (with csrf + base/start timestamps) [4](https://stackoverflow.com/questions/2163828/reading-cookies-via-https-that-were-set-using-http)

rm -rf "$tmpdir"
'''
    }
  }
}
