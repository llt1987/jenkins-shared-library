import org.junit.Before
import org.junit.Test

class emailNotiTest extends BaseSharedLibraryTest {

    def emailNoti

    @Before
    void init() {
        super.setUp()
        emailNoti = loadVarScript('emailNoti')
    }

    @Test
    void test_sendMailClientReleaseSuccess_success() {
        emailNoti.sendMailClientReleaseSuccess(
            clientName: 'ClientA',
            userId: 'julian.tran',
            version: 'NOVA7-00-01-01',
            jiraTicket: 'JIRA-123',
            recipients: 'devops-apac@contemi.com',
            attachLog: true
        )

        printCallStack()
        assertJobStatusSuccess()
    }

    @Test
    void test_sendMailClientReleaseFailure_success() {
        emailNoti.sendMailClientReleaseFailure(
            clientName: 'ClientA',
            userId: 'julian.tran',
            version: 'NOVA7-00-01-01',
            jiraTicket: 'JIRA-123',
            recipients: 'devops-apac@contemi.com',
            attachLog: true
        )

        printCallStack()
        assertJobStatusSuccess()
    }
    
    // 
    @Test
    void test_sendMailBuildSuccess_success() {
        emailNoti.sendMailBuildSuccess(
            userId: 'julian.tran',
            version: 'NOVA7-00-01-01',
            jiraTicket: 'JIRA-123',
            recipients: 'devops-apac@contemi.com',
            attachLog: true
        )

        printCallStack()
        assertJobStatusSuccess()
    }

    @Test
    void test_sendMailBuildFailure_success() {
        emailNoti.sendMailBuildFailure(
            userId: 'julian.tran',
            version: 'NOVA7-00-01-01',
            jiraTicket: 'JIRA-123',
            recipients: 'devops-apac@contemi.com',
            attachLog: true
        )

        printCallStack()
        assertJobStatusSuccess()
    }
}