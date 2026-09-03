package com.learn.mycc.core.hook;

import com.learn.mycc.core.exception.MyccException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HookEventTypeTest {

    @Test
    void exposesSixCanonicalEventNames() {
        assertThat(HookEventType.values()).hasSize(6);
        assertThat(HookEventType.SESSION_START.eventName()).isEqualTo("session_start");
        assertThat(HookEventType.SESSION_END.eventName()).isEqualTo("session_end");
        assertThat(HookEventType.TOOL_CALL_BEFORE.eventName()).isEqualTo("tool_call_before");
        assertThat(HookEventType.TOOL_CALL_AFTER.eventName()).isEqualTo("tool_call_after");
        assertThat(HookEventType.ERROR.eventName()).isEqualTo("error");
        assertThat(HookEventType.USER_PROMPT_SUBMIT.eventName()).isEqualTo("user_prompt_submit");
    }

    @Test
    void fromNameMapsKnownNames() {
        assertThat(HookEventType.fromName("session_start")).isEqualTo(HookEventType.SESSION_START);
        assertThat(HookEventType.fromName("user_prompt_submit")).isEqualTo(HookEventType.USER_PROMPT_SUBMIT);
    }

    @Test
    void fromNameRejectsUnknownName() {
        assertThatThrownBy(() -> HookEventType.fromName("no_such_event"))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("未知钩子事件");
    }
}
