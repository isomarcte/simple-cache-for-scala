package io.isomarcte.simple.cache.cats

import cats.effect._
import cats.implicits._
import io.isomarcte.simple.cache.core._

object CatsCache {

  private[this] final class ProxySyncCache[A, B, F[_]](cache: Cache.SimpleMutableCache[A, B])(implicit F: Sync[F]) extends Cache[A, B, Unit, F] {
    override final protected def fmap[C, D](fc: F[C], f: C => D): F[D] =
      F.map(fc)(f)
    override final protected def pure[C](c: C): F[C] = F.pure(c)

    override final val clear: F[Unit] =
      F.delay(this.cache.clear)
    override final def modifyWithKey(key: A, f: (A => Option[ExpiringEntry[B]] => Option[ExpiringEntry[B]])): F[(Unit, Option[B])] =
      F.delay(this.cache.modifyWithKey(key, f))
    override final def get(key: A): F[Option[B]] =
      F.delay(this.cache.get(key))
    override final def remove(key: A): F[(Unit, Option[B])] =
      F.delay(this.cache.remove(key))
  }

  def synchronizedMutableCache[A, B, F[_]: Sync]: Cache.MutableCache[A, B, F] =
    new ProxySyncCache(Cache.synchronizedMutableCache)

  def lockBasedMutableCache[A, B, F[_]: Sync]: Cache.MutableCache[A, B, F] =
    new ProxySyncCache(Cache.lockBasedMutableCache)
}
