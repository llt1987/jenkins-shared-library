import com.trigger.ci.TriggerUser
import com.trigger.ci.PackageCopier

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

/**
 * copyPackages(
 *   srcRoot: '/mnt/novabld/novaarchive/700-BUILDS',
 *   destRoot: '/path/to/release/TEST',
 *   config: TEST,                         // Map with packages & extraPackages
 *   collapseArchive: true,                // default true
 *   dryRun: false,                        // default false
 *   failIfMissing: true                   // default true
 * )
 */
 
def copyPackages(Map args = [:]) {
  args.script = this
  PackageCopier.copy(args)
}

