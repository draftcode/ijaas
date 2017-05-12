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

import com.google.common.util.concurrent.SettableFuture;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public abstract class BaseHandler<ReqT, ResT> implements IjaasHandler {
  protected abstract Class<ReqT> requestClass();

  protected void validate(ReqT request) {}

  protected abstract ResT handle(ReqT request);

  @Override
  public JsonElement handle(JsonElement params) {
    SettableFuture<JsonElement> ret = SettableFuture.create();
    AtomicReference<ProgressIndicator> indicatorRef = new AtomicReference<>();
    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(
                null, this.getClass().getCanonicalName(), true, PerformInBackgroundOption.DEAF) {
              @Override
              public void run(ProgressIndicator indicator) {
                indicatorRef.set(indicator);
                Gson gson = new Gson();
                ReqT request = gson.fromJson(params, requestClass());
                try {
                  validate(request);
                } catch (Exception e) {
                  ret.setException(new RuntimeException("Validation error", e));
                }
                ret.set(gson.toJsonTree(handle(request)));
              }
            });
    try {
      return ret.get(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    } catch (TimeoutException | ExecutionException e) {
      ProgressIndicator indicator = indicatorRef.get();
      if (indicator != null) {
        indicator.cancel();
      }
      throw new RuntimeException(e);
    }
  }
}
