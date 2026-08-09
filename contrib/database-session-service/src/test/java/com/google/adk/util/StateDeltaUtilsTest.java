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

import com.google.adk.entity.StateDelta;
import com.google.adk.sessions.State;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link StateDeltaUtils}. */
public class StateDeltaUtilsTest {

  // ------------------------------------------------------------------------------------------------
  // extract
  // ------------------------------------------------------------------------------------------------

  @Test
  void extract_nullState_returnsEmptyDelta() {
    StateDelta delta = StateDeltaUtils.extract(null);
    assertThat(delta.appState()).isEmpty();
    assertThat(delta.userState()).isEmpty();
    assertThat(delta.sessionState()).isEmpty();
  }

  @Test
  void extract_emptyMap_returnsEmptyDelta() {
    StateDelta delta = StateDeltaUtils.extract(new HashMap<>());
    assertThat(delta.appState()).isEmpty();
    assertThat(delta.userState()).isEmpty();
    assertThat(delta.sessionState()).isEmpty();
  }

  @Test
  void extract_appPrefixedKeys_stripsPrefixAndPutsInAppState() {
    Map<String, Object> state = new HashMap<>();
    state.put("app:theme", "dark");
    state.put("app:version", 3);
    StateDelta delta = StateDeltaUtils.extract(state);
    assertThat(delta.appState()).hasSize(2);
    assertThat(delta.appState()).containsEntry("theme", "dark");
    assertThat(delta.appState()).containsEntry("version", 3);
    assertThat(delta.userState()).isEmpty();
    assertThat(delta.sessionState()).isEmpty();
  }

  @Test
  void extract_userPrefixedKeys_stripsPrefixAndPutsInUserState() {
    Map<String, Object> state = new HashMap<>();
    state.put("user:name", "alice");
    state.put("user:role", "admin");
    StateDelta delta = StateDeltaUtils.extract(state);
    assertThat(delta.userState()).hasSize(2);
    assertThat(delta.userState()).containsEntry("name", "alice");
    assertThat(delta.userState()).containsEntry("role", "admin");
    assertThat(delta.appState()).isEmpty();
    assertThat(delta.sessionState()).isEmpty();
  }

  @Test
  void extract_tempPrefixedKeys_dropsThem() {
    Map<String, Object> state = new HashMap<>();
    state.put("temp:cache", "data");
    state.put("temp:counter", 1);
    StateDelta delta = StateDeltaUtils.extract(state);
    assertThat(delta.appState()).isEmpty();
    assertThat(delta.userState()).isEmpty();
    assertThat(delta.sessionState()).isEmpty();
  }

  @Test
  void extract_unprefixedKeys_putsInSessionState() {
    Map<String, Object> state = new HashMap<>();
    state.put("currentPage", "home");
    state.put("step", 2);
    StateDelta delta = StateDeltaUtils.extract(state);
    assertThat(delta.sessionState()).hasSize(2);
    assertThat(delta.sessionState()).containsEntry("currentPage", "home");
    assertThat(delta.sessionState()).containsEntry("step", 2);
    assertThat(delta.appState()).isEmpty();
    assertThat(delta.userState()).isEmpty();
  }

  @Test
  void extract_mixedKeys_sortsIntoCorrectBuckets() {
    Map<String, Object> state = new HashMap<>();
    state.put("app:config", "prod");
    state.put("user:id", "u-123");
    state.put("temp:transient", "x");
    state.put("sessionKey", "sessionVal");
    StateDelta delta = StateDeltaUtils.extract(state);
    assertThat(delta.appState()).containsEntry("config", "prod");
    assertThat(delta.userState()).containsEntry("id", "u-123");
    assertThat(delta.sessionState()).containsEntry("sessionKey", "sessionVal");
    // temp: key is dropped
    assertThat(delta.appState()).hasSize(1);
    assertThat(delta.userState()).hasSize(1);
    assertThat(delta.sessionState()).hasSize(1);
  }

  @Test
  void extract_appPrefixOnlyKey_resultsInEmptyPrefixedKey() {
    Map<String, Object> state = new HashMap<>();
    state.put("app:", "value");
    StateDelta delta = StateDeltaUtils.extract(state);
    assertThat(delta.appState()).containsEntry("", "value");
  }

  // ------------------------------------------------------------------------------------------------
  // merge
  // ------------------------------------------------------------------------------------------------

  @Test
  void merge_allThreeBuckets_prependsPrefixes() {
    Map<String, Object> appState = Map.of("theme", "dark");
    Map<String, Object> userState = Map.of("name", "alice");
    Map<String, Object> sessionState = Map.of("step", 1);

    Map<String, Object> merged = StateDeltaUtils.merge(appState, userState, sessionState);

    assertThat(merged).hasSize(3);
    assertThat(merged).containsEntry("app:theme", "dark");
    assertThat(merged).containsEntry("user:name", "alice");
    assertThat(merged).containsEntry("step", 1);
  }

  @Test
  void merge_nullAppAndUserState_returnsSessionStateCopy() {
    Map<String, Object> sessionState = new HashMap<>();
    sessionState.put("key", "value");

    Map<String, Object> merged = StateDeltaUtils.merge(null, null, sessionState);

    assertThat(merged).containsEntry("key", "value");
    // Verify it's a copy, not the same reference
    merged.put("newKey", "newVal");
    assertThat(sessionState).doesNotContainKey("newKey");
  }

  @Test
  void merge_emptyStates_returnsEmptyMap() {
    Map<String, Object> merged =
        StateDeltaUtils.merge(new HashMap<>(), new HashMap<>(), new HashMap<>());
    assertThat(merged).isEmpty();
  }

  @Test
  void merge_appStateKeyOverridesSessionStateWithSamePrefixedKey() {
    // If sessionState has "app:theme" and appState also has "theme",
    // the appState entry (with prefix) is written after sessionState entries.
    Map<String, Object> appState = new HashMap<>();
    appState.put("theme", "fromApp");
    Map<String, Object> sessionState = new HashMap<>();
    sessionState.put("app:theme", "fromSession");

    Map<String, Object> merged = StateDeltaUtils.merge(appState, null, sessionState);

    assertThat(merged).containsEntry("app:theme", "fromApp");
  }

  @Test
  void merge_userStateKeyOverridesSessionStateWithSamePrefixedKey() {
    Map<String, Object> userState = new HashMap<>();
    userState.put("name", "fromUser");
    Map<String, Object> sessionState = new HashMap<>();
    sessionState.put("user:name", "fromSession");

    Map<String, Object> merged = StateDeltaUtils.merge(null, userState, sessionState);

    assertThat(merged).containsEntry("user:name", "fromUser");
  }

  @Test
  void merge_returnsMutableMap() {
    Map<String, Object> merged = StateDeltaUtils.merge(Map.of("a", "b"), null, new HashMap<>());
    merged.put("newKey", "newVal");
    assertThat(merged).containsEntry("newKey", "newVal");
  }

  // ------------------------------------------------------------------------------------------------
  // extract + merge round-trip
  // ------------------------------------------------------------------------------------------------

  @Test
  void extract_thenMerge_preservesNonTempKeysWithPrefixes() {
    Map<String, Object> original = new HashMap<>();
    original.put("app:config", "prod");
    original.put("user:id", "u-1");
    original.put("temp:drop", "me");
    original.put("plain", "value");

    StateDelta delta = StateDeltaUtils.extract(original);
    Map<String, Object> merged =
        StateDeltaUtils.merge(delta.appState(), delta.userState(), delta.sessionState());

    assertThat(merged).containsEntry("app:config", "prod");
    assertThat(merged).containsEntry("user:id", "u-1");
    assertThat(merged).containsEntry("plain", "value");
    assertThat(merged).doesNotContainKey("temp:drop");
  }

  @Test
  void extract_preservesStateRemovedSentinelInAppAndUserState() {
    Map<String, Object> state = new HashMap<>();
    state.put("app:removed", State.REMOVED);
    state.put("user:removed", State.REMOVED);
    state.put("sessionRemoved", State.REMOVED);

    StateDelta delta = StateDeltaUtils.extract(state);

    assertThat(delta.appState()).containsEntry("removed", State.REMOVED);
    assertThat(delta.userState()).containsEntry("removed", State.REMOVED);
    assertThat(delta.sessionState()).containsEntry("sessionRemoved", State.REMOVED);
  }
}
