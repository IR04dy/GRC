/*
 * TSS GRC Platform - Database Creation Script
 * Purpose: Create the TSS_GRC database with enterprise-grade settings
 * Target:  SQL Server 2019+
 * Author:  TSS Platform Team
 * Date:    2026-04-05
 *
 * Notes:
 *   - Collation supports both Arabic and English via UTF-8
 *   - Filegroups separate data, indexes, and LOB storage for manageability
 *   - Adjust file paths to match your server environment before execution
 */

USE [master];
GO

-- --------------------------------------------------------------------------
-- 1. Drop the database if it exists (DEV/QA only -- NEVER run in production)
-- --------------------------------------------------------------------------
IF DB_ID(N'TSS_GRC') IS NOT NULL
BEGIN
    ALTER DATABASE [TSS_GRC] SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE [TSS_GRC];
END
GO

-- --------------------------------------------------------------------------
-- 2. Create the database
-- --------------------------------------------------------------------------
CREATE DATABASE [TSS_GRC]
ON PRIMARY
(
    NAME       = N'TSS_GRC_Primary',
    FILENAME   = N'D:\SQLData\TSS_GRC_Primary.mdf',
    SIZE       = 512MB,
    MAXSIZE    = UNLIMITED,
    FILEGROWTH = 256MB
),
FILEGROUP [FG_Data] DEFAULT
(
    NAME       = N'TSS_GRC_Data01',
    FILENAME   = N'D:\SQLData\TSS_GRC_Data01.ndf',
    SIZE       = 1024MB,
    MAXSIZE    = UNLIMITED,
    FILEGROWTH = 512MB
),
FILEGROUP [FG_Index]
(
    NAME       = N'TSS_GRC_Index01',
    FILENAME   = N'D:\SQLData\TSS_GRC_Index01.ndf',
    SIZE       = 512MB,
    MAXSIZE    = UNLIMITED,
    FILEGROWTH = 256MB
),
FILEGROUP [FG_LOB]
(
    NAME       = N'TSS_GRC_LOB01',
    FILENAME   = N'D:\SQLData\TSS_GRC_LOB01.ndf',
    SIZE       = 256MB,
    MAXSIZE    = UNLIMITED,
    FILEGROWTH = 128MB
),
FILEGROUP [FG_Audit]
(
    NAME       = N'TSS_GRC_Audit01',
    FILENAME   = N'D:\SQLData\TSS_GRC_Audit01.ndf',
    SIZE       = 512MB,
    MAXSIZE    = UNLIMITED,
    FILEGROWTH = 256MB
)
LOG ON
(
    NAME       = N'TSS_GRC_Log',
    FILENAME   = N'E:\SQLLogs\TSS_GRC_Log.ldf',
    SIZE       = 512MB,
    MAXSIZE    = UNLIMITED,
    FILEGROWTH = 256MB
)
COLLATE Latin1_General_100_CI_AS_SC_UTF8;
GO

-- --------------------------------------------------------------------------
-- 3. Configure database options
-- --------------------------------------------------------------------------
ALTER DATABASE [TSS_GRC] SET COMPATIBILITY_LEVEL = 150; -- SQL Server 2019
GO

ALTER DATABASE [TSS_GRC] SET ANSI_NULL_DEFAULT OFF;
GO
ALTER DATABASE [TSS_GRC] SET ANSI_NULLS ON;
GO
ALTER DATABASE [TSS_GRC] SET ANSI_PADDING ON;
GO
ALTER DATABASE [TSS_GRC] SET ANSI_WARNINGS ON;
GO
ALTER DATABASE [TSS_GRC] SET ARITHABORT ON;
GO
ALTER DATABASE [TSS_GRC] SET CONCAT_NULL_YIELDS_NULL ON;
GO
ALTER DATABASE [TSS_GRC] SET QUOTED_IDENTIFIER ON;
GO

-- Enable snapshot isolation for read-consistency without blocking writers
ALTER DATABASE [TSS_GRC] SET ALLOW_SNAPSHOT_ISOLATION ON;
GO
ALTER DATABASE [TSS_GRC] SET READ_COMMITTED_SNAPSHOT ON;
GO

-- Recovery model -- use FULL for production; SIMPLE acceptable for dev/test
ALTER DATABASE [TSS_GRC] SET RECOVERY FULL;
GO

-- Query Store for performance monitoring (SQL Server 2019 defaults)
ALTER DATABASE [TSS_GRC] SET QUERY_STORE = ON
(
    OPERATION_MODE          = READ_WRITE,
    DATA_FLUSH_INTERVAL_SECONDS = 900,
    INTERVAL_LENGTH_MINUTES = 60,
    MAX_STORAGE_SIZE_MB     = 1024,
    QUERY_CAPTURE_MODE      = AUTO,
    SIZE_BASED_CLEANUP_MODE = AUTO,
    MAX_PLANS_PER_QUERY     = 200
);
GO

-- Accelerated database recovery (SQL Server 2019 feature)
ALTER DATABASE [TSS_GRC] SET ACCELERATED_DATABASE_RECOVERY = ON;
GO

-- Page-level verification
ALTER DATABASE [TSS_GRC] SET PAGE_VERIFY CHECKSUM;
GO

-- Auto-tuning
ALTER DATABASE [TSS_GRC] SET AUTOMATIC_TUNING (FORCE_LAST_GOOD_PLAN = ON);
GO

PRINT 'Database [TSS_GRC] created successfully.';
GO
