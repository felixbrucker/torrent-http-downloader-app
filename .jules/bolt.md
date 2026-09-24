## 2026-09-24 - Remember Dependency Container Lookups in Composables
**Learning:** Calling dependency container lookups like `Container.getOptionalService` directly in composable bodies causes hash map lookups on every composition frame, especially during active progress animations.
**Action:** Always wrap service/container lookups in `remember` when accessed inside composable functions.
