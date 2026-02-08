import org.junit.Before
import org.junit.Test

class gitUtilsTest extends BaseSharedLibraryTest {

    def gitUtils

    @Before
    void init() {
        gitUtils = loadVarScript('gitUtils')

        
    }

    @Test
    void test_getLatestTag_success() {
        def result = gitUtils.getLatestTag("gitapac.devops/example-repo", "git-creds")
        // just check the syntax
        assert result == null
    }

}