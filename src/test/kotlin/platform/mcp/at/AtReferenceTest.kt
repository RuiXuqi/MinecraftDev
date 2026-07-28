/*
 * Minecraft Development for IntelliJ
 *
 * https://mcdev.io/
 *
 * Copyright (C) 2026 minecraft-dev
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

@file:Suppress("DEPRECATION")

package com.demonwav.mcdev.platform.mcp.at

import com.demonwav.mcdev.facet.MinecraftFacet
import com.demonwav.mcdev.framework.BaseMinecraftTest
import com.demonwav.mcdev.framework.EdtInterceptor
import com.demonwav.mcdev.platform.PlatformType
import com.demonwav.mcdev.platform.mcp.McpModuleSettings
import com.demonwav.mcdev.platform.mcp.McpModuleSettings.AccessTransformerNamespace
import com.demonwav.mcdev.platform.mcp.McpModuleType
import com.demonwav.mcdev.platform.mcp.at.completion.AtTypedHandlerDelegate
import com.demonwav.mcdev.platform.mcp.srg.SrgType
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestModeFlags
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir

@ExtendWith(EdtInterceptor::class)
@DisplayName("Access Transformer reference tests")
class AtReferenceTest : BaseMinecraftTest(PlatformType.MCP) {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun configureMcp() {
        CodeInsightSettings.getInstance().AUTO_POPUP_COMPLETION_LOOKUP = true

        val mappingsFile = tempDir.resolve("named-to-intermediary.srg")
        Files.writeString(
            mappingsFile,
            """
            FD: net/minecraft/Test/namedField net/minecraft/Test/field_1_value
            FD: net/minecraft/Test/otherField net/minecraft/Test/field_2_other
            MD: net/minecraft/Test/namedMethod (I)V net/minecraft/Test/func_1_run (I)V
            MD: net/minecraft/Test/namedMethod (Lnet/minecraft/Arg;)V net/minecraft/Test/func_2_arg (Lnet/minecraft/Arg;)V
            MD: net/minecraft/Test/namedReturn ()Lnet/minecraft/Arg; net/minecraft/Test/func_3_return ()Lnet/minecraft/Arg;
            """.trimIndent(),
        )

        val mcpModule = MinecraftFacet.getInstance(module, McpModuleType)
        assertNotNull(mcpModule)
        mcpModule!!.updateSettings(
            McpModuleSettings.State(
                minecraftVersion = "1.12.2",
                mappingFile = mappingsFile.toString(),
                srgType = SrgType.SRG,
                accessTransformerNamespace = AccessTransformerNamespace.INTERMEDIARY,
            ),
        )
        mcpModule.mappingsManager!!.mappings.blockingGet(10, TimeUnit.SECONDS)
    }

    @Test
    fun `class name resolves to Java class`() {
        addTargetClass()
        buildProject {
            at("example_at.cfg", "public net.mine<caret>craft.Test")
        }

        val target = fixture.file.findReferenceAt(fixture.caretOffset)?.resolve()
        val documentationTarget = DocumentationManager.getInstance(project)
            .findTargetElement(fixture.editor, fixture.file)

        assertNotNull(target)
        assertEquals("net.minecraft.Test", (target as PsiClass).qualifiedName)
        assertEquals(target, documentationTarget)
    }

    @Test
    fun `intermediary field name resolves to named Java field`() {
        addTargetClass()
        buildProject {
            at("example_at.cfg", "public-f net.minecraft.Test field_1_va<caret>lue")
        }

        val target = fixture.file.findReferenceAt(fixture.caretOffset)?.resolve()

        assertNotNull(target)
        assertEquals("namedField", (target as PsiField).name)
    }

    @Test
    fun `intermediary method descriptor resolves the correct named overload`() {
        addTargetClass()
        buildProject {
            at("example_at.cfg", "public net.minecraft.Test func_1_r<caret>un(I)V")
        }

        val target = fixture.file.findReferenceAt(fixture.caretOffset)?.resolve()
        val documentationTarget = DocumentationManager.getInstance(project)
            .findTargetElement(fixture.editor, fixture.file)

        assertNotNull(target)
        val method = target as PsiMethod
        assertEquals("namedMethod", method.name)
        assertEquals("int", method.parameterList.parameters.single().type.canonicalText)
        assertEquals(target, documentationTarget)
    }

    @Test
    fun `method descriptor class resolves to Java class`() {
        addTargetClass()
        fixture.addClass("package net.minecraft; public class Arg {}")
        buildProject {
            at(
                "example_at.cfg",
                "public net.minecraft.Test func_2_arg(Lnet/mine<caret>craft/Arg;)V",
            )
        }

        val target = fixture.file.findReferenceAt(fixture.caretOffset)?.resolve()

        assertNotNull(target)
        assertEquals("net.minecraft.Arg", (target as PsiClass).qualifiedName)
    }

    @Test
    fun `method return descriptor class resolves to Java class`() {
        addTargetClass()
        fixture.addClass("package net.minecraft; public class Arg {}")
        buildProject {
            at(
                "example_at.cfg",
                "public net.minecraft.Test func_3_return()Lnet/mine<caret>craft/Arg;",
            )
        }

        val target = fixture.file.findReferenceAt(fixture.caretOffset)?.resolve()

        assertNotNull(target)
        assertEquals("net.minecraft.Arg", (target as PsiClass).qualifiedName)
    }

    @Test
    fun `primitive descriptor resolves to boxed Java class`() {
        addTargetClass()
        buildProject {
            at("example_at.cfg", "public net.minecraft.Test func_1_run(<caret>I)V")
        }

        val target = fixture.file.findReferenceAt(fixture.caretOffset)?.resolve()

        assertNotNull(target)
        assertEquals("java.lang.Integer", (target as PsiClass).qualifiedName)
    }

    @Test
    fun `named field resolves without intermediary mapping`() {
        setNamespace(AccessTransformerNamespace.NAMED)
        addTargetClass()
        buildProject {
            at("example_at.cfg", "public-f net.minecraft.Test namedFi<caret>eld")
        }

        val target = fixture.file.findReferenceAt(fixture.caretOffset)?.resolve()

        assertNotNull(target)
        assertEquals("namedField", (target as PsiField).name)
    }

    @Test
    fun `named method descriptor resolves the correct overload`() {
        setNamespace(AccessTransformerNamespace.NAMED)
        addTargetClass()
        buildProject {
            at("example_at.cfg", "public net.minecraft.Test namedMet<caret>hod(I)V")
        }

        val target = fixture.file.findReferenceAt(fixture.caretOffset)?.resolve()

        assertNotNull(target)
        val method = target as PsiMethod
        assertEquals("namedMethod", method.name)
        assertEquals("int", method.parameterList.parameters.single().type.canonicalText)
    }

    @Test
    fun `first line completion suggests access modifier keywords`() {
        buildProject {
            at("example_at.cfg", "pub<caret>")
        }

        val variants = fixture.completeBasic().orEmpty()
        val lookupStrings = variants.map { it.lookupString }

        assertTrue("public" in lookupStrings, "Completion variants: $lookupStrings")
        assertTrue("public-f" in lookupStrings, "Completion variants: $lookupStrings")
        assertTrue("public+f" in lookupStrings, "Completion variants: $lookupStrings")

        fixture.lookup.currentItem = variants.first { it.lookupString == "public" }
        fixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
        assertEquals("public ", fixture.file.text)
    }

    @Test
    fun `accepting access modifier completion with space inserts one space`() {
        buildProject {
            at("example_at.cfg", "pub<caret>")
        }

        val variants = fixture.completeBasic().orEmpty()
        fixture.lookup.currentItem = variants.first { it.lookupString == "public" }
        fixture.finishLookup(' ')

        assertEquals("public ", fixture.file.text)
    }

    @Test
    fun `qualified class completion inserts a package dot and opens the next segment completion`() {
        addTargetClass()
        buildProject {
            at("example_at.cfg", "public net.<caret>")
        }

        TestModeFlags.runWithFlag(CompletionAutoPopupHandler.ourTestingAutopopup, true) {
            val variants = fixture.completeBasic()
            if (!variants.isNullOrEmpty()) {
                val packageVariant = variants.firstOrNull { "minecraft" in it.allLookupStrings }
                assertNotNull(packageVariant, "Completion variants: ${variants.map { it.lookupString }}")
                fixture.lookup.currentItem = packageVariant
                fixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
            }

            PlatformTestUtil.waitWithEventsDispatching(
                "next class segment completion popup",
                {
                    LookupManager.getActiveLookup(fixture.editor)?.items?.any {
                        "Test" in it.allLookupStrings
                    } == true
                },
                5,
            )
        }

        assertEquals("public net.minecraft.", fixture.file.text)
        val lookup = LookupManager.getActiveLookup(fixture.editor)
        assertNotNull(lookup)
        assertTrue(
            lookup!!.items.any { "Test" in it.allLookupStrings },
            "Completion variants: ${lookup.items.map { it.lookupString }}",
        )
    }

    @Test
    fun `qualified class completion replaces a partial package segment`() {
        addTargetClass()
        buildProject {
            at("example_at.cfg", "public net.mine<caret>")
        }

        val variants = fixture.completeBasic()
        if (!variants.isNullOrEmpty()) {
            val packageVariant = variants.firstOrNull { "minecraft" in it.allLookupStrings }
            assertNotNull(packageVariant, "Completion variants: ${variants.map { it.lookupString }}")
            fixture.lookup.currentItem = packageVariant
            fixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
        }

        assertEquals("public net.minecraft.", fixture.file.text)
    }

    @Test
    fun `qualified class completion suggests and inserts a subpackage`() {
        addTargetClass()
        fixture.addClass("package net.minecraft.world; public class Example {}")
        buildProject {
            at("example_at.cfg", "public net.minecraft.<caret>")
        }

        val variants = fixture.completeBasic().orEmpty()
        val packageVariant = variants.firstOrNull { "world" in it.allLookupStrings }
        assertNotNull(packageVariant, "Completion variants: ${variants.map { it.lookupString }}")
        fixture.lookup.currentItem = packageVariant
        fixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)

        assertEquals("public net.minecraft.world.", fixture.file.text)
    }

    @Test
    fun `qualified class completion inserts a space and opens member completion`() {
        addTargetClass()
        buildProject {
            at("example_at.cfg", "public net.minecraft.<caret>")
        }

        TestModeFlags.runWithFlag(CompletionAutoPopupHandler.ourTestingAutopopup, true) {
            val variants = fixture.completeBasic()
            if (!variants.isNullOrEmpty()) {
                val classVariant = variants.firstOrNull { "Test" in it.allLookupStrings }
                assertNotNull(classVariant, "Completion variants: ${variants.map { it.lookupString }}")
                fixture.lookup.currentItem = classVariant
                fixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
            }

            PlatformTestUtil.waitWithEventsDispatching(
                "member completion popup",
                { LookupManager.getActiveLookup(fixture.editor)?.items?.isNotEmpty() == true },
                5,
            )
        }

        assertEquals("public net.minecraft.Test ", fixture.file.text)
        val lookup = LookupManager.getActiveLookup(fixture.editor)
        assertNotNull(lookup)
        assertTrue(
            lookup!!.items.any { it.`object` is PsiField || it.`object` is PsiMethod },
            "Completion variants: ${lookup.items.map { it.lookupString }}",
        )
    }

    @Test
    fun `dot after a class package automatically opens package completion`() {
        addTargetClass()
        buildProject {
            at("example_at.cfg", "public net<caret>")
        }

        TestModeFlags.runWithFlag(CompletionAutoPopupHandler.ourTestingAutopopup, true) {
            fixture.type(".")
            PlatformTestUtil.waitWithEventsDispatching(
                "package completion popup",
                {
                    LookupManager.getActiveLookup(fixture.editor)?.items?.any {
                        "minecraft" in it.allLookupStrings
                    } == true
                },
                5,
            )
        }

        assertEquals("public net.", fixture.file.text)
        val lookup = LookupManager.getActiveLookup(fixture.editor)
        assertNotNull(lookup)
        assertTrue(
            lookup!!.items.any { "minecraft" in it.allLookupStrings },
            "Completion variants: ${lookup.items.map { it.lookupString }}",
        )
    }

    @Test
    fun `dot completion respects disabled automatic completion popup setting`() {
        addTargetClass()
        buildProject {
            at("example_at.cfg", "public net.<caret>")
        }

        val settings = CodeInsightSettings.getInstance()
        val previousAutoPopupSetting = settings.AUTO_POPUP_COMPLETION_LOOKUP
        try {
            settings.AUTO_POPUP_COMPLETION_LOOKUP = false
            LookupManager.hideActiveLookup(project)
            AtTypedHandlerDelegate().checkAutoPopup('.', project, fixture.editor, fixture.file)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        } finally {
            settings.AUTO_POPUP_COMPLETION_LOOKUP = previousAutoPopupSetting
        }

        assertEquals("public net.", fixture.file.text)
        assertNull(LookupManager.getActiveLookup(fixture.editor))
    }

    @Test
    fun `explicit qualified class completion works when automatic popup is disabled`() {
        addTargetClass()
        buildProject {
            at("example_at.cfg", "public net.<caret>")
        }

        val settings = CodeInsightSettings.getInstance()
        val previousAutoPopupSetting = settings.AUTO_POPUP_COMPLETION_LOOKUP
        try {
            settings.AUTO_POPUP_COMPLETION_LOOKUP = false
            val variants = fixture.completeBasic()
            if (!variants.isNullOrEmpty()) {
                val packageVariant = variants.firstOrNull { "minecraft" in it.allLookupStrings }
                assertNotNull(packageVariant, "Completion variants: ${variants.map { it.lookupString }}")
                fixture.lookup.currentItem = packageVariant
                fixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
            }
        } finally {
            settings.AUTO_POPUP_COMPLETION_LOOKUP = previousAutoPopupSetting
        }

        assertEquals("public net.minecraft.", fixture.file.text)
    }

    @Test
    fun `space after class name automatically opens member completion`() {
        addTargetClass()
        buildProject {
            at("example_at.cfg", "public net.minecraft.Test<caret>")
        }

        TestModeFlags.runWithFlag(CompletionAutoPopupHandler.ourTestingAutopopup, true) {
            fixture.type(" ")
            PlatformTestUtil.waitWithEventsDispatching(
                "member completion popup",
                { LookupManager.getActiveLookup(fixture.editor)?.items?.isNotEmpty() == true },
                5,
            )
        }

        val lookup = LookupManager.getActiveLookup(fixture.editor)
        assertNotNull(lookup)
        assertTrue(
            lookup!!.items.any { it.`object` is PsiField || it.`object` is PsiMethod },
            "Completion variants: ${lookup.items.map { it.lookupString }}",
        )
    }

    @Test
    fun `class completion inserts space and automatically opens member completion`() {
        addTargetClass()
        buildProject {
            at("example_at.cfg", "public Tes<caret>")
        }

        TestModeFlags.runWithFlag(CompletionAutoPopupHandler.ourTestingAutopopup, true) {
            val variants = fixture.completeBasic()

            if (variants != null) {
                val classVariant = variants.firstOrNull { it.lookupString == "net.minecraft.Test" }
                assertNotNull(classVariant, "Completion variants: ${variants.map { it.lookupString }}")
                fixture.lookup.currentItem = classVariant
                fixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
            }

            PlatformTestUtil.waitWithEventsDispatching(
                "member completion popup",
                { LookupManager.getActiveLookup(fixture.editor)?.items?.isNotEmpty() == true },
                5,
            )
        }

        assertEquals("public net.minecraft.Test ", fixture.file.text)
        val lookup = LookupManager.getActiveLookup(fixture.editor)
        assertNotNull(lookup)
        assertTrue(
            lookup!!.items.any { it.`object` is PsiField || it.`object` is PsiMethod },
            "Completion variants: ${lookup.items.map { it.lookupString }}",
        )
    }

    @Test
    fun `accepting class completion with space inserts one space and opens member completion`() {
        addTargetClass()
        buildProject {
            at("example_at.cfg", "public Tes<caret>")
        }

        TestModeFlags.runWithFlag(CompletionAutoPopupHandler.ourTestingAutopopup, true) {
            val variants = fixture.completeBasic().orEmpty()
            fixture.lookup.currentItem = variants.first { it.lookupString == "net.minecraft.Test" }
            fixture.finishLookup(' ')
            PlatformTestUtil.waitWithEventsDispatching(
                "member completion popup",
                { LookupManager.getActiveLookup(fixture.editor)?.items?.isNotEmpty() == true },
                5,
            )
        }

        assertEquals("public net.minecraft.Test ", fixture.file.text)
        val lookup = LookupManager.getActiveLookup(fixture.editor)
        assertNotNull(lookup)
        assertTrue(
            lookup!!.items.any { it.`object` is PsiField || it.`object` is PsiMethod },
            "Completion variants: ${lookup.items.map { it.lookupString }}",
        )
    }

    @Test
    fun `class completion respects disabled automatic completion popup setting`() {
        addTargetClass()
        buildProject {
            at("example_at.cfg", "public Tes<caret>")
        }

        val settings = CodeInsightSettings.getInstance()
        val previousAutoPopupSetting = settings.AUTO_POPUP_COMPLETION_LOOKUP
        try {
            settings.AUTO_POPUP_COMPLETION_LOOKUP = false
            TestModeFlags.runWithFlag(CompletionAutoPopupHandler.ourTestingAutopopup, true) {
                val variants = fixture.completeBasic()
                if (!variants.isNullOrEmpty()) {
                    fixture.lookup.currentItem = variants.first { it.lookupString == "net.minecraft.Test" }
                    fixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
                }
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            }
        } finally {
            settings.AUTO_POPUP_COMPLETION_LOOKUP = previousAutoPopupSetting
        }

        assertEquals("public net.minecraft.Test ", fixture.file.text)
        assertNull(LookupManager.getActiveLookup(fixture.editor))
    }

    @Test
    fun `space after class name respects disabled automatic completion popup setting`() {
        addTargetClass()
        buildProject {
            at("example_at.cfg", "public net.minecraft.Test <caret>")
        }

        val settings = CodeInsightSettings.getInstance()
        val previousAutoPopupSetting = settings.AUTO_POPUP_COMPLETION_LOOKUP
        try {
            settings.AUTO_POPUP_COMPLETION_LOOKUP = false
            LookupManager.hideActiveLookup(project)
            AtTypedHandlerDelegate().checkAutoPopup(' ', project, fixture.editor, fixture.file)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        } finally {
            settings.AUTO_POPUP_COMPLETION_LOOKUP = previousAutoPopupSetting
        }

        assertEquals("public net.minecraft.Test ", fixture.file.text)
        assertNull(LookupManager.getActiveLookup(fixture.editor))
    }

    @Test
    fun `binary class completion retains inner anonymous and local classes`() {
        fixture.addClass(
            """
            package net.minecraft;

            public class Outer {
                private final Runnable anonymous = new Runnable() {
                    public void run() {}
                };

                public void createLocal() {
                    class Local {}
                }

                public static class Inner {}
            }
            """.trimIndent(),
        )
        buildProject {
            at("example_at.cfg", "public net.minecraft.Outer${'$'}<caret>")
        }

        val variants = fixture.completeBasic().orEmpty()
        val lookupStrings = variants.map { it.lookupString }

        assertTrue("net.minecraft.Outer${'$'}Inner" in lookupStrings, "Completion variants: $lookupStrings")
        assertTrue("net.minecraft.Outer${'$'}1" in lookupStrings, "Completion variants: $lookupStrings")
        assertTrue(
            variants.any { (it.`object` as? PsiClass)?.name == "Local" },
            "Completion variants: $lookupStrings",
        )

        fixture.lookup.currentItem = variants.first { it.lookupString == "net.minecraft.Outer${'$'}Inner" }
        fixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
        assertEquals("public net.minecraft.Outer${'$'}Inner ", fixture.file.text)
    }

    @Test
    fun `intermediary field prefix completes named field to intermediary name`() {
        addTargetClass()
        buildProject {
            at("example_at.cfg", "public-f net.minecraft.Test field_<caret>")
        }

        val variants = fixture.completeBasic().orEmpty()
        val fieldVariant = variants.firstOrNull { it.lookupString == "namedField" }

        assertNotNull(fieldVariant, "Completion variants: ${variants.map { it.lookupString }}")
        assertEquals("namedField", (fieldVariant!!.`object` as PsiField).name)

        fixture.lookup.currentItem = fieldVariant
        fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        assertEquals("public-f net.minecraft.Test field_1_value # namedField", fixture.file.text)
    }

    @Test
    fun `intermediary method prefix completes correct overload with descriptor`() {
        addTargetClass()
        fixture.addClass("package net.minecraft; public class Arg {}")
        buildProject {
            at("example_at.cfg", "public net.minecraft.Test func_<caret>")
        }

        val variants = fixture.completeBasic().orEmpty()
        val methodVariant = variants.firstOrNull {
            val method = it.`object` as? PsiMethod
            method?.parameterList?.parameters?.singleOrNull()?.type?.canonicalText == "int"
        }

        assertNotNull(methodVariant, "Completion variants: ${variants.map { it.lookupString }}")
        assertEquals("namedMethod(int)", methodVariant!!.lookupString)

        fixture.lookup.currentItem = methodVariant
        fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        assertEquals("public net.minecraft.Test func_1_run(I)V # namedMethod", fixture.file.text)
    }

    @Test
    fun `named method completion inserts named name with descriptor`() {
        setNamespace(AccessTransformerNamespace.NAMED)
        addTargetClass()
        buildProject {
            at("example_at.cfg", "public net.minecraft.Test namedM<caret>")
        }

        val variants = fixture.completeBasic().orEmpty()
        val methodVariant = variants.firstOrNull {
            val method = it.`object` as? PsiMethod
            method?.parameterList?.parameters?.singleOrNull()?.type?.canonicalText == "int"
        }

        assertNotNull(methodVariant, "Completion variants: ${variants.map { it.lookupString }}")
        assertEquals("namedMethod(int)", methodVariant!!.lookupString)

        fixture.lookup.currentItem = methodVariant
        fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        assertEquals("public net.minecraft.Test namedMethod(I)V", fixture.file.text)
    }

    private fun addTargetClass() {
        fixture.addClass(
            """
            package net.minecraft;

            public class Test {
                private int namedField;
                private int otherField;
                private void namedMethod(int value) {}
                private void namedMethod(String value) {}
                private void namedMethod(Arg value) {}
                private Arg namedReturn() { return null; }
            }
            """.trimIndent(),
        )
    }

    private fun setNamespace(namespace: AccessTransformerNamespace) {
        val mcpModule = MinecraftFacet.getInstance(module, McpModuleType)
        assertNotNull(mcpModule)
        mcpModule!!.getSettings().accessTransformerNamespace = namespace
    }
}
