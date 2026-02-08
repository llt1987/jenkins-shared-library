import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before

/**
 * Base class for all Jenkins Shared Library unit tests.
 * Contains common setup logic that other test classes can reuse.
 */
abstract class BaseSharedLibraryTest extends BasePipelineTest {

    @Before
    void setUp() {
        super.setUp()

        // Mock common Jenkins environment variables
        binding.setVariable('JOB_NAME', 'example-job')
        binding.setVariable('JOB_URL', 'http://jenkinsapac.devops/job/example-job/')
        binding.setVariable('buildId', '123')

        /*** Register commonly used Jenkins pipeline methods ***/
        // generic methods
        helper.registerAllowedMethod('script', [Closure.class], { closure -> closure() })

        helper.registerAllowedMethod('sh', [Map.class], { args ->
            println "[Mock] Run shell: ${args.script}"
            return 0
        })
        helper.registerAllowedMethod('error', [String.class], { msg ->
            throw new Exception("Jenkins error: ${msg}")
        })
        
        helper.registerAllowedMethod('withCredentials', [List.class, Closure.class], { list, closure ->
            binding.setVariable('GIT_USER', 'testuser')
            binding.setVariable('GIT_PASS', 'testpass')
            closure()
        })
        helper.registerAllowedMethod('usernamePassword', [Map.class], { args -> return args })

        // Mock email sending
        helper.registerAllowedMethod('emailext', [Map.class], { args ->
            println "[Mock] Sending email to: ${args.to}"
            println "[Mock] Subject: ${args.subject}"
            println "[Mock] Attach log: ${args.attachLog}"
            return null
        })
    }

    /**
     * Helper method to load a Groovy script from the vars/ directory.
     */
    def loadVarScript(String name) {
        return loadScript("vars/${name}.groovy")
    }
}
