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

import com.google.common.base.Throwables;
import com.google.devtools.intellij.ijaas.handlers.EchoHandler;
import com.google.devtools.intellij.ijaas.handlers.JavaCompleteHandler;
import com.google.devtools.intellij.ijaas.handlers.JavaGetImportCandidatesHandler;
import com.google.devtools.intellij.ijaas.handlers.JavaSrcUpdateHandler;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonStreamParser;
import com.google.gson.stream.JsonWriter;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;

public class IjaasServer {
  private final int port;
  private final Gson gson = new Gson();
  private final HashMap<String, IjaasHandler> handlers = new HashMap<>();

  IjaasServer(int port) {
    this.port = port;
    // TODO: Add handlers
    handlers.put("echo", new EchoHandler());
    handlers.put("java_complete", new JavaCompleteHandler());
    handlers.put("java_src_update", new JavaSrcUpdateHandler());
    handlers.put("java_get_import_candidates", new JavaGetImportCandidatesHandler());
  }

  void start() {
    new Thread(
            () -> {
              ExecutorService executorService = Executors.newCachedThreadPool();
              try (ServerSocket serverSocket =
                  new ServerSocket(port, 0, InetAddress.getLoopbackAddress())) {
                while (true) {
                  Socket socket = serverSocket.accept();
                  executorService.execute(() -> process(socket));
                }
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            })
        .start();
  }

  private void process(Socket socket) {
    try {
      try {
        JsonStreamParser parser =
            new JsonStreamParser(
                new InputStreamReader(
                    new BufferedInputStream(socket.getInputStream()), StandardCharsets.UTF_8));
        try (JsonWriter writer =
            gson.newJsonWriter(
                new OutputStreamWriter(
                    new BufferedOutputStream(socket.getOutputStream()), StandardCharsets.UTF_8))) {
          // There are several top-level values.
          writer.setLenient(true);
          while (parser.hasNext()) {
            JsonArray request = parser.next().getAsJsonArray();
            long id = request.get(0).getAsLong();
            GenericRequest genericRequest = gson.fromJson(request.get(1), GenericRequest.class);

            JsonElement response;
            try {
              response = gson.toJsonTree(new GenericResponse(processRequest(genericRequest)));
            } catch (Exception e) {
              response =
                  gson.toJsonTree(
                      new ErrorResponse(e.getMessage(), Throwables.getStackTraceAsString(e)));
            }
            writer.beginArray();
            writer.value(id);
            gson.toJson(response, writer);
            writer.endArray();
            writer.flush();
          }
        }
      } finally {
        socket.close();
      }
    } catch (JsonIOException e) {
      Throwable t = e.getCause();
      if (t instanceof EOFException) {
        // Ignore. This happens when the input is empty.
      } else {
        throw new RuntimeException(e);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private JsonElement processRequest(GenericRequest genericRequest) {
    if (genericRequest == null) {
      throw new RuntimeException("method is required");
    }
    IjaasHandler handler = handlers.get(genericRequest.method);
    if (handler == null) {
      throw new RuntimeException(genericRequest.method + "is not found");
    }
    return handler.handle(genericRequest.params);
  }

  private static class GenericRequest {
    @Nullable String method;
    @Nullable JsonElement params;
  }

  private static class GenericResponse {
    private final JsonElement result;

    GenericResponse(JsonElement result) {
      this.result = result;
    }
  }

  private static class ErrorResponse {
    private final String error;
    private final String cause;

    ErrorResponse(String error, String cause) {
      this.error = error;
      this.cause = cause;
    }
  }
}
