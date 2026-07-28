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

package com.demonwav.mcdev.platform.mcp.at.completion

import com.demonwav.mcdev.platform.mcp.at.AtLanguage
import com.demonwav.mcdev.platform.mcp.at.gen.psi.AtClassName
import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

class AtTypedHandlerDelegate : TypedHandlerDelegate() {
    override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (file.language != AtLanguage) {
            return super.checkAutoPopup(charTyped, project, editor, file)
        }

        if (charTyped == ' ') {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor) {
                val classNameEndOffset = editor.caretModel.offset - 1
                if (classNameEndOffset <= 0) {
                    return@scheduleAutoPopup false
                }
                val element = it.findElementAt(classNameEndOffset - 1)
                val className = element?.let { current ->
                    PsiTreeUtil.getParentOfType(current, AtClassName::class.java, false)
                }
                className?.textRange?.endOffset == classNameEndOffset
            }
            return Result.CONTINUE
        }

        if (charTyped == '$') {
            return Result.CONTINUE
        }

        return super.checkAutoPopup(charTyped, project, editor, file)
    }
}
