-- ============================================================================
-- TSS GRC Platform - Schema Creation Script
-- Database: TSS_GRC
-- Purpose: Create all application schemas
-- ============================================================================

USE TSS_GRC;
GO

CREATE SCHEMA platform AUTHORIZATION dbo;
GO
CREATE SCHEMA iam AUTHORIZATION dbo;
GO
CREATE SCHEMA admin AUTHORIZATION dbo;
GO
CREATE SCHEMA workflow AUTHORIZATION dbo;
GO
CREATE SCHEMA risk AUTHORIZATION dbo;
GO
CREATE SCHEMA cyber AUTHORIZATION dbo;
GO
CREATE SCHEMA bcm AUTHORIZATION dbo;
GO
CREATE SCHEMA privacy AUTHORIZATION dbo;
GO
CREATE SCHEMA compliance AUTHORIZATION dbo;
GO
CREATE SCHEMA reporting AUTHORIZATION dbo;
GO
CREATE SCHEMA audit AUTHORIZATION dbo;
GO

PRINT 'All schemas created successfully.';
GO
