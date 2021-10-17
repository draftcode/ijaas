package com.google.devtools.intellij.ijaas;

import com.google.devtools.intellij.ijaas.OpenFileManager.OpenedFile;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;

public class IjaasTextDocumentService implements TextDocumentService {
  private final OpenFileManager manager;
  private final CompletionProducer completionProducer;

  @Inject
  IjaasTextDocumentService(OpenFileManager manager, CompletionProducer completionProducer) {
    this.manager = manager;
    this.completionProducer = completionProducer;
  }

  @Override
  public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(
      CompletionParams position) {
    return completionProducer.completion(position);
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>
      definition(DefinitionParams params) {
    OpenedFile of = manager.getByURI(params.getTextDocument().getUri());
    try {
      return ReadAction.compute(
          () -> {
            int offset =
                of.getEditor()
                    .logicalPositionToOffset(
                        new LogicalPosition(
                            params.getPosition().getLine(), params.getPosition().getCharacter()));
            PsiElement elem;
            {
              PsiReference ref = of.getPsiFile().findReferenceAt(offset);
              if (ref != null) {
                elem = ref.resolve().getNavigationElement();
              } else {
                elem = of.getPsiFile().findElementAt(offset);
              }
            }
            VirtualFile file = elem.getContainingFile().getViewProvider().getVirtualFile();
            String url = file.getUrl();
            if (url.startsWith("jar:")) {
              url = url.replaceFirst("jar:", "zipfile:").replaceFirst("!/", "::");
            }
            Position pos =
                OffsetConverter.offsetToPosition(file.contentsToByteArray(), elem.getTextOffset());
            Location loc = new Location(url, new Range(pos, pos));
            return CompletableFuture.completedFuture(Either.forLeft(List.of(loc)));
          });
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void didOpen(DidOpenTextDocumentParams params) {
    manager.didOpen(params);
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {
    manager.didChange(params);
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    manager.didClose(params);
  }

  @Override
  public void didSave(DidSaveTextDocumentParams params) {
    manager.didSave(params);
  }
}
