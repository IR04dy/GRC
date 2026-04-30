# 16. Deployment Model Options

## Document Information

| Field | Value |
|-------|-------|
| Document Title | TSS GRC Platform - Deployment Model Options |
| Version | 1.0 |
| Classification | Internal |
| Last Updated | 2026-04-05 |

## 1. Overview

The TSS GRC Platform is designed with a flexible architecture that supports multiple deployment models. This document defines the four primary deployment options, their respective architectures, and the technical considerations for each. The platform's architecture decisions are made to support all deployment models from a single codebase.

### Deployment Model Summary

| Model | Infrastructure Owner | Operations | Data Location | Isolation Level |
|-------|---------------------|------------|---------------|----------------|
| **On-Premises** | Customer | Customer IT | Customer data center | Physical (dedicated hardware) |
| **Private Cloud** | Customer (cloud) | Customer IT or managed | Customer cloud account | Virtual (dedicated cloud resources) |
| **SaaS Multi-Tenant** | TSS | TSS operations | TSS-managed cloud | Logical (shared infrastructure, data-layer isolation) |
| **Hybrid** | Shared | Shared | Split (sensitive on-prem, management in cloud) | Physical + Logical |

---

## 2. On-Premises Deployment

### 2.1 Architecture

```
Customer Data Center
├── Application Tier
│   ├── Application Server (Tomcat embedded in Spring Boot JAR)
│   │   ├── TSS GRC Application (single deployable JAR/WAR)
│   │   ├── Embedded Tomcat (or deployed to external Tomcat)
│   │   └── Configuration: application-prod.yml
│   └── [Optional] Load Balancer (for clustered deployment)
│       ├── App Server Node 1
│       └── App Server Node 2
│
├── Database Tier
│   ├── SQL Server 2019+ (Standard or Enterprise Edition)
│   │   ├── GRC Database (tss_grc)
│   │   └── Transaction Log
│   └── [Optional] SQL Server AlwaysOn AG (for HA)
│       ├── Primary Replica
│       └── Secondary Replica
│
├── File Storage
│   ├── Network File Share (SMB/CIFS) or local disk
│   └── Stores: uploaded documents, generated reports, temporary files
│
├── Email Relay
│   └── Customer SMTP server or Exchange
│
└── Supporting Infrastructure
    ├── Active Directory / LDAP (for SSO integration)
    ├── Backup Storage (network attached or SAN)
    └── Monitoring (customer's existing monitoring tools)
```

### 2.2 Architecture Considerations

| Aspect | Specification |
|--------|---------------|
| **Deployment Artifact** | Single executable JAR (Spring Boot fat JAR) or WAR file deployable to an external Tomcat instance. |
| **Java Runtime** | OpenJDK 21 LTS or compatible JDK. Customer provides and maintains the JVM. |
| **Single Instance** | Minimum deployment: one application server, one SQL Server instance. Suitable for up to 100 concurrent users. |
| **Clustered** | Two or more application instances behind a hardware or software load balancer (e.g., HAProxy, F5, Nginx). Requires external session store (Spring Session with JDBC) or sticky sessions. |
| **Configuration** | `application-onprem.yml` profile with environment-specific settings. Database credentials, SMTP settings, file storage paths configured per environment. |
| **Windows Service** | Application can be registered as a Windows Service using `winsw` or `procrun` for automatic startup and service management. |
| **Linux Service** | Application can be registered as a systemd service for Linux-based deployments. |

### 2.3 Data Isolation

| Aspect | Approach |
|--------|----------|
| **Level** | Physical isolation. Customer has their own dedicated database on their own hardware. |
| **Database** | Single database instance exclusively for the customer. No multi-tenant data mixing. |
| **File Storage** | Dedicated file storage on customer's network. |
| **Encryption** | Customer manages SQL Server TDE (Transparent Data Encryption) for data at rest. Application enforces TLS for data in transit. |

### 2.4 Networking

| Aspect | Specification |
|--------|---------------|
| **Internal Access** | Application accessible via internal network only (no public internet exposure required). |
| **HTTPS** | TLS termination at the application server (embedded Tomcat with customer-provided certificate) or at an upstream reverse proxy/load balancer. |
| **Firewall Rules** | Only port 443 (HTTPS) needs to be opened for user access. SQL Server port (1433) accessible only from the application server. |
| **No Outbound** | No outbound internet connectivity required for core functionality. Outbound may be needed for: email relay to external SMTP, future integration connectors. |

### 2.5 Security

| Aspect | Specification |
|--------|---------------|
| **Authentication** | Local username/password by default. Integration with customer's Active Directory or LDAP for SSO. |
| **Encryption Keys** | Customer manages encryption keys and certificates. |
| **Audit Compliance** | Full audit logs stored locally. Customer has complete control over audit data retention and access. |
| **Vulnerability Management** | Customer responsible for OS patching, Java updates, and network security. TSS provides application security patches. |

### 2.6 Backup and Recovery

| Aspect | Specification |
|--------|---------------|
| **Database Backup** | Customer manages SQL Server backup schedules using SQL Server Agent, Ola Hallengren scripts, or their existing backup tools. |
| **Application Backup** | JAR/WAR file and configuration files backed up as part of file system backup. |
| **File Storage Backup** | Network file share backed up through customer's file-level backup solution. |
| **Recovery** | Customer executes recovery using documented runbooks. RTO and RPO targets as defined in NFR-BKP requirements. |
| **Tested Recovery** | TSS provides recovery test scripts. Customer responsible for scheduling and executing DR drills. |

### 2.7 Updates and Patching

| Aspect | Specification |
|--------|---------------|
| **Application Updates** | TSS provides updated JAR/WAR files with release notes. Customer schedules and applies updates during maintenance windows. |
| **Database Migrations** | Flyway migration scripts are included in the application. On startup, the application automatically applies pending migrations. |
| **Update Frequency** | Quarterly feature releases. Monthly security patches. Emergency patches as needed. |
| **Rollback** | Database migration rollback scripts provided. Application rollback by deploying previous version JAR/WAR. |
| **Version Compatibility** | Each release documents the minimum required database version. Forward-only migrations ensure compatibility. |

### 2.8 Monitoring

| Aspect | Specification |
|--------|---------------|
| **Health Check** | Spring Boot Actuator `/actuator/health` endpoint for monitoring integration. |
| **Metrics** | Actuator `/actuator/metrics` and `/actuator/prometheus` endpoints for metrics collection. |
| **Log Files** | Application logs written to configurable directory. Compatible with customer's log aggregation tools (Splunk, ELK, etc.). |
| **Alerting** | Customer configures alerting based on health check and metric data using their existing monitoring tools. |

### 2.9 Cost Model

| Cost Component | Bearer |
|----------------|--------|
| Hardware (servers, storage, networking) | Customer |
| Operating System licenses | Customer |
| SQL Server license | Customer |
| Java runtime | Free (OpenJDK) |
| TSS GRC application license | Customer (subscription to TSS) |
| IT operations (monitoring, patching, backup) | Customer IT team |
| Support and maintenance | TSS (included in subscription) |

### 2.10 Suitable Customer Profiles

- Government entities with strict data sovereignty requirements.
- Organizations in regulated industries (banking, healthcare) with data residency mandates.
- Large enterprises with existing IT infrastructure and operations teams.
- Organizations with air-gapped or restricted network environments.

---

## 3. Private Cloud Deployment

### 3.1 Architecture

```
Customer Cloud Account (Azure / AWS / GCP)
├── Virtual Network (VNet / VPC)
│   ├── Application Subnet
│   │   ├── VM-Based Deployment:
│   │   │   ├── VM 1: Application Server (Spring Boot JAR)
│   │   │   ├── VM 2: Application Server (Spring Boot JAR) [optional HA]
│   │   │   └── Application Load Balancer (ALB / Azure LB)
│   │   │
│   │   └── Container-Based Deployment (Alternative):
│   │       ├── Container Registry (ACR / ECR)
│   │       ├── Container Orchestration (AKS / EKS / ECS)
│   │       │   ├── GRC App Container (Pod/Task) x 2+
│   │       │   └── Auto-scaling group
│   │       └── Application Load Balancer
│   │
│   ├── Database Subnet
│   │   ├── Azure SQL Database / Azure SQL Managed Instance
│   │   │   OR
│   │   ├── Amazon RDS for SQL Server
│   │   │   OR
│   │   └── SQL Server on VM (customer-managed)
│   │
│   └── Storage Subnet
│       ├── Azure Blob Storage / AWS S3 (for file uploads)
│       └── [Optional] Azure Files / AWS EFS (for shared file system)
│
├── Security
│   ├── Network Security Groups (NSGs) / Security Groups
│   ├── Azure Key Vault / AWS Secrets Manager (credentials, certificates)
│   ├── Web Application Firewall (WAF) [optional]
│   └── DDoS Protection [optional]
│
├── Monitoring
│   ├── Azure Monitor + Application Insights / AWS CloudWatch
│   └── Log Analytics Workspace / CloudWatch Logs
│
└── Email
    └── Azure Communication Services / Amazon SES / Customer SMTP
```

### 3.2 Architecture Considerations

| Aspect | VM-Based | Container-Based |
|--------|---------|----------------|
| **Deployment** | JAR/WAR deployed to VMs using scripts or configuration management (Ansible, Chef) | Docker image pushed to container registry, orchestrated by AKS/EKS |
| **Scaling** | Manual scaling (add VMs) or VM Scale Sets | Auto-scaling based on CPU/memory/request count |
| **Startup Time** | 30-60 seconds per instance | 30-60 seconds per container |
| **Complexity** | Lower (familiar to traditional IT teams) | Higher (requires container expertise) |
| **Recommendation** | Default for customers with traditional IT teams | For customers with cloud-native operations teams |

### 3.3 Data Isolation

| Aspect | Approach |
|--------|----------|
| **Level** | Virtual isolation. Dedicated cloud resources within the customer's account. |
| **Database** | Dedicated SQL Server instance (managed service or VM) in the customer's cloud account. |
| **Network** | Private virtual network with no public endpoints (access via VPN or ExpressRoute/Direct Connect). |
| **Encryption** | Cloud provider's encryption at rest (Azure SSE, AWS EBS encryption). TLS for in-transit. Customer-managed keys (CMK) option. |
| **Data Residency** | Data stored in customer-selected cloud region (e.g., UAE North, Saudi Arabia coming soon for Azure). |

### 3.4 Networking

| Aspect | Specification |
|--------|---------------|
| **External Access** | Application Gateway or ALB with HTTPS termination. Optional WAF for additional security. |
| **Internal Network** | Private subnets for application and database tiers. No public IP addresses on backend resources. |
| **Connectivity** | VPN Gateway or ExpressRoute/Direct Connect for on-premises connectivity (AD integration, email relay). |
| **DNS** | Custom domain with customer's DNS provider. Azure Private DNS / Route 53 for internal resolution. |

### 3.5 Security

| Aspect | Specification |
|--------|---------------|
| **Identity** | Integration with Azure AD / AWS IAM for infrastructure access. Application-level authentication separate. |
| **Secrets Management** | Azure Key Vault or AWS Secrets Manager for database credentials, certificates, API keys. Application reads secrets at startup. |
| **Network Security** | NSGs restrict traffic between subnets. Only ALB can reach application servers. Only application servers can reach the database. |
| **Compliance** | Cloud provider compliance certifications (ISO 27001, SOC 2) support customer's compliance posture. |

### 3.6 Backup and Recovery

| Aspect | Specification |
|--------|---------------|
| **Database** | Managed SQL Server (Azure SQL, RDS) provides automated backups with configurable retention. Point-in-time restore available. |
| **File Storage** | Cloud storage (Blob/S3) provides built-in redundancy (LRS/ZRS). Cross-region replication optional. |
| **Application** | Container images or deployment packages stored in the registry/artifact store. Infrastructure-as-Code (Terraform, ARM) enables full environment recreation. |
| **DR** | Cross-region replication for database and storage. Secondary region deployment for critical customers. |

### 3.7 Updates and Patching

| Aspect | Specification |
|--------|---------------|
| **Application Updates** | VM: Updated JAR deployed via CI/CD pipeline or configuration management. Container: New Docker image built and deployed via rolling update. |
| **Zero-Downtime Deploy** | Rolling deployment: new instances are started before old ones are drained. Health checks ensure new instances are ready before receiving traffic. |
| **OS Patching** | VM: Customer manages OS updates. Container: Base image updates in new container builds. |
| **Database** | Managed SQL Server handles engine patches automatically (configurable maintenance window). |

### 3.8 Monitoring

| Aspect | Specification |
|--------|---------------|
| **Application Monitoring** | Azure Application Insights or AWS X-Ray for distributed tracing and application performance monitoring. |
| **Infrastructure Monitoring** | Azure Monitor / CloudWatch for VM/container metrics, alerts, and dashboards. |
| **Log Aggregation** | Application logs shipped to Azure Log Analytics / CloudWatch Logs. Structured JSON logs for queryability. |
| **Alerting** | Cloud-native alerting rules for: application errors, response time degradation, resource utilization, health check failures. |

### 3.9 Cost Model

| Cost Component | Bearer |
|----------------|--------|
| Cloud infrastructure (VMs/containers, networking, storage) | Customer |
| Managed database service | Customer |
| Cloud monitoring and security services | Customer |
| TSS GRC application license | Customer (subscription to TSS) |
| Cloud operations (if managed) | Customer or managed service provider |
| Support and maintenance | TSS (included in subscription) |

### 3.10 Suitable Customer Profiles

- Organizations with existing cloud infrastructure and cloud operations expertise.
- Organizations seeking cloud scalability without SaaS multi-tenancy.
- Organizations with data residency requirements satisfied by a specific cloud region.
- Mid-to-large enterprises with private cloud or hybrid cloud strategy.

---

## 4. SaaS Multi-Tenant Deployment

### 4.1 Architecture

```
TSS-Managed Cloud Environment
├── Edge Layer
│   ├── CDN (Azure Front Door / CloudFront)
│   ├── WAF (Web Application Firewall)
│   ├── DDoS Protection
│   └── SSL/TLS Termination (*.tssgrc.com)
│
├── Application Tier (Shared Infrastructure)
│   ├── Load Balancer
│   ├── Application Cluster
│   │   ├── App Instance 1 (serves all tenants)
│   │   ├── App Instance 2 (serves all tenants)
│   │   ├── App Instance N (auto-scaled)
│   │   └── Tenant context resolved per request from subdomain/header
│   └── Background Job Workers
│       ├── Report Generation Worker Pool
│       ├── Email Notification Worker Pool
│       └── Scheduled Job Worker Pool
│
├── Data Tier (Tenant Isolation Options)
│   │
│   ├── Option A: Shared Database, Shared Schema (Recommended)
│   │   ├── Single SQL Server cluster
│   │   ├── Single database (tss_grc_saas)
│   │   ├── All tables include tenant_id column
│   │   ├── Row-Level Security (RLS) enforced at database level
│   │   └── Application-level tenant filter on all queries
│   │
│   ├── Option B: Shared Database, Separate Schema
│   │   ├── Single SQL Server cluster
│   │   ├── Single database
│   │   ├── Each tenant has its own schema (tenant_001.*, tenant_002.*)
│   │   └── Schema resolved dynamically per request
│   │
│   └── Option C: Separate Database per Tenant
│       ├── SQL Server cluster
│       ├── Each tenant has a dedicated database
│       ├── Database routing resolved per request
│       └── Higher isolation but more operational overhead
│
├── File Storage
│   ├── Cloud Object Storage (Blob / S3)
│   ├── Tenant-partitioned containers/buckets
│   └── Access controlled by tenant-scoped SAS tokens / presigned URLs
│
├── Shared Services
│   ├── Email Service (multi-tenant sender)
│   ├── PDF Generation Service (shared worker pool)
│   ├── Tenant Management Service
│   └── License Management Service
│
└── Operations
    ├── CI/CD Pipeline (single deployment pipeline for all tenants)
    ├── Centralized Monitoring (per-tenant metrics and dashboards)
    ├── Centralized Logging (tenant_id as a log field for filtering)
    └── Automated Backup (database-level, covering all tenants)
```

### 4.2 Architecture Considerations

| Aspect | Specification |
|--------|---------------|
| **Tenant Resolution** | Tenant identified by subdomain (`customer1.tssgrc.com`) or custom domain mapping. A `TenantResolver` filter extracts the tenant context at the beginning of each request. |
| **Tenant Context Propagation** | `TenantContext` stored in a `ThreadLocal` (or request-scoped bean). All service and repository layers access the tenant context for data filtering. |
| **Recommended Isolation** | Option A (shared database, shared schema with `tenant_id`) is recommended for initial SaaS launch. It minimizes operational complexity while providing sufficient isolation through RLS and application-level filtering. |
| **Upgrade Path** | Architecture supports migration from Option A to Option B or C for tenants requiring stronger isolation (e.g., government customers paying for dedicated resources). |

### 4.3 Data Isolation

| Aspect | Approach |
|--------|----------|
| **Primary Mechanism** | `tenant_id` column on all tenant-specific tables. Application-level Hibernate filters enforce tenant scoping on all queries. |
| **Defense in Depth** | SQL Server Row-Level Security (RLS) policies as a database-level safeguard independent of application code. |
| **Cross-Tenant Prevention** | Integration tests verify that no API endpoint, query, or report can return data from a different tenant. |
| **Tenant Admin** | Each tenant has independent admin users. TSS platform admin has cross-tenant access for support and operations. |
| **Data Export** | Tenants can export their complete data set for portability. Export includes all module data, configurations, and audit logs. |

### 4.4 Networking

| Aspect | Specification |
|--------|---------------|
| **Public Access** | Application accessible over the public internet via HTTPS. CDN provides edge caching for static assets. |
| **Custom Domains** | Tenants can configure custom domains (e.g., `grc.customer.com`) with TSS-managed SSL certificates (Let's Encrypt or similar). |
| **API Access** | API endpoints available at `api.tssgrc.com/{tenant}/v1/` or `{tenant}.tssgrc.com/api/v1/`. API rate limiting per tenant. |
| **Network Security** | WAF rules protect against common attacks. DDoS protection at the edge. No direct database access from the internet. |

### 4.5 Security

| Aspect | Specification |
|--------|---------------|
| **Authentication** | Per-tenant user directories. Each tenant's users are completely independent. |
| **SSO** | Per-tenant SSO configuration. Each tenant can configure their own SAML 2.0 or OAuth 2.0 identity provider. |
| **Encryption** | TLS 1.2+ for data in transit. Cloud provider encryption at rest. Optional customer-managed keys for Enterprise tier tenants. |
| **Compliance** | SOC 2 Type II certification for the SaaS platform. Regular penetration testing and security audits. |
| **Data Residency** | Primary deployment in a cloud region compliant with Saudi data residency requirements. |

### 4.6 Backup and Recovery

| Aspect | Specification |
|--------|---------------|
| **Database** | Automated daily backups with point-in-time restore (managed SQL Server). Backup covers all tenants. |
| **Per-Tenant Recovery** | Individual tenant data can be restored without affecting other tenants (requires data extraction from backup). |
| **File Storage** | Cloud object storage with geographic redundancy. Versioning enabled for document overwrites. |
| **DR** | Cross-region replication for business continuity. Automated failover for the database tier. |

### 4.7 Updates and Patching

| Aspect | Specification |
|--------|---------------|
| **Deployment** | All tenants run the same application version. Updates are deployed via rolling deployment with zero downtime. |
| **Database Migrations** | Flyway migrations run on the shared database. Migrations are backward-compatible to support rolling deployments. |
| **Tenant-Specific Config** | Tenant-specific configurations (branding, workflow, feature toggles) are stored in the database and unaffected by application deployments. |
| **Release Cadence** | Continuous deployment with feature flags. New features are deployed disabled, then enabled per-tenant or globally. |
| **Canary Releases** | New versions are rolled out to a subset of instances first. Monitoring validates stability before full rollout. |

### 4.8 Monitoring

| Aspect | Specification |
|--------|---------------|
| **Per-Tenant Metrics** | Application metrics are tagged with `tenant_id` for per-tenant performance tracking. |
| **Centralized Dashboard** | TSS operations dashboard shows aggregate and per-tenant health, performance, and usage. |
| **Anomaly Detection** | Automated alerts for: tenant-specific performance degradation, unusual usage patterns, error rate spikes. |
| **SLA Monitoring** | Uptime and response time tracked per SLA commitments. Automated SLA breach alerting. |

### 4.9 Cost Model

| Cost Component | Bearer |
|----------------|--------|
| Cloud infrastructure | TSS (amortized across tenants) |
| Platform operations | TSS operations team |
| TSS GRC application license (SaaS subscription) | Customer |
| Implementation and data migration | Customer (one-time) |
| Support | TSS (included in SaaS subscription) |

**SaaS Pricing Advantage:** Lower TCO for customers compared to on-premises. No hardware procurement, no IT operations overhead, automatic updates included.

### 4.10 Suitable Customer Profiles

- Small to mid-size organizations without dedicated IT infrastructure.
- Organizations prioritizing speed of deployment and low operational burden.
- Organizations comfortable with cloud-hosted data in a compliant region.
- Growing organizations that want to start small and scale without infrastructure changes.

---

## 5. Hybrid Deployment

### 5.1 Architecture

```
Customer Data Center (Sensitive Data)
├── SQL Server Database
│   └── All GRC module data, audit logs, documents
│       (data never leaves customer premises)
│
├── File Storage
│   └── Uploaded documents and generated reports
│
└── Connectivity
    └── Secure VPN / ExpressRoute / Direct Connect to cloud

TSS Cloud (Management Plane)
├── Application Servers
│   ├── GRC Application Instances
│   ├── Connected to customer DB via secure tunnel
│   └── No persistent storage of customer data
│
├── Supporting Services
│   ├── Email Notification Service
│   ├── Report Generation Service
│   ├── Application Monitoring
│   └── Update Management
│
└── Edge Services
    ├── CDN for static assets
    ├── WAF
    └── HTTPS termination
```

### 5.2 Architecture Considerations

| Aspect | Specification |
|--------|---------------|
| **Data Plane** | All persistent data (database, files) remains on customer premises. No customer data is stored in the cloud. |
| **Compute Plane** | Application logic runs in the cloud for ease of management and updates. The application connects to the on-premises database through a secure encrypted tunnel. |
| **Connection** | A site-to-site VPN or dedicated private connection (ExpressRoute, Direct Connect) provides secure, low-latency connectivity between the cloud application and on-premises database. |
| **Latency** | Database query latency is higher than co-located deployments (typically 5-20ms additional network latency). Application design accounts for this with eager fetching and batch queries. |
| **Fallback** | If the connection to the on-premises database is lost, the application shows an appropriate error state. Connection retry with exponential backoff. |

### 5.3 Data Isolation

| Aspect | Approach |
|--------|----------|
| **Data Residency** | All persistent data remains on customer premises, satisfying the strictest data residency requirements. |
| **In-Transit Data** | Application queries and responses are encrypted via the VPN tunnel. Additional application-level encryption for sensitive fields is optional. |
| **No Cloud Storage** | The cloud application instances are stateless. No customer data is cached or stored on cloud infrastructure beyond in-memory request processing. |
| **Session Data** | Session data stored in the on-premises database (Spring Session JDBC) to avoid cloud-side data persistence. |

### 5.4 Networking

| Aspect | Specification |
|--------|---------------|
| **VPN Tunnel** | IPsec VPN (minimum 100 Mbps) or private connection between customer premises and cloud environment. |
| **Bandwidth** | Minimum 50 Mbps dedicated bandwidth recommended. Higher for deployments with > 100 concurrent users. |
| **Latency** | Target < 30ms round-trip between cloud application and on-premises database. |
| **Redundancy** | Dual VPN tunnels or ExpressRoute with redundant circuits for high availability. |
| **Firewall** | Customer firewall allows outbound HTTPS to the cloud management plane. Inbound: only SQL Server port from the cloud application's IP range. |

### 5.5 Security

| Aspect | Specification |
|--------|---------------|
| **Network Encryption** | VPN provides network-level encryption. TLS on top for application-level encryption. |
| **Cloud Access** | Cloud application instances have minimal permissions: database read/write only. No access to customer's other on-premises systems. |
| **Credential Management** | Database credentials stored in cloud-side secrets management (Key Vault / Secrets Manager), accessible only by the application. |
| **Audit** | All audit logs are written to the on-premises database. Cloud application logs (access logs, error logs) are stored in the cloud but contain no customer data. |

### 5.6 Backup and Recovery

| Aspect | Specification |
|--------|---------------|
| **Database** | Customer manages database backups on-premises (same as on-premises model). |
| **Application** | Cloud application can be redeployed from CI/CD pipeline within minutes. No backup needed for stateless cloud instances. |
| **DR** | Database recovery is customer-managed. Application recovery is TSS-managed (cloud infrastructure recreation). |

### 5.7 Updates and Patching

| Aspect | Specification |
|--------|---------------|
| **Application** | TSS manages application updates in the cloud. Rolling deployments with zero downtime. Customer is notified of updates but no action is required on their part. |
| **Database** | Flyway migrations are applied to the on-premises database via the cloud application on startup. Customer approves major database changes during maintenance windows. |
| **OS/Infrastructure** | Cloud infrastructure patching is TSS-managed. On-premises database server patching is customer-managed. |

### 5.8 Monitoring

| Aspect | Specification |
|--------|---------------|
| **Application** | TSS monitors cloud application health, performance, and errors. |
| **Database** | Customer monitors on-premises database health. Application health check includes database connectivity status. |
| **Connectivity** | VPN tunnel health is monitored by both parties. Alerts on tunnel failure trigger automatic failover to secondary tunnel. |
| **Shared Dashboard** | Customer and TSS share a monitoring dashboard showing application and connectivity health. |

### 5.9 Cost Model

| Cost Component | Bearer |
|----------------|--------|
| On-premises hardware (database, storage) | Customer |
| SQL Server license | Customer |
| VPN / private connection | Shared (customer provides on-prem endpoint, TSS provides cloud endpoint) |
| Cloud application hosting | TSS |
| Application operations | TSS |
| TSS GRC license (hybrid subscription) | Customer |
| On-premises database administration | Customer |

### 5.10 Suitable Customer Profiles

- Organizations with strict data sovereignty mandates but limited IT operations capacity.
- Government entities that cannot host data outside their premises but want managed application delivery.
- Organizations seeking automatic application updates without on-premises deployment processes.
- Customers transitioning from on-premises to cloud gradually.

---

## 6. Architecture Decisions Supporting All Models

### 6.1 Externalized Configuration

| Decision | Details |
|----------|---------|
| **Spring Profiles** | Environment-specific configurations use Spring profiles: `onprem`, `private-cloud`, `saas`, `hybrid`. |
| **Configuration Sources** | Application reads configuration from (in priority order): environment variables, external `application-{profile}.yml`, embedded `application.yml`. |
| **Sensitive Config** | Database credentials, API keys, and encryption keys are never in property files. They are sourced from: environment variables (on-prem), cloud secrets manager (cloud), or Kubernetes secrets (container). |
| **Config Properties** | All configurable aspects have documented default values that work for on-premises single-instance deployment. |

### 6.2 Spring Profile Examples

| Profile | Purpose | Key Configurations |
|---------|---------|-------------------|
| `onprem` | On-premises single instance | Local file storage, embedded session store, local SMTP |
| `onprem-cluster` | On-premises clustered | Shared file storage (NFS), JDBC session store, distributed lock |
| `private-cloud` | Private cloud VM-based | Cloud storage adapter, managed DB connection, cloud SMTP |
| `private-cloud-k8s` | Private cloud container-based | Same as above + Kubernetes-specific health probes and config |
| `saas` | SaaS multi-tenant | Tenant resolver enabled, shared infrastructure, cloud services |
| `hybrid` | Hybrid deployment | Cloud app config + on-prem DB connection via VPN |

### 6.3 Abstracted Infrastructure Services

The application uses abstracted interfaces for infrastructure services, with implementations selected by Spring profile:

| Service Interface | On-Prem Implementation | Cloud Implementation |
|-------------------|----------------------|---------------------|
| `FileStorageService` | `LocalFileStorageService` (local/NFS path) | `CloudBlobStorageService` (Azure Blob / S3) |
| `EmailService` | `SmtpEmailService` (direct SMTP) | `CloudEmailService` (SES / Azure Communication) |
| `SessionStore` | `EmbeddedSessionStore` (in-memory, single instance) | `JdbcSessionStore` (database-backed, multi-instance) |
| `DistributedLock` | `NoOpDistributedLock` (single instance, no locking) | `JdbcDistributedLock` (ShedLock with SQL Server) |
| `HealthIndicator` | Checks: DB, disk space | Checks: DB, cloud storage, email service |
| `TenantResolver` | `SingleTenantResolver` (always returns default tenant) | `SubdomainTenantResolver` (extracts tenant from subdomain) |

### 6.4 Containerization Readiness

| Aspect | Specification |
|--------|---------------|
| **Dockerfile** | A multi-stage Dockerfile is maintained for building production container images. Base image: Eclipse Temurin 21 JRE Alpine. |
| **Image Size** | Target image size < 300MB (JRE + application JAR + fonts). |
| **Health Probes** | Kubernetes liveness probe: `/actuator/health/liveness`. Readiness probe: `/actuator/health/readiness`. Startup probe: `/actuator/health` with extended initial delay. |
| **Environment Variables** | All configuration can be injected via environment variables following the Spring relaxed binding convention. |
| **Statelessness** | Application instances are stateless (session externalized, files in shared storage). Any instance can handle any request. |
| **Graceful Shutdown** | `server.shutdown=graceful` configured. In-progress requests complete before shutdown (configurable timeout: 30 seconds). |
| **Resource Limits** | Container resource recommendations documented: CPU: 2 cores, Memory: 2GB heap + 512MB overhead per instance. |

### 6.5 Database Migration Strategy

| Aspect | Specification |
|--------|---------------|
| **Tool** | Flyway for database schema migrations. |
| **Migration Naming** | `V{version}__{description}.sql` (e.g., `V1.0.0__initial_schema.sql`). |
| **Backward Compatibility** | Migrations are forward-only and backward-compatible for rolling deployments. Column additions are nullable or have defaults. Column removals are deferred (deprecated first, removed in next major version). |
| **Multi-Tenant** | For SaaS, migrations run once on the shared database. Tenant-specific data initialization is handled separately. |
| **Execution** | Migrations run automatically on application startup (`spring.flyway.enabled=true`). Can be disabled for managed migration processes. |

### 6.6 Feature Toggle per Deployment

| Feature | On-Prem | Private Cloud | SaaS | Hybrid |
|---------|---------|--------------|------|--------|
| Multi-tenancy | Disabled | Disabled | Enabled | Disabled |
| Tenant subdomain routing | Disabled | Disabled | Enabled | Disabled |
| Cloud file storage | Disabled | Enabled | Enabled | Configurable |
| Local file storage | Enabled | Disabled | Disabled | Configurable |
| SSO (SAML/OAuth) | Optional | Optional | Per-tenant config | Optional |
| Auto-updates | Disabled | Optional | Enabled | Enabled (app only) |
| Usage telemetry | Opt-in | Opt-in | Enabled | Opt-in |

---

## 7. Deployment Comparison Matrix

| Dimension | On-Premises | Private Cloud | SaaS Multi-Tenant | Hybrid |
|-----------|------------|--------------|-------------------|--------|
| **Data Location** | Customer DC | Customer cloud account | TSS cloud | DB on-prem, app in cloud |
| **Data Control** | Full customer control | Customer control | TSS managed (customer owns data) | Customer controls data |
| **Infrastructure Mgmt** | Customer | Customer (or managed) | TSS | Shared |
| **Application Mgmt** | Customer applies updates | Customer applies updates | TSS (automatic) | TSS (automatic) |
| **Scalability** | Limited by hardware | Cloud elastic scaling | Auto-scaling | Limited by on-prem DB |
| **Time to Deploy** | Weeks | Days to weeks | Hours to days | Days to weeks |
| **TCO (3 year)** | Highest | High | Lowest | Medium |
| **Data Residency** | Full compliance | Regional compliance | Regional compliance | Full compliance |
| **Customization** | Full | Full | Tier-limited | Full (DB), limited (app) |
| **SLA** | Customer-managed | Customer-managed | TSS SLA (99.5%+) | Shared SLA |
| **Internet Required** | No | Depends | Yes | Yes (VPN) |
| **Ideal Org Size** | Large enterprise | Mid to large | Small to large | Large with data concerns |

---

## 8. Migration Paths Between Models

### 8.1 Supported Migrations

| From | To | Complexity | Data Migration | Downtime |
|------|-----|-----------|----------------|----------|
| On-Premises | Private Cloud | Medium | Database backup/restore to cloud SQL. File migration to cloud storage. | Planned window (4-8 hours) |
| On-Premises | SaaS | Medium | Data export from on-prem, import to SaaS tenant. Schema alignment if versions differ. | Planned cutover (1-2 days) |
| On-Premises | Hybrid | Low | Keep existing DB. Point cloud app to on-prem DB via VPN. | Minimal (VPN setup + app deployment) |
| Private Cloud | SaaS | Medium | Database export from private cloud, import to SaaS tenant. | Planned cutover (1 day) |
| SaaS | On-Premises | Medium | Full data export from SaaS. On-prem deployment setup. Database import. | Planned cutover (1-2 days) |
| Hybrid | On-Premises | Low | Deploy application on-prem instead of cloud. DB already on-prem. | Planned window (2-4 hours) |
| Hybrid | Private Cloud | Medium | Migrate DB to cloud. Reconfigure app to use cloud DB. | Planned window (4-8 hours) |

### 8.2 Data Portability Guarantee

- Customers can export their complete data set at any time via the admin data export function.
- Export includes: all module data, user configurations, audit logs, uploaded documents.
- Export formats: SQL Server backup (.bak) for database, ZIP archive for files, CSV for data tables.
- TSS provides migration tools and documentation for supported migration paths.
- Data portability is a contractual commitment regardless of deployment model.

---

## 9. Recommended Deployment Model Selection Guide

| Customer Need | Recommended Model | Rationale |
|---------------|-------------------|-----------|
| Strict data sovereignty, no cloud | On-Premises | Full data control, air-gap possible |
| Data sovereignty with managed app | Hybrid | Data stays on-prem, TSS manages app |
| Cloud-first, need isolation | Private Cloud | Dedicated resources in customer's cloud |
| Quick start, low TCO | SaaS Multi-Tenant | Fastest deployment, lowest operational cost |
| Government entity, Saudi hosting | Private Cloud (Saudi region) or On-Premises | Compliance with government data requirements |
| Large enterprise, existing DC | On-Premises or Hybrid | Leverage existing infrastructure investment |
| Growing startup/mid-size | SaaS Multi-Tenant | Scale without infrastructure planning |
| Multi-national, regional compliance | Private Cloud (per region) | Regional data residency with cloud operations |
