# Platform Diagrams

## Current-state pressure map

```mermaid
flowchart TB
    UI["React SPA<br/>300+ routes"] --> API["Spring Boot monolith<br/>58 source modules"]
    API --> SQL["SQL Server<br/>many schemas"]
    API --> FILES["Evidence/files"]
    API --> EXT["External systems"]

    P["platform"] <--> R["risk"]
    P <--> B["bcm"]
    P <--> I["iam"]
    C["compliance"] <--> PO["policy"]
    T["tprm"] <--> S["scm"]
    T <--> PR["privacy"]
    E["evidence"] <--> P
```

The issue is not one deployable. It is bidirectional ownership and direct access.

## Target commercial composition

```mermaid
flowchart TB
    K["Platform Kernel"] --> M["Package Registry"]
    M --> ERM["ERM"]
    M --> CMP["Compliance"]
    M --> PDP["Privacy"]
    M --> BCM["BCM"]
    M --> TPR["TPRM"]
    M --> CYB["Cyber"]
    M --> GOV["Governance"]
    M --> ESG["ESG"]
    CP["Regulatory content packs"] --> CMP
    CP --> PDP
    CP --> CYB
    CP --> BCM
    ERM & CMP & PDP & BCM & TPR & CYB & GOV & ESG --> G["Evidence graph / projections"]
```

## Material decision sequence

```mermaid
sequenceDiagram
    participant U as Requester
    participant D as Domain package
    participant A as Authority service
    participant G as Governance
    participant E as Evidence
    participant L as Audit ledger

    U->>D: Submit command
    D->>A: Resolve authenticated authority and SoD
    A-->>D: Eligible authority / denial
    D->>E: Validate required evidence versions
    D->>G: Create decision request
    G->>A: Revalidate member/role at decision time
    G->>G: Record immutable decision
    G->>L: Append actor, authority, object version, rationale
    G-->>D: decision.approved.v1
    D->>D: Apply state transition
```

## Evidence lineage

```mermaid
flowchart LR
    O["Obligation"] --> C["Common control"]
    C --> T["Control test"]
    T --> V["Evidence version + hash"]
    T --> I["Issue"]
    I --> A["Corrective action"]
    A --> VT["Validation test"]
    VT --> C
    C --> R["Risk"]
    C --> P["Posture projection"]
    P --> X["Executive metric"]
```

## Package lifecycle

```mermaid
stateDiagram-v2
    [*] --> Available
    Available --> DependencyCheck: select
    DependencyCheck --> Enabled: dependencies valid
    DependencyCheck --> Available: reject
    Enabled --> DisablePending: request disable
    DisablePending --> ReadOnly: jobs/integrations stopped
    ReadOnly --> Enabled: reactivate
    ReadOnly --> RemovalPreflight: request removal
    RemovalPreflight --> ReadOnly: holds/dependencies fail
    RemovalPreflight --> Removed: export and retention accepted
    Removed --> [*]
```

## UX shell

```mermaid
flowchart LR
    LOGIN["Identity"] --> SHELL{"Persona shell"}
    SHELL --> WORK["My Work"]
    SHELL --> ASSURE["Assurance"]
    SHELL --> DOMAIN["Installed workspace"]
    SHELL --> EXEC["Executive"]
    WORK --> OBJECT["Object workspace"]
    ASSURE --> OBJECT
    DOMAIN --> OBJECT
    OBJECT --> DECIDE["Guided decision"]
    OBJECT --> EVID["Evidence lineage"]
```

