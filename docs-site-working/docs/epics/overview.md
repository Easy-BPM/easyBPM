---
sidebar_position: 0
---

# EPIC Overview

Welcome to the **Easy BPM EPIC Documentation**. This section contains comprehensive specifications for major feature initiatives (EPICs) that drive the evolution of the Easy BPM platform.

## What are EPICs?

An EPIC is a large body of work that can be broken down into smaller, manageable user stories. Each EPIC provides:
- **Executive Summary**: Business value and objectives
- **Architecture Design**: Technical implementation approach
- **Implementation Strategy**: Phased breakdown with effort estimates
- **Success Criteria**: Measurable goals and acceptance criteria
- **Risk Assessment**: Potential challenges and mitigations

---

## Current EPICs

### ✅ Phase 7: Call Activity & Subprocess Support
**Status**: COMPLETE (2026-04-22)  
**Effort**: 40 story points  
**Duration**: 4 weeks

[📄 EPIC-CALL-001: Call Activity & Subprocess Support](epic-call-activity-subprocess-support.md)

Enable process definitions to invoke other process definitions as sub-processes (called activities). This allows building complex workflows through composition, reducing duplication, and improving process maintainability.

**Key Features**:
- ✅ Call Activity nodes for subprocess invocation
- ✅ Parent → child variable input mapping
- ✅ Child → parent variable output mapping
- ✅ Call activity error boundary handling
- ✅ Admin UI hierarchy visualization
- ✅ Modeler support for call activity design

---

### ⏳ Phase 8: Code Task & JAR Execution
**Status**: In Planning (2026-04-22)  
**Effort**: 24-32 story points  
**Duration**: 4-5 weeks

[📄 EPIC-Code-Task: Code Task & JAR Execution Support](epic-code-task-support.md)

Extend Easy BPM with **Code Task** capability - a new task type that executes custom Java code from uploaded JAR files, enabling complex business logic execution with explicit process variable mapping.

**Key Features**:
- JAR file upload and storage
- Class/method discovery and invocation via reflection
- Explicit input/output variable mapping
- Execution audit trail and monitoring in Admin UI
- Comprehensive testing and documentation

---

## EPIC Planning Workflow

Each EPIC follows a structured planning and execution workflow:

### 1. **Planning Phase**
- Define business value and objectives
- Identify stakeholders and teams
- Create architecture design
- Estimate effort and timeline

### 2. **Implementation Phases**
- Backend infrastructure
- Frontend/UI implementation
- Admin UI enhancements
- QA testing and validation
- Documentation

### 3. **Launch Phase**
- Code review and approval
- Production deployment
- Monitoring and support
- Post-launch analysis

### 4. **Success Metrics**
- Adoption rates
- Quality metrics (test coverage, incident rates)
- Performance metrics
- Customer satisfaction

---

## How to Use EPIC Documentation

### For Project Managers
- Review **Executive Summary** for business value
- Check **Implementation Strategy** for timeline and effort
- Track **Success Criteria** for acceptance validation

### For Developers
- Study **Architecture Design** for technical approach
- Review **Implementation Phases** for task breakdown
- Follow **Detailed Specifications** for code guidelines

### For QA Engineers
- Review **Test Scenarios** and **Test Matrix**
- Understand **Success Criteria** and acceptance criteria
- Plan testing around **Risk Assessment**

### For Tech Writers
- Follow **Phase X.5: Documentation** for writing requirements
- Review **Examples** for documentation content
- Update related guides with new feature information

---

## Related Documentation

- [Implementation Status](../implementation-status.md) - Overall project tracking
- [Architecture Overview](../architecture.md) - System design and components
- [Easy BPMN Modeler Guide](../easy-modeler-overview.md) - Process design tool
- [Easy BPM Admin Guide](../easy-admin-overview.md) - Instance management
- [Easy BPM Task Portal Guide](../easy-task-portal-overview.md) - Task execution

---

## Phase Roadmap

```
Phase 1-6: Foundation & QA ✅ (COMPLETE)
├─ Core process engine
├─ Error boundaries
├─ Form support
├─ Call activities (Phase 7)
└─ QA improvements

Phase 7: Call Activity & Subprocess ✅ (COMPLETE)
├─ T7.1: Backend core
├─ T7.2: Modeler support
├─ T7.3: Admin UI
└─ T7.4-7.5: QA & Documentation

Phase 8: Code Task & JAR Execution ⏳ (PLANNING)
├─ T8.1: Backend infrastructure
├─ T8.2: Modeler UI
├─ T8.3: Admin UI
└─ T8.4-8.5: QA & Documentation

Phase 9+: Future Enhancements
├─ Timer events and boundary timers
├─ CORS configuration and auth
├─ Advanced form features
└─ Performance optimization
```

---

## Quick Links

| Document | Purpose |
|----------|---------|
| [Call Activity EPIC](epic-call-activity-subprocess-support.md) | Subprocess composition and variable mapping |
| [Code Task EPIC](epic-code-task-support.md) | JAR execution and business logic |
| [Implementation Status](../implementation-status.md) | Current progress tracking |
| [Architecture Overview](../architecture.md) | System design and components |

---

## Questions or Feedback?

For questions about EPIC planning, architecture, or implementation:
1. Review the specific EPIC document for details
2. Check the **FAQ section** in the relevant EPIC
3. Consult the **Related Documentation** section
4. Contact the CTO or project manager

---

**Last Updated**: 2026-04-22  
**Maintained By**: CTO & Engineering Team
