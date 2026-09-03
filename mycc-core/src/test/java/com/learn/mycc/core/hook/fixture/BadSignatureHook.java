package com.learn.mycc.core.hook.fixture;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Hook;

/** 测试用：@Hook 方法签名错误（参数非 HookEvent），注册时应报错。 */
@Component
public class BadSignatureHook {

    @Hook(event = "session_start")
    public void wrongSignature(String notAnEvent) {
    }
}
