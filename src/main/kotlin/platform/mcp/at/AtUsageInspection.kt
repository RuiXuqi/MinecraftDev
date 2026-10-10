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

import com.demonwav.mcdev.platform.mcp.at.gen.psi.AtEntry
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor

class AtUsageInspection : LocalInspectionTool() {

    override fun getStaticDescription(): String {
        return "The declared access transformer is never used"
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is AtEntry) {
                    return
                }

                val source = element.function?.funcName ?: element.fieldName ?: return
                val psi = AtSymbolResolver.resolve(source) ?: return

                val query = ReferencesSearch.search(psi, GlobalSearchScope.projectScope(element.project))
                val hasNonAtUsage = !query.forEach(Processor { it.element.containingFile is AtFile })
                if (!hasNonAtUsage) {
                    holder.registerProblem(
                        element,
                        "Access Transformer entry is never used",
                        ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                    )
                }
            }
        }
    }
}
