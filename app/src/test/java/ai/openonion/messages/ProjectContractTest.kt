package ai.openonion.messages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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

    @Test
    fun openSourceReleaseFilesStayPublished() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val root = generateSequence(File(workingDirectory)) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val required = listOf(
            "LICENSE",
            "NOTICE",
            "README.md",
            "CONTRIBUTING.md",
            "CODE_OF_CONDUCT.md",
            "SECURITY.md",
            "SUPPORT.md",
            "PRIVACY.md",
            "DATA_SAFETY.md",
            "docs/README.md",
            "docs/releases/1.0.0.md",
        )

        required.forEach { path ->
            assertTrue("Missing public project file: $path", File(root, path).isFile)
        }
        assertTrue(File(root, "LICENSE").readText().contains("Apache License"))
        assertTrue(File(root, "README.md").readText().contains("releases/tag/v1.0.0"))
    }
}
