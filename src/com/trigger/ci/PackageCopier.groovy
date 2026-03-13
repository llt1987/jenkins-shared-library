package com.trigger.ci

/**
 * PackageCopier
 *
 * Copies a set of paths (with glob support) from a source root to a destination root.
 * - Preserves directory structure for all non-archive paths.
 * - Collapses *any* source path that contains '/archive/' or starts with 'archive/'
 *   into a single destination '<destRoot>/archive' folder (flat files).
 *
 * Options (args Map):
 *   - script         : REQUIRED Jenkins pipeline script binding (`this`)
 *   - srcRoot        : REQUIRED absolute path to source root
 *   - destRoot       : REQUIRED absolute path to destination root
 *   - config         : REQUIRED Map with keys 'packages' and (optionally) 'extraPackages', both List<String>
 *   - collapseArchive: boolean (default: true)
 *   - dryRun         : boolean (default: false) -> print plan only, no copy
 *   - failIfMissing  : boolean (default: true)  -> fail if any pattern has no matches
 *
 * Requires a shell with bash + rsync available on the agent.
 */
class PackageCopier implements Serializable {

  static void copy(Map args) {
    def script          = require(args, 'script')
    String srcRoot      = require(args, 'srcRoot') as String
    String destRoot     = require(args, 'destRoot') as String
    Map config          = require(args, 'config') as Map
    boolean collapseArc = (args.containsKey('collapseArchive') ? args.collapseArchive as boolean : true)
    boolean dryRun      = (args.containsKey('dryRun') ? args.dryRun as boolean : false)
    boolean failMissing = (args.containsKey('failIfMissing') ? args.failIfMissing as boolean : true)

    List<String> patterns = []
    patterns.addAll( normalizeList(config.get('packages')) )
    patterns.addAll( normalizeList(config.get('extraPackages')) )

    if (patterns.isEmpty()) {
      echo(script, '[PackageCopier] No patterns provided. Nothing to do.')
      return
    }

    def destArchive = "${destRoot}/archive"

    echo(script, "[PackageCopier] Source: ${srcRoot}")
    echo(script, "[PackageCopier] Destination: ${destRoot}")
    echo(script, "[PackageCopier] Collapse archive: ${collapseArc}, Dry run: ${dryRun}, Fail if missing: ${failMissing}")
    echo(script, "[PackageCopier] Patterns (${patterns.size()}): ${patterns.join(', ')}")

    // Ensure base destination exists
    mkdirs(script, destRoot, dryRun)
    if (collapseArc) {
      mkdirs(script, destArchive, dryRun)
    }

    int totalMatched = 0
    int totalCopied  = 0
    List<String> missing = []

    patterns.each { pat ->
      boolean isArchive = isArchivePattern(pat)
      def matches = listMatches(script, srcRoot, pat)

      if (matches.isEmpty()) {
        echo(script, "[PackageCopier] No matches for pattern: ${pat}")
        missing << pat
        return
      }

      echo(script, "[PackageCopier] Pattern: ${pat} -> ${matches.size()} match(es)")
      totalMatched += matches.size()

      matches.each { absSrcPath ->
        // absSrcPath is absolute path
        if (isArchive && collapseArc) {
          // Collapse any archive source into a single dest archive folder (flatten)
          def cmd = rsyncCopyFlat(absSrcPath, destArchive, dryRun)
          sh(script, cmd, dryRun)
          totalCopied++
          echo(script, "  ↳ archive: ${absSrcPath}  →  ${destArchive}/")
        } else {
          // Preserve path relative to srcRoot using rsync --relative
          // cd to srcRoot, then rsync --relative ./relPath destRoot/
          String relPath = relativize(srcRoot, absSrcPath)
          def cmd = rsyncCopyPreserve(relPath, destRoot, srcRoot, dryRun)
          sh(script, cmd, dryRun)
          totalCopied++
          echo(script, "  ↳ preserve: ${absSrcPath}  →  ${destRoot}/${relPath}")
        }
      }
    }

    echo(script, "[PackageCopier] Total matches: ${totalMatched}, Total copied (or planned): ${totalCopied}")

    if (failMissing && !missing.isEmpty()) {
      error(script, "[PackageCopier] Missing patterns (${missing.size()}): ${missing.join(', ')}")
    }
  }

  // -------- helpers --------

  private static Object require(Map args, String key) {
    if (!args.containsKey(key) || args.get(key) == null) {
      throw new IllegalArgumentException("Missing required argument: '${key}'")
    }
    return args.get(key)
  }

  private static List<String> normalizeList(Object val) {
    if (val == null) return []
    if (val instanceof List) return (val as List).collect { it.toString() }
    return [val.toString()]
  }

  private static boolean isArchivePattern(String pat) {
    // archive at start OR any '/archive/' segment in the path
    return pat ==~ /^archive(\/|$).*/ || pat.contains('/archive/')
  }

  /**
   * Lists absolute file paths that match a glob pattern under srcRoot.
   * Uses bash nullglob (no literal echo when no match) and prints one per line.
   */
  private static List<String> listMatches(def script, String srcRoot, String pattern) {
    String safeSrc = shellEscape(srcRoot)
    String safePat = shellEscape(pattern)
    String out = script.sh(
      script: """#!/usr/bin/env bash
set -euo pipefail
shopt -s nullglob dotglob
cd ${safeSrc}
# Expand pattern safely
for f in ${pattern}; do
  if [[ -f "\$f" ]]; then
    printf '%s\\n' "\$PWD/\$f"
  fi
done
""",
      returnStdout: true
    ).trim()

    if (!out) return []
    return out.readLines().collect { it.trim() }.findAll { it }
  }

  /**
   * rsync command to copy one file, preserving its relative path from srcRoot.
   * Uses --relative with "./<relPath>" trick.
   */
  private static String rsyncCopyPreserve(String relPath, String destRoot, String srcRoot, boolean dryRun) {
    String safeDest = shellEscape("${destRoot}/")
    String safeRel  = shellEscape("./${relPath}")
    String safeSrc  = shellEscape(srcRoot)
    String nflag    = dryRun ? "-n" : ""
    return """#!/usr/bin/env bash
set -euo pipefail
cd ${safeSrc}
rsync -a ${nflag} --relative ${safeRel} ${safeDest}
"""
  }

  /**
   * rsync command to copy one file to a flat destination folder.
   */
  private static String rsyncCopyFlat(String absSrc, String destDir, boolean dryRun) {
    String safeSrc  = shellEscape(absSrc)
    String safeDest = shellEscape("${destDir}/")
    String nflag    = dryRun ? "-n" : ""
    return """#!/usr/bin/env bash
set -euo pipefail
rsync -a ${nflag} ${safeSrc} ${safeDest}
"""
  }

  private static String relativize(String root, String abs) {
    String normRoot = root.endsWith("/") ? root : (root + "/")
    if (abs.startsWith(normRoot)) {
      return abs.substring(normRoot.length())
    }
    return abs // fallback
  }

  private static void mkdirs(def script, String path, boolean dryRun) {
    String safe = shellEscape(path)
    sh(script, """#!/usr/bin/env bash
set -euo pipefail
mkdir -p ${safe}
""", dryRun)
  }

  private static void sh(def script, String cmd, boolean dryRun) {
    if (dryRun) {
      echo(script, "[DRY-RUN] ${oneLine(cmd)}")
    } else {
      script.sh(script: cmd)
    }
  }

  private static void echo(def script, String msg) {
    try { script.echo(msg) } catch (ignored) { println msg }
  }

  private static void error(def script, String msg) {
    try { script.error(msg) } catch (ignored) { throw new RuntimeException(msg) }
  }

  private static String oneLine(String s) {
    return s?.replaceAll(/\s+/, ' ')?.trim()
  }

  private static String shellEscape(String s) {
    // Simple single-quote escaping for bash
    if (s == null) return "''"
    return "'" + s.replace("'", "'\"'\"'") + "'"
  }
}