package com.aetherlearn.controller;

import com.aetherlearn.common.BusinessException;
import com.aetherlearn.common.RoleConstant;
import com.aetherlearn.common.SecurityUtils;
import com.aetherlearn.common.Result;
import com.aetherlearn.kb.DocumentParser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传控制器（F-FILE-01 统一上传接口）
 * <p>路径：POST /api/file/upload?bizType=xxx 。
 * 按业务分桶存到 uploads/{bizType}/ 下，返回可访问的虚拟路径。</p>
 */
@RestController
@RequestMapping("/api/file")
public class FileController {

    /** 上传根目录（来自 application.yml 的 file.upload-dir） */
    @Value("${file.upload-dir}")
    private String uploadDir;

    private final DocumentParser documentParser;

    public FileController(DocumentParser documentParser) {
        this.documentParser = documentParser;
    }

    /** 允许的业务分桶 */
    private static final String[] BIZ_TYPES = {"avatar", "course", "knowledge", "answer", "export"};

    /**
     * 统一上传
     *
     * @param file    上传的文件
     * @param bizType 业务类型（avatar/course/knowledge/answer/export）
     * @return 含访问路径的 Map
     */
    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file,
                                              @RequestParam("bizType") String bizType,
                                              HttpServletRequest request) {
        // 1. 校验业务分桶
        boolean valid = false;
        for (String t : BIZ_TYPES) {
            if (t.equals(bizType)) { valid = true; break; }
        }
        if (!valid) {
            throw new BusinessException(400, "非法的 bizType：" + bizType);
        }
        validateBizTypePermission(bizType);
        if (file.isEmpty()) {
            throw new BusinessException(400, "上传文件不能为空");
        }

        try {
            // 2. 目标目录 uploads/{bizType}/（不存在则创建）
            Path dir = Paths.get(uploadDir, bizType);
            Files.createDirectories(dir);

            // 3. 生成唯一文件名（保留原扩展名）
            String original = file.getOriginalFilename();
            String ext = "";
            if (original != null && original.contains(".")) {
                String candidate = original.substring(original.lastIndexOf(".")).toLowerCase();
                // 仅保留安全扩展名，避免原始文件名中的路径分隔符进入目标路径。
                if (candidate.matches("\\.[a-z0-9]{1,10}")) {
                    ext = candidate;
                }
            }
            String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
            Path target = dir.resolve(fileName);
            file.transferTo(target);

            // 4. 组装可访问路径（与 WebConfig 静态映射 /uploads/** 对应）
            String accessPath = "/" + Paths.get("uploads", bizType, fileName).toString().replace("\\", "/");

            Map<String, String> data = new HashMap<>();
            data.put("url", accessPath);
            data.put("fileName", fileName);
            data.put("bizType", bizType);
            return Result.success("上传成功", data);
        } catch (IOException e) {
            throw new BusinessException(500, "文件保存失败：" + e.getMessage());
        }
    }

    /**
     * 解析已上传的课程章节资料为纯文本，供教师二次编辑章节内容。
     */
    @GetMapping("/parse")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<Map<String, String>> parseUploaded(@RequestParam("url") String url) {
        if (url == null || !url.startsWith("/uploads/")) {
            throw new BusinessException(400, "非法文件路径");
        }
        try {
            Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
            String relative = url.replaceFirst("^/uploads/", "");
            Path target = root.resolve(relative).normalize();
            if (!target.startsWith(root) || !Files.exists(target)) {
                throw new BusinessException(404, "文件不存在");
            }
            String fileName = target.getFileName().toString();
            String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf(".") + 1) : "";
            try (InputStream in = Files.newInputStream(target)) {
                String text = documentParser.parse(in, ext);
                Map<String, String> data = new HashMap<>();
                data.put("text", text == null ? "" : text.trim());
                data.put("fileName", fileName);
                return Result.success("解析成功", data);
            }
        } catch (IOException e) {
            throw new BusinessException(500, "文件解析失败：" + e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, e.getMessage());
        }
    }

    /** 按业务分桶限制上传角色，避免客户端修改 bizType 写入其他业务目录。 */
    private void validateBizTypePermission(String bizType) {
        Integer role = SecurityUtils.getCurrentRole();
        if ("avatar".equals(bizType)) {
            return;
        }
        if ("answer".equals(bizType)
                && (RoleConstant.STUDENT == role || RoleConstant.TEACHER == role)) {
            return;
        }
        if (("course".equals(bizType) || "knowledge".equals(bizType) || "export".equals(bizType))
                && (RoleConstant.TEACHER == role || RoleConstant.ADMIN == role)) {
            return;
        }
        throw new BusinessException(403, "当前角色无权上传该类型文件");
    }
}
