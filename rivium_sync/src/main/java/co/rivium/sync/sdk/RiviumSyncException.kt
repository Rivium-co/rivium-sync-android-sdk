package co.rivium.sync.sdk

/**
 * Exceptions for RiviumSync SDK
 */
sealed class RiviumSyncException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    
    class NotInitializedException : RiviumSyncException("RiviumSync SDK not initialized. Call RiviumSync.initialize() first.")
    
    open class NetworkException(message: String, cause: Throwable? = null) : RiviumSyncException(message, cause)

    /**
     * The project requires a signed user token and the request carried none.
     * Not a network failure: nothing changes until the app supplies a token.
     */
    class UserTokenRequiredException(message: String) : NetworkException(message)
    
    class AuthenticationException(message: String) : RiviumSyncException(message)
    
    class DatabaseException(message: String) : RiviumSyncException(message)
    
    class CollectionException(message: String) : RiviumSyncException(message)
    
    class DocumentException(message: String) : RiviumSyncException(message)
    
    class ConnectionException(message: String, cause: Throwable? = null) : RiviumSyncException(message, cause)
    
    class TimeoutException(message: String) : RiviumSyncException(message)
    
    class PermissionException(message: String) : RiviumSyncException(message)

    class BatchWriteException(message: String, cause: Throwable? = null) : RiviumSyncException(message, cause)

    class TransactionException(message: String, cause: Throwable? = null) : RiviumSyncException(message, cause)
}
