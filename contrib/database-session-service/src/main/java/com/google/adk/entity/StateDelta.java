/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.adk.entity;

import java.util.Collections;
import java.util.Map;

/**
 * Holds state changes split by ADK prefix: {@code app:}, {@code user:}, and unprefixed (session)
 * keys. {@code temp:} keys are dropped (never persisted).
 *
 * <p>Keys in {@link #appState()} and {@link #userState()} have their prefix stripped. Keys in
 * {@link #sessionState()} are unprefixed.
 */
public record StateDelta(
    Map<String, Object> appState, Map<String, Object> userState, Map<String, Object> sessionState) {

  public StateDelta {
    appState = appState == null ? Collections.emptyMap() : appState;
    userState = userState == null ? Collections.emptyMap() : userState;
    sessionState = sessionState == null ? Collections.emptyMap() : sessionState;
  }
}
