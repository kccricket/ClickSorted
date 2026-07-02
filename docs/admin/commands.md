# Admin Commands

These commands require `clicksorted.admin.commands.*` (op by default). See [Permissions](permissions.md).

| Command | Description |
|---|---|
| `/clicksorted admin reload` | Reload all config files (`config.yml`, `groups.yml`, `items.yml`, `lang.yml`) without a server restart. |
| `/clicksorted admin config` | Print every `config.yml` key/value to the console or chat. |
| `/clicksorted admin debug [off\|debug\|trace]` | Set logging verbosity at runtime (not persisted to `config.yml`). With no argument, toggles between `off` and `debug`. |
| `/clicksorted admin benchmark [iterations]` | Run an in-situ micro-benchmark of the sort and bundle-repack paths and report per-operation timings. Runs synchronously, briefly pausing the server. Default 2000 iterations (100–50000). |
