package com.learn.mycc.web;

import com.learn.mycc.ui.OutputEvent;

/** SSE 订阅者抽象：接收一个输出事件。便于在无 Web 容器时对路由逻辑单测。 */
interface SseSink {

    /** 下发一个输出事件；连接已断时实现应静默忽略。 */
    void send(OutputEvent event);
}