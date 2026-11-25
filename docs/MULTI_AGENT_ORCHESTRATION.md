# FlipScan v0.7.0 — Multi-Agent Audit & Release Orchestration

**Project:** FlipScan  
**Application ID:** `app.flipscan`  
**Current target:** v0.7.0 release readiness  
**Workflow:** Independent audit → Cross-review → Consolidation → Implementation → Verification

---

# 1. Mission

Conduct a comprehensive technical audit of FlipScan using multiple specialist AI agents.

The objective is to make FlipScan secure, reliable, performant and ready for Google Play Store release without damaging existing functionality.

This is an existing Android application. Do not rebuild it from scratch.

The audit must be based on the actual current repository, not assumptions, old README descriptions or previous AI-generated reports.

## Non-negotiable project principles

- Offline-first application.
- No cloud processing.
- No advertisements.
- No analytics or tracking.
- No mandatory login.
- No unnecessary Internet permission.
- Preserve existing functionality.
- Preserve the current UI and architecture unless a justified issue requires changes.
- Never weaken security to improve convenience.
- Never remove important features simply to reduce APK size.
- Protect existing user documents and encrypted data.

---

# 2. Agent architecture

Use one coordinator and six specialist agents.

## Coordinator — Project Lead

Responsibilities:

1. Understand the repository structure.
2. Inspect existing OpenCode agent capabilities and configuration.
3. Delegate independent audit tasks.
4. Ensure all agents use the same repository state.
5. Collect specialist reports.
6. Identify overlapping and contradictory findings.
7. Assign the independent reviewer.
8. Produce a consolidated audit.
9. Prepare a prioritized implementation plan.
10. Coordinate implementation and final verification.

The coordinator must not blindly trust specialist conclusions.

It must verify important findings against source code and test evidence.

## Agent 1 — Digital Locker Security

**Scope:**

- LockerCrypto
- LockerStore
- Android Keystore
- AES-GCM encryption/decryption
- Encryption IV and authentication tag handling
- Encrypted metadata and thumbnails
- Temporary plaintext files
- Backup exclusions
- BiometricPrompt
- Device credential fallback
- Authentication lifecycle
- Session expiration
- Background locking
- Recent-app previews
- FLAG_SECURE
- Key invalidation and recovery
- Corrupted encrypted data handling

**Questions to investigate:**

- Are all sensitive files encrypted at rest?
- Are keys handled safely?
- Can authentication state remain valid after session expiration?
- Can decrypted data remain on disk after failure or process death?
- Are encrypted documents recoverable after application updates?
- Can a failed operation leave inconsistent storage?
- Does the implementation handle Android lifecycle events correctly?

**Output:**

`docs/multi-agent/01_SECURITY_AUDIT.md`

Do not modify production source code during the audit.

## Agent 2 — OCR and Digital Pins

**Scope:**

- ML Kit OCR integration
- Document type detection
- Aadhaar
- PAN
- Passport
- Driving Licence
- Voter ID
- Format validation
- Aadhaar Verhoeff checksum
- Manual correction
- Pin masking
- Reveal/hide behavior
- Sensitive value encryption
- Clipboard handling
- OCR failure handling

**Questions to investigate:**

- Are extracted values treated as unverified until user confirmation?
- Are format checks appropriate?
- Can OCR accidentally change important characters?
- Are values normalized consistently?
- Can sensitive values appear in logs?
- Does clipboard handling avoid deleting newer clipboard content?
- Is validation incorrectly presented as government verification?

**Output:**

`docs/multi-agent/02_OCR_DIGITAL_PINS_AUDIT.md`

Do not modify production source code during the audit.

## Agent 3 — Export, Sharing and File Storage

**Scope:**

- PDF export
- JPG export
- PNG export
- Digital Locker export
- FileProvider
- Content URIs
- Share permissions
- Temporary files
- File cleanup
- Multi-page documents
- Export cancellation
- Export failure handling
- Storage isolation between normal scans and Locker

**Questions to investigate:**

- Can sensitive data escape encrypted storage unintentionally?
- Are exported copies clearly distinguished from encrypted originals?
- Are temporary plaintext files removed reliably?
- Are URI permissions scoped correctly?
- Are large exports handled without memory exhaustion?
- Can interrupted exports leave incomplete files?
- Are normal scan workflows unaffected?

**Output:**

`docs/multi-agent/03_EXPORT_STORAGE_AUDIT.md`

Do not modify production source code during the audit.

## Agent 4 — Scanning Performance and QA

**Scope:**

- CameraX
- OpenCV
- ImageAnalysis
- Document detection
- Image processing
- Bitmap allocation
- Threading
- Executor management
- Camera lifecycle
- Memory usage
- Large document handling
- Existing regression tests

**Questions to investigate:**

- Is expensive processing running on the main thread?
- Are unnecessary image copies being created?
- Is frame processing properly controlled?
- Can camera resources leak?
- Are image buffers closed correctly?
- Can large images cause OutOfMemoryError?
- Are there opportunities to reduce CPU usage without reducing detection quality?

**Testing priorities:**

- Camera startup.
- Continuous preview.
- Document edge detection.
- Capture.
- Crop and perspective correction.
- Rotation.
- Multi-page scanning.
- PDF generation.
- Camera release and reopening.

**Output:**

`docs/multi-agent/04_PERFORMANCE_QA_AUDIT.md`

Do not modify production source code during the audit.

## Agent 5 — Android Configuration and Play Store

**Scope:**

- AndroidManifest.xml
- Gradle configuration
- compileSdk
- targetSdk
- minSdk
- AGP
- Kotlin
- Java compatibility
- Dependencies
- Permissions
- Backup configuration
- Release build configuration
- R8
- Resource shrinking
- ABI configuration
- APK/AAB size

**Questions to investigate:**

- Is the project compliant with current Google Play requirements?
- Are any permissions unnecessary?
- Is INTERNET permission absent?
- Are backup rules appropriate for sensitive documents?
- Are release builds correctly configured?
- Are dependencies outdated or vulnerable?
- Are there unnecessary packaged native libraries?
- Does the project build reproducibly?

Use current official Android and Google Play documentation for time-sensitive policy requirements, if web research is available.

Clearly distinguish policy requirements from recommendations.

**Output:**

`docs/multi-agent/05_ANDROID_RELEASE_AUDIT.md`

Do not modify production source code during the audit.

## Agent 6 — Independent Security and Quality Reviewer

This agent must independently challenge the other five agents.

It must not simply summarize their reports.

**Responsibilities:**

1. Review all five reports.
2. Inspect the source code supporting important findings.
3. Verify evidence and file paths.
4. Identify incorrect assumptions.
5. Identify duplicate findings.
6. Identify contradictions.
7. Identify missing audit areas.
8. Distinguish theoretical risks from confirmed vulnerabilities.
9. Check whether suggested fixes could break existing features.
10. Identify potential data loss or migration risks.

**Questions:**

- Is the issue reproducible?
- Is the severity justified?
- Does the evidence support the claim?
- Does the suggested fix introduce a new vulnerability?
- Could the fix invalidate existing encrypted documents?
- Is a manual device test required?
- Has an important security boundary been overlooked?

**Output:**

`docs/multi-agent/06_INDEPENDENT_REVIEW.md`

This agent should run after the five specialist reports are available.

---

# 3. Execution strategy

## Stage 0 — Repository reconnaissance

The coordinator must:

1. Inspect the Git working tree.
2. Record the current branch and commit.
3. Identify uncommitted user changes.
4. Inspect the existing project architecture.
5. Discover the available OpenCode subagent/spawn mechanism.
6. Identify available models and agent capabilities.
7. Confirm the agents can receive separate task instructions.

Do not overwrite existing user changes.

If subagent spawning is unavailable, explain the limitation and run the specialist audits sequentially rather than pretending parallel execution occurred.

## Stage 1 — Parallel read-only audit

Launch Agents 1–5 independently when supported.

All five agents must inspect the same repository revision.

Rules:

- No production code modifications.
- No Gradle dependency upgrades.
- No database migrations.
- No encryption format changes.
- No signing configuration changes.
- No deletion or movement of user files.
- No Git commits or pushes.
- Do not overwrite another agent's report.
- Agents must not trust one another's findings without evidence.

Each agent should inspect its own scope thoroughly and produce its report.

The coordinator should record which agents completed successfully.

## Stage 2 — Independent review

After Agents 1–5 finish, launch Agent 6.

Give it all five reports and ask it to independently verify high-impact findings.

The reviewer should inspect actual source code instead of trusting report summaries.

## Stage 3 — Consolidated audit

The coordinator must produce:

`docs/FLIPSCAN_V070_AUDIT.md`

Include:

1. Executive summary.
2. Repository and architecture overview.
3. Agent completion status.
4. Confirmed findings.
5. Potential risks requiring further investigation.
6. Duplicate findings.
7. Reviewer disagreements.
8. Security assessment.
9. Performance assessment.
10. Build and test results.
11. Remaining manual verification.
12. Release-readiness status.

Use the following finding format:

| Field | Description |
|---|---|
| Finding ID | Unique identifier, e.g. SEC-001 |
| Category | Security, OCR, Storage, Performance, Release |
| Severity | Critical, High, Medium, Low, Informational |
| Status | Confirmed, Potential, Not Reproduced, False Positive |
| File | Exact source path |
| Lines | Relevant line numbers |
| Evidence | Code behavior or test result |
| Impact | Real-world consequence |
| Recommendation | Specific fix |
| Verification | Test needed to confirm resolution |

Do not classify a vulnerability as confirmed based only on theoretical possibilities.

---

# 4. Stage 4 — Implementation planning

Create:

`docs/FLIPSCAN_V070_IMPLEMENTATION_PLAN.md`

Group issues into:

- P0 — Critical security/data-loss issue.
- P1 — High-priority release blocker.
- P2 — Medium-priority quality issue.
- P3 — Low-priority improvement.

For every proposed fix include:

- Finding ID.
- Root cause.
- Exact files affected.
- Proposed implementation.
- Regression risk.
- Required tests.
- Data migration implications.
- Dependencies on other fixes.

The coordinator must approve the implementation plan before production code changes begin.

---

# 5. Stage 5 — Controlled implementation

Only after the audit and implementation plan are complete:

1. Implement confirmed P0 and P1 issues first.
2. Address safe P2 issues.
3. Consider P3 issues only when they are low risk.
4. Keep changes focused.
5. Add regression tests.
6. Preserve backward compatibility.
7. Review every database or encryption-format change carefully.

If multiple implementation agents are used:

- Assign separate, clearly defined tasks.
- Prefer separate Git worktrees or branches.
- Never allow agents to edit shared files concurrently.
- Coordinate integration through the main coordinator.
- Review diffs before merging.

Never silently delete user documents or reset encrypted storage.

Never invalidate existing encryption keys or change encrypted data formats without a verified migration and recovery plan.

---

# 6. Stage 6 — Verification

Run available checks:

- Unit tests.
- Instrumentation tests.
- Kotlin compilation.
- Debug build.
- Release build.
- Lint.
- Dependency checks.
- Manifest and permission inspection.

Add or improve tests for:

### Security
- Encryption/decryption round trip.
- Wrong-key handling.
- Corrupted ciphertext.
- Key invalidation.
- Session timeout.
- Authentication cancellation.
- Temporary plaintext cleanup.

### Digital Pins
- Masking.
- Reveal/hide.
- Formatting.
- Aadhaar checksum.
- OCR correction.
- Clipboard behavior.

### Export
- PDF export.
- JPG export.
- PNG export.
- Export cancellation.
- Failure cleanup.
- File sharing permissions.

### Scanning
- Camera startup and shutdown.
- Image processing.
- Multi-page scanning.
- Crop and rotation.
- Memory handling.

Never claim physical-device testing without actually performing it.

Primary physical test device: Nothing Phone (2a), 8 GB RAM, Android 16.

Other devices should be used when available.

---

# 7. Stage 7 — Release preparation

Create:

`docs/FLIPSCAN_RELEASE_CHECKLIST.md`

Include:

- Security verification.
- Functional regression testing.
- Android compatibility.
- Performance.
- SDK compliance.
- Permissions.
- Backup and privacy review.
- Privacy policy.
- Google Play Data safety.
- Store listing assets.
- Signed AAB preparation.
- Testing track requirements.
- Production release checklist.

Do not upload or publish the application.

Do not expose signing credentials or modify signing configuration without explicit authorization.

---

# 8. Final report

The coordinator must provide a final summary containing:

1. What was inspected.
2. Which agents completed their work.
3. Confirmed issues found.
4. Issues fixed.
5. Important unresolved risks.
6. Files changed.
7. Tests passed.
8. Tests failed.
9. Tests not executable.
10. Build results.
11. APK/AAB measurements.
12. Manual device tests pending.
13. Play Store readiness.

Use one of these final statuses:

- **READY**
- **READY WITH MANUAL TESTS PENDING**
- **NOT READY**

Never report READY if a critical security issue or release blocker remains unresolved.

---

# 9. Global safety rules

These rules override individual agent instructions.

- Inspect before editing.
- Audit before implementing.
- Evidence before conclusions.
- No invented test results.
- No unverified security claims.
- No unnecessary architecture rewrites.
- No destructive database operations.
- No production data deletion.
- No weakening security.
- No exposing secrets.
- No unrequested commits or pushes.
- No publishing to Google Play.
- Preserve the offline-first product direction.

**Success means a verified, stable, secure FlipScan release — not merely a successful Gradle build.**