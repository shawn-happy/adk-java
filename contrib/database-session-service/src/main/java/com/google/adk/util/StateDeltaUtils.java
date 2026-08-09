package com.google.adk.util;

import com.google.adk.entity.StateDelta;
import com.google.adk.sessions.State;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class StateDeltaUtils {

  private StateDeltaUtils() {}

  /**
   * Splits the given state map into {@link StateDelta}.
   *
   * <p>{@code app:}-prefixed keys go to {@code appState} (prefix stripped), {@code user:}-prefixed
   * keys go to {@code userState} (prefix stripped), {@code temp:}-prefixed keys are dropped, and
   * other keys go to {@code sessionState}.
   */
  public static StateDelta extract(@Nullable Map<String, Object> state) {
    Map<String, Object> appState = new HashMap<>();
    Map<String, Object> userState = new HashMap<>();
    Map<String, Object> sessionState = new HashMap<>();

    if (state != null) {
      for (Map.Entry<String, Object> entry : state.entrySet()) {
        String key = entry.getKey();
        Object value = entry.getValue();
        if (key.startsWith(State.APP_PREFIX)) {
          appState.put(key.substring(State.APP_PREFIX.length()), value);
        } else if (key.startsWith(State.USER_PREFIX)) {
          userState.put(key.substring(State.USER_PREFIX.length()), value);
        } else if (!key.startsWith(State.TEMP_PREFIX)) {
          sessionState.put(key, value);
        }
        // temp: keys are dropped intentionally.
      }
    }
    return new StateDelta(appState, userState, sessionState);
  }

  /**
   * Merges the three state buckets into a single map. App-state keys get {@link State#APP_PREFIX}
   * and user-state keys get {@link State#USER_PREFIX} prepended.
   */
  public static Map<String, Object> merge(
      Map<String, Object> appState,
      Map<String, Object> userState,
      Map<String, Object> sessionState) {
    Map<String, Object> merged = new HashMap<>(sessionState);
    if (appState != null) {
      for (Map.Entry<String, Object> entry : appState.entrySet()) {
        merged.put(State.APP_PREFIX + entry.getKey(), entry.getValue());
      }
    }
    if (userState != null) {
      for (Map.Entry<String, Object> entry : userState.entrySet()) {
        merged.put(State.USER_PREFIX + entry.getKey(), entry.getValue());
      }
    }
    return merged;
  }
}
