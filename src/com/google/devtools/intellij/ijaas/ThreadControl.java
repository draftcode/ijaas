package com.google.devtools.intellij.ijaas;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.DocumentUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ThrowableRunnable;
import java.util.concurrent.atomic.AtomicReference;

public abstract class ThreadControl {
  private ThreadControl() {}

  public static <E extends Throwable> void runOnReadThread(ThrowableRunnable<E> action) throws E {
    ReadAction.run(action);
  }

  public static <T, E extends Throwable> T computeOnReadThread(ThrowableComputable<T, E> action)
      throws E {
    return ReadAction.compute(action);
  }

  public static <T> T computeOnEDT(Computable<T> action) {
    AtomicReference<T> result = new AtomicReference<>();
    ApplicationManager.getApplication().invokeAndWait(() -> result.set(action.compute()));
    return result.get();
  }

  public static <E extends Throwable> void runOnWriteThread(ThrowableRunnable<E> action) throws E {
    WriteAction.runAndWait(action);
  }

  public static void runOnWriteThreadAndWaitForDocument(Runnable r) {
    WriteAction.runAndWait(
        () -> {
          DocumentUtil.writeInRunUndoTransparentAction(r);
        });
  }

  public static <T, E extends Throwable> T computeOnWriteThreadAndWaitForDocument(
      ThrowableComputable<T, E> c) throws E {
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
