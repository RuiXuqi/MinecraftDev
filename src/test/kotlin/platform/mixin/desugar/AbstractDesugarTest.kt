/*
 * Minecraft Development for IntelliJ
 *
 * https://mcdev.io/
 *
 * Copyright (C) 2025 minecraft-dev
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, version 3.0 only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.demonwav.mcdev.platform.mixin.desugar

import com.demonwav.mcdev.framework.BaseMinecraftTest
import com.demonwav.mcdev.platform.mixin.handlers.desugar.DesugarContext
import com.demonwav.mcdev.platform.mixin.handlers.desugar.DesugarUtil
import com.demonwav.mcdev.platform.mixin.handlers.desugar.Desugarer
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.childrenOfType
import com.intellij.testFramework.IndexingTestUtil
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.objectweb.asm.Opcodes

abstract class AbstractDesugarTest : BaseMinecraftTest() {
    abstract val desugarer: Desugarer

    protected fun doTestNoChange(@Language("JAVA") code: String, classVersion: Int = Opcodes.V21) {
        doTest(code, code, classVersion)
    }

    protected fun doTest(@Language("JAVA") before: String, @Language("JAVA") after: String, classVersion: Int = Opcodes.V21) {
        // Setup can invalidate the indexes (e.g. adding a library to the module). This has to run outside a
        // write action, otherwise the reindex cannot proceed and resolving references below fails with
        // IndexNotReadyException.
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        WriteCommandAction.runWriteCommandAction(project) {
            val codeStyleManager = CodeStyleManager.getInstance(project)
            val javaCodeStyleManager = JavaCodeStyleManager.getInstance(project)

            val expectedFile = fixture.addClass(after).containingFile
            assertEquals(
                expectedFile,
                codeStyleManager.reformat(expectedFile),
                "Reformatting changed the file!",
            )
            val expectedText = expectedFile.text
            expectedFile.delete()

            val testFile = assertInstanceOf(
                PsiJavaFile::class.java,
                fixture.configureByText("Test.java", before)
            )
            assertEquals(
                testFile,
                codeStyleManager.reformat(testFile),
                "Reformatting changed the file!",
            )

            IndexingTestUtil.waitUntilIndexesAreReady(project)

            val desugaredFile = testFile.copy() as PsiJavaFile
            DesugarUtil.setOriginalRecursive(desugaredFile, testFile)
            desugarer.desugar(project, desugaredFile, DesugarContext(classVersion))
            assertEquals(
                expectedText,
                codeStyleManager.reformat(javaCodeStyleManager.shortenClassReferences(desugaredFile.copy())).text
            )

            PsiTreeUtil.processElements(desugaredFile) { desugaredElement ->
                val originalElement = DesugarUtil.getOriginalElement(desugaredElement)
                if (originalElement != null) {
                    assertTrue(
                        PsiTreeUtil.isAncestor(testFile, originalElement, false)
                    ) {
                        "The original element of $desugaredElement is not from the original file"
                    }
                }
                true
            }

            val originalClasses = testFile.childrenOfType<PsiClass>()
            val desugaredClassesSet = mutableSetOf<PsiClass>()
            val originalToDesugaredMap = DesugarUtil.getOriginalToDesugaredMap(desugaredFile)
            for (clazz in originalClasses) {
                val desugaredClasses = originalToDesugaredMap[clazz]?.filterIsInstance<PsiClass>() ?: emptyList()
                assertEquals(1, desugaredClasses.size) { "Unexpected number of desugared classes for ${clazz.name}" }
                desugaredClassesSet += desugaredClasses.first()
            }
            assertEquals(originalClasses.size, desugaredClassesSet.size, "Unexpected number of desugared classes")
        }
    }
}
