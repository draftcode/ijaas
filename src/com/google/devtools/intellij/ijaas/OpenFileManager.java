package com.google.devtools.intellij.ijaas;

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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.HashMap;
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
  public OpenedFile getByURI(String uri) {
    return files.get(uri);
  }

  public void didOpen(DidOpenTextDocumentParams params) {
    Pair<Editor, PsiFile> p =
        ThreadControl.computeOnWriteThreadAndWaitForDocument(
            () -> {
              String path;
              try {
                path = Paths.get(new URI(params.getTextDocument().getUri())).toFile().getPath();
              } catch (URISyntaxException e) {
                throw new RuntimeException("Cannot find the VirtualFile");
              }
              VirtualFile vf = LocalFileSystem.getInstance().findFileByPath(path);
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
              editor.getDocument().setText(params.getTextDocument().getText());
              return Pair.pair(editor, psiFile);
            });
    OpenedFile file =
        new OpenedFile(
            params.getTextDocument().getUri(),
            p.getFirst(),
            p.getSecond(),
            params.getTextDocument().getVersion());
    files.put(file.uri, file);
    diagnosticsProducer.updateAsync(file);
  }

  public void didChange(DidChangeTextDocumentParams params) {
    OpenedFile file = files.get(params.getTextDocument().getUri());
    ThreadControl.runOnWriteThreadAndWaitForDocument(
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
    file.version = params.getTextDocument().getVersion();
    diagnosticsProducer.updateAsync(file);
  }

  public void didClose(DidCloseTextDocumentParams params) {
    OpenedFile file = files.remove(params.getTextDocument().getUri());
    ThreadControl.runOnWriteThread(() -> EditorFactory.getInstance().releaseEditor(file.editor));
  }

  public void didSave(DidSaveTextDocumentParams params) {
    OpenedFile file = files.get(params.getTextDocument().getUri());
    ThreadControl.runOnWriteThreadAndWaitForDocument(
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

    private OpenedFile(String uri, Editor editor, PsiFile psiFile, int version) {
      this.uri = uri;
      this.editor = editor;
      this.psiFile = psiFile;
      this.version = version;
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
