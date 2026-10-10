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

package com.demonwav.mcdev.platform.mcp.at

import com.demonwav.mcdev.facet.MinecraftFacet
import com.demonwav.mcdev.platform.mcp.McpModuleSettings.AccessTransformerNamespace
import com.demonwav.mcdev.platform.mcp.McpModule
import com.demonwav.mcdev.platform.mcp.McpModuleType
import com.demonwav.mcdev.platform.mcp.at.gen.psi.AtArgument
import com.demonwav.mcdev.platform.mcp.at.gen.psi.AtClassName
import com.demonwav.mcdev.platform.mcp.at.gen.psi.AtEntry
import com.demonwav.mcdev.platform.mcp.at.gen.psi.AtFieldName
import com.demonwav.mcdev.platform.mcp.at.gen.psi.AtFuncName
import com.demonwav.mcdev.platform.mcp.at.gen.psi.AtFunction
import com.demonwav.mcdev.platform.mcp.at.gen.psi.AtReturnValue
import com.demonwav.mcdev.platform.mcp.mappings.Mappings
import com.demonwav.mcdev.util.MemberReference
import com.demonwav.mcdev.util.findQualifiedClass
import com.demonwav.mcdev.util.getPrimitiveType
import com.demonwav.mcdev.util.parseClassDescriptor
import com.demonwav.mcdev.util.qualifiedMemberReference
import com.demonwav.mcdev.util.simpleQualifiedMemberReference
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod

object AtSymbolResolver {

    fun resolve(source: PsiElement): PsiElement? = when (source) {
        is AtClassName -> resolveClass(source)
        is AtFieldName -> resolveField(source)
        is AtFuncName -> resolveMethod(source)
        is AtArgument -> resolveType(source.argumentText, source)
        is AtReturnValue -> resolveType(source.returnValueText, source)
        else -> null
    }

    private fun resolveClass(source: AtClassName): PsiElement? {
        val className = mapClassName(source.classNameText, source) ?: return null
        return findQualifiedClass(source.project, className, source.resolveScope)
    }

    private fun resolveField(source: AtFieldName): PsiField? {
        val entry = source.parent as? AtEntry ?: return null
        val reference = AtMemberReference.get(entry, source) ?: return null
        val mappedReference = findNamespaceMapper(source)?.toNamedField(reference) ?: return null
        return mappedReference.resolveMember(source.project, source.resolveScope) as? PsiField
    }

    private fun resolveMethod(source: AtFuncName): PsiMethod? {
        val function = source.parent as? AtFunction ?: return null
        val entry = function.parent as? AtEntry ?: return null
        val reference = AtMemberReference.get(entry, function) ?: return null
        val mappedReference = findNamespaceMapper(source)?.toNamedMethod(reference) ?: return null
        return mappedReference.resolveMember(source.project, source.resolveScope) as? PsiMethod
    }

    private fun resolveType(descriptor: String, source: PsiElement): PsiElement? {
        val componentDescriptor = descriptor.dropWhile { it == '[' }
        if (componentDescriptor.length == 1) {
            val boxedTypeName = getPrimitiveType(componentDescriptor.single())?.boxedTypeName ?: return null
            return JavaPsiFacade.getInstance(source.project).findClass(boxedTypeName, source.resolveScope)
        }
        val className = mapClassName(parseClassDescriptor(componentDescriptor), source) ?: return null
        return findQualifiedClass(source.project, className, source.resolveScope)
    }

    private fun mapClassName(className: String, source: PsiElement): String? {
        return findNamespaceMapper(source)?.toNamedClass(className)
    }

    private fun findNamespaceMapper(source: PsiElement): AtNamespaceMapper? =
        findMcpModule(source)?.let(AtNamespaceMapper::create)

    private fun findMcpModule(source: PsiElement): McpModule? {
        val module = ModuleUtilCore.findModuleForPsiElement(source) ?: return null
        return MinecraftFacet.getInstance(module)?.getModuleOfType(McpModuleType)
    }
}

internal class AtNamespaceMapper private constructor(private val mappings: Mappings?) {

    val usesNamedNames: Boolean
        get() = mappings == null

    fun toNamedClass(className: String): String = mappings?.getMappedClass(className) ?: className

    fun toNamedField(reference: MemberReference): MemberReference = mappings?.getMappedField(reference) ?: reference

    fun toNamedMethod(reference: MemberReference): MemberReference = mappings?.getMappedMethod(reference) ?: reference

    fun fromNamed(field: PsiField): MemberReference =
        mappings?.getIntermediaryField(field) ?: field.simpleQualifiedMemberReference

    fun fromNamed(method: PsiMethod): MemberReference =
        mappings?.getIntermediaryMethod(method) ?: method.qualifiedMemberReference

    companion object {
        fun create(module: McpModule): AtNamespaceMapper? = when (module.getSettings().accessTransformerNamespace) {
            AccessTransformerNamespace.NAMED -> AtNamespaceMapper(null)
            AccessTransformerNamespace.INTERMEDIARY -> module.mappingsManager?.mappingsNow?.let(::AtNamespaceMapper)
        }
    }
}
