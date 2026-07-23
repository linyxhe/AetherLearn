package com.aetherlearn.service;

import com.aetherlearn.dto.WrongBookItemVO;

import java.util.List;

/**
 * 错题本服务接口
 */
public interface WrongBookService {

    /**
     * 获取当前学生错题本
     */
    List<WrongBookItemVO> list(Long studentId, String sourceType, Long courseId);
}
