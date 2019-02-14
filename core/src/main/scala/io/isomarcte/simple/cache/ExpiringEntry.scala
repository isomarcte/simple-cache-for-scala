package io.isomarcte.simple.cache.core

import java.time._

final case class ExpiringEntry[A](value: A, duration: Option[Duration]) {
  val expiration: Option[Instant] =
    this.duration.map(d => Instant.now().plus(d))

  def isExpired: Boolean = ExpiringEntry.isExpired(this)
  def isNotExpired: Boolean = ExpiringEntry.isNotExpired(this)
}

object ExpiringEntry {
  def isExpired[A](e: ExpiringEntry[A]): Boolean =
    e.expiration.map(_.isBefore(Instant.now())).getOrElse(false)
  def isNotExpired[A](e: ExpiringEntry[A]): Boolean =
    !isExpired(e)
}
