package com.google.devtools.intellij.ijaas;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.DocumentUtil;
import com.intellij.util.ExceptionUtil;
import java.util.concurrent.atomic.AtomicReference;

public abstract class MoreWriteAction {
  private MoreWriteAction() {}

  public static void runAndWaitForDocument(Runnable r) {
    WriteAction.runAndWait(
        () -> {
          DocumentUtil.writeInRunUndoTransparentAction(r);
        });
  }

  public static <T, E extends Throwable> T computeAndWaitForDocument(ThrowableComputable<T, E> c)
      throws E {
    return WriteAction.computeAndWait(
        () -> {
          AtomicReference<T> result = new AtomicReference<>();
          AtomicReference<Throwable> exception = new AtomicReference<>();
          DocumentUtil.writeInRunUndoTransparentAction(
              () -> {
                try {
                  result.set(c.compute());
                } catch (Throwable t) {
                  exception.set(t);
                }
              });

          Throwable t = exception.get();
          if (t != null) {
            t.addSuppressed(new RuntimeException());
            ExceptionUtil.rethrowUnchecked(t);
            throw (E) t;
          }
          return result.get();
        });
  }
}
