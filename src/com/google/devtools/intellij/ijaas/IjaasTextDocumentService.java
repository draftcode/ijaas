package com.google.devtools.intellij.ijaas;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
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
