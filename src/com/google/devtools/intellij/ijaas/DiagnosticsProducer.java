package com.google.devtools.intellij.ijaas;

import com.google.devtools.intellij.ijaas.OpenFileManager.OpenedFile;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionEngine;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.Tools;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.ExternallyAnnotated;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import javax.inject.Inject;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.Endpoint;

public class DiagnosticsProducer {
  private final Project project;
  private final ExecutorService executor;
  private final Endpoint endpoint;

  @Inject
  DiagnosticsProducer(Project project, ExecutorService executor, Endpoint endpoint) {
    this.project = project;
    this.executor = executor;
    this.endpoint = endpoint;
  }

  public void updateAsync(OpenedFile file) {
    executor.submit(
        () -> {
          ProgressIndicatorBase indicator = new ProgressIndicatorBase();
          // This is required by AbstractProgressIndicatorBase.
          indicator.setIndeterminate(false);
          ProgressManager.getInstance().runProcess(() -> updateInternal(file), indicator);
        });
  }

  private void updateInternal(OpenedFile file) {
    ThreadControl.runOnReadThread(
        () -> {
          try {
            // NOTE: CodeSmellInfo is another source for diagnostics. However, it seems that it
            // produces the same diagnostics now. Maybe this is wrong, but for now use
            // InspectionManager only. Another approach is to use highlights on the editor, but the
            // background highlighting process won't run for editors that are not shown.
            InspectionManager inspectionManager = InspectionManager.getInstance(project);
            GlobalInspectionContext context = inspectionManager.createNewGlobalContext();
            PsiFile psiFile = file.getPsiFile();
            List<Tools> toolsList =
                InspectionProfileManager.getInstance(project)
                    .getCurrentProfile()
                    .getAllEnabledInspectionTools(project);
            List<Diagnostic> diagnostics = new ArrayList<>();
            for (Tools tools : toolsList) {
              InspectionToolWrapper<?, ?> tool = tools.getInspectionTool(psiFile);
              List<ProblemDescriptor> descs =
                  InspectionEngine.runInspectionOnFile(psiFile, tool, context);
              for (ProblemDescriptor desc : descs) {
                // NOTE: There might be other information we can send back. If there's something
                // useful, add more.
                Diagnostic diag = new Diagnostic();
                diag.setRange(getRange(file, desc));
                diag.setSeverity(getSeverity(desc.getHighlightType()));
                diag.setSource(tools.getShortName());
                diag.setMessage(desc.toString());
                diagnostics.add(diag);
              }
            }
            endpoint.notify(
                "textDocument/publishDiagnostics",
                new PublishDiagnosticsParams(file.getURI(), diagnostics));
          } catch (Exception e) {
            e.printStackTrace();
            throw e;
          }
        });
  }

  private static Range getRange(OpenedFile file, ProblemDescriptor desc) {
    // NOTE: It's hard to see which PsiElement cannot be null from the IntelliJ source code. Some
    // implementation uses ExternallyAnnotated as the TextRange's source. However, those seem to be
    // nullable. This conversion code is being overly conservative against the nullness.
    PsiElement startElem = desc.getStartElement();
    PsiElement endElem = desc.getEndElement();
    TextRange startRange = null;
    TextRange endRange = null;
    if (startElem instanceof ExternallyAnnotated) {
      startRange = ((ExternallyAnnotated) startElem).getAnnotationRegion();
    }
    if (endElem instanceof ExternallyAnnotated) {
      endRange = ((ExternallyAnnotated) endElem).getAnnotationRegion();
    }
    if (startRange == null) {
      startRange = startElem.getTextRange();
    }
    if (endRange == null) {
      endRange = endElem.getTextRange();
    }
    if (endRange == null) {
      // Does this happen? Fallback to the start range.
      endRange = startRange;
    }
    if (startRange == null) {
      // Does this happen? Fallback to the start of the file.
      return new Range(new Position(0, 0), new Position(0, 0));
    }
    LogicalPosition startPos =
        file.getEditor().offsetToLogicalPosition(startRange.getStartOffset());
    LogicalPosition endPos = file.getEditor().offsetToLogicalPosition(endRange.getEndOffset());
    return new Range(
        new Position(startPos.line, startPos.column), new Position(endPos.line, endPos.column));
  }

  private static DiagnosticSeverity getSeverity(ProblemHighlightType type) {
    // NOTE: This mapping is an arbitrary choice.
    switch (type) {
      case ERROR:
      case GENERIC_ERROR:
      case LIKE_UNKNOWN_SYMBOL:
        return DiagnosticSeverity.Error;
      default:
        return DiagnosticSeverity.Warning;
    }
  }
}
