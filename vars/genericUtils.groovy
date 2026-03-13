import com.trigger.ci.TriggerUser

/**
 * Pipeline step: getUserId(preferUpstream: true, maxDepth: 5)
 *
 * Returns:
 *   - user id (String) if a human triggered the build
 *   - "timer" when triggered by cron
 *   - "system" otherwise (SCM/Remote/automation)
 */
def call(Map args = [:]) {
  boolean preferUpstream = (args.containsKey('preferUpstream') ? args.preferUpstream as boolean : true)
  int maxDepth = (args.containsKey('maxDepth') ? args.maxDepth as int : 5)
  // Coalesce to ensure non-null result
  def uid = TriggerUser.resolve(this, preferUpstream, maxDepth)
  return uid ?: "system"
}