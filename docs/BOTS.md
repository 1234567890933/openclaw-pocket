# Bots

A bot is a persona, an allow-list of tools, a model preference and a budget. Not a
second model — the same phone plays all five roles.

| Bot | Job | Tools | Default mode |
|---|---|---|---|
| Scout | research with sources | search, http, browser | cloud |
| Forge | write and run code | files, code, search | cloud |
| Ledger | numbers and assumptions | files, code | auto |
| Critic | find what is wrong | files, code | auto |
| Runner | operate the device | code, files | auto |

## Why allow-lists

A bot's tool set is an allow-list, never a deny-list. If a deny-list, a tool added to
the registry later would silently become available to every bot — which is how an
assistant acquires the ability to delete files because somebody added a feature.

## Orchestration

`Orchestrator` maps a request to a short plan using keyword rules, then runs the
steps in sequence, feeding each output into the next task so a reviewer reviews
something rather than the original question again.

```
"compare X and Y"   → Scout (facts) → Ledger (numbers) → Critic (weakest assumption)
"fix this error"    → Forge (diagnose) → Critic (how it fails) → Forge (apply)
"build me ..."      → Forge (write) → Critic (review)
"what is ..."       → Scout (answer) → Critic (unsupported claims)
anything else       → Scout
```

The plan is chosen by rules, not by a model. A model-written plan costs a full
inference before any work starts, and for five bots the mapping is knowable. Rules
are also testable; a prompt is not.

## Concurrency

Two bots at a time, capped by `BotSupervisor`. That is a thermal decision: three
concurrent loops hit the governor inside a minute and then *everything* is slow,
which is worse than running two and queueing the third. Each step also has a
four-minute ceiling and a cancellation path.

## Adding a bot

Add an entry to `BotRoster` with a persona that says something specific — a persona
that says "be helpful" is the same as no persona — and a tool set that is the
smallest one that does the job.
