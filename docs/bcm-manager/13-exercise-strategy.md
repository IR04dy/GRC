# 13 · Exercise Strategy & Scenario Library
**Document owner:** BCM Manager + Exercise Lead · **Backing entity:** `bcm.bcm_exercises`, `bcm.exercise_scenarios`

---

## 1 · Annual exercise programme

| Quarter | Exercise type | Target |
|---|---|---|
| Q1 | Tabletop × 3 (covering 3 TIER_1 processes) | Test plan currency |
| Q1 | Comms-tree test (monthly) | Verify L1+L2 reachable |
| Q2 | Walkthrough × 2 (covering 2 BCPs) | Validate procedure steps |
| Q2 | Functional × 1 (DR failover drill — non-prod system) | Test technical procedures |
| Q3 | SAMA Major-Incident 6h drill | Verify regulator-notification SLA |
| Q3 | Tabletop × 3 | Cover remaining TIER_1 |
| Q4 | **Full-scale** annual (rotates across TIER_1 processes) | End-to-end validation |
| Q4 | Vendor BCP attestation review meeting | Vendor coverage |

**Minimum coverage rule**: every TIER_1 BCP exercised in some form at least annually; every TIER_1 plan in full-scale exercise at least every 2 years.

## 2 · Exercise types

| Type | Effort | Disruption | When to use |
|---|---|---|---|
| **Discussion-based / tabletop** | Low | None | New plan, training, refresh |
| **Walkthrough** | Low-Mid | None | Plan-step validation |
| **Functional** | Mid | Some (subsystem isolated) | Recovery-procedure test |
| **Full-scale** | High | Significant (real activation) | Annual validation |
| **Simulation** | High | None (simulated) | Cyber scenarios, multi-event |
| **Inject-driven live exercise** | High | Mid | Complex scenarios with timing |

## 3 · Scenario library (target: 50+)

### 3.1 Cyber scenarios
1. Ransomware on retail-banking endpoint — TIER_1 affected
2. SADAD payment-switch DDoS — peak Hajj
3. T24 admin-console unauthorised access
4. F5 BIG-IP CVE active exploit
5. AWS me-south-1 regional outage
6. Insider exfiltration via cloud storage
7. SS7 attack on OTP delivery
8. SIM-swap fraud cluster
9. Supply-chain attack — npm package in CI/CD
10. Ransomware via vendor token compromise

### 3.2 Physical scenarios
11. Eastern Province sandstorm — branches + DC cooling
12. Riyadh extreme-heat day — branch power + cooling
13. HQ fire + smoke evacuation
14. Construction-induced power outage at DC
15. Coastal flooding — Eastern Province branch
16. Mass-gathering disruption during Hajj
17. Earthquake (low probability, high impact)

### 3.3 Technology scenarios
18. Core T24 database corruption
19. Storage subsystem failure
20. Network backbone outage
21. Identity service (AD) unavailability
22. Card-management system (Way4) outage
23. Treasury (Murex) outage during settlement
24. SADAD direct-debit batch failure
25. ATM switch outage — cardless cash impact

### 3.4 Vendor / 3rd-party scenarios
26. Critical SaaS vendor outage (Salesforce / ServiceNow)
27. SOC vendor (Crowdstrike) service interruption
28. Telco backbone (STC) sustained outage
29. Microsoft 365 EX5 regional outage
30. AWS Bahrain extended outage
31. Vendor cyber breach impacting our data
32. Vendor bankruptcy with no notice

### 3.5 People scenarios
33. Pandemic absenteeism > 30%
34. Key-person departure (CISO + 3 senior cyber engineers)
35. Strike or labour action at vendor
36. WFH surge — VPN capacity stress

### 3.6 Regulatory scenarios
37. SAMA major-incident report missed by 1h — regulator action
38. SDAIA PDPL Art. 20 breach — 72h notification challenge
39. NRC sector-wide cybersecurity alert — cross-bank coordination
40. CMA Art. 41 disclosure dispute
41. Cross-border data transfer restriction change

### 3.7 Climate / ESG scenarios
42. Extreme heat — branch + DC cooling failure
43. Sandstorm — IT + OT impact
44. Sea-level + storm — coastal Eastern Province
45. Carbon-price shock + Scope-3 emission impact

### 3.8 Multi-event / cascading scenarios
46. Ransomware + parallel DDoS (combined cyber + DDoS)
47. Major operational incident + pandemic absenteeism
48. Cyber + critical-vendor outage same day
49. Hajj peak + payment-switch failure + customer-comms strain
50. Full-stack: DC outage + DR site impacted + vendor support delay

## 4 · Exercise design checklist

For each exercise:
- [ ] Objectives written (test what specifically?)
- [ ] Scope defined (which processes, which systems)
- [ ] Scenario chosen from library
- [ ] Injects timeline (for live exercises)
- [ ] Observer team designated
- [ ] Success criteria (measurable, e.g. "RTO target met within 110%")
- [ ] After-action timing (within 48h of exercise)
- [ ] Attendees confirmed
- [ ] Approval to disrupt (for functional / full-scale)

## 5 · Observer dashboard requirements (Slice 10-C future)
Observers need live view of:
- Decisions made + by whom
- Comms sent (audience + channel)
- Time-to-decision metrics
- Procedure step completion %
- Deviation from plan

## 6 · Post-exercise

- AAR within 48h
- Lessons captured (`bcm.lessons_learned`)
- CAPA opened for gaps (`bcm.corrective_actions`)
- Plan updates triggered
- Board pack section drafted for next quarterly RC

## 7 · Exercise measurement

| Metric | Target |
|---|---|
| % of plans exercised in year | 100% TIER_1, 80% TIER_2 |
| Average exercise score | > 80% |
| Lessons → CAPA conversion | > 95% |
| CAPA closure within target date | > 85% |
| Improvement vs prior year | year-over-year delta on score |
