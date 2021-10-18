package com.google.devtools.intellij.ijaas;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import java.io.File;
import java.net.URI;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;

@Singleton
public class OpenFileManager {
  private final Project project;
  private final DiagnosticsProducer diagnosticsProducer;
  private final Map<String, OpenedFile> files = new HashMap<>();

  @Inject
  OpenFileManager(Project project, DiagnosticsProducer diagnosticsProducer) {
    this.project = project;
    this.diagnosticsProducer = diagnosticsProducer;
  }

  @Nullable
  public OpenedFile getByPath(String filePath) {
    try {
      return getByURI(new File(filePath).toURI().toURL().toExternalForm());
    } catch (MalformedURLException e) {
      return null;
    }
  }

  @Nullable
  public OpenedFile getByURI(String uri) {
    return files.get(uri);
  }

  public void didOpen(DidOpenTextDocumentParams params) {
    OpenedFile file =
        new OpenedFile(
            params.getTextDocument().getUri(),
            params.getTextDocument().getVersion(),
            params.getTextDocument().getText());
    files.put(params.getTextDocument().getUri(), file);
    diagnosticsProducer.updateAsync(file);
  }

  public void didChange(DidChangeTextDocumentParams params) {
    OpenedFile file = files.get(params.getTextDocument().getUri());
    MoreWriteActions.runAndWaitForDocument(
        () -> {
          for (TextDocumentContentChangeEvent e : params.getContentChanges()) {
            Position startPos = e.getRange().getStart();
            int startOff =
                file.editor.logicalPositionToOffset(
                    new LogicalPosition(startPos.getLine(), startPos.getCharacter()));
            Position endPos = e.getRange().getEnd();
            int endOff =
                file.editor.logicalPositionToOffset(
                    new LogicalPosition(endPos.getLine(), endPos.getCharacter()));
            file.editor.getDocument().replaceString(startOff, endOff, e.getText());
          }
          file.version = params.getTextDocument().getVersion();
        });
    diagnosticsProducer.updateAsync(file);
  }

  public void didClose(DidCloseTextDocumentParams params) {
    OpenedFile file = files.remove(params.getTextDocument().getUri());
    WriteAction.runAndWait(() -> EditorFactory.getInstance().releaseEditor(file.editor));
  }

  public void didSave(DidSaveTextDocumentParams params) {
    OpenedFile file = files.get(params.getTextDocument().getUri());
    MoreWriteActions.runAndWaitForDocument(
        () -> {
          FileDocumentManager.getInstance().reloadFromDisk(file.editor.getDocument());
        });
    diagnosticsProducer.updateAsync(file);
  }

  public class OpenedFile {
    private final String uri;
    private final Editor editor;
    private final PsiFile psiFile;
    private int version;

    private OpenedFile(String uri, int version, String text) {
      this.uri = uri;
      this.version = version;
      Pair<Editor, PsiFile> p =
          MoreWriteActions.computeAndWaitForDocument(
              () -> {
                ;
                String path = null;
                try {
                  path = Paths.get(new URI(uri)).toFile().getPath();
                } catch(URISyntaxException e) {
                  throw new RuntimeException("Cannot find the VirtualFile");
                }
                VirtualFile vf =
                    LocalFileSystem.getInstance().findFileByPath(path);
                if (vf == null) {
                  throw new RuntimeException("Cannot find the VirtualFile");
                }
                PsiManager psiManager = PsiManager.getInstance(project);
                PsiFile psiFile = psiManager.findFile(vf);
                if (psiFile == null) {
                  throw new RuntimeException("Cannot find the PsiFile");
                }
                Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
                if (document == null) {
                  throw new RuntimeException("Cannot get the Document");
                }
                Editor editor = EditorFactory.getInstance().createEditor(document, project);
                editor.getDocument().setText(text);
                return Pair.pair(editor, psiFile);
              });
      this.editor = p.first;
      this.psiFile = p.second;
    }

    public String getURI() {
      return uri;
    }

    public Editor getEditor() {
      return editor;
    }

    public PsiFile getPsiFile() {
      return psiFile;
    }
  }
}
