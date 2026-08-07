// 04.08.2026 Seed Data cursor by Me4Hik START - исключения seed
package com.me4hik.praktika.data.seed

class SeedParseException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

class SeedValidationException(message: String) : IllegalArgumentException(message)

class SeedCorruptionException(message: String) : IllegalStateException(message)

class SeedMigrationRequiredException(message: String) : IllegalStateException(message)
// 04.08.2026 Seed Data cursor by Me4Hik END
