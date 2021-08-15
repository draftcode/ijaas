package com.google.devtools.intellij.ijaas;

import com.google.devtools.intellij.ijaas.OpenFileManager.OpenedFile;
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionPhase;
import com.intellij.codeInsight.completion.CompletionProgressIndicator;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class CompletionProducer {
  private final Project project;
  private final ExecutorService executor;
  private final OpenFileManager manager;

  @Inject
  CompletionProducer(Project project, ExecutorService executor, OpenFileManager manager) {
    this.project = project;
    this.executor = executor;
    this.manager = manager;
  }

  CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(
      CompletionParams position) {
    return CompletableFuture.supplyAsync(
        () -> Either.forRight(completionInner(position)), executor);
  }

  private CompletionList completionInner(CompletionParams position) {
    OpenedFile file = manager.getByURI(position.getTextDocument().getUri());
    Editor editor = file.getEditor();
    WriteAction.runAndWait(
        () -> {
          editor
              .getCaretModel()
              .moveToLogicalPosition(
                  new LogicalPosition(
                      position.getPosition().getLine(), position.getPosition().getCharacter()));
        });
    AtomicReference<CompletionList> response = new AtomicReference<>();
    ApplicationManager.getApplication()
        .invokeAndWait(
            () -> {
              try {
                CompletionList resp = new CompletionList();
                CompletionHandler handler = new CompletionHandler();
                handler.invokeCompletion(project, editor);
                resp.setItems(handler.items);
                response.set(resp);
              } catch (Exception e) {
                e.printStackTrace();
              }
            });
    return response.get();
  }

  private static class CompletionHandler extends CodeCompletionHandlerBase {
    private List<CompletionItem> items = new ArrayList<>();

    private CompletionHandler() {
      super(CompletionType.BASIC);
    }

    @Override
    protected void completionFinished(CompletionProgressIndicator indicator, boolean hasModifiers) {
      CompletionServiceImpl.setCompletionPhase(new CompletionPhase.ItemsCalculated(indicator));
      LookupImpl lookup = indicator.getLookup();
      for (LookupElement item : lookup.getItems()) {
        PsiElement psi = item.getPsiElement();
        if (psi == null) {
          continue;
        }
        CompletionItem c = new CompletionItem();
        LookupElementPresentation presentation = new LookupElementPresentation();
        item.renderElement(presentation);
        c.setLabel(item.getLookupString());
        if (psi instanceof PsiMethod) {
          PsiMethod m = (PsiMethod) psi;
          if (m.getParameterList().getParametersCount() == 0) {
            c.setInsertText(c.getLabel() + "()");
          } else {
            c.setInsertText(c.getLabel() + "(");
          }
          c.setDetail(presentation.getTypeText() + " - " + presentation.getTailText());
          c.setKind(CompletionItemKind.Method);
        } else if (psi instanceof PsiKeyword) {
          c.setKind(CompletionItemKind.Keyword);
        } else if (psi instanceof PsiClass) {
          c.setDetail(presentation.getTailText());
          c.setKind(CompletionItemKind.Class);
        } else if (psi instanceof PsiVariable) {
          c.setDetail(presentation.getTypeText());
          c.setKind(CompletionItemKind.Variable);
        } else {
          c.setDetail(psi.getClass().getSimpleName());
        }
        items.add(c);
      }
    }
  }
}
