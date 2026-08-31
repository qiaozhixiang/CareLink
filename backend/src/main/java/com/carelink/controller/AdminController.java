package com.carelink.controller;

import com.carelink.dto.ApiResponse;
import com.carelink.entity.AppConfig;
import com.carelink.repository.AppConfigRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 后台管理接口 · App 部署管理
 *
 * 功能：
 *  1. 上传新 APK 文件
 *  2. 查询 / 修改版本信息
 *  3. 下载当前 APK
 *
 * 访问地址（部署后）：
 *   http://140.143.139.16:8080/admin/upload.html
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminController {

    private final AppConfigRepository appConfigRepository;

    // 上传目录（application.properties 中配置）
    @Value("${carelink.upload.base-dir:C:/carelink/uploads}")
    private String uploadDir;

    // 对外下载地址前缀（application.properties 中配置）
    @Value("${carelink.upload.url-prefix:http://140.143.139.16:8080}")
    private String urlPrefix;

    // APK 下载路径（相对上传目录）
    private static final String APK_SUB_DIR = "apk";

    // ─────────────────────────────────────────────────────────────
    // 1. 上传 APK + 自动更新版本信息（一步完成）
    // ─────────────────────────────────────────────────────────────

    /**
     * 上传新 APK 并更新版本信息
     *
     * @param file          APK 文件（必填）
     * @param version       版本号字符串，如 "1.1.0"（必填）
     * @param versionCode   版本号整数，如 11（必填，需 > 当前版本号）
     * @param message       更新说明（可选，默认 "优化性能，修复已知问题"）
     * @param forceUpdate   是否强制更新（可选，默认 false）
     */
    @PostMapping("/upload-apk")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadApk(
            @RequestParam("file") MultipartFile file,
            @RequestParam("version") String version,
            @RequestParam("versionCode") Integer versionCode,
            @RequestParam(value = "message", required = false, defaultValue = "优化性能，修复已知问题") String message,
            @RequestParam(value = "forceUpdate", required = false, defaultValue = "false") Boolean forceUpdate,
            HttpServletRequest request) {

        // ── 参数校验 ──
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("请选择 APK 文件"));
        }
        if (!file.getOriginalFilename().toLowerCase().endsWith(".apk")) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("只支持上传 .apk 格式文件"));
        }
        if (version == null || version.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("版本号不能为空"));
        }
        if (versionCode == null || versionCode <= 0) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("版本号整数必须大于 0"));
        }

        try {
            // ── 1. 保存 APK 文件 ──
            Path uploadPath = Paths.get(uploadDir, APK_SUB_DIR);
            Files.createDirectories(uploadPath);

            // 文件名格式：CareLink_v1.1.0_build11_20260409.apk
            String timestamp = java.time.LocalDate.now().toString().replace("-", "");
            String safeVersion = version.trim().replace(".", "_").replace(" ", "_");
            String fileName = String.format("CareLink_v%s_build%d_%s.apk",
                    safeVersion, versionCode, timestamp);

            Path targetPath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            long fileSizeKB = file.getSize() / 1024;
            long fileSizeMB = fileSizeKB / 1024;

            // ── 2. 更新版本信息到数据库 ──
            String downloadUrl = urlPrefix + "/api/admin/download-apk/" + fileName;

            saveOrUpdateConfig("app.version", version.trim());
            saveOrUpdateConfig("app.version_code", String.valueOf(versionCode));
            saveOrUpdateConfig("app.update_message", message);
            saveOrUpdateConfig("app.download_url", downloadUrl);
            saveOrUpdateConfig("app.force_update", String.valueOf(forceUpdate));

            // ── 3. 返回结果 ──
            Map<String, Object> result = new HashMap<>();
            result.put("fileName", fileName);
            result.put("fileSize", fileSizeMB > 0 ? fileSizeMB + " MB" : fileSizeKB + " KB");
            result.put("version", version.trim());
            result.put("versionCode", versionCode);
            result.put("message", message);
            result.put("forceUpdate", forceUpdate);
            result.put("downloadUrl", downloadUrl);
            result.put("apkPath", targetPath.toString());
            result.put("uploadedAt", java.time.LocalDateTime.now().toString());

            return ResponseEntity.ok(ApiResponse.ok("APK 上传成功，版本信息已更新！", result));

        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.fail("文件保存失败: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. 查询当前版本信息
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/version-info")
    public ResponseEntity<ApiResponse<Map<String, String>>> getVersionInfo() {
        Map<String, String> info = new HashMap<>();
        info.put("version",     getConfig("app.version", "未设置"));
        info.put("versionCode", getConfig("app.version_code", "0"));
        info.put("message",     getConfig("app.update_message", ""));
        info.put("downloadUrl", getConfig("app.download_url", ""));
        info.put("forceUpdate", getConfig("app.force_update", "false"));
        return ResponseEntity.ok(ApiResponse.ok("当前版本信息", info));
    }

    // ─────────────────────────────────────────────────────────────
    // 3. 仅更新版本信息（不上传新 APK）
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/update-version")
    public ResponseEntity<ApiResponse<Void>> updateVersion(
            @RequestParam("version") String version,
            @RequestParam("versionCode") Integer versionCode,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "forceUpdate", required = false, defaultValue = "false") Boolean forceUpdate,
            @RequestParam(value = "downloadUrl", required = false) String downloadUrl) {

        if (version == null || version.trim().isEmpty() || versionCode == null || versionCode <= 0) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("版本号不能为空"));
        }

        saveOrUpdateConfig("app.version", version.trim());
        saveOrUpdateConfig("app.version_code", String.valueOf(versionCode));
        if (message != null) saveOrUpdateConfig("app.update_message", message);
        if (downloadUrl != null) saveOrUpdateConfig("app.download_url", downloadUrl);
        saveOrUpdateConfig("app.force_update", String.valueOf(forceUpdate));

        return ResponseEntity.ok(ApiResponse.ok("版本信息已更新", null));
    }

    // ─────────────────────────────────────────────────────────────
    // 4. 下载 APK（供 App 端调用 / 也可直接用 URL 直接下载）
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/download-apk/{fileName}")
    public ResponseEntity<Resource> downloadApk(@PathVariable String fileName) {
        try {
            Path filePath = Paths.get(uploadDir, APK_SUB_DIR, fileName);
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + fileName + "\"")
                    .body(resource);

        } catch (MalformedURLException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 5. 查询已上传的 APK 列表
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/apk-list")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getApkList() {
        try {
            Path apkDir = Paths.get(uploadDir, APK_SUB_DIR);
            Files.createDirectories(apkDir);

            java.util.List<Map<String, String>> files = Files.list(apkDir)
                    .filter(p -> p.toString().toLowerCase().endsWith(".apk"))
                    .map(p -> {
                        Map<String, String> info = new HashMap<>();
                        info.put("name", p.getFileName().toString());
                        info.put("size", formatFileSize(p.toFile().length()));
                        try {
                            info.put("modified", java.time.Instant
                                    .ofEpochMilli(Files.getLastModifiedTime(p).toMillis())
                                    .atZone(java.time.ZoneId.of("Asia/Shanghai"))
                                    .toLocalDateTime().toString());
                        } catch (IOException e) {
                            info.put("modified", "未知");
                        }
                        return info;
                    })
                    .sorted((a, b) -> b.get("modified").compareTo(a.get("modified")))
                    .toList();

            Map<String, Object> result = new HashMap<>();
            result.put("count", files.size());
            result.put("files", files);
            result.put("downloadBaseUrl", urlPrefix + "/api/admin/download-apk/");

            return ResponseEntity.ok(ApiResponse.ok("APK 列表", result));

        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.fail("读取 APK 列表失败: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 工具方法
    // ─────────────────────────────────────────────────────────────

    private String getConfig(String key, String defaultValue) {
        return appConfigRepository.findById(key)
                .map(AppConfig::getConfigValue)
                .orElse(defaultValue);
    }

    private void saveOrUpdateConfig(String key, String value) {
        AppConfig config = appConfigRepository.findById(key)
                .orElse(AppConfig.builder().configKey(key).build());
        config.setConfigValue(value);
        appConfigRepository.save(config);
    }

    private String formatFileSize(long bytes) {
        if (bytes >= 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format("%.1f KB", bytes / 1024.0);
    }
}
