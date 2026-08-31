package com.learn.mycc.ui;

/** 界面端口：agent 只通过它下发 {@link OutputEvent}，不直接碰终端/网络。 */
public interface InteractionPort {

    void onEvent(OutputEvent event);
}
