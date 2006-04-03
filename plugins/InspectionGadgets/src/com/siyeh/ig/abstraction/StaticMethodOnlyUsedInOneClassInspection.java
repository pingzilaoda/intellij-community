/**
 * (c) 2006 Carp Technologies BV
 * Brouwerijstraat 1, 7523XC Enschede
 * Created: 20060329, 11:55:52 AM
 */
package com.siyeh.ig.abstraction;

import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.InspectionGadgetsBundle;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.util.Query;
import com.intellij.util.Processor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.ide.DataManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StaticMethodOnlyUsedInOneClassInspection
        extends MethodInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "static.method.only.used.in.one.class.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.ABSTRACTION_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        final PsiNamedElement element = (PsiNamedElement)infos[0];
        final String name = element.getName();
        if (infos.length > 1) {
            if (Boolean.TRUE.equals(infos[1])) {
                return InspectionGadgetsBundle.message(
                        "static.method.only.used.in.one.class.problem.descriptor.anonymous.extending",
                        name);
            }
            return InspectionGadgetsBundle.message(
                    "static.method.only.used.in.one.class.problem.descriptor.anonymous.implementing",
                    name);
        }
        return InspectionGadgetsBundle.message(
                "static.method.only.used.in.one.class.problem.descriptor", name);
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new StaticMethodOnlyUsedInOneClassFix();
    }

    private static class StaticMethodOnlyUsedInOneClassFix
            extends InspectionGadgetsFix {

        public String getName() {
            return InspectionGadgetsBundle.message(
                    "static.method.only.used.in.one.class.quickfix");
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement location = descriptor.getPsiElement();
            final PsiMethod method = (PsiMethod)location.getParent();
            final RefactoringActionHandlerFactory factory =
                    RefactoringActionHandlerFactory.getInstance();
            final RefactoringActionHandler moveHandler =
                    factory.createMoveHandler();
            final DataManager dataManager = DataManager.getInstance();
            final DataContext dataContext = dataManager.getDataContext();
            moveHandler.invoke(project, new PsiElement[]{method}, dataContext);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new StaticmethodOnlyUsedInOneOtherClassVisitor();
    }

    private static class StaticmethodOnlyUsedInOneOtherClassVisitor
            extends BaseInspectionVisitor {

        public void visitMethod(PsiMethod method) {
            super.visitMethod(method);
            if (!method.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
            if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
                return;
            }
            if (method.getNameIdentifier() == null) {
                return;
            }
            final UsageProcessor usageProcessor = new UsageProcessor();
            final PsiClass usageClass = usageProcessor.getUsageClass(method);
            if (usageClass == null) {
                return;
            }
            if (usageClass.equals(method.getContainingClass())) {
                return;
            }
            if (usageClass instanceof PsiAnonymousClass) {
                final PsiClass[] interfaces =
                        usageClass.getInterfaces();
                final PsiClass superClass;
                if (interfaces.length == 1) {
                    superClass = interfaces[0];
                    registerMethodError(method, superClass,
                            Boolean.valueOf(false));
                } else {
                    superClass = usageClass.getSuperClass();
                    if (superClass == null) {
                        return;
                    }
                    registerMethodError(method, superClass,
                            Boolean.valueOf(true));
                }
            } else {
                registerMethodError(method, usageClass);
            }
        }
    }

    private static class UsageProcessor implements Processor<PsiReference> {

        private PsiClass usageClass = null;

        public boolean process(PsiReference reference) {
            final PsiElement element = reference.getElement();
            final PsiClass usageClass = PsiTreeUtil.getParentOfType(element,
                    PsiClass.class);
            if (this.usageClass != null &&
                    !this.usageClass.equals(usageClass)) {
                this.usageClass = null;
                return false;
            }
            this.usageClass = usageClass;
            return true;
        }

        /**
         * @return the class the specified method is used from, or null if it is
         * used from 0 or more than 1 other classes.
         */
        @Nullable
        public PsiClass getUsageClass(final PsiMethod method) {
            final ProgressManager progressManager =
                    ProgressManager.getInstance();
            progressManager.runProcess(new Runnable() {
                public void run() {
                    final Query<PsiReference> query =
                            ReferencesSearch.search(method);
                    query.forEach(UsageProcessor.this);
                }
            }, null);
            return usageClass;
        }
    }
}