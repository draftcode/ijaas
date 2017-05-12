// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.intellij.ijaas.handlers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;
import com.google.devtools.intellij.ijaas.BaseHandler;
import com.google.devtools.intellij.ijaas.handlers.JavaSrcUpdateHandler.Request;
import com.google.devtools.intellij.ijaas.handlers.JavaSrcUpdateHandler.Response;
import com.intellij.codeInsight.CodeSmellInfo;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionEngine;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.Tools;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.CodeSmellDetector;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class JavaSrcUpdateHandler extends BaseHandler<Request, Response> {
  @Override
  protected Class<Request> requestClass() {
    return Request.class;
  }

  @Override
  protected Response handle(Request request) {
    File file = new File(FileUtil.toSystemDependentName(request.file));
    if (!file.exists()) {
      throw new RuntimeException("Cannot find the file");
    }
    Application application = ApplicationManager.getApplication();
    Response response = new Response();
    Ref<VirtualFile> vfRef = new Ref<>();
    application.invokeAndWait(
        () -> {
          VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
          if (vf == null) {
            throw new RuntimeException("Cannot find the file");
          }
          vfRef.set(vf);
        });
    VirtualFile vf = vfRef.get();

    Ref<Project> projectRef = new Ref<>();
    Ref<PsiFile> psiFileRef = new Ref<>();
    application.runReadAction(
        () -> {
          Project project = ProjectLocator.getInstance().guessProjectForFile(vf);
          if (project == null) {
            throw new RuntimeException("Cannot find the target project");
          }
          PsiManager psiManager = PsiManager.getInstance(project);
          PsiFile psiFile = psiManager.findFile(vf);
          if (psiFile == null) {
            throw new RuntimeException("Cannot find the PsiFile");
          }
          projectRef.set(project);
          psiFileRef.set(psiFile);
        });
    Project project = projectRef.get();
    PsiFile psiFile = psiFileRef.get();

    Ref<List<CodeSmellInfo>> codeSmellInfosRef = new Ref<>();
    application.invokeAndWait(
        () -> {
          application.runWriteAction(
              () -> {
                vf.refresh(false, false);
                PsiManager.getInstance(project).reloadFromDisk(psiFile);
              });
          codeSmellInfosRef.set(
              CodeSmellDetector.getInstance(project).findCodeSmells(ImmutableList.of(vf)));
        });

    application.runReadAction(
        () -> {
          for (CodeSmellInfo codeSmellInfo : codeSmellInfosRef.get()) {
            Problem problem = new Problem();
            problem.lnum = codeSmellInfo.getStartLine() + 1;
            problem.text = codeSmellInfo.getDescription();
            problem.type = toProblemType(codeSmellInfo.getSeverity().myVal);
            response.problems.add(problem);
          }

          InspectionManager inspectionManager = InspectionManager.getInstance(project);
          GlobalInspectionContext context = inspectionManager.createNewGlobalContext(false);

          List<Tools> toolsList =
              InspectionProfileManager.getInstance(project)
                  .getCurrentProfile()
                  .getAllEnabledInspectionTools(project);
          for (Tools tools : toolsList) {
            InspectionToolWrapper tool = tools.getInspectionTool(psiFile);
            List<ProblemDescriptor> descs =
                InspectionEngine.runInspectionOnFile(psiFile, tool, context);
            for (ProblemDescriptor desc : descs) {
              Problem problem = new Problem();
              problem.lnum = desc.getLineNumber() + 1;
              problem.text = desc.toString();
              problem.type = toProblemType(desc.getHighlightType());
              response.problems.add(problem);
            }
          }
        });
    response.problems.sort(new ProblemOrdering());
    return response;
  }

  private static String toProblemType(int severityValue) {
    if (severityValue < HighlightSeverity.WARNING.myVal) {
      return Problem.INFO;
    } else if (severityValue < HighlightSeverity.ERROR.myVal) {
      return Problem.WARNING;
    } else {
      return Problem.ERROR;
    }
  }

  private static String toProblemType(ProblemHighlightType type) {
    switch (type) {
      case ERROR:
      case GENERIC_ERROR:
      case LIKE_UNKNOWN_SYMBOL:
        return Problem.ERROR;
      default:
        return Problem.WARNING;
    }
  }

  public static class Request {
    String file;
  }

  public static class Response {
    List<Problem> problems = new ArrayList<>();
  }

  public class Problem {
    // Quickfix type characters
    // https://github.com/vim/vim/blob/3653822546fb0f1005c32bb5b70dc9bfacdfc954/src/quickfix.c#L2871
    public static final String INFO = "I";
    public static final String WARNING = "W";
    public static final String ERROR = "E";

    public int lnum;
    public String text;
    public String type;
  }

  private static class ProblemOrdering extends Ordering<Problem> {
    private static final ImmutableMap<String, Integer> SEVERITY_ORDER =
        ImmutableMap.of(
            Problem.INFO, 2,
            Problem.WARNING, 1,
            Problem.ERROR, 0);

    @Override
    public int compare(Problem arg0, Problem arg1) {
      int arg0Severity = SEVERITY_ORDER.get(arg0.type);
      int arg1Severity = SEVERITY_ORDER.get(arg1.type);
      if (arg0Severity != arg1Severity) {
        return arg0Severity - arg1Severity;
      }
      if (arg0.lnum != arg1.lnum) {
        return arg0.lnum - arg1.lnum;
      }
      return arg0.text.compareTo(arg1.text);
    }
  }
}
