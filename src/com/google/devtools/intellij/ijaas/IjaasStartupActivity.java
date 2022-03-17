package com.google.devtools.intellij.ijaas;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.jetbrains.annotations.NotNull;

public class IjaasStartupActivity implements StartupActivity {
  @Override
  public void runActivity(@NotNull Project project) {
    new Thread(
            () -> {
              ExecutorService executorService = Executors.newCachedThreadPool();
              try (ServerSocket serverSocket =
                  new ServerSocket(getPort(), 0, InetAddress.getLoopbackAddress())) {
                while (true) {
                  Socket socket = serverSocket.accept();
                  IjaasLspServer server = new IjaasLspServer(project);
                  Launcher<?> launcher =
                      LSPLauncher.createServerLauncher(
                          server, socket.getInputStream(), socket.getOutputStream());
                  server.setRemoteEndpoint(launcher.getRemoteEndpoint());
                  executorService.execute(launcher::startListening);
                }
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            })
        .start();
  }

  private static int getPort() {
    String portStr = System.getProperty("ijaas.port");
    if (portStr == null) {
      return 5800;
    }
    return Integer.parseInt(portStr);
  }
}
