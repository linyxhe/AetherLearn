package com.aetherlearn.controller;

import com.aetherlearn.common.Result;
import com.aetherlearn.common.RoleConstant;
import com.aetherlearn.common.SecurityUtils;
import com.aetherlearn.dto.WrongBookItemVO;
import com.aetherlearn.service.WrongBookService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 错题本控制器（F-LEARN 扩展模块）
 */
@RestController
@RequestMapping("/api/wrong-book")
public class WrongBookController {

    private final WrongBookService wrongBookService;

    public WrongBookController(WrongBookService wrongBookService) {
        this.wrongBookService = wrongBookService;
    }

    /**
     * 当前学生错题本
     */
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/list")
    public Result<List<WrongBookItemVO>> list(@RequestParam(required = false) String sourceType,
                                              @RequestParam(required = false) Long courseId) {
        return Result.success(wrongBookService.list(SecurityUtils.getCurrentUserId(), sourceType, courseId));
    }
}
