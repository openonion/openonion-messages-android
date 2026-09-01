package ai.openonion.messages

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectContractTest {
    @Test
    fun packageNameMatchesPublicIdentity() {
        assertEquals("ai.openonion.messages", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun stableReleaseVersionMatchesPublishedIdentity() {
        assertEquals("1.0.0", BuildConfig.VERSION_NAME)
        assertEquals(1, BuildConfig.VERSION_CODE)
    }
}
