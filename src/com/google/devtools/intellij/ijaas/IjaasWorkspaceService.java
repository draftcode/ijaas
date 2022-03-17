package com.google.devtools.intellij.ijaas;

import javax.inject.Inject;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.services.WorkspaceService;

class IjaasWorkspaceService implements WorkspaceService {
  @Inject
  IjaasWorkspaceService() {}

  @Override
  public void didChangeConfiguration(DidChangeConfigurationParams params) {}

  @Override
  public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {}
}
