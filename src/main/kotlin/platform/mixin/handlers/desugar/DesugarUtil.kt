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

package com.demonwav.mcdev.platform.mixin.handlers.desugar

import com.demonwav.mcdev.util.cached
import com.demonwav.mcdev.util.childrenOfType
import com.demonwav.mcdev.util.findContainingClass
import com.demonwav.mcdev.util.packageName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.UnfairTextRange
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiMethodReferenceExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiSuperExpression
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiVariable
import com.intellij.psi.impl.light.LightMemberReference
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.parents
import com.intellij.refactoring.util.LambdaRefactoringUtil
import com.intellij.util.JavaPsiConstructorUtil
import com.intellij.util.Processor
import org.jetbrains.annotations.VisibleForTesting
import org.objectweb.asm.Opcodes

object DesugarUtil {
    private val ORIGINAL_ELEMENT_KEY = Key.create<PsiElement>("mcdev.desugar.originalElement")
    private val UNNAMED_VARIABLE_KEY = Key.create<Boolean>("mcdev.desugar.unnamedVariable")

    private val DESUGARERS = arrayOf(
        RemoveVarArgsDesugarer,
        AnonymousAndLocalClassDesugarer,
        FieldAssignmentDesugarer,
    )

    fun getOriginalElement(desugared: PsiElement): PsiElement? {
        return desugared.parents(true).firstNotNullOfOrNull { it.getCopyableUserData(ORIGINAL_ELEMENT_KEY) }
    }

    fun setOriginalElement(desugared: PsiElement, original: PsiElement?) {
        desugared.putCopyableUserData(ORIGINAL_ELEMENT_KEY, original)
    }

    fun getOriginalToDesugaredMap(desugared: PsiElement): Map<PsiElement, List<PsiElement>> {
        val desugaredFile = desugared.containingFile ?: return emptyMap()
        return desugaredFile.cached {
            val result = mutableMapOf<PsiElement, MutableList<PsiElement>>()
            PsiTreeUtil.processElements(desugaredFile) { desugaredElement ->
                desugaredElement.getCopyableUserData(ORIGINAL_ELEMENT_KEY)?.let { original ->
                    result.getOrPut(original) { mutableListOf() } += desugaredElement
                }
                true
            }
            result
        }
    }

    fun isUnnamedVariable(variable: PsiVariable): Boolean {
        return variable.getCopyableUserData(UNNAMED_VARIABLE_KEY) == true
    }

    fun setUnnamedVariable(variable: PsiVariable, value: Boolean) {
        variable.putCopyableUserData(UNNAMED_VARIABLE_KEY, value)
    }

    fun desugar(project: Project, clazz: PsiClass, context: DesugarContext): PsiClass? {
        if (clazz is PsiCompiledElement) return null
        val file = clazz.containingFile as? PsiJavaFile ?: return null
        return file.cached {
            val desugaredFile = file.copy() as PsiJavaFile
            setOriginalRecursive(desugaredFile, file)
            for (desugarer in DESUGARERS) {
                desugarer.desugar(project, desugaredFile, context)
            }
            getOriginalToDesugaredMap(desugaredFile)[clazz]?.filterIsInstance<PsiClass>()?.firstOrNull()
        }
    }

    @VisibleForTesting
    fun setOriginalRecursive(desugared: PsiElement, original: PsiElement) {
        val desugaredElements = mutableListOf<PsiElement>()
        desugared.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)
                desugaredElements.add(element)
            }
        })

        val originalElements = mutableListOf<PsiElement>()
        original.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)
                originalElements.add(element)
            }
        })

        for ((originalElement, desugaredElement) in originalElements.zip(desugaredElements)) {
            setOriginalElement(desugaredElement, originalElement)
        }
    }

    internal fun allClasses(file: PsiJavaFile): List<PsiClass> {
        return file.childrenOfType<PsiClass>().filter { it !is PsiTypeParameter }
    }

    internal fun allClassesShallow(clazz: PsiClass): List<PsiClass> {
        val allClasses = mutableListOf<PsiClass>()
        clazz.acceptChildren(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitClass(aClass: PsiClass) {
                if (aClass !is PsiTypeParameter) {
                    allClasses += aClass
                }
            }
        })
        return allClasses
    }

    internal fun findReferencesInFile(element: PsiElement): List<PsiReference> {
        fun PsiMember.createSyntheticReference(): PsiReference {
            return object : LightMemberReference(manager, this, PsiSubstitutor.EMPTY) {
                override fun getElement() = this@createSyntheticReference
                override fun getRangeInElement(): TextRange {
                    val identifier = (this@createSyntheticReference as? PsiNameIdentifierOwner)?.nameIdentifier
                    if (identifier != null) {
                        val startOffsetInParent = identifier.startOffsetInParent
                        return if (startOffsetInParent >= 0) {
                            TextRange.from(startOffsetInParent, identifier.textLength)
                        } else {
                            UnfairTextRange(-1, -1)
                        }
                    }

                    return super.getRangeInElement()
                }
            }
        }

        val file = element.containingFile as? PsiJavaFile ?: return emptyList()
        if (element is PsiCompiledElement) return emptyList()
        val results = mutableListOf<PsiReference>()

        ReferencesSearch.search(element, LocalSearchScope(file)).forEach(Processor {
            results += it
            true
        })

        // subclass constructor references don't work for non-physical files
        if (element is PsiMethod && element.isConstructor) {
            val clazz = element.containingClass
            if (clazz != null) {
                for (subClass in allClasses(file)) {
                    if (subClass !is PsiAnonymousClass && subClass.isInheritor(clazz, false)) {
                        val countImplicitSuperCalls = element.parameterList.isEmpty
                        val constructors = subClass.constructors
                        for (constructor in constructors) {
                            val thisOrSuperCall = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(constructor)
                            if (JavaPsiConstructorUtil.isSuperConstructorCall(thisOrSuperCall)) {
                                val reference = thisOrSuperCall!!.methodExpression.reference
                                if (reference != null && reference.isReferenceTo(element)) {
                                    results += reference
                                }
                            } else if (thisOrSuperCall == null && countImplicitSuperCalls) {
                                results += constructor.createSyntheticReference()
                            }
                        }

                        if (constructors.isEmpty() && countImplicitSuperCalls) {
                            results += subClass.createSyntheticReference()
                        }
                    }
                }
            }
        }

        return results
    }

    internal fun desugarMethodReferenceToLambda(methodReference: PsiMethodReferenceExpression): PsiLambdaExpression? {
        val originalMethodRef = getOriginalElement(methodReference)
        val lambda = LambdaRefactoringUtil.convertMethodReferenceToLambda(methodReference, false, true)
            ?: return null
        setOriginalElement(lambda, originalMethodRef)
        for (parameter in lambda.parameterList.parameters) {
            setUnnamedVariable(parameter, true)
        }
        return lambda
    }

    // see com.sun.tools.javac.comp.Lower.access, accReq
    fun needsBridgeMethod(expression: PsiJavaCodeReferenceElement, classVersion: Int): Boolean {
        // method calls with qualified super need bridge methods
        if (expression is PsiMethodCallExpression) {
            val qualifier = PsiUtil.skipParenthesizedExprDown(expression.methodExpression.qualifierExpression)
            if (qualifier is PsiSuperExpression && qualifier.qualifier != null) {
                return true
            }
        }

        val resolved = expression.resolve() as? PsiMember ?: return false
        val resolvedClass = resolved.containingClass ?: return false
        val fromClass = expression.findContainingClass() ?: return false

        if (resolvedClass == fromClass) {
            return false
        }

        if (classVersion <= Opcodes.V1_8 && resolved.hasModifierProperty(PsiModifier.PRIVATE)) {
            return true
        }

        if (resolved.hasModifierProperty(PsiModifier.PROTECTED) &&
            fromClass.packageName != resolvedClass.packageName &&
            !fromClass.isInheritor(resolvedClass, true)
        ) {
            return true
        }

        return false
    }
}
