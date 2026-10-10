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

package com.demonwav.mcdev.translations.intentions

import com.demonwav.mcdev.translations.Translation
import com.demonwav.mcdev.translations.TranslationFiles
import com.demonwav.mcdev.translations.index.TranslationIndex
import com.demonwav.mcdev.translations.index.TranslationIndexEntry
import com.demonwav.mcdev.translations.lang.gen.psi.LangTypes
import com.demonwav.mcdev.util.mcDomain
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

class TranslationFileAnnotator : Annotator {
    override fun annotate(element: PsiElement, annotations: AnnotationHolder) {
        val translation = TranslationFiles.toTranslation(element)
        if (translation != null) {
            val file = element.containingFile
            val fileTranslations = cachedFileTranslations(file)
            val defaultEntries = cachedDefaultEntries(file)
            checkEntryKey(element, translation, annotations)
            checkEntryDuplicates(element, translation, annotations, fileTranslations)
            checkEntryMatchesDefault(element, translation, annotations, defaultEntries)
        }
        if (element.node.elementType == LangTypes.DUMMY) {
            annotations.newAnnotation(HighlightSeverity.ERROR, "Translations must not contain incomplete entries.")
                .range(element)
                .create()
        }
    }

    private fun cachedFileTranslations(file: PsiFile?): Map<String, Int> {
        file ?: return emptyMap()
        return CachedValuesManager.getCachedValue(file) {
            val counts = TranslationIndex.Util.getTranslations(file)
                .groupingBy { it.key }
                .eachCount()
            CachedValueProvider.Result.create(counts, file, PsiModificationTracker.MODIFICATION_COUNT)
        }
    }

    private fun cachedDefaultEntries(file: PsiFile?): List<TranslationIndexEntry> {
        file ?: return emptyList()
        val project = file.project
        val domain = file.virtualFile?.mcDomain
        return CachedValuesManager.getManager(project).getCachedValue(file) {
            val entries = TranslationIndex.Util.getAllDefaultEntries(project, domain).toList()
            CachedValueProvider.Result.create(entries, PsiModificationTracker.MODIFICATION_COUNT)
        }
    }

    private fun checkEntryKey(element: PsiElement, translation: Translation, annotations: AnnotationHolder) {
        if (translation.key != translation.trimmedKey) {
            annotations.newAnnotation(HighlightSeverity.WARNING, "Translation key contains whitespace at start or end.")
                .range(element)
                .newFix(TrimKeyIntention(element)).universal().registerFix()
                .create()
        }
    }

    private fun checkEntryDuplicates(
        element: PsiElement,
        translation: Translation,
        annotations: AnnotationHolder,
        fileTranslations: Map<String, Int>,
    ) {
        val count = fileTranslations[translation.key] ?: 0
        if (count > 1) {
            annotations.newAnnotation(HighlightSeverity.WARNING, "Duplicate translation keys \"${translation.key}\".")
                .newFix(RemoveDuplicatesIntention(translation, element)).universal().registerFix()
                .create()
        }
    }

    private fun checkEntryMatchesDefault(
        element: PsiElement,
        translation: Translation,
        annotations: AnnotationHolder,
        defaultEntries: List<TranslationIndexEntry>,
    ) {
        if (defaultEntries.any { it.contains(translation.key) }) {
            return
        }
        val warningText = "Translation key not included in default localization file."
        annotations.newAnnotation(HighlightSeverity.WARNING, warningText)
            .newFix(RemoveUnmatchedEntryIntention(element)).universal().registerFix()
            .create()
    }
}
