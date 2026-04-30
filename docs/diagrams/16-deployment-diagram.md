# Deployment Architecture Diagram

## Purpose
Shows the deployment topology for the TSS GRC Platform across on-premises and future cloud deployment models.

---

## On-Premises Deployment (Phase 1)

```mermaid
flowchart TB
    subgraph Users["Users (Browsers)"]
        U1[Internal Users\nChrome / Edge / Firefox]
        U2[Admin Users\nSame browsers]
    end

    subgraph Network["Network Layer"]
        FW[Firewall / WAF\nHTTPS 443 only]
        LB[Load Balancer\nOptional: Nginx or IIS ARR]
    end

    subgraph AppTier["Application Tier — Windows Server / Linux"]
        APP1[TSS GRC Application\nSpring Boot 3.5\nJava 17+\nPort 8443]
        APP2[TSS GRC Application\nSpring Boot 3.5\nStandby/Scale-out]
    end

    subgraph DataTier["Data Tier"]
        SQL[SQL Server 2019 Express\nPort 1433\nTSS_GRC Database\n≤10 GB]
        FS[File Storage\nLocal Filesystem or NAS\nAttachments / Evidence files]
    end

    subgraph Integration["Integration Points"]
        SMTP[SMTP Server\nEmail Notifications]
        LDAP[LDAP / Active Directory\nUser Authentication\nOptional]
    end

    U1 & U2 -->|HTTPS| FW
    FW -->|HTTPS| LB
    LB -->|HTTP internal| APP1
    LB -->|HTTP internal| APP2
    APP1 & APP2 -->|JDBC/TLS| SQL
    APP1 & APP2 -->|File I/O| FS
    APP1 & APP2 -->|SMTP| SMTP
    APP1 & APP2 -->|LDAP/S| LDAP
```

---

## Future SaaS Deployment (Phase 10+)

```mermaid
flowchart TB
    subgraph Internet
        U[Customer Users\nAll Browsers]
    end

    subgraph CloudEdge["Cloud Edge (Azure / AWS)"]
        CDN[CDN\nStatic assets]
        WAF[Cloud WAF\nDDoS + Bot protection]
        AGW[API Gateway\nRate limiting + SSL]
    end

    subgraph AppCluster["Application Cluster"]
        AS1[App Server 1\nSpring Boot]
        AS2[App Server 2\nSpring Boot]
        AS3[App Server N\nSpring Boot]
        SESSION[Session Store\nRedis / Azure Cache]
    end

    subgraph DataCluster["Data Cluster"]
        SQLP[SQL Server Primary\nAzure SQL / RDS SQL]
        SQLR[SQL Server Read Replica\nReporting queries]
        BLOB[Object Storage\nAzure Blob / S3\nAttachments]
    end

    U --> CDN & WAF
    WAF --> AGW
    AGW --> AS1 & AS2 & AS3
    AS1 & AS2 & AS3 --> SESSION
    AS1 & AS2 & AS3 -->|Write| SQLP
    AS1 & AS2 & AS3 -->|Read| SQLR
    AS1 & AS2 & AS3 --> BLOB
    SQLP -->|Replication| SQLR
```

---

## Component Health Monitoring

```mermaid
flowchart LR
    APP[Spring Boot App] --> ACT[/actuator/health]
    ACT --> DB_CHK[Database Connectivity Check]
    ACT --> MAIL_CHK[Mail Server Check]
    ACT --> FS_CHK[File Storage Check]
    ACT --> CACHE_CHK[Cache Health Check]
    LB2[Load Balancer] -->|Probes every 30s| ACT
```
