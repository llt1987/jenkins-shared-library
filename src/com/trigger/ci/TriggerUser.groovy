package com.trigger.ci

class TriggerUser implements Serializable {

  static String resolve(Object script, boolean preferUpstream = true, int maxDepth = 5) {
    try {
      // Prefer Build User Vars Plugin env var if wrapped with BuildUser
      String fromEnv = readFromBuildUserEnv(script)
      if (fromEnv) return fromEnv

      def run = pickInitialRun(script, preferUpstream)
      def visited = new HashSet<String>()
      return resolveFromRun(script, run, visited, maxDepth) ?: "system"
    } catch (Throwable t) {
      safeEcho(script, "[TriggerUser] resolve failed: ${t.class?.name}: ${t.message}")
      return "system"
    }
  }

  // ---------- internals ----------

  private static String resolveFromRun(Object script, def run, Set<String> visited, int depth) {
    if (!run || depth <= 0) return null

    try {
      String key = run?.getExternalizableId() ?: (run?.getParent()?.fullName + "#" + run?.number)
      if (key && !visited.add(key)) return null
    } catch (ignored) {}

    def causes = getCausesSafe(run) ?: []

    String userId = extractUserIdFromCauses(causes)
    if (userId) return userId

    if (hasTimerCause(causes)) return "timer"

    def upstreamRef = getUpstreamRef(causes)
    if (upstreamRef) {
      def upstreamRun = loadUpstreamRun(upstreamRef)
      String upstreamUser = resolveFromRun(script, upstreamRun, visited, depth - 1)
      if (upstreamUser) return upstreamUser
    }

    if (hasScmCause(causes) || hasRemoteCause(causes)) return "system"

    return null
  }

  private static String readFromBuildUserEnv(Object script) {
    try {
      def env = script?.env
      // BUILD_USER_ID is set when using: wrap([$class: 'BuildUser'])
      return env?.BUILD_USER_ID ?: null
    } catch (ignored) {
      return null
    }
  }

  private static def pickInitialRun(Object script, boolean preferUpstream) {
    try {
      def cb = script?.currentBuild
      if (preferUpstream && cb?.upstreamBuilds && !cb.upstreamBuilds.isEmpty()) {
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
        // May return a List<Map> on some setups
        causes = run?.getBuildCauses() ?: []
      } catch (ignored2) { causes = [] }
    }
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

    def real = causes.find { c -> classNameOf(c) == 'hudson.model.Cause$UserIdCause' }
    if (real) {
      try {
        def id = real?.userId ?: real?.getUserId()
        if (id) return id as String
      } catch (ignored) {}
    }

    def mapUser = causes.find { c -> (c instanceof Map) && (c.userId || c['userId']) }
    if (mapUser instanceof Map) {
      def id = mapUser.userId ?: mapUser['userId']
      if (id) return id as String
    }

    return null
  }

  private static Map getUpstreamRef(List causes) {
    if (!causes) return null

    def up = causes.find { c -> classNameOf(c) == 'hudson.model.Cause$UpstreamCause' }
    if (up) {
      try {
        return [ project: up.getUpstreamProject(), number: (up.getUpstreamBuild() as int) ]
      } catch (ignored) {}
    }

    def upMap = causes.find { c -> (c instanceof Map) && (c.upstreamProject && c.upstreamBuild) }
    if (upMap instanceof Map) {
      return [ project: upMap.upstreamProject, number: (upMap.upstreamBuild as int) ]
    }

    return null
  }

  private static def loadUpstreamRun(Map ref) {
    if (!ref) return null
    try {
      def j = jenkins.model.Jenkins.get()
      def job = j?.getItemByFullName(ref.project)
      return job?.getBuildByNumber(ref.number as int)
    } catch (ignored) { return null }
  }

  private static String classNameOf(Object o) {
    if (o == null) return null
    if (o instanceof Map) return 'Map'
    return o.getClass()?.name
  }

  private static void safeEcho(Object script, String msg) {
    try { script?.echo(msg) } catch (ignored) {}
  }
}