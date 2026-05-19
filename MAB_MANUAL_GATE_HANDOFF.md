# MAB Manual Gate Handoff

Current state: automated final gate passed, but the active goal is not complete
until a human-visible GUI playthrough is recorded.

Checklist artifact to fill:

```text
target\mab-manual-gate-20260518-195121.md
```

The checklist must mark PASS, leave FAIL unmarked, and check all 11 manual
items after the playthrough.

Validate the filled checklist:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\validate_mab_manual_checklist.ps1 -Path "target\mab-manual-gate-20260518-195121.md"
```

Record the manual result:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\record_mab_manual_gate.ps1 -Result PASS -Tester "name" -ArtifactPath "target\mab-manual-gate-20260518-195121.md" -Notes "brief notes"
```

Recheck completion:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\check_mab_completion_gate.ps1
```

Do not mark the goal complete unless the completion checker reports PASS.
