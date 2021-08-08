package com.google.devtools.intellij.ijaas;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

public class IjaasStartupActivity implements StartupActivity {
  @Override
  public void runActivity(@NotNull Project project) {
    IjaasServer server = new IjaasServer(getPort());
    server.start();
  }

  private static int getPort() {
    String portStr = System.getProperty("ijaas.port");
    if (portStr == null) {
      return 5800;
    }
    return Integer.parseInt(portStr);
  }
}
