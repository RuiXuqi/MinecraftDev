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

package com.demonwav.mcdev.platform.mixin.util

import com.demonwav.mcdev.platform.mixin.util.MixinConstants.Annotations.SLICE
import com.demonwav.mcdev.platform.mixin.util.MixinConstants.Classes.SPECIFIER
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.parentOfType

enum class InjectionPointSpecifier {
    FIRST, LAST, ONE, ALL;

    companion object {
        fun isAllowed(at: PsiAnnotation): Boolean {
            if (isAllowedOutsideSlice(at.project)) {
                return true
            }
            val isInsideSlice = at.parentOfType<PsiAnnotation>()?.hasQualifiedName(SLICE) == true
            return isInsideSlice
        }

        private fun isAllowedOutsideSlice(project: Project): Boolean =
            CachedValuesManager.getManager(project).getCachedValue(project) {
                val result = JavaPsiFacade.getInstance(project)
                    .findClass(SPECIFIER, GlobalSearchScope.allScope(project)) != null
                CachedValueProvider.Result.create(result, ProjectRootManager.getInstance(project))
            }
    }
}
