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

package com.google.adk.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.adk.JsonBaseModel;
import com.google.adk.sessions.SessionException;
import java.util.HashMap;
import java.util.Map;

/** JSON helpers backed by the shared ADK {@code ObjectMapper}. */
public final class JsonUtils {

  private JsonUtils() {}

  /** Serializes a map to a JSON string. */
  public static String toJson(Map<String, Object> map) {
    return JsonBaseModel.toJsonString(map);
  }

  /**
   * Deserializes a JSON string to a mutable map. Returns an empty map for {@code null}/{@code
   * "{}"}.
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> fromJson(String json) {
    if (json == null || json.isBlank() || "{}".equals(json.trim())) {
      return new HashMap<>();
    }
    try {
      return JsonBaseModel.getMapper().readValue(json, Map.class);
    } catch (JsonProcessingException e) {
      throw new SessionException("Failed to parse JSON state: " + json, e);
    }
  }
}
