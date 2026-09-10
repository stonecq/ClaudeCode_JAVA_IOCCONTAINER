package com.learn.mycc.subagent;

import com.learn.mycc.ui.InteractionPort;
import com.learn.mycc.ui.OutputEvent;

import java.util.ArrayList;
import java.util.List;

/** 测试用 InteractionPort：记录所有事件。 */
public final class RecordingPort implements InteractionPort {

    public final List<OutputEvent> events = new ArrayList<>();

    @Override
    public void onEvent(OutputEvent event) {
        events.add(event);
    }
}