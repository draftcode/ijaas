// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.intellij.ijaas;

import com.intellij.openapi.components.ApplicationComponent;

public class IjaasApplicationComponent implements ApplicationComponent {
  private final IjaasServer server = new IjaasServer(getPort());

  @Override
  public void initComponent() {
    server.start();
  }

  @Override
  public void disposeComponent() {}

  @Override
  public String getComponentName() {
    return "com.google.devtools.intellij.ijaas.IjaasApplicationComponent";
  }

  private static int getPort() {
    String portStr = System.getProperty("ijaas.port");
    if (portStr == null) {
      return 5800;
    }
    return Integer.parseInt(portStr);
  }
}
