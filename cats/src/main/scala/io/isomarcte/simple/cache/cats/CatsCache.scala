package io.isomarcte.simple.cache.cats

import cats.effect._
import cats.implicits._
import io.isomarcte.simple.cache.core._

object CatsCache {

  private[this] final class ProxySyncCache[A, B, F[_]](cache: Cache.SimpleMutableCache[A, B])(implicit F: Sync[F]) extends Cache[A, B, Unit, F] {
    override final protected def functorMap[C, D](fc: F[C], f: C => D): F[D] =
      fc.map(f)
    override final protected def applicativePure[C](c: C): F[C] = F.pure(c)

    override final val clear: F[Unit] =
      F.delay(this.cache.clear)
    override final def modifyWithKey(key: A, f: (A => Option[B] => Option[B])): F[(Unit, Option[B])] =
      F.delay(this.cache.modifyWithKey(key, f))
  }

  def concurrentSimpleCatsCacheDefaultsUnsafe[A, B, F[_]: Sync]: Cache.MutableCache[A, B, F] =
    new ProxySyncCache(Cache.concurrentSimpleCacheDefaultsUnsafe)
}
