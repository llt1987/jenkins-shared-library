package com.trigger.ci

/**
 * Resolve the triggering userId for the current build.
 *
 * Returns:
 *   - user id (String) if a human triggered the build (UserIdCause or Build User Vars)
 *   - "timer" if triggered by cron/timer
 *   - "system" for all other non-user triggers (SCM webhook, Remote/API, bot, etc.)
 *
 * Usage from Jenkinsfile:
 *   @Library('your-lib') _
 *   stage('Resolve User') {
 *     steps {
 *       script {
 *         env.USER_ID = getUserId()  // or com.yourorg.ci.TriggerUser.resolve(this)
 *       }
 *     }
 *   }
 *
 * Notes:
 *   - Call from INSIDE a Pipeline `steps { script { ... } }` context.
 *   - If you use the Build User Vars Plugin, wrap a step with `wrap([$class: 'BuildUser'])`
 *     to populate BUILD_USER_ID/BUILD_USER; this function will prefer those env vars.
 *   - Approved methods may be required by Script Security for first-time use.
 */
class TriggerUser implements Serializable {

  /**
   * Resolve user id. preferUpstream = true mirrors original logic (check upstream first).
   * maxDepth controls how far up the upstream chain we look.
   */
  static String resolve(Object script, boolean preferUpstream = true, int maxDepth = 5) {
    try {
      // 1) Prefer Build User Vars Plugin env when present (only for human/manual)
      String byEnv = readFromBuildUserEnv(script)
      if (byEnv) {
        return byEnv
      }

      // 2) Decide which run to inspect first
      def run = pickInitialRun(script, preferUpstream)

      // 3) Follow upstream chain to find user/timer/system
      def visited = new HashSet<String>()
      return resolveFromRun(script, run, visited, maxDepth) ?: "system"
    } catch (Throwable t) {
      safeEcho(script, "[TriggerUser] resolve failed: ${t.class?.name}: ${t.message}")
      return "system"
    }
  }

  // ------------ internals ------------

  private static String resolveFromRun(Object script, def run, Set<String> visited, int depth) {
    if (!run || depth <= 0) return null

    // Prevent cycles
    try {
      String key = run?.getExternalizableId() ?: (run?.getParent()?.fullName + "#" + run?.number)
      if (key && !visited.add(key)) {
        return null
      }
    } catch (ignored) { /* best effort */ }

    def causes = getCausesSafe(run) ?: []

    // 1) If human UserIdCause exists on this run, return it
    String userId = extractUserIdFromCauses(causes)
    if (userId) return userId

    // 2) If this run is timer-triggered, return "timer"
    if (hasTimerCause(causes)) return "timer"

    // 3) If upstream exists, recurse into upstream
    def upstreamRef = getUpstreamRef(causes)
    if (upstreamRef) {
      def upstreamRun = loadUpstreamRun(upstreamRef)
      String upstreamUser = resolveFromRun(script, upstreamRun, visited, depth - 1)
      if (upstreamUser) return upstreamUser
    }

    // 4) SCM/Remote/Other → treat as "system"
    if (hasScmCause(causes) || hasRemoteCause(causes)) {
      return "system"
    }

    // 5) Default null (caller will coalesce to "system")
    return null
  }

  private static String readFromBuildUserEnv(Object script) {
    try {
      def env = script?.env
      def fromId = env?.BUILD_USER_ID
      def fromName = env?.BUILD_USER
      // Prefer BUILD_USER_ID; if unavailable, you might prefer name or keep null
      return (fromId ?: null)
    } catch (ignored) {
      return null
    }
  }

  private static def pickInitialRun(Object script, boolean preferUpstream) {
    try {
      def cb = script?.currentBuild
      if (preferUpstream && cb?.upstreamBuilds && !cb.upstreamBuilds.isEmpty()) {
        // Pipeline’s upstreamBuilds items are Runs or wrappers; use as-is
        return cb.upstreamBuilds[0]?.rawBuild ?: cb.upstreamBuilds[0]
      }
      return cb?.rawBuild ?: cb
    } catch (ignored) {
      return script?.currentBuild
    }
  }

  private static List getCausesSafe(def run) {
    List causes = []
    try {
      def r = (run?.hasProperty('rawBuild') ? run.rawBuild : run)
      causes = r?.getCauses() ?: []
    } catch (ignored) {
      try {
        // Some Pipeline APIs return a list of Maps here; we handle this elsewhere
        causes = run?.getBuildCauses() ?: []
      } catch (ignored2) {
        causes = []
      }
    }
    // Filter nulls defensively
    (causes ?: []).findAll { it != null }
  }

  private static boolean hasTimerCause(List causes) {
    causes?.any { c -> classNameOf(c) == 'hudson.triggers.TimerTrigger$TimerTriggerCause' }
  }

  private static boolean hasScmCause(List causes) {
    causes?.any { c -> classNameOf(c) == 'hudson.triggers.SCMTrigger$SCMTriggerCause' }
  }

  private static boolean hasRemoteCause(List causes) {
    causes?.any { c -> classNameOf(c) == 'hudson.model.Cause$RemoteCause' }
  }

  private static String extractUserIdFromCauses(List causes) {
    if (!causes) return null

    // Case 1: Real UserIdCause
    def ui = causes.find { c -> classNameOf(c) == 'hudson.model.Cause$UserIdCause' }
    if (ui) {
      try {
        def id = ui?.userId ?: ui?.getUserId()
        if (id) return id as String
      } catch (ignored) { /* continue */ }
    }

    // Case 2: Map returned by getBuildCauses() (on some setups)
    def mapUser = causes.find { c -> (c instanceof Map) && (c.userId || c['userId']) }
    if (mapUser instanceof Map) {
      def id = mapUser.userId ?: mapUser['userId']
      if (id) return id as String
    }

    return null
  }

  private static Map getUpstreamRef(List causes) {
    if (!causes) return null

    // True UpstreamCause
    def up = causes.find { c -> classNameOf(c) == 'hudson.model.Cause$UpstreamCause' }
    if (up) {
      try {
        return [
          project: up.getUpstreamProject(),
          number : (up.getUpstreamBuild() as int)
        ]
      } catch (ignored) { /* continue */ }
    }

    // Map variant
    def upMap = causes.find { c -> (c instanceof Map) && (c.upstreamProject && c.upstreamBuild) }
    if (upMap instanceof Map) {
      return [
        project: upMap.upstreamProject,
        number : (upMap.upstreamBuild as int)
      ]
    }
    return null
  }

  private static def loadUpstreamRun(Map ref) {
    if (!ref) return null
    try {
      def j = jenkins.model.Jenkins.get()
      def job = j?.getItemByFullName(ref.project)
      return job?.getBuildByNumber(ref.number as int)
    } catch (ignored) {
      return null
    }
  }

  private static String classNameOf(Object o) {
    if (o == null) return null
    // If it's a Map (from getBuildCauses), return "Map" marker to skip instanceof checks
    if (o instanceof Map) return 'Map'
    return o.getClass()?.name
  }

  private static void safeEcho(Object script, String msg) {
    try {
      script?.echo(msg)
    } catch (ignored) {
      // no-op
    }
  }
}