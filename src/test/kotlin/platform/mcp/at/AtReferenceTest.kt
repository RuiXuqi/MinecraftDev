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
import com.demonwav.mcdev.platform.mcp.srg.SrgType
import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
    fun `short class name completion suggests qualified Java class`() {
        addTargetClass()
        buildProject {
            at("example_at.cfg", "public Tes<caret>")
        }

        val variants = fixture.completeBasic()

        if (variants == null) {
            assertEquals("public net.minecraft.Test", fixture.file.text)
        } else {
            val lookupStrings = variants.map { it.lookupString }
            assertTrue("net.minecraft.Test" in lookupStrings, "Completion variants: $lookupStrings")
        }
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
