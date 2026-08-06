package com.aetherlearn.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Vue 单页应用入口控制器。
 * <p>生产环境将前端构建产物放入 classpath:/static/；用户刷新任意前端路由时，
 * 统一转发至 index.html，再由 Vue Router 接管页面渲染。</p>
 */
@Controller
public class SpaWebController {

    /**
     * 转发所有已定义的前端路由到 Vue 入口页。
     *
     * @return 前端入口页面
     */
    @GetMapping({"/", "/login", "/dashboard", "/student-dashboard", "/ai-advice", "/paper-builder",
            "/course", "/user", "/config", "/knowledge", "/homework", "/notice", "/knowledge-graph",
            "/my-course", "/my-notes", "/my-homework", "/wrong-book", "/todo", "/ai-report", "/profile", "/qa"})
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
