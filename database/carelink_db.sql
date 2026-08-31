-- CareLink database full schema
-- Source: backend JPA entities + DatabaseSchemaCompatibilityRunner
-- Generated on: 2026-04-30
-- Target: MySQL 8.0+

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS `carelink_db`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
USE `carelink_db`;

-- =====================================
-- 1) users
-- =====================================
CREATE TABLE IF NOT EXISTS `users` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `email` VARCHAR(100) NOT NULL,
  `password` VARCHAR(255) NOT NULL,
  `nickname` VARCHAR(50) NULL,
  `role` VARCHAR(20) NULL,
  `adminProof` VARCHAR(1000) NULL,
  `adminVerified` BIT(1) NULL DEFAULT b'0',
  `adminProofExpireAt` BIGINT NULL,
  `avatarUrl` VARCHAR(500) NULL,
  `emergencyContactName` VARCHAR(50) NULL,
  `emergencyContactPhone` VARCHAR(20) NULL,
  `familyId` BIGINT NULL,
  `roleSelectedAt` BIGINT NULL,
  `emailVerified` BIT(1) NULL DEFAULT b'0',
  `wechatOpenid` VARCHAR(100) NULL,
  `createdAt` DATETIME(6) NULL,
  `updatedAt` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_email` (`email`),
  KEY `idx_users_familyId` (`familyId`),
  KEY `idx_users_wechatOpenid` (`wechatOpenid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================
-- 2) families
-- =====================================
CREATE TABLE IF NOT EXISTS `families` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NULL,
  `inviteCode` VARCHAR(10) NULL,
  `creatorId` BIGINT NULL,
  `createdAt` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_families_inviteCode` (`inviteCode`),
  KEY `idx_families_creatorId` (`creatorId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================
-- 3) appointments
-- =====================================
CREATE TABLE IF NOT EXISTS `appointments` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `elderId` BIGINT NULL,
  `title` VARCHAR(255) NULL,
  `category` VARCHAR(20) NULL,
  `startTime` DATETIME(6) NULL,
  `endTime` DATETIME(6) NULL,
  `location` VARCHAR(200) NULL,
  `notes` VARCHAR(500) NULL,
  `reminderType` VARCHAR(20) NULL,
  `remindBefore` INT NULL,
  `status` INT NULL DEFAULT 0,
  `createdBy` BIGINT NULL,
  `createdAt` DATETIME(6) NULL,
  `updatedAt` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  KEY `idx_appointments_elderId` (`elderId`),
  KEY `idx_appointments_startTime` (`startTime`),
  KEY `idx_appointments_elderId_startTime` (`elderId`, `startTime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================
-- 4) care_notes
-- =====================================
CREATE TABLE IF NOT EXISTS `care_notes` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `elderId` BIGINT NULL,
  `authorId` BIGINT NULL,
  `content` VARCHAR(1000) NULL,
  `tags` VARCHAR(200) NULL,
  `isImportant` INT NULL DEFAULT 0,
  `imageUrl` VARCHAR(500) NULL,
  `createdAt` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  KEY `idx_care_notes_elderId` (`elderId`),
  KEY `idx_care_notes_authorId` (`authorId`),
  KEY `idx_care_notes_createdAt` (`createdAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================
-- 5) checkin_tasks
-- =====================================
CREATE TABLE IF NOT EXISTS `checkin_tasks` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `elderId` BIGINT NULL,
  `title` VARCHAR(255) NULL,
  `category` VARCHAR(20) NULL,
  `expectedTime` VARCHAR(10) NULL,
  `active` BIT(1) NULL DEFAULT b'1',
  `createdAt` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  KEY `idx_checkin_tasks_elderId` (`elderId`),
  KEY `idx_checkin_tasks_elderId_active` (`elderId`, `active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================
-- 6) checkin_records
-- =====================================
CREATE TABLE IF NOT EXISTS `checkin_records` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `elderId` BIGINT NULL,
  `taskId` BIGINT NULL,
  `title` VARCHAR(255) NULL,
  `completedAt` DATETIME(6) NULL,
  `status` VARCHAR(20) NULL DEFAULT 'DONE',
  `note` VARCHAR(300) NULL,
  `createdAt` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  KEY `idx_checkin_records_elderId` (`elderId`),
  KEY `idx_checkin_records_taskId` (`taskId`),
  KEY `idx_checkin_records_completedAt` (`completedAt`),
  KEY `idx_checkin_records_elderId_completedAt` (`elderId`, `completedAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================
-- 7) companion_reminders
-- =====================================
CREATE TABLE IF NOT EXISTS `companion_reminders` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `family_id` BIGINT NOT NULL,
  `sender_user_id` BIGINT NOT NULL,
  `elder_user_id` BIGINT NOT NULL,
  `emoji` VARCHAR(20) NULL,
  `label` VARCHAR(50) NULL,
  `message` VARCHAR(500) NOT NULL,
  `image_url` VARCHAR(1000) NULL,
  `sender_name` VARCHAR(50) NULL,
  `is_read` BIT(1) NULL DEFAULT b'0',
  `created_at` DATETIME(6) NULL,
  `updated_at` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  KEY `idx_companion_reminders_family_id` (`family_id`),
  KEY `idx_companion_reminders_sender_user_id` (`sender_user_id`),
  KEY `idx_companion_reminders_elder_user_id` (`elder_user_id`),
  KEY `idx_companion_reminders_elder_unread_created` (`elder_user_id`, `is_read`, `created_at`),
  KEY `idx_companion_reminders_sender_created` (`sender_user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================
-- 8) location_shares
-- =====================================
CREATE TABLE IF NOT EXISTS `location_shares` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `elderId` BIGINT NULL,
  `userId` BIGINT NULL,
  `userRole` VARCHAR(20) NULL,
  `nickname` VARCHAR(50) NULL,
  `avatarUrl` VARCHAR(500) NULL,
  `latitude` DOUBLE NULL,
  `longitude` DOUBLE NULL,
  `address` VARCHAR(300) NULL,
  `enabled` BIT(1) NULL DEFAULT b'1',
  `expireAt` DATETIME(6) NULL,
  `updatedAt` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  KEY `idx_location_shares_elderId` (`elderId`),
  KEY `idx_location_shares_userId` (`userId`),
  KEY `idx_location_shares_elder_updated` (`elderId`, `updatedAt`),
  KEY `idx_location_shares_user_updated` (`userId`, `updatedAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================
-- 9) alert_events
-- =====================================
CREATE TABLE IF NOT EXISTS `alert_events` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `elderId` BIGINT NULL,
  `alertType` VARCHAR(30) NULL,
  `description` VARCHAR(500) NULL,
  `level` INT NULL DEFAULT 1,
  `status` VARCHAR(20) NULL DEFAULT 'PENDING',
  `assignedTo` BIGINT NULL,
  `handleNote` VARCHAR(300) NULL,
  `handledAt` BIGINT NULL,
  `createdAt` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  KEY `idx_alert_events_elderId` (`elderId`),
  KEY `idx_alert_events_assignedTo` (`assignedTo`),
  KEY `idx_alert_events_status` (`status`),
  KEY `idx_alert_events_createdAt` (`createdAt`),
  KEY `idx_alert_events_elder_status_created` (`elderId`, `status`, `createdAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================
-- 10) admin_help_requests
-- =====================================
CREATE TABLE IF NOT EXISTS `admin_help_requests` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `requesterId` BIGINT NULL,
  `requesterRole` VARCHAR(20) NULL,
  `requesterName` VARCHAR(80) NULL,
  `familyId` BIGINT NULL,
  `familyName` VARCHAR(80) NULL,
  `latitude` DOUBLE NULL,
  `longitude` DOUBLE NULL,
  `address` VARCHAR(300) NULL,
  `message` VARCHAR(1000) NULL,
  `status` VARCHAR(20) NULL DEFAULT 'PENDING',
  `createdAtMs` BIGINT NULL,
  `completedAtMs` BIGINT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_admin_help_requester_created` (`requesterId`, `createdAtMs`),
  KEY `idx_admin_help_status_created` (`status`, `createdAtMs`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================
-- 11) admin_help_replies
-- =====================================
CREATE TABLE IF NOT EXISTS `admin_help_replies` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `requestId` BIGINT NULL,
  `adminId` BIGINT NULL,
  `adminName` VARCHAR(80) NULL,
  `message` VARCHAR(500) NULL,
  `createdAtMs` BIGINT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_admin_help_replies_request` (`requestId`, `createdAtMs`),
  KEY `idx_admin_help_replies_admin` (`adminId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================
-- 12) app_configs
-- =====================================
CREATE TABLE IF NOT EXISTS `app_configs` (
  `configKey` VARCHAR(255) NOT NULL,
  `configValue` VARCHAR(255) NULL,
  `updatedAt` DATETIME(6) NULL,
  PRIMARY KEY (`configKey`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================
-- Compatibility patch (from DatabaseSchemaCompatibilityRunner)
-- =====================================
SET @carelink_ddl := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `companion_reminders` ADD COLUMN `image_url` VARCHAR(1000) NULL AFTER `message`',
    'DO 0'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'companion_reminders'
    AND COLUMN_NAME = 'image_url'
);
PREPARE carelink_stmt FROM @carelink_ddl;
EXECUTE carelink_stmt;
DEALLOCATE PREPARE carelink_stmt;

SET @carelink_ddl := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `users` ADD COLUMN `adminProof` VARCHAR(1000) NULL AFTER `role`',
    'DO 0'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'users'
    AND COLUMN_NAME = 'adminProof'
);
PREPARE carelink_stmt FROM @carelink_ddl;
EXECUTE carelink_stmt;
DEALLOCATE PREPARE carelink_stmt;

SET @carelink_ddl := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `users` ADD COLUMN `adminVerified` BIT(1) NULL DEFAULT b''0'' AFTER `adminProof`',
    'DO 0'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'users'
    AND COLUMN_NAME = 'adminVerified'
);
PREPARE carelink_stmt FROM @carelink_ddl;
EXECUTE carelink_stmt;
DEALLOCATE PREPARE carelink_stmt;

SET @carelink_ddl := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `users` ADD COLUMN `adminProofExpireAt` BIGINT NULL AFTER `adminVerified`',
    'DO 0'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'users'
    AND COLUMN_NAME = 'adminProofExpireAt'
);
PREPARE carelink_stmt FROM @carelink_ddl;
EXECUTE carelink_stmt;
DEALLOCATE PREPARE carelink_stmt;

-- If old column imageUrl exists, migrate data manually before dropping:
-- UPDATE companion_reminders
-- SET image_url = COALESCE(image_url, imageUrl)
-- WHERE (image_url IS NULL OR image_url = '')
--   AND imageUrl IS NOT NULL AND imageUrl <> '';

SET FOREIGN_KEY_CHECKS = 1;
