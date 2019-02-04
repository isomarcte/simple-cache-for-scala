package io.isomarcte.simple.cache.core

import scala.util.control._

sealed trait CacheInitializationError {
  this: IllegalArgumentException with NoStackTrace =>
}

object CacheInitializationError {
}
