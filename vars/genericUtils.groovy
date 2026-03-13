import com.trigger.ci.TriggerUser

/**
 * Global var 'genericUtils' exposing helper methods, including getUserId().
 *
 * Usage:
 *   def uid = genericUtils.getUserId()
 *   def uid = genericUtils.getUserId(preferUpstream: false, maxDepth: 3)
 */
def getUserId(Map args = [:]) {
  boolean preferUpstream = args.containsKey('preferUpstream') ? args.preferUpstream as boolean : true
  int maxDepth = args.containsKey('maxDepth') ? args.maxDepth as int : 5
  def uid = TriggerUser.resolve(this, preferUpstream, maxDepth)
  return uid ?: 'system'
}

