# Runtime risk repair plan

Scope: preserve recipe selection, batch dispatch, inventory acceptance and long-running crafts. No automatic timeout cancellation or arbitrary quantity cap.

1. [x] Capture CPU compatibility on the server thread when creating the calculation; pass only a boolean into the calculation worker. Keep stock reads on AE2 simulation snapshots. Calculations constructed off-thread conservatively disable growth execution.
2. [x] Gate the native wildcard provider mixin against all shadow fields and its injected method; report unsupported contracts.
3. [x] Add state-rich failure logs and rate-limited stalled-return warnings after five minutes without progress. Partial returns reset the warning interval. Preserve fail-closed cancellation for inconsistent execution state; never cancel due to elapsed waiting time alone.
4. [x] Reduce bounded provider work to 32 per CPU pass; preserve the native resumable cursor. Verify complete traversal of 600 providers and recovery after collection changes.
5. [x] Isolate wildcard caches by level/recipe-manager identity, make counters atomic and own cached NBT snapshots.
6. [x] Add negative compatibility contracts and regression checks; build beta artifact 2026.11.8-beta. Passed 7137 regression checks, 147 universal/interface checks and 43 installed-mod checks. No Forge world was launched.

Additional diagnostics: wildcard expansion errors now include machine, dimension and stack trace; missing legacy item migration is logged explicitly. Changes remain uncommitted.

Deferred architectural work: asynchronous wildcard expansion requires a separate provider refresh protocol; do not return incomplete patterns from the existing synchronous API. Arbitrary third-party iterable replacement is not treated as a stable provider-list contract.
