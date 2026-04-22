---
title: Phase 7 Epic Kickoff Summary
subtitle: Call Activity & Subprocess Support
date: 2026-04-23
audience: Engineering Team, Product, Stakeholders
duration: 30 minutes
---

# 🚀 PHASE 7 KICKOFF - CALL ACTIVITY SUPPORT

## Mission (30 seconds)

**What**: Enable process definitions to invoke child subprocesses with variable mapping  
**Why**: Unlock process composition and reduce workflow duplication  
**When**: 4 weeks (Target June 30, 2026)  
**Who**: 5 team members across Backend, Frontend, QA, Tech Writing  
**Effort**: 40 story points, 1 full-time equivalent

---

## Business Value (1 minute)

### Current Limitation
Today, complex business processes must be implemented as monolithic BPMN definitions, leading to:
- ❌ Duplication of common subprocess logic
- ❌ Difficult to maintain and update
- ❌ Large process definitions (hard to understand)
- ❌ Testing challenges for reusable patterns

### After Phase 7
- ✅ Reusable subprocess definitions
- ✅ Order Processing → Validation → Payment → Shipping
- ✅ Cleaner process definitions
- ✅ Reduced maintenance burden
- ✅ Easier to understand and test

**Customer Impact**: Support for complex, real-world business processes

---

## High-Level Architecture (2 minutes)

### Core Concept: Call Activity Node

```
Parent Process
├─ Start
├─ Call Activity Node (target=PaymentSubprocess)
│  ├─ Input Map: orderId → order_id
│  ├─ Output Map: paymentStatus ← status
│  └─ Error Boundary
├─ End
```

### Database Changes
New columns on `process_instance`:
- `parent_instance_id` - Link to parent process
- `call_activity_node_id` - Node that triggered this instance
- `nesting_level` - Depth in hierarchy
- `completion_node_id` - Where to resume after child

New table: `call_activity_mapping` - Track input/output variable mappings

### Variable Flow
```
Parent Variables (orderId=123)
  ↓ Input Mapping
Child Variables (order_id=123)
  ↓ [Child Process Executes]
Child Variables (status=APPROVED)
  ↓ Output Mapping
Parent Variables (paymentStatus=APPROVED)
```

---

## Team Assignments (2 minutes)

| Role | Name | Effort | Start | Sprint |
|------|------|--------|-------|--------|
| **Backend** | [TBD] | 16sp | Week 1 | S1-3 |
| **Frontend (Modeler)** | [TBD] | 8sp | Week 2 | S2 |
| **Frontend (Admin)** | [TBD] | 6sp | Week 3 | S3 |
| **QA** | [TBD] | 6sp | Week 1 | S1-3 |
| **Tech Writer** | [TBD] | 4sp | Week 3 | S3-4 |
| **Code Review** | [TBD] | Continuous | Week 1 | S1-4 |

**Total Team Capacity**: ~15% each (leaves 85% for other work)

---

## Sprint Breakdown (1 minute)

### Sprint 1: Backend Core (Weeks 1-2)
**Backend Owner Focused**
- [ ] Database V17 migration
- [ ] CallActivityHandler service
- [ ] Variable mapping engine
- [ ] Instance lifecycle management
- **Goal**: Core feature functional, basic tests passing

### Sprint 2: Modeler UI (Weeks 2-3)
**Frontend Owner Focused**
- [ ] Call Activity palette item
- [ ] Properties panel with variable mapping UI
- [ ] Canvas rendering updates
- [ ] Process key validation
- **Goal**: Design and deploy call activities in modeler

### Sprint 3: Admin UI + Testing (Weeks 3-4)
**Frontend + QA + Tech Writer Focused**
- [ ] Hierarchy visualization
- [ ] Child instance inspection
- [ ] Comprehensive test execution
- [ ] Documentation complete
- **Goal**: End-to-end feature complete, 95%+ tests

### Sprint 4: Polish (Optional)
**All Teams**
- [ ] Bug fixes
- [ ] Performance tuning
- [ ] Production readiness
- **Goal**: Ready to ship

---

## Key Deliverables (1 minute)

### Code
- ✅ CallActivityHandler.kt
- ✅ VariableMappingService.kt
- ✅ V17 Flyway migration
- ✅ Modeler Call Activity UI
- ✅ Admin hierarchy views
- ✅ 40+ test cases

### Documentation
- ✅ Call Activity Guide
- ✅ Variable Mapping Tutorial
- ✅ Error Handling & Compensation
- ✅ Real-World Examples
- ✅ API Reference Updates

### Outcomes
- ✅ 95%+ test coverage
- ✅ Zero TypeScript errors
- ✅ Performance < 500ms per invocation
- ✅ Production ready

---

## Critical Success Factors (1 minute)

### Must Have
1. **Circular Reference Detection** - Prevent A → B → A loops
2. **Performance** - Child creation < 500ms
3. **Error Isolation** - Child errors don't crash parent
4. **Test Coverage** - 95%+ of code covered
5. **Database Safety** - Migration works on all versions

### Nice to Have
- Timeout handling at any nesting level
- Performance metrics dashboard
- Advanced variable transformations
- Process hierarchy visualization

---

## Risks & Mitigation (1 minute)

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|-----------|
| Circular references escape | HIGH | MEDIUM | Unit tests + regex check |
| Performance degradation | HIGH | LOW | Benchmark sprint 3 |
| Database migration failure | CRITICAL | LOW | Test on all versions |
| Variable mapping bugs | MEDIUM | MEDIUM | Comprehensive tests |
| Scope creep | HIGH | MEDIUM | Strict definition of done |

---

## Timeline & Milestones (1 minute)

```
Week 1 (Apr 23-29)
├─ Sprint 1 Kickoff (Tue 4/23)
├─ Backend: Database + Core Handler
├─ QA: Test plan definition
└─ Milestone: Core feature functional

Week 2-3 (Apr 30-May 13)
├─ Sprint 2 Kickoff (Tue 4/30)
├─ Frontend: Modeler UI
├─ Backend: Testing & error handling
└─ Milestone: Design & deploy call activities

Week 3-4 (May 14-27)
├─ Sprint 3 Kickoff (Tue 5/14)
├─ Frontend: Admin UI
├─ QA: Comprehensive testing
├─ Tech Writer: Documentation
└─ Milestone: Feature complete

Week 4 (May 28-Jun 3)
├─ Sprint 4 (Optional)
├─ Polish & optimization
└─ Milestone: Production ready 🎉
```

**Target Ship Date**: June 30, 2026

---

## Communication Plan (1 minute)

### Daily
- **Standup**: 09:00 UTC (15 min)
- **Slack**: #epic-call-001 channel
- **Status**: Async updates

### Weekly
- **Sprint Planning**: Mondays 10:00 UTC (1 hour)
- **Sprint Review**: Fridays 14:00 UTC (1 hour)
- **Stakeholder Update**: Fridays 15:00 UTC (30 min)

### Per-Sprint
- **Code Review**: Within 24 hours
- **Test Results**: Daily in #epic-call-001
- **Blocker Escalation**: ASAP to CTO

---

## Documents Reference (1 minute)

| Document | Purpose | Audience |
|----------|---------|----------|
| [EPIC-call-activity-subprocess-support.md](../EPIC-call-activity-subprocess-support.md) | Full epic specification | All teams |
| [task-delegation-call-activity.md](../.agents/task-delegation-call-activity.md) | Task assignments by role | Assigned team members |
| [API Reference](../docs-site-working/docs/api-controllers.md) | Backend API contracts | Integrators |
| [Modeler Guide](../docs-site-working/docs/easy-modeler-call-activity.md) | How to use modeler | End users |

---

## Next Actions (1 minute)

### Today (After Kickoff)
- [ ] Team members confirm task assignments
- [ ] Review task-delegation document thoroughly
- [ ] Ask clarifying questions

### This Week (By Friday)
- [ ] Setup project board / Jira epics
- [ ] Create feature branches
- [ ] Schedule 1:1 architecture reviews
- [ ] Backend: Begin V17 migration design

### Next Week (Sprint 1 Start)
- [ ] Pull request: V17 migration
- [ ] Pull request: CallActivityHandler skeleton
- [ ] QA: Test plan complete
- [ ] Daily standups begin

---

## Q&A Guide

**Q: Why subprocesses instead of inline?**  
A: Reusability, maintenance, testing simplicity. Real-world processes have common patterns.

**Q: What about error handling?**  
A: Errors in child processes propagate to parent's error boundary. Full compensation support.

**Q: Max nesting depth?**  
A: Configurable, default 10. Prevents infinite loops. Checked at deployment time.

**Q: What about circular references?**  
A: Detected via graph analysis. Rejected at design time (modeler) and runtime.

**Q: Timeline realistic?**  
A: Yes, 40sp over 4 weeks = 10sp/week = 1 FTE. Team is 5 people part-time. Conservative estimate.

**Q: What if we slip?**  
A: Prioritized by business value: Core feature (Sprints 1-2) > Admin UI (Sprint 3) > Docs (Sprint 4).

---

## Success Metrics

### By End of Phase 7
- ✅ 95%+ test coverage
- ✅ Zero critical bugs
- ✅ < 500ms per subprocess invocation
- ✅ < 50ms per variable mapped
- ✅ 5 comprehensive doc guides
- ✅ 40+ test cases executed
- ✅ Production ready

### Customer Satisfaction
- New feature available in production
- End users can design reusable subprocesses
- Zero regression in existing features
- Performance acceptable for production

---

## Key Contacts

| Role | Name | Email | Slack |
|------|------|-------|-------|
| **CTO / Epic Lead** | [Name] | [email] | @cto |
| **Backend Owner** | [TBD] | [TBD] | @backend |
| **Frontend Owner (Modeler)** | [TBD] | [TBD] | @modeler |
| **Frontend Owner (Admin)** | [TBD] | [TBD] | @admin |
| **QA Lead** | [TBD] | [TBD] | @qa |
| **Tech Writer** | [TBD] | [TBD] | @docs |

**Escalation**: Issues → Daily standup → CTO (same day)

---

## Final Checklist (For Meeting)

- [ ] All team members understand mission
- [ ] Task assignments confirmed
- [ ] Timeline agreed
- [ ] Success metrics clear
- [ ] No blockers identified
- [ ] Questions answered
- [ ] Ready to start Sprint 1

---

## Call to Action

**"Let's ship Call Activity support and unlock process composition for our customers. 4 weeks, 40 story points, 5 great engineers. Who's ready? 🚀"**

---

**Prepared by**: CTO  
**Date**: 2026-04-22  
**Kickoff**: Tuesday, 2026-04-23 at 10:00 UTC  
**Duration**: 30 minutes  
**Recording**: Will be available in Slack

**Questions before the meeting?** Slack #epic-call-001 or email CTO directly.
