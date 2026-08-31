package com.carelink.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * App 版本检查响应
 * 存放于数据库 app_config 表，由 UpdateManager 读取
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionCheckResponse {
    /** 最新版本号，如 "1.1.0" */
    private String version;

    /** 版本号整数，用于比较，如 110 */
    private int versionCode;

    /** 更新说明 */
    private String message;

    /** APK 下载地址 */
    private String downloadUrl;

    /** 是否强制更新 */
    private boolean forceUpdate;
}
