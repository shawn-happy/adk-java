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

import java.time.Instant;

/**
 * Row representation of {@code adk_app_states}.
 *
 * <p>The {@code state} column stores the app-scoped state as JSON. Mirrors the Python {@code
 * StorageAppState} ORM model.
 */
public record AdkAppState(String appName, String state, Instant updateTime) {}
