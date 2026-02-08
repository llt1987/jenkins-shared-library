import org.junit.Before
import org.junit.Test

class novaRelease extends BaseSharedLibraryTest  {

    def novaRelease

    @Before
    void init() {
        super.setUp()
        novaRelease = loadVarScript('novaRelease')
    }

    @Test
    void novaReleaseDockerHash() {
        novaRelease.ntierDockerHash(novaVersion: "NOVA7-00-01-47")
        printCallStack()
        assertJobStatusSuccess()
    }
}