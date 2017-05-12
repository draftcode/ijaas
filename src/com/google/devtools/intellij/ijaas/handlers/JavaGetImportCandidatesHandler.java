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

import static java.util.stream.Collectors.toList;

import com.google.devtools.intellij.ijaas.BaseHandler;
import com.google.devtools.intellij.ijaas.handlers.JavaGetImportCandidatesHandler.Request;
import com.google.devtools.intellij.ijaas.handlers.JavaGetImportCandidatesHandler.Response;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFix;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

public class JavaGetImportCandidatesHandler extends BaseHandler<Request, Response> {
  @Override
  protected Class<Request> requestClass() {
    return Request.class;
  }

  @Override
  protected Response handle(Request request) {
    Project project = findProject(request.file);
    if (project == null) {
      throw new RuntimeException("Cannot find the target project");
    }
    Application application = ApplicationManager.getApplication();
    Response response = new Response();
    application.runReadAction(
        () -> {
          PsiFile psiFile =
              PsiFileFactory.getInstance(project)
                  .createFileFromText(JavaLanguage.INSTANCE, request.text);
          if (!(psiFile instanceof PsiJavaFile)) {
            throw new RuntimeException("Cannot parse as Java file");
          }
          PsiJavaFile psiJavaFile = (PsiJavaFile) psiFile;

          Set<String> processed = new HashSet<>();
          for (PsiClass psiClass : psiJavaFile.getClasses()) {
            psiClass.accept(
                new JavaRecursiveElementWalkingVisitor() {
                  @Override
                  public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
                    try {
                      if (reference.getQualifier() != null) {
                        return;
                      }
                      String name = reference.getReferenceName();
                      if (processed.contains(name)) {
                        return;
                      }
                      processed.add(name);

                      Set<String> candidates = new HashSet<>();
                      for (PsiClass t : new ImportClassFix(reference).getClassesToImport()) {
                        candidates.add(String.format("import %s;", t.getQualifiedName()));
                      }
                      if (!candidates.isEmpty()) {
                        response.choices.add(candidates.stream().sorted().collect(toList()));
                      }
                    } finally {
                      super.visitReferenceElement(reference);
                    }
                  }
                });
          }
        });
    return response;
  }

  @Nullable
  private Project findProject(String file) {
    LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
    ProjectLocator projectLocator = ProjectLocator.getInstance();
    AtomicReference<Project> ret = new AtomicReference<>();
    FileUtil.processFilesRecursively(
        new File(file),
        (f) -> {
          VirtualFile vf = localFileSystem.findFileByIoFile(f);
          if (vf != null) {
            ret.set(projectLocator.guessProjectForFile(vf));
            return false;
          }
          return true;
        });
    return ret.get();
  }

  public static class Request {
    String file;
    String text;
  }

  public static class Response {
    List<String> debug = new ArrayList<>();
    List<List<String>> choices = new ArrayList<>();
  }
}
