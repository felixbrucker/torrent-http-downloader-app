## 2026-10-25 - Thread-safe date parsing with DateTimeFormatter
**Learning:** `SimpleDateFormat` is non-thread-safe and can cause synchronization overhead or date parsing errors when parsing RSS dates concurrently. Replacing it with immutable `DateTimeFormatter` from `java.time` ensures thread safety and avoids synchronization.
**Action:** Use `java.time.format.DateTimeFormatter` and `ZonedDateTime` for thread-safe date parsing across background threads/coroutines.
