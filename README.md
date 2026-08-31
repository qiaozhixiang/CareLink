# CareLink

CareLink 是一个面向老年人及其家属的关怀服务项目，包含 Android 客户端、Spring Boot 后端和 MySQL 数据库初始化脚本。

完整的项目定位与功能介绍请参阅[项目简介](项目简介.md)。

## 项目结构

```text
CareLink/
|- android/                 Android 客户端源码
|- backend/                 Spring Boot 后端源码与配置
|- database/                MySQL 数据库脚本
|  `- carelink_db.sql       数据库初始化脚本
|- artifacts/               已构建的发布产物，不参与源码编译
|  |- android/              Android APK
|  `- backend/              后端 JAR 包
`- README.md                项目说明
```

## 功能概览

- 用户注册、登录、角色选择和家庭成员管理
- 日程、打卡、陪伴提醒与关怀笔记
- 紧急告警、位置共享、远程协助和健康数据服务
- 家属端与长者端的差异化界面

## 数据库初始化

项目使用 MySQL 8.0+。创建数据库并导入脚本：

```powershell
mysql -u root -p < database/carelink_db.sql
```

默认数据库名为 `carelink_db`。本地数据库连接参数位于 `backend/src/main/resources/application.properties`，请按实际环境调整。

## 后端运行

环境要求：JDK 17、MySQL 8.0+。

```powershell
Set-Location backend
.\gradlew.bat bootRun
```

后端默认监听 `http://localhost:8080`。已有的发布 JAR 位于 `artifacts/backend/`。

## Android 客户端

Android 源码位于 `android/`，可通过 Android Studio 打开该目录。当前目录仅包含顶层 Gradle 配置和 `app/src` 源码，缺少 `android/app/build.gradle`，需补回模块构建脚本后才能从命令行或 Android Studio 完整构建。已有调试 APK 位于 `artifacts/android/app-debug.apk`。

## 配置与安全

- `application.properties` 当前为本地开发配置，不应提交真实数据库、邮件、JWT、短信或对象存储凭据。
- 生产环境请使用 `application-prod.properties` 或环境变量提供凭据。
- `artifacts/`、Gradle 缓存、构建目录和本地配置已列入 `.gitignore`，便于后续初始化 Git 仓库。

## 发布产物说明

`artifacts/backend/` 中的两个 JAR 内容相同，保留两份仅为保留整理前的原始文件：

- `carelink-backend-1.0.0.jar`：原发布目录中的文件
- `carelink-backend-1.0.0-duplicate.jar`：原后端源码目录中的同内容副本
