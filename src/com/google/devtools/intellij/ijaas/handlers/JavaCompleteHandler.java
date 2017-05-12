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

import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.intellij.ijaas.BaseHandler;
import com.google.devtools.intellij.ijaas.handlers.JavaCompleteHandler.Request;
import com.google.devtools.intellij.ijaas.handlers.JavaCompleteHandler.Response;
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionPhase;
import com.intellij.codeInsight.completion.CompletionProgressIndicator;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

public class JavaCompleteHandler extends BaseHandler<Request, Response> {
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
    SettableFuture<Response> responseFuture = SettableFuture.create();
    Application application = ApplicationManager.getApplication();
    Ref<PsiFile> psiFileRef = new Ref<>();
    application.runReadAction(
        () -> {
          psiFileRef.set(
              PsiFileFactory.getInstance(project)
                  .createFileFromText(JavaLanguage.INSTANCE, request.text));
        });
    PsiFile psiFile = psiFileRef.get();

    application.invokeAndWait(
        () -> {
          Editor editor =
              EditorFactory.getInstance()
                  .createEditor(
                      PsiDocumentManager.getInstance(project).getDocument(psiFile), project);
          editor.getCaretModel().moveToOffset(request.offset);
          CommandProcessor.getInstance()
              .executeCommand(
                  project,
                  () -> {
                    CodeCompletionHandlerBase handler =
                        new CodeCompletionHandlerBase(CompletionType.BASIC) {
                          @Override
                          protected void completionFinished(
                              CompletionProgressIndicator indicator, boolean hasModifiers) {
                            CompletionServiceImpl.setCompletionPhase(
                                new CompletionPhase.ItemsCalculated(indicator));
                            Response response = new Response();
                            LookupImpl lookup = indicator.getLookup();
                            for (LookupElement item : lookup.getItems()) {
                              PsiElement psi = item.getPsiElement();
                              if (psi == null) {
                                continue;
                              }
                              Completion c = new Completion();
                              LookupElementPresentation presentation =
                                  new LookupElementPresentation();
                              item.renderElement(presentation);
                              c.word =
                                  item.getLookupString().substring(lookup.getPrefixLength(item));
                              if (psi instanceof PsiMethod) {
                                PsiMethod m = (PsiMethod) psi;
                                if (m.getParameterList().getParametersCount() == 0) {
                                  c.word += "()";
                                } else {
                                  c.word += '(';
                                }
                                c.menu =
                                    presentation.getTypeText() + " - " + presentation.getTailText();
                                c.kind = Completion.FUNCTION;
                              } else if (psi instanceof PsiKeyword) {
                                c.kind = Completion.KEYWORD;
                              } else if (psi instanceof PsiClass) {
                                c.menu = presentation.getTailText();
                                c.kind = Completion.TYPE;
                              } else if (psi instanceof PsiVariable) {
                                c.menu = presentation.getTypeText();
                                c.kind = Completion.VARIABLE;
                              } else {
                                c.menu = psi.getClass().getSimpleName();
                                c.kind = "";
                              }
                              response.completions.add(c);
                            }
                            responseFuture.set(response);
                          }
                        };
                    handler.invokeCompletion(project, editor);
                  },
                  null,
                  null);
        });
    try {
      Response response = responseFuture.get();
      Collections.sort(response.completions, new CompletionOrdering());
      return response;
    } catch (ExecutionException e) {
      throw new RuntimeException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
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
    int offset;
  }

  public static class Response {
    ArrayList<Completion> completions = new ArrayList<>();
  }

  public class Completion {
    public static final String VARIABLE = "v";
    public static final String FUNCTION = "f";
    public static final String TYPE = "t";
    public static final String KEYWORD = "k";

    public String word;
    public String menu;
    public String kind;
  }

  private static class CompletionOrdering extends Ordering<Completion> {
    @Override
    public int compare(Completion arg0, Completion arg1) {
      boolean arg0Keyword = arg0.kind.equals(Completion.KEYWORD);
      boolean arg1Keyword = arg1.kind.equals(Completion.KEYWORD);
      if (arg0Keyword && !arg1Keyword) {
        return 1;
      } else if (!arg0Keyword && arg1Keyword) {
        return -1;
      }
      return arg0.word.compareTo(arg1.word);
    }
  }
}
