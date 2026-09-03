package com.learn.mycc.core.hook.fixture;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Hook;
import com.learn.mycc.core.hook.HookEvent;

/** 测试用：@Hook 声明了未知事件名，注册时应报错。 */
@Component
public class BadEventHook {

    @Hook(event = "no_such_event")
    public void onBad(HookEvent event) {
    }
}
