/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.jdk;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class VarargParameterInspection extends MethodInspection {

    public String getID(){
        return "VariableArgumentMethod";
    }

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "variable.argument.method.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.JDK_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "variable.argument.method.problem.descriptor");
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new VarargParameterFix();
    }

    private static class VarargParameterFix extends InspectionGadgetsFix {

        public String getName() {
            return InspectionGadgetsBundle.message(
                    "variable.argument.method.quickfix");
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiMethod method = (PsiMethod)element.getParent();
            final PsiParameterList parameterList = method.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            final PsiParameter lastParameter =
                    parameters[parameters.length - 1];
            if (!lastParameter.isVarArgs()) {
                return;
            }
            final PsiEllipsisType type =
                    (PsiEllipsisType)lastParameter.getType();
            final Query<PsiReference> query = ReferencesSearch.search(method);
            final PsiType componentType = type.getComponentType();
            final String typeText = componentType.getCanonicalText();
            final Collection<PsiReference> references = query.findAll();
            for (PsiReference reference : references) {
                modifyCalls(reference, typeText, parameters.length - 1);
            }
            final PsiType arrayType = type.toArrayType();
            final PsiManager psiManager = lastParameter.getManager();
            final PsiElementFactory factory = psiManager.getElementFactory();
            final PsiTypeElement newTypeElement =
                    factory.createTypeElement(arrayType);
            final PsiTypeElement typeElement =
                    lastParameter.getTypeElement();
            typeElement.replace(newTypeElement);
        }

        public static void modifyCalls(PsiReference reference,
                                       String arrayTypeText,
                                       int indexOfFirstVarargArgument)
                throws IncorrectOperationException {
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression)reference.getElement();
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression)referenceExpression.getParent();
            final PsiExpressionList argumentList =
                    methodCallExpression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            @NonNls final StringBuilder builder = new StringBuilder("new ");
            builder.append(arrayTypeText);
            builder.append("[]{");
            if (arguments.length > indexOfFirstVarargArgument) {
                final PsiExpression firstArgument =
                        arguments[indexOfFirstVarargArgument];
                final String firstArgumentText = firstArgument.getText();
                builder.append(firstArgumentText);
                for (int i = indexOfFirstVarargArgument + 1;
                     i < arguments.length; i++) {
                    builder.append(',');
                    builder.append(arguments[i].getText());
                }
            }
            builder.append('}');
            final PsiManager manager = referenceExpression.getManager();
            final PsiElementFactory factory = manager.getElementFactory();
            final PsiExpression arrayExpression =
                    factory.createExpressionFromText(builder.toString(),
                            referenceExpression);
            if (arguments.length > indexOfFirstVarargArgument) {
                final PsiExpression firstArgument =
                        arguments[indexOfFirstVarargArgument];
                argumentList.deleteChildRange(firstArgument,
                        arguments[arguments.length - 1]);
                argumentList.add(arrayExpression);
            } else {
                argumentList.add(arrayExpression);
            }
            final CodeStyleManager codeStyleManager =
                    manager.getCodeStyleManager();
            codeStyleManager.shortenClassReferences(argumentList);
            codeStyleManager.reformat(argumentList);
        }

    }

    public BaseInspectionVisitor buildVisitor() {
        return new VarargParameterVisitor();
    }

    private static class VarargParameterVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            final PsiParameterList parameterList = method.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            if (parameters.length < 1) {
                return;
            }
            final PsiParameter lastParameter =
                    parameters[parameters.length - 1];
            if (lastParameter.isVarArgs()) {
                registerMethodError(method);
            }
        }
    }
}