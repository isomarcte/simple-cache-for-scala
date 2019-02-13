package io.isomarcte.simple.cache.core

import java.util.{
  Map => JMap,
  WeakHashMap => JWeakHashMap,
  Collections => JCollections,
  Objects => JObjects
}
import java.util.concurrent._
import java.util.concurrent.locks._
import java.util.function.{BiFunction => JBiFunction}

trait Cache[A, B, C, F[_]] {
  final def modify(key: A, f: Option[ExpiringEntry[B]] => Option[ExpiringEntry[B]]): F[(C, Option[B])] =
    this.modifyWithKey(key, Function.const(f))
  final def remove_(key: A): F[C] =
    this.fmap[(C, Option[B]), C](this.remove(key), _._1)
  protected final def void(f: F[_]): F[Unit] =
    this.pure(())
  def clear: F[Unit]
  def get(key: A): F[Option[B]]
  def modifyWithKey(key: A, f: (A => Option[ExpiringEntry[B]] => Option[ExpiringEntry[B]])): F[(C, Option[B])]

  protected def fmap[D, E](fc: F[D], f: D => E): F[E]
  protected def pure[D](d: D): F[D]

  def putIfAbsent(key: A, value: ExpiringEntry[B]): F[(C, Option[B])] =
    this.modify(key, o => Some(o.getOrElse(value)))
  def replace(key: A, value: ExpiringEntry[B]): F[(C, Option[B])] =
    this.modify(key, Function.const(Some(value)))
  final def replace_(key: A, value: ExpiringEntry[B]): F[C] =
    this.fmap[(C, Option[B]), C](replace(key, value), _._1)
  def remove(key: A): F[(C, Option[B])] =
    this.modify(key, Function.const(None))
}

object Cache {

  private[this] def initialCapacityErrorString(initialCapacity: Int): String =
    s"Invalid Initial Capacity (Must be >= 0): $initialCapacity"

  private[this] def loadFactorErrorString(loadFactor: Float): String =
    s"Invalid Load Factor (Must be > 0): $loadFactor"

  type MutableCache[A, B, F[_]] = Cache[A, B, Unit, F]
  type SimpleMutableCache[A, B] = MutableCache[A, B, Id]

  private[this] def noneIfExpired[A](e: ExpiringEntry[A]): Option[A] =
    if (e.isExpired) {
      None
    } else {
      Some(e.value)
    }

  private[this] def unitTupled[A](a: A): (Unit, A) = ((), a)

  private[this] final class Simple[A, B](m: JMap[A, Option[ExpiringEntry[B]]]) extends Cache[A, B, Unit, Id] {
    override final protected def fmap[D, E](fc: Id[D], f: D => E): Id[E] = f(fc)
    override final protected def pure[D](d: D): Id[D] = d
    override final def clear: Id[Unit] = this.m.clear
    override final def modifyWithKey(key: A, f: (A => Option[ExpiringEntry[B]] => Option[ExpiringEntry[B]])): Id[(Unit, Option[B])] =
      Tuple2(
        (),
        this.m.compute(
          key,
          new JBiFunction[A, Option[ExpiringEntry[B]], Option[ExpiringEntry[B]]]{
            override def apply(key: A, currentValue: Option[ExpiringEntry[B]]): Option[ExpiringEntry[B]] =
              if (JObjects.isNull(currentValue) || currentValue.map(_.isExpired).getOrElse(false)) {
                f(key)(None)
              } else {
                f(key)(currentValue)
              }
          }
        ).flatMap(noneIfExpired)
      )

    override final def remove(key: A): Id[(Unit, Option[B])] = {
      val v: Option[ExpiringEntry[B]] = this.m.remove(key)
      unitTupled(
        if (JObjects.isNull(v)) {
          None
        } else {
          v.flatMap(noneIfExpired)
        }
      )
    }

    override final def get(key: A): Id[Option[B]] =
      this.m.getOrDefault(key, None) match {
        case Some(v) if v.isExpired =>
          this.remove_(key)
          None
        case otherwise => otherwise.flatMap(noneIfExpired)
      }
  }

  private[this] final class SimpleLockBased[A, B](fair: Boolean) extends Cache[A, B, Unit, Id] {
    private val m: JWeakHashMap[A, Option[ExpiringEntry[B]]] = new JWeakHashMap()
    private val rwLock: ReadWriteLock = new ReentrantReadWriteLock(fair)

    override final protected def fmap[D, E](fc: Id[D], f: D => E): Id[E] = f(fc)
    override final protected def pure[D](d: D): Id[D] = d

    override final def clear: Id[Unit] = {
      this.rwLock.writeLock.lockInterruptibly()
      this.m.clear
      this.rwLock.writeLock.unlock()
    }

    override final def modifyWithKey(key: A, f: (A => Option[ExpiringEntry[B]] => Option[ExpiringEntry[B]])): Id[(Unit, Option[B])] = {
      this.rwLock.writeLock.lockInterruptibly()
      val result: Option[ExpiringEntry[B]] =
        this.m.compute(
          key,
          new JBiFunction[A, Option[ExpiringEntry[B]], Option[ExpiringEntry[B]]]{
            override def apply(key: A, currentValue: Option[ExpiringEntry[B]]): Option[ExpiringEntry[B]] =
              if (JObjects.isNull(currentValue) || currentValue.map(_.isExpired).getOrElse(false)) {
                f(key)(None)
              } else {
                f(key)(currentValue)
              }
          }
        )
      this.rwLock.writeLock.unlock()
      unitTupled(result.flatMap(noneIfExpired))
    }

    override final def remove(key: A): Id[(Unit, Option[B])] = {
      this.rwLock.writeLock.lockInterruptibly()
      val value: Option[ExpiringEntry[B]] = this.m.remove(key)
      this.rwLock.writeLock.unlock()
      if (JObjects.isNull(value)) {
        unitTupled(None)
      } else {
        unitTupled(value.flatMap(noneIfExpired))
      }
    }

    override final def get(key: A): Id[Option[B]] = {
      this.rwLock.readLock.lockInterruptibly()
      val value: Option[ExpiringEntry[B]] = this.m.getOrDefault(key, None)
      this.rwLock.readLock.unlock()
      value match {
        case Some(v) if v.isExpired =>
          this.remove_(key)
          None
        case otherwise => otherwise.flatMap(noneIfExpired)
      }
    }
  }

  def simpleCache[A, B](
    initialCapacity: Int,
    loadFactor: Float
  ): Either[String, SimpleMutableCache[A, B]] = {
    val invalidInitialCapacity: Boolean = initialCapacity < 0
    val invalidLoadFactor: Boolean = loadFactor <= 0.toFloat // Seems like > 1 should be invalid, but Java allows it.
    lazy val initialCapacityErrorS: String = initialCapacityErrorString(initialCapacity)
    lazy val loadFactorErrorS: String = loadFactorErrorString(loadFactor)
    if (invalidInitialCapacity && invalidLoadFactor) {
      Left(
        List(
          "Cache Initialization Error:",
          initialCapacityErrorS,
          loadFactorErrorS
        ).mkString("\n\t")
      )
    } else if (invalidInitialCapacity) {
      Left(initialCapacityErrorS)
    } else if (invalidLoadFactor) {
      Left(loadFactorErrorS)
    } else {
      Right(simpleCacheUnsafe(initialCapacity, loadFactor))
    }
  }

  def synchronizedMutableCache[A, B]: SimpleMutableCache[A, B] = {
    val m: JMap[A, Option[ExpiringEntry[B]]] =
      JCollections.synchronizedMap(new JWeakHashMap[A, Option[ExpiringEntry[B]]]())
    new Simple(m)
  }

  def lockBasedMutableCache[A, B]: SimpleMutableCache[A, B] =
    new SimpleLockBased(false)

  def simpleCacheDeafults[A, B]: SimpleMutableCache[A, B] =
    new Simple(new JWeakHashMap[A, Option[ExpiringEntry[B]]])

  def simpleCacheUnsafe[A, B](
    initialCapacity: Int,
    loadFactor: Float
  ): SimpleMutableCache[A, B] = new Simple(new JWeakHashMap[A, Option[ExpiringEntry[B]]](initialCapacity, loadFactor))
}
