#!/bin/bash
# =============================================================================
# Git Changelog / Release Notes HTML Generator with Hash, Date, Message & Author
# Usage: ./generate_changelog.sh <start_tag> <end_tag>
# =============================================================================

set -Eeuxo pipefail

if [ $# -ne 2 ]; then
  echo "❌ Usage: $0 <start_tag> <end_tag>"
  exit 1
fi

cd ../../../roadmap

START_TAG="$1"
END_TAG="$2"
GENERATED_ON=$(date "+%d-%b-%Y %H:%M %Z")

# -----------------------------------------------------------------------------
# Validate tags
# -----------------------------------------------------------------------------
git rev-parse --verify "$START_TAG" >/dev/null 2>&1 || {
  echo "❌ Start tag not found: $START_TAG"
  exit 1
}
git rev-parse --verify "$END_TAG" >/dev/null 2>&1 || {
  echo "❌ End tag not found: $END_TAG"
  exit 1
}

# Enforce ancestry (CRITICAL FIX)
if ! git merge-base --is-ancestor "$START_TAG" "$END_TAG"; then
  echo "❌ ERROR: $START_TAG is NOT an ancestor of $END_TAG"
  echo "   Release notes must follow commit history."
  exit 1
fi

# -----------------------------------------------------------------------------
# Prepare output
# -----------------------------------------------------------------------------
OUTPUT_DIR="ReleaseNotes"
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

echo "Finding tags between $START_TAG and $END_TAG..."

# -----------------------------------------------------------------------------
# Collect tags safely (VERSION order + ANCESTRY validation)
# -----------------------------------------------------------------------------
#mapfile -t ALL_TAGS < <(git tag --sort=version:refname)

mapfile -t ALL_TAGS < <(
  git for-each-ref --sort=version:refname --format='%(refname:short)' refs/tags |
  awk -v start="$START_TAG" -v end="$END_TAG" '
    $0 == start { in_range = 1 }
    in_range { print }
    $0 == end { exit }
  '
)

TAG_LIST=()
in_range=0
for tag in "${ALL_TAGS[@]}"; do
  [[ "$tag" == "$START_TAG" ]] && in_range=1
  if [[ $in_range -eq 1 ]]; then
      TAG_LIST+=("$tag")
    else
      echo "⚠️ Skipping non-descendant tag: $tag"
  fi
  [[ "$tag" == "$END_TAG" ]] && break
done

[[ ${#TAG_LIST[@]} -eq 0 ]] && {
  echo "❌ No valid tags found between range"
  exit 1
}

echo "Valid tags: ${TAG_LIST[*]}"

# =============================================================================
# Generate HTML page for each tag
# =============================================================================
generate_tag_html() {
  local tag="$1"
  local prev="$2"
  local file="$OUTPUT_DIR/$tag.html"
  local tag_date

  tag_date=$(git log -1 --date=format:'%d-%b-%Y' --format='%ad' "$tag" 2>/dev/null || echo "Unknown")

cat >"$file" <<EOF
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Release $tag</title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
</head>
<body class="p-4 bg-light">
<div class="container">
<h1 class="text-center">Release Items in $tag</h1>
<p class="text-center">Date: $tag_date</p>

<table class="table table-hover">
<thead class="table-dark">
<tr>
  <th>Hash</th>
  <th>Date</th>
  <th>Author</th>
  <th>Message</th>
</tr>
</thead>
<tbody>
EOF

  if [ -n "$prev" ]; then
    git log \
      --no-merges \
      --extended-regexp \
      --grep='^(Fix|Reg test|regression|ntier unit test)' \
      --invert-grep \
      --regexp-ignore-case \
      --pretty=format:'<tr><td><a href="https://git.novacmx.com/nova/nova/-/commit/%H" target="_blank">%h</a></td><td>%as</td><td>%an</td><td>%s</td></tr>' \
      "$prev..$tag" >>"$file"
  else
    git log -10 \
      --no-merges \
      --extended-regexp \
      --grep='^(Fix|Reg test|regression|ntier unit test)' \
      --invert-grep \
      --regexp-ignore-case \
      --pretty=format:'<tr><td><a href="https://git.novacmx.com/nova/nova/-/commit/%H" target="_blank">%h</a></td><td>%as</td><td>%an</td><td>%s</td></tr>' \
      "$tag" >>"$file"
  fi

cat >>"$file" <<EOF
</tbody>
</table>

<a href="index.html">← Back to Index</a>
</div>
</body>
</html>
EOF

echo "✓ Generated $tag.html"
}

# =============================================================================
# Generate release pages sequentially
# =============================================================================
PREV=""
for TAG in "${TAG_LIST[@]}"; do
  generate_tag_html "$TAG" "$PREV"
  PREV="$TAG"
done

# =============================================================================
# Create Index Page
# =============================================================================
INDEX="$OUTPUT_DIR/index.html"

cat >"$INDEX" <<EOF
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>NOVA Release Notes</title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
</head>
<body class="p-4">
<div class="container">

<h1 class="text-center">NOVA Release Notes</h1>
<ul class="list-group my-4">
EOF

for TAG in "${TAG_LIST[@]}"; do
  echo "<li class=\"list-group-item\"><a href=\"$TAG.html\">$TAG</a></li>" >>"$INDEX"
done

cat >>"$INDEX" <<EOF
</ul>

<div class="text-center text-muted mt-4">
Generated on $GENERATED_ON
</div>

</div>
</body>
</html>
EOF

echo "✅ Done → $OUTPUT_DIR/index.html"

# =============================================================================
# Create TAR archive
# =============================================================================
TAR_NAME="${OUTPUT_DIR}-${START_TAG}-to-${END_TAG}.tar.gz"
tar -czf "$TAR_NAME" "$OUTPUT_DIR"
echo "Archive created: $TAR_NAME"

echo "Release notes generation complete"