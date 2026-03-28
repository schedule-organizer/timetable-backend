# SchediFlow — Market Requirements Document (MRD)
**Version 1.0 | March 2026 | Confidential**

---

## Table of Contents

1. [Document Purpose](#1-document-purpose)
2. [Market Overview](#2-market-overview)
3. [Target Market & Segments](#3-target-market--segments)
4. [Market Problem](#4-market-problem)
5. [Customer Personas & Jobs To Be Done](#5-customer-personas--jobs-to-be-done)
6. [Market Size & Opportunity](#6-market-size--opportunity)
7. [Competitive Landscape](#7-competitive-landscape)
8. [Differentiation & Strategic Positioning](#8-differentiation--strategic-positioning)
9. [Business Model & Pricing](#9-business-model--pricing)
10. [Go-To-Market Strategy](#10-go-to-market-strategy)
11. [Market Requirements by Segment](#11-market-requirements-by-segment)
12. [Success Metrics](#12-success-metrics)
13. [Risks & Mitigations](#13-risks--mitigations)
14. [Open Questions](#14-open-questions)

---

## 1. Document Purpose

This Market Requirements Document (MRD) defines the market context, customer needs, competitive landscape, and strategic positioning for **SchediFlow** — a multi-tenant SaaS platform for automated school timetable management.

The MRD captures *why* the product should exist and *for whom*, providing the market foundation for the Product Requirements Document (PRD). Engineering and design teams should reference the PRD for functional specifications. This document is intended for founders, product leadership, sales, and investors.

> **Relationship to the PRD:** The MRD defines market needs. The PRD defines how the product meets those needs. Both documents should be read together.

---

## 2. Market Overview

School timetabling is a universal operational challenge. Every educational institution in the world — regardless of size, country, or academic model — must produce a timetable at least once per academic term. The process coordinates teachers, students, rooms, and subjects across hundreds of time slots, subject to dozens of constraints.

Despite this universal need, the tooling landscape remains fragmented:

- Most small and medium institutions still rely on **spreadsheets** or outdated desktop software
- Enterprise solutions exist but are **expensive, complex, and built for IT teams** rather than educators
- Modern cloud-native tools are scarce, especially ones that combine **automation with flexibility and approachability**
- No leading solution adequately addresses the growing need for **AI-assisted scheduling**

SchediFlow is positioned to become the default timetabling platform for the global education market — starting with small-to-medium institutions underserved by current offerings, and expanding upward into larger institutions through a scalable SaaS model.

---

## 3. Target Market & Segments

### 3.1 Primary Target: Small and Medium Educational Institutions

Our immediate market focus is institutions with 10–200 teaching staff. This segment is large, underserved, and actively looking for better tools.

| Segment | Description | Examples |
|---|---|---|
| **Primary Schools** | Ages 5–11, typically 10–30 teachers, 10–25 classes, 2 terms per year | State primaries, private prep schools, international primaries |
| **Secondary / Middle Schools** | Ages 11–16, 30–80 teachers, 20–60 classes, increasing elective complexity | Public high schools, independent schools, international schools |
| **High Schools / Senior Schools** | Ages 16–18, up to 100 teachers, heavy option block usage, A-level / IB / AP timetabling | Sixth forms, colleges, international baccalaureate schools |
| **Language Schools** | Rolling cohorts, small groups, flexible term structures | English language centres, private tutoring academies |
| **Vocational & Training Centres** | Practical-heavy scheduling, workshop rooms, flexible hours | Trade schools, TAFE institutions, professional training providers |

### 3.2 Secondary Target: Universities and Colleges

Universities represent a larger deal size but higher complexity and a longer sales cycle. SchediFlow's flexible Cycle and Teaching Group model supports university scheduling. This segment is targeted from Year 2 onward with dedicated features (lecture theatre management, course section scheduling, exam period management).

### 3.3 Tertiary Target: School Districts and Multi-Campus Groups

Groups of schools under centralized administration represent a significant enterprise opportunity. A district-level SchediFlow account that manages 10–50 schools through a shared Template Library and centralized reporting is a compelling enterprise product for Year 3.

### 3.4 Geographic Priority

| Priority | Markets | Rationale |
|---|---|---|
| **Tier 1** | United Kingdom, Ireland, Australia, New Zealand, Canada | English-language markets, mature SaaS adoption, established timetabling culture, high willingness to pay for cloud tools |
| **Tier 2** | United States | Large market but fragmented by state; strong competition from ERP-bundled solutions; approach via independent and charter school segment first |
| **Tier 3** | Western Europe (Germany, Netherlands, Scandinavia) | Sophisticated education systems, strong data privacy expectations (GDPR expertise required), moderate timetabling software awareness |
| **Tier 4** | Southeast Asia, India, Middle East | High growth in private education, large class sizes, price-sensitive, strong opportunity for a modern cloud tool with appropriate localization |

---

## 4. Market Problem

### 4.1 The Core Problem

School timetabling is genuinely hard. A medium-sized secondary school with 60 teachers, 40 classes, 8 periods per day, and dozens of constraint rules has a scheduling problem with billions of possible combinations. Finding one that satisfies all constraints — let alone an optimal one — requires either expert human judgment developed over years, or intelligent software.

The current reality for most institutions:

- **Weeks of manual work** by a single administrator each term
- **High error rates** — teacher double-bookings, rooms over-capacity, subject hour quotas missed
- **Knowledge silos** — one person holds the "scheduling brain" and transitions are risky
- **No teacher input** — teacher availability preferences are collected informally, frequently ignored or lost
- **No student visibility** — students learn their timetable from a printed sheet posted on a wall
- **Rigid tools** — existing software forces institutions to adapt their terminology and workflow to the tool

### 4.2 The Absence Management Gap

Beyond initial timetable creation, institutions struggle with the day-to-day:

- **Teacher absences** have no structured digital workflow — cover is assigned via phone calls or WhatsApp groups
- **Lesson swaps** between teachers are informal, untracked, and frequently forgotten
- **Exceptional weeks** (exam periods, events weeks, trips) require ad hoc manual changes with no rollback mechanism

### 4.3 The AI Opportunity

The next generation of timetabling tools will use AI to lower the barrier to entry further. An administrator should be able to describe their scheduling needs in plain language and have the system build the timetable. This represents both a product differentiator and a market expectation that is forming now.

---

## 5. Customer Personas & Jobs To Be Done

### 5.1 The Timetabler / Scheduling Coordinator

**Who:** A teacher, department head, deputy principal, or administrative staff member who has been given timetabling responsibility. Usually not a scheduling expert. Often learns on the job. May only build the timetable once or twice a year.

**Jobs to be done:**
- Build a conflict-free timetable that teachers will accept
- Handle teacher requests and preferences without constant rework
- Fix problems quickly when things change mid-term
- Reduce the personal stress of a complex annual task

**Pain points:**
- The process takes 2–4 weeks and feels like it should take days
- Every change breaks something else
- They receive complaints from teachers whose preferences weren't honored
- No tools to help them learn best practices

**What they need from SchediFlow:**
- A guided, structured process that doesn't require expert knowledge
- A generator that does the heavy lifting
- Clear explanations when something can't be scheduled
- Manual control when they know better than the algorithm

---

### 5.2 The Principal / Institution Head

**Who:** The school leader who is accountable for operational efficiency and staff satisfaction. Does not build the timetable personally but cares deeply about the outcome.

**Jobs to be done:**
- Ensure the institution runs smoothly operationally
- Manage teacher workload fairly and transparently
- Demonstrate to parents and governors that the school is well-organized
- Reduce dependence on a single scheduling expert

**Pain points:**
- Timetable quality directly affects teacher morale and retention
- When the timetabling expert leaves, institutional knowledge goes with them
- No visibility into whether the current timetable is actually optimal

**What they need from SchediFlow:**
- Trust that the tool produces high-quality, fair timetables
- Reporting and visibility into teacher workload and room utilization
- Reduced single-point-of-failure risk

---

### 5.3 The Teacher

**Who:** A teaching staff member who experiences the timetable rather than builds it.

**Jobs to be done:**
- Know their schedule reliably and in advance
- Have their preferences and constraints respected
- Handle unexpected absences and cover gracefully
- Swap lessons with colleagues when needed

**Pain points:**
- Timetable changes communicated via email or paper notices — easy to miss
- No formal process for requesting lesson swaps
- Cover assignments feel arbitrary and last-minute

**What they need from SchediFlow:**
- A clean personal timetable view accessible from any device
- A way to declare availability preferences that actually get considered
- A structured delegation request process
- Timely notifications of any changes affecting them

---

### 5.4 The Student

**Who:** An enrolled student who needs to know where to be and when.

**Jobs to be done:**
- Know their daily and weekly schedule
- Track which room to go to for each lesson
- Stay informed of any timetable changes

**Pain points:**
- Timetables distributed as printed paper — lost within a week
- No way to integrate school schedule with personal calendar
- Changes communicated inconsistently

**What they need from SchediFlow:**
- A personal timetable view accessible from their phone
- Push/email notifications for any changes
- A calendar export to integrate with their personal calendar app

---

### 5.5 The Parent / Guardian

**Who:** A parent who wants visibility into their child's education schedule.

**Jobs to be done:**
- Know their child's schedule to plan family logistics
- Be informed of unexpected changes affecting their child

**Pain points:**
- No reliable access to the current timetable
- Changes discovered after the fact

**What they need from SchediFlow:**
- Read-only access to their child's timetable
- Notification of changes affecting their child

---

## 6. Market Size & Opportunity

### 6.1 Total Addressable Market (TAM)

There are approximately **1.5 million educational institutions** globally that require formal timetabling (excluding micro-schools and single-teacher operations). This includes primary schools, secondary schools, high schools, vocational centres, language schools, and universities.

At an average SaaS subscription of **$800–$3,000 per institution per year** (depending on size and tier), the global TAM is in the range of **$1.5B–$4.5B annually**.

### 6.2 Serviceable Addressable Market (SAM)

SchediFlow's initial serviceable market focuses on:
- English-speaking markets (UK, Australia, Ireland, New Zealand, Canada, USA)
- Institutions with 10–200 teaching staff
- Estimated **250,000 institutions** in this cohort

At an average revenue of **$1,200 per institution per year**, the SAM is approximately **$300M annually**.

### 6.3 Serviceable Obtainable Market (SOM) — 3-Year Target

A realistic 3-year target for SchediFlow is to capture **0.3–0.5% of the SAM**, representing **750–1,250 paying institutions** and approximately **$1M–$1.5M ARR** by end of Year 3.

> This is a conservative target. The education SaaS market rewards strong word-of-mouth — a single satisfied principal often influences 3–5 peer institutions. Community-driven growth is a realistic multiplier.

---

## 7. Competitive Landscape

### 7.1 Competitive Categories

| Category | Description | Typical Price |
|---|---|---|
| **Spreadsheets (Excel/Google Sheets)** | Free, flexible, universal — but require complete manual effort and offer no constraint engine | Free |
| **Legacy desktop software** | Established tools used by experienced timetablers. Windows-only, not cloud-native, dated UX. Strong in UK and European markets. | $300–$1,500/year |
| **Modern cloud timetabling tools** | Browser-based, more accessible UX, auto-generation. Growing category. | $500–$3,000/year |
| **ERP/MIS modules** | Timetabling module bundled into a School Information System (SIS). High switching cost, often underpowered as a standalone tool. | Bundled — hard to separate |
| **University-specific tools** | Complex, expensive, IT-managed platforms built for large institutions. Not appropriate for K-12. | $10,000–$100,000+/year |

### 7.2 Competitive Gaps SchediFlow Addresses

| Gap | How SchediFlow Addresses It |
|---|---|
| Steep learning curve for new timetablers | Setup Templates and guided onboarding wizard |
| Rigid terminology that doesn't match the institution | Fully configurable labels and domain values |
| No structured absence/cover workflow | Cover, Delegation, and Temporary Schedule as first-class features |
| No AI assistance | AI Scheduling Assistant in Phase 2 |
| No profile self-management for teachers | Teacher self-registration and profile management |
| Hardcoded scheduling structures | Configurable Cycle, Bell Schedule, Term structure |
| Holiday calendars managed manually | Public holiday API integration |
| Poor mobile experience | Responsive web-first with calendar export for personal devices |

### 7.3 Competitive Positioning Matrix

|  | Traditional Desktop Tools | Basic Cloud Tools | SchediFlow |
|---|---|---|---|
| Cloud-native | ❌ | ✅ | ✅ |
| Configurable terminology | ❌ | ⚠️ Partial | ✅ |
| Auto-scheduling engine | ✅ | ✅ | ✅ |
| Sensitivity control | ❌ | ⚠️ Limited | ✅ |
| Cover / Delegation workflow | ❌ | ❌ | ✅ |
| Temporary schedules | ❌ | ❌ | ✅ |
| Teacher self-service | ❌ | ⚠️ Limited | ✅ |
| Holiday calendar integration | ❌ | ⚠️ Manual | ✅ |
| AI Scheduling Assistant | ❌ | ❌ | ✅ (Phase 2) |
| Multi-tenant SaaS | ❌ | ✅ | ✅ |
| Price for SMB institutions | $$$ | $$ | $$ |

---

## 8. Differentiation & Strategic Positioning

### 8.1 Positioning Statement

> **For educational institutions that are frustrated by the complexity and time cost of manual timetabling, SchediFlow is the intelligent timetable management platform that adapts to how your institution works — not the other way around. Unlike existing tools that require institutions to conform to rigid structures, SchediFlow is fully configurable, guided by smart automation, and built for the entire school community — not just the scheduling expert.**

### 8.2 Core Differentiators

**1. Full Configurability**
Every domain in SchediFlow — terminology, difficulty scales, room types, Bell Schedule, Term structure — is configurable per institution. Competitors hardcode assumptions about how schools are structured. SchediFlow does not.

**2. Generator Sensitivity Control**
A unique runtime dial that lets timetablers progressively relax soft constraints when a complex configuration cannot produce a valid schedule. This reduces the frustration of the engine "failing" and replaces it with a transparent, guided process.

**3. Structured Absence Workflows**
Cover, Delegation, and Temporary Schedules are not afterthoughts — they are first-class features with full approval workflows, notifications, and audit trails. This addresses the daily operational reality of running a school, not just the once-per-term scheduling event.

**4. Community-Wide Access**
SchediFlow is not a tool for administrators alone. Teachers manage their profiles and availability. Students see their personal timetable. Parents have read-only visibility. This makes SchediFlow the timetabling layer for the entire institution, increasing stickiness.

**5. AI Scheduling Assistant (Phase 2)**
Natural language scheduling assistance will be a significant market differentiator as AI capabilities become an expected feature in education software. SchediFlow's architecture is designed from the start to enable this.

**6. Template Library**
Pre-built configuration templates for common institution types reduce time-to-first-schedule from days to hours for new users — removing a major barrier to adoption.

### 8.3 Brand Principles

- **Approachable** — any administrator, regardless of experience, can build a timetable
- **Trustworthy** — the engine is transparent, not a black box
- **Adaptable** — the platform fits the institution, not the reverse
- **Community-first** — built for everyone in the school, not just the scheduler

---

## 9. Business Model & Pricing

### 9.1 Revenue Model

SchediFlow operates on an annual subscription SaaS model, billed per institution. Pricing scales with institution size (number of teaching staff and classes), not per-user seat — this aligns incentive with value and simplifies procurement for schools.

### 9.2 Tier Structure

| Tier | Target | Key Limits | Price Range (Annual) |
|---|---|---|---|
| **Free Trial** | All new institutions | 30 days, full features | Free |
| **Starter** | Small schools (primary, language schools) | 20 classes, 30 teachers, 2 terms | $400–$800/year |
| **Professional** | Medium schools | 100 classes, 200 teachers, unlimited terms | $1,200–$2,400/year |
| **Enterprise** | Large schools, universities, multi-campus | Unlimited, API access, SSO, SLA | Custom / $4,000+/year |

> **Note:** Pricing should be validated with target market interviews before launch. The education market is price-sensitive in some segments and value-driven in others. Consider regional pricing for Tier 4 markets.

### 9.3 Additional Revenue Streams (Post-MVP)

- **Professional Services** — onboarding support and configuration setup for Enterprise customers
- **Template Marketplace** — third-party timetablers selling Custom Templates (revenue share model)
- **Training & Certification** — online training for school timetablers (low-cost, high-margin)
- **API Access** — Enterprise tier add-on for SIS/LMS integration projects

### 9.4 Unit Economics Targets

| Metric | Target |
|---|---|
| Average Revenue Per Institution (ARPI) | $1,200/year |
| Gross Margin | >80% |
| Customer Acquisition Cost (CAC) | <$400 |
| Payback Period | <6 months |
| Net Revenue Retention (NRR) | >110% |
| Churn Rate (Annual) | <10% |

---

## 10. Go-To-Market Strategy

### 10.1 Phase 1 GTM — Tier 1 Markets, SMB Focus

**Target:** Primary and secondary schools in the UK, Australia, and Ireland with 15–80 teaching staff.

**Channel Priority:**

1. **Inbound / SEO** — content marketing targeting timetabling pain points: "how to build a school timetable", "timetabling software for small schools", "school scheduling conflicts". Blog content from practitioner perspective (written by or in collaboration with experienced timetablers).

2. **Free Trial with Low Friction** — self-serve registration, immediate access, guided wizard. No credit card required. The product must demonstrate value before the trial ends.

3. **Community & Word of Mouth** — education is a relationship-driven sector. Principals talk to each other. Investing in customer success for the first 50 paying schools creates a referral engine. Offer referral incentives.

4. **Educational Conferences & Associations** — presence at national school administrator conferences (NAHT UK, APPA Australia, etc.). Demo-driven, not booth-driven.

5. **Partnership with School Information Systems (SIS)** — integration partnerships with regional SIS providers can provide distribution. Schools that already use an SIS will adopt a complementary scheduling tool more readily.

### 10.2 Phase 2 GTM — Expand to Universities and Districts

- Dedicated sales motion for university segment (longer cycle, higher ACV)
- District licensing model for multi-school groups
- Regional reseller partnerships in Tier 2 and Tier 3 markets
- Launch AI Scheduling Assistant as a marketing event — product-led growth moment

### 10.3 Retention Strategy

- **Seasonal engagement** — reach out at the start of each term planning cycle with feature highlights and configuration tips
- **Annual timetabling review** — offer a free configuration health check for Professional and Enterprise customers
- **Community forum** — timetablers sharing configurations, tips, and templates builds a switching cost through knowledge investment
- **In-product onboarding milestones** — celebrate first published Timetable, first generator run, etc.

---

## 11. Market Requirements by Segment

The following requirements are derived from market research and reflect what each customer segment values most, independent of specific feature implementation. These inform feature prioritization in the PRD.

### 11.1 Primary Schools

| Priority | Market Requirement |
|---|---|
| Must Have | Simple, guided setup that doesn't require prior timetabling experience |
| Must Have | Automatic conflict detection and resolution |
| Must Have | PDF export for printing and posting |
| Should Have | Holiday calendar auto-import |
| Should Have | Teacher personal timetable access |
| Nice to Have | Parent visibility of child's schedule |

### 11.2 Secondary / High Schools

| Priority | Market Requirement |
|---|---|
| Must Have | Ability set / Teaching Group management per Subject |
| Must Have | Option Block management for elective subjects |
| Must Have | Teacher availability and Forbidden Slot management |
| Must Have | Cover and Delegation workflows for daily operations |
| Must Have | Notification system for teachers and students |
| Should Have | Multi-subject teacher qualifications |
| Should Have | Temporary Schedule for exceptional weeks |
| Should Have | Calendar export for students and teachers |
| Nice to Have | Difficulty-aware scheduling optimization |

### 11.3 Language Schools & Training Centres

| Priority | Market Requirement |
|---|---|
| Must Have | Flexible term dates — rolling and overlapping terms |
| Must Have | Small group Teaching Group support |
| Must Have | Fully configurable Bell Schedule and Cycle |
| Should Have | Custom terminology (may not use 'Class' or 'Period') |
| Should Have | Multi-level teacher assignments (same teacher, different proficiency groups) |

### 11.4 Universities / Colleges

| Priority | Market Requirement |
|---|---|
| Must Have | Course section and lecture group management at scale |
| Must Have | Large venue / auditorium room type support |
| Must Have | Flexible Cycle (block scheduling, semester-based) |
| Should Have | Student self-enrollment into course sections |
| Should Have | Exam period scheduling |
| Nice to Have | Integration with student registration systems |

---

## 12. Success Metrics

### 12.1 Product-Market Fit Indicators

| Signal | Target |
|---|---|
| Free trial → paid conversion rate | >25% |
| Time to first published Timetable | <2 hours (median) |
| Generator acceptance rate (schedule accepted without full manual redo) | >85% |
| NPS from Timetablers / Admins | >45 |
| NPS from Teachers | >35 |
| 30-day activation rate (completed first Timetable) | >60% of trials |

### 12.2 Business Metrics — Year 1

| Metric | Target |
|---|---|
| Paying institutions | 100+ |
| ARR | $100,000+ |
| Churn rate | <15% |
| Gross margin | >75% |
| CAC | <$500 |

### 12.3 Business Metrics — Year 3

| Metric | Target |
|---|---|
| Paying institutions | 1,000+ |
| ARR | $1.2M+ |
| Churn rate | <10% |
| NRR | >110% |
| Markets active | 5+ countries |

---

## 13. Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Scheduling engine fails to produce valid schedules for complex institutions | Medium | High | Generator Sensitivity dial; clear user feedback; partial regeneration mode |
| Price sensitivity in target markets prevents conversion | Medium | High | Generous free trial; Starter tier at low price point; demonstrate clear time savings in onboarding |
| Data privacy concerns (GDPR, student data) delay adoption | Medium | Medium | GDPR compliance from day 1; data residency options; clear privacy policy |
| A well-funded competitor launches a similar product | Low | High | Speed to market; community and word-of-mouth moat; Template Library as defensible library asset |
| Timetabling is "solved" by an existing SIS for the target customer | Medium | Medium | Focus on institutions where SIS timetabling module is absent or inadequate; integration partnership rather than competition |
| AI Scheduling Assistant (Phase 2) does not meet quality bar | Low | Medium | Phase 2 is explicitly decoupled from MVP; AI depends on stable MVP foundation; internal testing before release |
| High implementation complexity causes churn after trial | Medium | High | Onboarding wizard; Setup Templates; dedicated customer success for first 50 customers |
| Single-person scheduling knowledge silos at customer institutions | Low | Low | This is a pain point we solve, not create; multi-moderator support by design |

---

## 14. Open Questions

1. **Regional pricing** — Should Tier 4 markets (Southeast Asia, India) be offered regional pricing from launch, or at a fixed global price with a later regional adjustment?

2. **Free tier** — Should SchediFlow offer a permanent free tier (e.g., for micro-schools with up to 5 teachers) as a market seeding strategy, or only a time-limited trial?

3. **SIS integration priority** — Which student information systems should be prioritized for data import partnerships? Regional variation is significant.

4. **Certification / training product** — Is a paid training and certification program for timetablers a viable companion product from Year 1, or is it a distraction?

5. **District licensing model** — Should a district-tier product be designed into the platform from the start, or retrofitted in Year 3? Data model implications need early resolution.

6. **AI timing** — As AI scheduling tools emerge rapidly, is Phase 2 timeline (post-stable MVP) still appropriate, or does competitive pressure require accelerating the AI layer?

7. **Freemium vs. trial-only** — Education markets have strong freemium expectations (Google Workspace, etc.). Should SchediFlow offer a permanent free tier for very small schools as a customer acquisition strategy?

---

## Document Information

| Field | Value |
|---|---|
| Document Type | Market Requirements Document (MRD) |
| Product | SchediFlow |
| Version | 1.0 |
| Date | March 2026 |
| Status | Draft |
| Related Documents | SchediFlow PRD v3.0 |

---

*This document is confidential and intended for internal product, leadership, and investor use only.*
