package com.learn.mycc.ai.spi;

import com.learn.mycc.ai.model.ChatResponse;
import com.learn.mycc.ai.model.StreamChunk;

/**
 * 流式输出回调：Provider 边生成边回调，最后以完整响应或错误结束。
 * <p>回调保证：按 onChunk... 若干次后，最终恰好调用一次 onComplete 或 onError（二者互斥），
 * 接收方据此结束本轮。本接口在 mycc-ui 模块有对应实现用于界面渲染。
 */
public interface StreamSink {

    /** 追加一段增量文本（正文或思考内容），可能被多次调用。 */
    void onChunk(StreamChunk chunk);

    /** 流式结束（正常）：下发累加完成的完整响应，此后不应再有任何回调。 */
    void onComplete(ChatResponse response);

    /** 流式结束（异常）：上报错误，此后不应再有任何回调。 */
    void onError(Throwable error);
}
