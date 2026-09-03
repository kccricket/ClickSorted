# Admin Commands

These commands require `clicksorted.admin.commands.*` (op by default). See [Permissions](permissions.md).

| Command | Description |
|---|---|
| `/clicksorted admin reload` | Reload all config files (`config.yml`, `groups.yml`, `items.yml`, `lang.yml`) without a server restart. |
| `/clicksorted admin config` | Print every `config.yml` key/value to the console or chat. |
| `/clicksorted admin debug [off\|debug\|trace]` | Set logging verbosity at runtime (not persisted to `config.yml`). With no argument, toggles between `off` and `debug`. |
| `/clicksorted admin benchmark [iterations]` | Run an in-situ micro-benchmark of the sort and bundle-repack paths and report per-operation timings. Runs synchronously, briefly pausing the server. Default 2000 iterations (100–50000). Requires `enable_benchmark: true` in `config.yml` (default `false`) in addition to the permission node. |
| `/clicksorted admin selftest algo` | Run the self-test's pure algorithm checks (no Paper API surface). |
| `/clicksorted admin selftest probe` | Run the capability-probe suite alone — a quick compatibility fingerprint of the running server. |
| `/clicksorted admin selftest sim` | Run synthetic-event checks dispatched through the plugin's real listeners. |
| `/clicksorted admin selftest start [quick\|full]` | Start an interactive LIVE self-test session: `quick` walks through the 6 click-method gestures, `full` (default) adds region/lock/blacklist/bundle scenario cycles. Stages test fixtures in the tester's own inventory and restores their real inventory/preferences from a crash-safe backup when the session ends. |
| `/clicksorted admin selftest next` | Skip the current LIVE prompt (mark it skipped) and advance to the next one. |
| `/clicksorted admin selftest status` | Show progress of the tester's in-progress LIVE session. |
| `/clicksorted admin selftest stop` | Abort the in-progress LIVE session and restore the tester's inventory/preferences. |

`selftest` requires `enable_selftest: true` in `config.yml` (default `false`) in addition to `clicksorted.admin.commands.selftest`, since a LIVE run stages fixtures in and restores the tester's real inventory and preferences. It's a diagnostic tool for validating a plugin update against your exact Paper build, not something to leave on for routine play.

Every report groups its results into three categories, in increasing order of how much they actually prove about compatibility with the running server version: `SANITY` (pure algorithm calls, no Paper API surface — a failure here is never a version signal), `INTEGRATION` (a synthetic event dispatched through the real listeners), and `ENDTOEND` (a real client gesture during a LIVE session — the only category that can catch an actual version-behavior difference).
