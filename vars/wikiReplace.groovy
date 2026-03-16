// vars/wikiReplace.groovy
def call(Map cfg = [:]) {
  // -------- Required / common options --------
  String wikiApi      = (cfg.wikiApi ?: env.WIKI_API ?: '').trim()
  String pageTitle    = (cfg.pageTitle ?: '').trim()
  String find         = cfg.containsKey('find') ? "${cfg.find}" : ''
  String replace      = cfg.containsKey('replace') ? "${cfg.replace}" : null  // may be null -> prompt
  boolean useRegex    = (cfg.containsKey('useRegex') ? cfg.useRegex : true) as boolean

  // -------- Auth / behavior toggles --------
  String credentialsId = (cfg.credentialsId ?: 'mediawiki-bot-creds').trim()
  String lgdomain      = (cfg.lgdomain ?: '').trim()
  String assertLevel   = (cfg.assertLevel ?: 'user').trim()         // 'user' or 'bot'
  boolean markBot      = (cfg.containsKey('markBot') ? cfg.markBot : false) as boolean

  // -------- Other niceties --------
  String editSummary   = (cfg.editSummary ?: 'Automated: wikiReplace from Jenkins').trim()
  boolean dryRun       = (cfg.containsKey('dryRun') ? cfg.dryRun : false) as boolean

  if (!wikiApi)    error "wikiReplace: 'wikiApi' is required (e.g., https://wiki.example.com/w/api.php)"
  if (!pageTitle)  error "wikiReplace: 'pageTitle' is required"
  if (!find)       error "wikiReplace: 'find' (pattern or literal) is required"

  // If replace not provided, ask the user at runtime
  if (replace == null) {
    def resp = input(
      message: "Enter replacement text for page '${pageTitle}'",
      parameters: [text(name: 'REPLACE', defaultValue: '', description: 'Replacement text (can be multiline)')]
    )
    replace = "${resp}"
  }

  // Write find/replace to files to avoid quoting pitfalls
  writeFile file: '.wiki_find',    text: find
  writeFile file: '.wiki_replace', text: replace

  withCredentials([usernamePassword(credentialsId: credentialsId,
    usernameVariable: 'WIKI_USER', passwordVariable: 'WIKI_PASS')]) {

    withEnv([
      "WIKI_API=${wikiApi}",
      "PAGE_TITLE=${pageTitle}",
      "USE_REGEX=${useRegex}",
      "EDIT_SUMMARY=${editSummary}",
      "ASSERT_LEVEL=${assertLevel}",
      "MARK_BOT=${markBot}",
      "LGDOMAIN=${lgdomain}",
      "DRY_RUN=${dryRun}"
    ]) {
      sh '''#!/bin/sh
set -eu

require_bin() { command -v "$1" >/dev/null 2>&1 || { echo "Missing required tool: $1" >&2; exit 2; }; }
require_bin curl
require_bin python3

FIND="$(cat .wiki_find)"
REPLACE="$(cat .wiki_replace)"

tmpdir="$(mktemp -d)"
cookiejar="$tmpdir/cookies.txt"

# Always reuse cookies across requests
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
# (Login requires a login token)  [API:Login]  ⟂

echo "==> 2) Login (Bot Password: RealUser@BotName)"
LOGIN_ARGS="
  --data-urlencode action=login
  --data-urlencode format=json
  --data-urlencode lgname=${WIKI_USER}
  --data-urlencode lgpassword=${WIKI_PASS}
  --data-urlencode lgtoken=${login_token}
"
[ -n "${LGDOMAIN:-}" ] && LOGIN_ARGS="$LOGIN_ARGS --data-urlencode lgdomain=${LGDOMAIN}"
_curl -s -X POST "$WIKI_API" $LOGIN_ARGS >/dev/null
# (Use Bot Passwords for automation; username is RealUser@BotName)  ⟂

echo "==> 3) Verify session (meta=userinfo)"
who_json=$(_curl -sG "$WIKI_API" \
  --data-urlencode action=query \
  --data-urlencode meta=userinfo \
  --data-urlencode format=json)
who=$(
  printf %s "$who_json" | python3 -c 'import sys,json; j=json.load(sys.stdin); print(j.get("query",{}).get("userinfo",{}).get("name",""))'
)
if [ -z "$who" ] || printf %s "$who" | grep -Eq "^[0-9]+(\\.[0-9]+){3}$"; then
  echo "ERROR: Not logged in (userinfo shows anonymous/IP). Use HTTPS endpoint and verify credentials." >&2
  exit 2
fi
echo "    Logged in as: $who"
# (HTTPS ensures Secure cookies round-trip)  ⟂

echo "==> 4) Get CSRF token (required for edit)"
csrf_token=$(
  _curl -sG "$WIKI_API" \
    --data-urlencode action=query \
    --data-urlencode meta=tokens \
    --data-urlencode format=json \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["query"]["tokens"]["csrftoken"])'
)
# (Data-modifying actions require a CSRF token)  ⟂

echo "==> 5) Read full page wikitext"
page_json=$(
  _curl -sG "$WIKI_API" \
    --data-urlencode action=query \
    --data-urlencode prop=revisions \
    --data-urlencode rvslots=main \
    --data-urlencode 'rvprop=content|timestamp' \
    --data-urlencode "titles=${PAGE_TITLE}" \
    --data-urlencode format=json \
    --data-urlencode formatversion=2
)
# rvprop must be pipe-separated; rvslots=main + formatversion=2 returns content under slots.main.content  ⟂

[ -z "${page_json:-}" ] && { echo "ERROR: Empty response for '${PAGE_TITLE}'" >&2; exit 2; }

export PAGE_JSON="$page_json"

old_text=$(
  PAGE_JSON="$PAGE_JSON" python3 - <<'PY'
import os, json
d = json.loads(os.environ.get("PAGE_JSON","{}"))
try:
    rev = d["query"]["pages"][0]["revisions"][0]
    slots = rev.get("slots", {})
    main  = slots.get("main", {})
    print(main.get("content", ""))
except Exception:
    print("", end="")
PY
)

basets=$(
  PAGE_JSON="$PAGE_JSON" python3 - <<'PY'
import os, json
d = json.loads(os.environ.get("PAGE_JSON","{}"))
try:
    print(d["query"]["pages"][0]["revisions"][0].get("timestamp",""))
except Exception:
    print("", end="")
PY
)

if [ -z "${basets:-}" ]; then
  echo "ERROR: Could not parse base timestamp from API response." >&2
  echo "DEBUG: $PAGE_JSON" >&2
  exit 2
fi

echo "    Base timestamp: $basets"
echo "    Preview (first 120 chars):"
printf '%s' "$old_text" | head -c 120; echo

echo "==> 6) Replace matching string locally (USE_REGEX=${USE_REGEX})"
export OLD_TEXT="$old_text" FIND="$FIND" REPLACE="$REPLACE"
if [ "${USE_REGEX}" = "true" ]; then
  new_text=$(
    python3 - <<'PY'
import os, re, sys
old = os.environ.get("OLD_TEXT","")
pat = os.environ.get("FIND","")
rep = os.environ.get("REPLACE","")
try:
    new = re.sub(pat, rep, old, flags=re.MULTILINE)
except re.error as e:
    sys.stderr.write(f"ERROR: invalid regex: {e}\\n"); sys.exit(2)
sys.stdout.write(new)
PY
  )
else
  new_text=$(
    python3 - <<'PY'
import os, sys
old = os.environ.get("OLD_TEXT","")
find = os.environ.get("FIND","")
rep = os.environ.get("REPLACE","")
sys.stdout.write(old.replace(find, rep))
PY
  )
fi

if [ "x$old_text" = "x$new_text" ]; then
  echo "==> No changes after replacement; skipping edit."
  rm -rf "$tmpdir"; exit 0
fi

if [ "${DRY_RUN:-false}" = "true" ]; then
  echo "==> DRY RUN: not saving changes. (Set dryRun=false to save)"
  rm -rf "$tmpdir"; exit 0
fi

echo "==> 7) Edit the page (POST-only) with conflict protection"
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
# (action=edit is POST-only; include basetimestamp/starttimestamp to avoid clobbering others)  ⟂

rm -rf "$tmpdir"
'''
    }
  }
}