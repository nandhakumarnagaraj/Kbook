# Evidence

Dated records of what was true, observed, or believed on a date. **Read-only by convention.**

## Rules

1. **Never edit an evidence file to match the current tree.** File paths, class names, versions and
   feature claims inside these docs describe the date in the filename. A link here that no longer
   resolves is expected — it is a fact about history, not a broken build.
2. **Corrections go forward, not back.** If a finding is wrong or a claim has decayed, write the
   correction in the living doc that cites it (`../product/COMPETITORS.md`, `../product/PROBLEM.md`,
   `../meta/KNOWN_ISSUES.md`) and cite this file as the superseded source.
3. **Filenames carry dates** — `<subject>-YYYY-MM-DD.md`. A file without a date belongs in a living
   folder instead.
4. **Evidence class is not uniform.** Within one folder you will find vendor-published material,
   APK static analysis, code reading, and runtime observation. Living docs that cite evidence must
   label which class a claim comes from; `../product/COMPETITORS.md` is the worked example.
5. **Do not add secrets.** Review and teardown evidence was scrubbed of key material; keep it that
   way (`../engineering/SECURITY.md`).

## Layout

| Folder | Holds |
|---|---|
| `reviews/` | Code, API, QA, reliability and design reviews |
| `rnd/` | Competitor teardowns (Arow, Khide) with the raw APK evidence JSONs they were derived from |
| `planning/` | Plans and production reviews whose work is finished or abandoned |
| `reports/` | One-off investigation write-ups |
| `logs/` | Session logs |
| `archive/` | Large historical dumps and file indexes |

Promoting a dated plan back to `../planning/` is fine if work restarts — move the file, keep its date.
