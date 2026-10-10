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

package com.demonwav.mcdev.platform.mcp.srg

import com.demonwav.mcdev.util.MemberReference
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

class StandardSrgParserTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `identical mappings are ignored`() {
        val mappingFile = tempDir.resolve("duplicate.srg")
        Files.writeString(
            mappingFile,
            """
            FD: net/minecraft/util/text/TextFormatting/BLACK net/minecraft/util/text/TextFormatting/BLACK
            FD: net/minecraft/util/text/TextFormatting/BLACK net/minecraft/util/text/TextFormatting/BLACK
            FD: net/minecraft/util/text/TextFormatting/fancyStyling net/minecraft/util/text/TextFormatting/field_96303_A
            """.trimIndent(),
        )

        val mappings = StandardSrgParser.parseSrg(mappingFile)

        val black = MemberReference("BLACK", null, "net.minecraft.util.text.TextFormatting")
        assertEquals(black, mappings.getIntermediaryField(black))
        val fancyStyling = MemberReference("fancyStyling", null, "net.minecraft.util.text.TextFormatting")
        val field96303A = MemberReference("field_96303_A", null, "net.minecraft.util.text.TextFormatting")
        assertEquals(field96303A, mappings.getIntermediaryField(fancyStyling))
    }

    @Test
    fun `conflicting mappings are rejected`() {
        val mappingFile = tempDir.resolve("conflicting.srg")
        Files.writeString(
            mappingFile,
            """
            FD: net/minecraft/Test/value net/minecraft/Test/field_1
            FD: net/minecraft/Test/value net/minecraft/Test/field_2
            """.trimIndent(),
        )

        assertThrows<IllegalArgumentException> {
            StandardSrgParser.parseSrg(mappingFile)
        }
    }
}
