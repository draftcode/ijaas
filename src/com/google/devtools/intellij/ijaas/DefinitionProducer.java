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
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class DefinitionProducer {
  private final OpenFileManager manager;

  @Inject
  DefinitionProducer(OpenFileManager manager) {
    this.manager = manager;
  }

  CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(
      DefinitionParams params) {
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
}
