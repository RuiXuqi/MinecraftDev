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

package com.demonwav.mcdev.platform.mcp.at.psi.mixins.impl

import com.demonwav.mcdev.platform.mcp.at.AtElementFactory
import com.demonwav.mcdev.platform.mcp.at.AtSymbolReference
import com.demonwav.mcdev.platform.mcp.at.AtSymbolResolver
import com.demonwav.mcdev.platform.mcp.at.psi.mixins.AtReturnValueMixin
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiReference

abstract class AtReturnValueImplMixin(node: ASTNode) : ASTWrapperPsiElement(node), AtReturnValueMixin {

    override val returnValueClass: PsiClass?
        get() = AtSymbolResolver.resolve(this) as? PsiClass

    override fun getReference(): PsiReference = AtSymbolReference(this)

    override val returnValueText: String
        get() = primitive?.text ?: classValue!!.text

    override fun setReturnValue(returnValue: String) {
        replace(AtElementFactory.createReturnValue(project, returnValue))
    }
}
