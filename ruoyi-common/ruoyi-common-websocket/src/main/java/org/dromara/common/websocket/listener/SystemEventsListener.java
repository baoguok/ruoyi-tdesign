package org.dromara.common.websocket.listener;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.config.RuoYiConfig;
import org.dromara.common.core.events.LoginEvent;
import org.dromara.common.core.events.NoticeInsertEvent;
import org.dromara.common.core.service.DictService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.websocket.utils.WebSocketUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 系统事件监听
 *
 * @author hexm
 * @date 2023/11/20 15:52
 */
@Component
@RequiredArgsConstructor
public class SystemEventsListener {

    private final ScheduledExecutorService scheduledExecutorService;
    private final DictService dictService;
    private final RuoYiConfig ruoyiConfig;

    /**
     * 登录消息推送
     */
    @EventListener
    public void login(LoginEvent loginEvent) {
        scheduledExecutorService.schedule(() -> {
            String message = StringUtils.format("[登录] 欢迎登录{}后台管理系统", ruoyiConfig.getName());
            WebSocketUtils.sendMessage(LoginHelper.getLoginType(), loginEvent.getUserId(), message);
        }, 5, TimeUnit.SECONDS);
    }

    /**
     * 新增通知事件
     */
    @EventListener
    public void noticeInsert(NoticeInsertEvent event) {
        String type = dictService.getDictLabel("sys_notice_type", event.getType());
        WebSocketUtils.publishAll(LoginHelper.getLoginType(), "[" + type + "] " + event.getTitle());
    }
}
