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

package com.demonwav.mcdev.platform.mcp.gradle.datahandler

import com.demonwav.mcdev.platform.mcp.McpModuleSettings.AccessTransformerNamespace
import com.demonwav.mcdev.platform.mcp.gradle.tooling.McpModelMDG
import com.demonwav.mcdev.platform.mcp.srg.SrgType
import com.demonwav.mcdev.util.MemberReference
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class McpModelMDGHandlerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `MDG legacy uses standard SRG mappings and intermediary access transformers`() {
        val mappingFile = tempDir.resolve("namedToIntermediate.srg")
        Files.writeString(
            mappingFile,
            "FD: net/minecraft/Test/namedField net/minecraft/Test/field_1_value",
        )
        val model = object : McpModelMDG {
            override fun getMinecraftVersion() = "1.12.2"
            override fun getPlatformVersion() = "0.5.17-alpha"
            override fun getMcpVersion() = "stable_39-1.12"
            override fun getMappingsFile() = mappingFile.toFile()
            override fun getAccessTransformers() = listOf(File("src/main/resources/example_at.cfg"))
        }

        val state = McpModelMDGHandler.createState(model)

        assertEquals(SrgType.SRG, state.srgType)
        assertEquals(AccessTransformerNamespace.INTERMEDIARY, state.accessTransformerNamespace)

        val mappings = state.srgType!!.srgParser.parseSrg(mappingFile)
        val namedField = MemberReference("namedField", null, "net.minecraft.Test")
        val srgField = MemberReference("field_1_value", null, "net.minecraft.Test")
        assertEquals(srgField, mappings.getIntermediaryField(namedField))
    }
}
