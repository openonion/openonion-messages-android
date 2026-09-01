package ai.openonion.messages

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectContractTest {
    @Test
    fun packageNameMatchesPublicIdentity() {
        assertEquals("ai.openonion.messages", BuildConfig.APPLICATION_ID)
    }
}
