package com.google.devtools.intellij.ijaas;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Singleton;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.SaveOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.ServerInfo;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.jsonrpc.Endpoint;
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

class IjaasLspServer implements LanguageServer {
  private final LateBindEndpoint endpoint;
  private final Project project;
  private final ServerComponent component;

  IjaasLspServer(Project project) {
    this.project = project;
    this.endpoint =  new LateBindEndpoint();
    this.component =
        DaggerIjaasLspServer_ServerComponent.builder()
            .serverModule(new ServerModule(project, endpoint))
            .build();
  }

  void setRemoteEndpoint(RemoteEndpoint remoteEndpoint) {
    endpoint.delegate = remoteEndpoint;
  }

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    // gopls is a good reference on the expected behavior.
    // https://github.com/golang/tools/blob/116feaea4581560a370de353120153502e19fc48/internal/lsp/general.go#L120
    ServerCapabilities cap = new ServerCapabilities();
    {
      TextDocumentSyncOptions opts = new TextDocumentSyncOptions();
      opts.setOpenClose(true);
      opts.setChange(TextDocumentSyncKind.Incremental);
      opts.setSave(new SaveOptions(true));
      cap.setTextDocumentSync(opts);
    }
    {
      CompletionOptions opts = new CompletionOptions();
      opts.setTriggerCharacters(List.of("."));
      cap.setCompletionProvider(opts);
    }
    cap.setDefinitionProvider(true);
    return CompletableFuture.completedFuture(new InitializeResult(cap, new ServerInfo("ijaas")));
  }

  @Override
  public CompletableFuture<Object> shutdown() {
    Disposer.dispose(component.getDisposable());
    return null;
  }

  @Override
  public void exit() {}

  @Override
  public TextDocumentService getTextDocumentService() {
    return component.getTextDocumentService();
  }

  @Override
  public WorkspaceService getWorkspaceService() {
    return component.getWorkspaceService();
  }

  @Singleton
  @Component(modules = {ServerModule.class})
  interface ServerComponent {
    IjaasTextDocumentService getTextDocumentService();

    IjaasWorkspaceService getWorkspaceService();

    Disposable getDisposable();
  }

  @Module
  static class ServerModule {
    private final Project project;
    private final Endpoint endpoint;

    ServerModule(Project project, Endpoint endpoint) {
      this.project = project;
      this.endpoint = endpoint;
    }

    @Provides
    Project provideProject() {
      return project;
    }

    @Provides
    Endpoint provideEndpoint() {
      return endpoint;
    }

    @Singleton
    @Provides
    Disposable provideDisposable() {
      return Disposer.newDisposable();
    }

    @Singleton
    @Provides
    ExecutorService provideExecutorService() {
      return Executors.newCachedThreadPool();
    }
  }

  static class LateBindEndpoint implements Endpoint {
    private Endpoint delegate = null;

    @Override
    public CompletableFuture<?> request(String method, Object parameter) {
      return delegate.request(method, parameter);
    }

    @Override
    public void notify(String method, Object parameter) {
      delegate.notify(method, parameter);
    }
  }
}
