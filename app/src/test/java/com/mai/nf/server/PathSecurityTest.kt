package com.mai.nf.server

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PathSecurityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun isPathSafe(rootDir: File, targetFile: File): Boolean {
        val rootPath = rootDir.canonicalPath
        val targetPath = targetFile.canonicalPath
        return targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)
    }

    @Test
    fun testValidChildPathsAreSafe() {
        val rootDir = tempFolder.newFolder("MaiNFShared")
        val subDir = File(rootDir, "subfolder").apply { mkdirs() }
        val normalFile = File(subDir, "document.txt")

        assertTrue(isPathSafe(rootDir, rootDir))
        assertTrue(isPathSafe(rootDir, subDir))
        assertTrue(isPathSafe(rootDir, normalFile))
    }

    @Test
    fun testPathTraversalWithDotsIsBlocked() {
        val rootDir = tempFolder.newFolder("MaiNFShared")
        val outsideFile = tempFolder.newFile("secret.txt")

        // Try ../secret.txt traversal
        val traversalTarget = File(rootDir, "../secret.txt")
        assertFalse(isPathSafe(rootDir, traversalTarget))

        // Deep traversal ../../../
        val deepTraversal = File(rootDir, "../../../../../etc/passwd")
        assertFalse(isPathSafe(rootDir, deepTraversal))
    }

    @Test
    fun testSiblingDirectoryWithSamePrefixIsBlocked() {
        val parent = tempFolder.newFolder("root")
        val sharedDir = File(parent, "MaiNF").apply { mkdirs() }
        val siblingDir = File(parent, "MaiNF_Attacker").apply { mkdirs() }
        val attackerFile = File(siblingDir, "evil.txt")

        // MaiNF_Attacker starts with "MaiNF", but is NOT a child (separator check prevents this!)
        assertFalse(isPathSafe(sharedDir, attackerFile))
        assertFalse(isPathSafe(sharedDir, siblingDir))
    }
}
