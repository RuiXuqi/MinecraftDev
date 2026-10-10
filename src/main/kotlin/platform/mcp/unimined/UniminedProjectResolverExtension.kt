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

package com.demonwav.mcdev.platform.mcp.unimined

import com.demonwav.mcdev.platform.mcp.McpModuleSettings
import com.demonwav.mcdev.platform.mcp.McpModuleSettings.AccessTransformerNamespace
import com.demonwav.mcdev.platform.mcp.at.AtFileType
import com.demonwav.mcdev.platform.mcp.fabricloom.FabricLoomData
import com.demonwav.mcdev.platform.mcp.gradle.McpModelData
import com.demonwav.mcdev.platform.mcp.gradle.tooling.unimined.UniminedModel
import com.demonwav.mcdev.platform.mcp.srg.SrgType
import com.demonwav.mcdev.util.runWriteTaskLater
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.fileTypes.ExactFileNameMatcher
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.vfs.LocalFileSystem
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

class UniminedProjectResolverExtension : AbstractProjectResolverExtension() {

    override fun getExtraProjectModelClasses(): Set<Class<out Any>> =
        setOf(UniminedModel::class.java)

    override fun getToolingExtensionsClasses() = extraProjectModelClasses

    override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        val model = resolverCtx.getExtraProject(gradleModule, UniminedModel::class.java)
        if (model != null) {
            val bySourceSet = model.sourceSets.associateBy { it.sourceSetName }

            // Unimined configures each source set separately, potentially with a different
            // modloader, so unlike the single-config handlers the data is matched per source set
            // instead of copied to every child
            bySourceSet["main"]?.let { attach(it, ideModule, ideModule.data) }

            for (child in ideModule.children) {
                val childData = child.data as? GradleSourceSetData ?: continue
                val entry = bySourceSet[childData.id.substringAfterLast(':')] ?: continue
                attach(entry, child, childData)
            }
        }

        super.populateModuleExtraModels(gradleModule, ideModule)
    }

    private fun attach(entry: UniminedModel.SourceSetEntry, node: DataNode<*>, moduleData: ModuleData) {
        if (entry.isFabricLike) {
            node.createChild(
                FabricLoomData.KEY,
                FabricLoomData(moduleData, entry.tinyMappingsFile, emptyMap(), false),
            )
        }

        val srgFile = entry.srgMappingFile
        val state = if (srgFile != null) {
            McpModuleSettings.State(
                minecraftVersion = entry.minecraftVersion,
                mappingFile = srgFile.absolutePath,
                srgType = SrgType.SRG,
                accessTransformerNamespace = AccessTransformerNamespace.NAMED,
            )
        } else {
            McpModuleSettings.State(
                minecraftVersion = entry.minecraftVersion,
                accessTransformerNamespace = AccessTransformerNamespace.NAMED,
            )
        }

        val ats = entry.accessTransformers
        if (ats.isNotEmpty()) {
            runWriteTaskLater {
                for (at in ats) {
                    val fileTypeManager = FileTypeManager.getInstance()
                    val atFile = LocalFileSystem.getInstance().findFileByIoFile(at) ?: continue
                    fileTypeManager.associate(AtFileType, ExactFileNameMatcher(atFile.name))
                }
            }
        }

        node.createChild(McpModelData.KEY, McpModelData(moduleData, state, null, ats.takeIf { it.isNotEmpty() }))
    }
}
