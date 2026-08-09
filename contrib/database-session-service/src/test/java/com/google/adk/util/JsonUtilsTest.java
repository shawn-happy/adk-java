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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.adk.sessions.SessionException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link JsonUtils}. */
public class JsonUtilsTest {

  // ------------------------------------------------------------------------------------------------
  // toJson
  // ------------------------------------------------------------------------------------------------

  @Test
  void toJson_emptyMap_returnsEmptyJsonObject() {
    assertThat(JsonUtils.toJson(new HashMap<>())).isEqualTo("{}");
  }

  @Test
  void toJson_simpleMap_returnsCorrectJson() {
    Map<String, Object> map = new HashMap<>();
    map.put("key", "value");
    map.put("count", 42);
    String json = JsonUtils.toJson(map);
    assertThat(json).contains("\"key\":\"value\"");
    assertThat(json).contains("\"count\":42");
  }

  @Test
  void toJson_nestedMap_returnsCorrectJson() {
    Map<String, Object> inner = new HashMap<>();
    inner.put("nestedKey", "nestedValue");
    Map<String, Object> map = new HashMap<>();
    map.put("outer", inner);
    String json = JsonUtils.toJson(map);
    assertThat(json).contains("\"outer\":{\"nestedKey\":\"nestedValue\"}");
  }

  @Test
  void toJson_mapWithNullValue_omitsNullEntry() {
    Map<String, Object> map = new HashMap<>();
    map.put("nullable", null);
    String json = JsonUtils.toJson(map);
    // Jackson omits null values by default
    assertThat(json).isEqualTo("{\"nullable\":null}");
  }

  // ------------------------------------------------------------------------------------------------
  // fromJson
  // ------------------------------------------------------------------------------------------------

  @Test
  void fromJson_null_returnsEmptyMap() {
    Map<String, Object> result = JsonUtils.fromJson(null);
    assertThat(result).isEmpty();
  }

  @Test
  void fromJson_blankString_returnsEmptyMap() {
    assertThat(JsonUtils.fromJson("")).isEmpty();
    assertThat(JsonUtils.fromJson("   ")).isEmpty();
  }

  @Test
  void fromJson_emptyJsonObject_returnsEmptyMap() {
    assertThat(JsonUtils.fromJson("{}")).isEmpty();
    assertThat(JsonUtils.fromJson("  {}  ")).isEmpty();
  }

  @Test
  void fromJson_validJson_returnsMap() {
    Map<String, Object> result = JsonUtils.fromJson("{\"key\":\"value\",\"count\":42}");
    assertThat(result).hasSize(2);
    assertThat(result).containsEntry("key", "value");
    assertThat(result).containsEntry("count", 42);
  }

  @Test
  void fromJson_validJsonWithNestedObject_returnsMapWithNestedMap() {
    Map<String, Object> result = JsonUtils.fromJson("{\"outer\":{\"inner\":\"val\"}}");
    assertThat(result).hasSize(1);
    Object outer = result.get("outer");
    assertThat(outer).isInstanceOf(Map.class);
    @SuppressWarnings("unchecked")
    Map<String, Object> innerMap = (Map<String, Object>) outer;
    assertThat(innerMap).containsEntry("inner", "val");
  }

  @Test
  void fromJson_invalidJson_throwsSessionException() {
    SessionException ex =
        assertThrows(SessionException.class, () -> JsonUtils.fromJson("{invalid json}"));
    assertThat(ex.getMessage()).contains("Failed to parse JSON state");
  }

  @Test
  void fromJson_returnsMutableMap() {
    Map<String, Object> result = JsonUtils.fromJson("{\"key\":\"value\"}");
    result.put("newKey", "newValue");
    assertThat(result).hasSize(2);
    assertThat(result).containsEntry("newKey", "newValue");
  }

  // ------------------------------------------------------------------------------------------------
  // Round-trip
  // ------------------------------------------------------------------------------------------------

  @Test
  void toJson_thenFromJson_roundTrip_preservesData() {
    Map<String, Object> original = new HashMap<>();
    original.put("string", "hello");
    original.put("int", 123);
    original.put("bool", true);
    original.put("nested", Map.of("a", "b"));

    String json = JsonUtils.toJson(original);
    Map<String, Object> roundTripped = JsonUtils.fromJson(json);

    assertThat(roundTripped).containsEntry("string", "hello");
    assertThat(roundTripped).containsEntry("int", 123);
    assertThat(roundTripped).containsEntry("bool", true);
    assertThat(roundTripped).containsKey("nested");
  }
}
