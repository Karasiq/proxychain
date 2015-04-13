package com.karasiq.proxy

import java.io.IOException

/**
 * Proxy connection error
 */
class ProxyException(message: String = null, cause: Throwable = null) extends IOException(message, cause)
