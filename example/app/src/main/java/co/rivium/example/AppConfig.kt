package co.rivium.example

/**
 * Configuration for the RiviumSync Example App
 *
 * For development, you can either:
 * 1. Edit this file directly (not recommended for production)
 * 2. Add to local.properties:
 *    riviumsync.apiKey=your-api-key
 *    riviumsync.databaseId=your-database-id
 */
object AppConfig {
    // These are read from BuildConfig (set via local.properties or build.gradle.kts)
    val apiKey: String = BuildConfig.RIVIUM_SYNC_API_KEY
    val databaseId: String = BuildConfig.RIVIUM_SYNC_DATABASE_ID

    // Collection names
    const val TODOS_COLLECTION = "todos"

}
