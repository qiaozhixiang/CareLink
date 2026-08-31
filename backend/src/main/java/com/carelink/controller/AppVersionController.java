package com.carelink.controller;

import com.carelink.dto.ApiResponse;
import com.carelink.dto.VersionCheckResponse;
import com.carelink.entity.AppConfig;
import com.carelink.repository.AppConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * App 版本管理接口
 * App 端通过此接口检查最新版本并获取 APK 下载地址
 *
 * 配置示例（在数据库 app_configs 表中插入）：
 * config_key                  | config_value
 * app.version                 | 1.1.0
 * app.version_code            | 11
 * app.update_message          | 优化性能，修复已知问题
 * app.download_url            | https://yourdomain.com/apk/FamilyCare.apk
 * app.force_update            | false
 */
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class AppVersionController {

    private final AppConfigRepository appConfigRepository;

    /**
     * 检查 App 最新版本
     *
     * @param platform 平台：android / ios（目前仅支持 android）
     * @param currentVersion 当前安装版本，如 "1.0.0"
     * @param currentVersionCode 当前版本号整数，如 10
     */
    @GetMapping("/version/check")
    public ResponseEntity<ApiResponse<VersionCheckResponse>> checkVersion(
            @RequestParam(defaultValue = "android") String platform,
            @RequestParam(required = false) String currentVersion,
            @RequestParam(required = false) Integer currentVersionCode) {

        if (!"android".equalsIgnoreCase(platform)) {
            return ResponseEntity.ok(ApiResponse.fail("暂不支持该平台"));
        }

        // 从数据库读取配置（若无配置则返回无更新）
        String latestVersion = getConfig("app.version", "1.0.0");
        int latestVersionCode = Integer.parseInt(getConfig("app.version_code", "10"));
        String message = getConfig("app.update_message", "发现新版本，请更新以获得最佳体验");
        String downloadUrl = getConfig("app.download_url", "");
        boolean forceUpdate = Boolean.parseBoolean(getConfig("app.force_update", "false"));

        // 与当前版本比较：需要更新的情况
        // 1. 数据库有配置且 versionCode > 当前版本
        // 2. 数据库无配置（latestVersionCode 默认 10 > 0）
        boolean needUpdate = currentVersionCode == null || latestVersionCode > currentVersionCode;

        if (!needUpdate) {
            Map<String, Object> noUpdate = new HashMap<>();
            noUpdate.put("latest", false);
            return ResponseEntity.ok(ApiResponse.ok("已是最新版本", null));
        }

        VersionCheckResponse response = VersionCheckResponse.builder()
                .version(latestVersion)
                .versionCode(latestVersionCode)
                .message(message)
                .downloadUrl(downloadUrl)
                .forceUpdate(forceUpdate)
                .build();

        return ResponseEntity.ok(ApiResponse.ok("发现新版本 v" + latestVersion, response));
    }

    private String getConfig(String key, String defaultValue) {
        return appConfigRepository.findById(key)
                .map(AppConfig::getConfigValue)
                .orElse(defaultValue);
    }
}
