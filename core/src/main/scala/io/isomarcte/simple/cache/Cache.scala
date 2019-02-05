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
  def clear: F[Unit]
  def modifyWithKey(key: A, f: (A => Option[B] => Option[B])): F[(C, Option[B])]
  protected def functorMap[D, E](fc: F[D], f: D => E): F[E]
  protected def applicativePure[D](d: D): F[D]
  protected final def void(f: F[_]): F[Unit] =
    applicativePure(())

  final def modify(key: A, f: Option[B] => Option[B]): F[(C, Option[B])] =
    modifyWithKey(key, Function.const(f))

  def get(key: A): F[Option[B]] =
    functorMap[(C, Option[B]), Option[B]](modify(key, identity), _._2)
  def putIfAbsent(key: A, value: B): F[(C, Option[B])] =
    modify(key, _.map(Some(_)).getOrElse(Some(value)))
  def replace(key: A, value: B): F[(C, Option[B])] =
    modify(key, Function.const(Some(value)))
  def replace_(key: A, value: B): F[C] =
    functorMap[(C, Option[B]), C](replace(key, value), _._1)
}

object Cache {

  private[this] def initialCapacityErrorString(initialCapacity: Int): String =
    s"Invalid Initial Capacity (Must be >= 0): $initialCapacity"

  private[this] def loadFactorErrorString(loadFactor: Float): String =
    s"Invalid Load Factor (Must be > 0): $loadFactor"

  type MutableCache[A, B, F[_]] = Cache[A, B, Unit, F]
  type SimpleMutableCache[A, B] = MutableCache[A, B, Id]

  private[this] final class Simple[A, B](m: JMap[A, Option[B]]) extends Cache[A, B, Unit, Id] {
    override final protected def functorMap[D, E](fc: Id[D], f: D => E): Id[E] =
      f(fc)
    override final protected def applicativePure[D](d: D): Id[D] = d

    override final def clear: Id[Unit] = this.m.clear
    override final def modifyWithKey(key: A, f: (A => Option[B] => Option[B])): Id[(Unit, Option[B])] =
      Tuple2(
        (),
        this.m.compute(
          key,
          new JBiFunction[A, Option[B], Option[B]]{
            override def apply(key: A, currentValue: Option[B]): Option[B] =
              if (JObjects.isNull(currentValue)) {
                f(key)(None)
              } else {
                f(key)(currentValue)
              }
          }
        )
      )
  }

  private[this] final class SimpleLockBased[A, B](fair: Boolean) extends Cache[A, B, Unit, Id] {
    private val m: JWeakHashMap[A, Option[B]] = new JWeakHashMap()
    private val rwLock: ReadWriteLock = new ReentrantReadWriteLock(fair)

    override final protected def functorMap[D, E](fc: Id[D], f: D => E): Id[E] =
      f(fc)
    override final protected def applicativePure[D](d: D): Id[D] = d

    override final def clear: Id[Unit] = {
      this.rwLock.writeLock.lockInterruptibly()
      this.m.clear
      this.rwLock.writeLock.unlock
    }
    override final def modifyWithKey(key: A, f: (A => Option[B] => Option[B])): Id[(Unit, Option[B])] = {
      this.rwLock.writeLock.lockInterruptibly()
      val result: Option[B] =
        this.m.compute(
          key,
          new JBiFunction[A, Option[B], Option[B]]{
            override def apply(key: A, currentValue: Option[B]): Option[B] =
              if (JObjects.isNull(currentValue)) {
                f(key)(None)
              } else {
                f(key)(currentValue)
              }
          }
        )
      this.rwLock.writeLock.unlock()
      ((), result)
    }
    override final def get(key: A): Id[Option[B]] = {
      this.rwLock.readLock.lockInterruptibly()
      val value: Option[B] = this.m.getOrDefault(key, None)
      this.rwLock.readLock.unlock()
      value
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

  def concurrentSimpleCacheDefaults[A, B]: SimpleMutableCache[A, B] = {
    val m: JMap[A, Option[B]] =
      JCollections.synchronizedMap(new JWeakHashMap[A, Option[B]]())
    new Simple(m)
  }

  def lockConcurrentSimpleMutableCacheDefaults[A, B]: SimpleMutableCache[A, B] =
    new SimpleLockBased(false)

  def simpleCacheUnsafeDefaults[A, B]: SimpleMutableCache[A, B] =
    new Simple(new JWeakHashMap[A, Option[B]])

  def simpleCacheUnsafe[A, B](
    initialCapacity: Int,
    loadFactor: Float
  ): SimpleMutableCache[A, B] = new Simple(new JWeakHashMap[A, Option[B]](initialCapacity, loadFactor))
}
