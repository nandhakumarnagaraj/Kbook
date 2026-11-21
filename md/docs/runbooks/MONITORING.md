# Monitoring and Alerting

**Current state:** there is **no application monitoring**. No uptime probe, no error tracking, no host
metrics collector, no alert channel. Failures are discovered by customers.
**Changed 2026-11-17:** `fail2ban` is installed and active (`sshd` + `xrdp-sesman` jails) — that protects
the host from brute force, but it is **not** application health monitoring and does not close this gap.
**Date:** 2026-11-17

---

## 1. What is already emitted (verified)

The Spring Boot app exposes Actuator on the public host under `/api/v1/actuator/*`:

| Endpoint | Anonymous result | Use |
| :-- | :-- | :-- |
| `/health`, `/health/liveness`, `/health/readiness` | **200** | the ONLY health signal currently used |
| `/prometheus` | **200** ← information leak, see §5 | ready-made metrics source |
| `/metrics`, `/env`, `/beans`, `/loggers` | 401 | correctly protected |
| `/api-docs`, `/swagger-ui` | 200 | public API map (accepted policy) |

Because `/prometheus` is already serving, **the cheapest possible monitoring win is an external
Prometheus scrape + a few alert rules** — no application change required.

---

## 2. What to watch, and why

### Infrastructure
| Signal | Threshold | Why |
| :-- | :-- | :-- |
| Disk `/` | > 80% warn, > 90% page | postgres + backups + images all share one disk; full disk = down |
| RAM | > 85% sustained | `mem_limit` is 6g server / 4g postgres; OOM kills look like random restarts |
| Container restarts | any increase | silent crash loops currently go unnoticed |
| Load average | > cores for 10 min | single host serving three products |

### Application
| Signal | Threshold | Why |
| :-- | :-- | :-- |
| 5xx rate on `/api/v1/**` | > 1% for 5 min | the case-variant-email defect produces a 500 and nobody knows |
| Login failures | spike vs. 7-day baseline | credential stuffing, or a broken deploy |
| `POST /auth/signup` 5xx | > 0 | signup failure is silent revenue loss |
| Registration duplicate rejections | any sudden change | the identity defect's fingerprint |
| DB connection pool saturation | > 80% | the classic pre-outage signal |
| Slow queries | > 2 s | bills and sync are the hot paths |
| Sync failures (POS → server) | > 0 sustained | orders stop reaching the owner |

### Business (the ones that matter most)
| Signal | Why |
| :-- | :-- |
| Bills created per hour vs. same hour last week | a 40% drop at lunch service is an incident, not a dip |
| Tenants with zero bills for > 7 days after signup | onboarding or product failure |
| Subscription payment failures | revenue leak |
| Never-billed live tenants (**77 of 161** on 2026-11-17) | the number this whole audit keeps returning to |

A server being "up" says nothing about a restaurant being able to bill. The business counters are the
real health check.

---

## 3. Recommended stack (smallest useful set)

| Layer | Tool | Effort | Why this one |
| :-- | :-- | :-- | :-- |
| External uptime | **Uptime Kuma** (separate host/instance) | low | must live *off* this VPS — an on-host monitor cannot report the host being down |
| Error tracking | **Sentry** (Spring + Android) | low–medium | turns "customer says it crashed" into a stack trace with a request id; the app already logs `errorId` |
| Metrics | **Prometheus + Grafana** scraping the existing `/prometheus` | medium | data is already exposed |
| Host metrics | **Netdata** | very low | instant CPU/RAM/disk/process view |
| Log retention | `docker logs` → file + rotate | low | currently logs vanish on container recreate |

Start with uptime + Sentry. Those two catch the majority of real incidents.

---

## 4. SLOs (proposed, deliberately modest)

| SLI | SLO | Error budget |
| :-- | :-- | :-- |
| API availability (`/actuator/health` + login) | 99.5% / 30 days | ~3.6 h |
| Bill creation success | 99.5% / 30 days | 1 in 200 bills may fail before action |
| Login success (excluding wrong credentials) | 99% / 30 days | 1 in 100 |
| Sync delivery within 60 s | 99% | — |

These are starting points, not commitments; they exist so "is it healthy?" has an answer that is not a
feeling.

---

## 5. Metrics exposure (fixed 2026-11-17)

`/api/v1/actuator/prometheus` was readable **anonymously**. It now has a dedicated exact-match nginx
location ahead of the `^~ /api/` block:

```nginx
location = /api/v1/actuator/prometheus {
    allow 127.0.0.1;
    allow ::1;
    deny all;
    proxy_pass http://127.0.0.1:8081/api/v1/actuator/prometheus;
}
```

Verified: **403** when the request originates off-host (from the host's public IP and from an isolated
container), **200** for `/api/v1/actuator/health` as the control.

**Consequence for monitoring:** a scraper must now run on the host or inside the Docker network. It can
no longer scrape through the public hostname. That is the intent.

Also verified public and **intentionally left so** under the "public sites, login is the gate" policy:
`/api/v1/api-docs` and `/api/v1/swagger-ui`.

---

## 6. Minimum viable alert set

If only five alerts are configured, configure these:

1. `https://kbook.iadv.cloud/api/v1/actuator/health` not 200 for 2 minutes.
2. Disk > 90%.
3. Any container restarted more than twice in an hour.
4. 5xx rate > 1% for 5 minutes.
5. Bills/hour < 50% of the same hour last week during service hours.

Each must notify a channel a human actually reads.
