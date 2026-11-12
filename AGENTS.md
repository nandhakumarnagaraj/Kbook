<!-- openqodex:start -->
## Review with OpenQodex before you push
- Before any `git push`, run `npx -y openqodex@0.16.0 review` from the repository root. It can take minutes, and OpenQodex stops the reviewer after 30 minutes. Run it in the background and wait for it to exit.
- Show the developer the receipt it prints: the verdict, one line per finding and the absolute path of `report.html`. Ask: "Fix all, or tell me which?" Fix only the findings they name (`npx -y openqodex@0.16.0 findings 1,3` prints them in full), then review again and show the new receipt. OpenQodex starts its own reviewer process, so by default the agent that wrote the code does not judge its own work.
- Do not push on a blocked verdict unless the developer says so after seeing the findings.
- Never add a file to `review.instructions_files` on your own. Ask the developer which files the reviewer may use, then set only what they name.
- The report is in `.openqodex/reviews/`.
<!-- openqodex:end -->

## AI Engineering & Code Reality Standard
- Every AI agent working on KhanaBook must strictly adhere to [AI_ENGINEERING_RULES.md](file:///C:/Users/nandh/Desktop/Khanabook/KhanaBook/AI_ENGINEERING_RULES.md).
- **Implementation is the source of truth.** Comments describe what the code actually does, not what was intended.
- Verify all boundary calculations and test assertions mathematically. Never write unverified or contradictory comments.

## Android POS UI Operations Documentation Policy
Whenever an AI agent explores, operates, tests, or analyzes the KhanaBook Android POS application:
1. It MUST read the relevant files inside `ANDROID_POS_DOCUMENTATION/` before starting.
2. It MUST document every newly discovered UI operation.
3. It MUST update existing documentation when actual UI behavior changes.
4. It MUST record bugs, suggestions, and recommendations.
5. It MUST maintain a session audit log in `ANDROID_POS_DOCUMENTATION/DEVICE_SESSIONS/SESSION_LOG.md`.
6. It MUST reference actual screenshots and logs.
7. It MUST distinguish verified behavior from assumptions and proposals.
8. It MUST NOT implement recommendations without explicit authorization.
9. It MUST NOT delete historical findings or overwrite evidence.
10. It MUST update `NEXT_ACTIONS.md` before completing the session.


