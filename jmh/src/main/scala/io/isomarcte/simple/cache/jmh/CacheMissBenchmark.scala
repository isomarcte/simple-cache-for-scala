package io.isomarcte.simple.cache.jmh

import cats.effect._
import io.isomarcte.simple.cache.cats._
import io.isomarcte.simple.cache.core._
import org.openjdk.jmh.annotations._

class CacheMissBenchmark {
  import CacheMissBenchmark._

  @Benchmark
  def catsLockCached(state: CatsLockSynchronizedBenchmarkState): Bar =
    state.cacheGet(20L).unsafeRunSync

  @Benchmark
  def catsCached(state: CatsSynchronizedBenchmarkState): Bar =
    state.cacheGet(20L).unsafeRunSync

  @Benchmark
  def lockCached(state: LockSynchronizedBenchmarkState): Bar =
    state.cacheGet(20L)

  @Benchmark
  def cached(state: SynchronizedBenchmarkState): Bar =
    state.cacheGet(20L)

  @Benchmark
  def uncached(state: SynchronizedBenchmarkState): Bar =
    state.missGet(20L)
}

object CacheMissBenchmark {
  final case class Foo(i: Int)
  final case class Bar(s: String)

  // Many Foos, Just a few Bars

  val fooCount: Int = 10000
  val barCount: Int = 10

  val m: Map[Int, String] = {
    val foos: Vector[Int] =
      (0 until fooCount).toVector
    foos.foldLeft((Map.empty[Int, String], 0)){
      case ((acc, state), value) if state == (barCount - 1) =>
        (acc + ((value, state.toString)), state)
      case ((acc, state), value) =>
        (acc + ((value, state.toInt.toString)), state + 1)
    }._1
  }

  abstract class AbstractBenchmarkState[A, B, F[_]] {
    @volatile private var state: Int = 0
    protected val cache: Cache.MutableCache[A, B, F[_]]
    private def getAndUpdateState: Int = {
      val currentValue: Int = this.state
      if (currentValue == (barCount - 1)) {
        this.state = 0
        currentValue
      } else {
        this.state = currentValue + 1
        currentValue
      }
    }
  }

  final class SynchronizedBenchmarkState extends AbstractBenchmarkState {
    private val cache:
  }

  @State(Scope.Benchmark)
  class SynchronizedBenchmarkState {
    @volatile
    private var state: Int = 0

    private val cache: Cache.SimpleMutableCache[Foo, Bar] =
      Cache.concurrentSimpleCacheDefaults

    private def getState: Int = {
      val currentValue: Int = this.state
      if (currentValue == (barCount - 1)) {
        this.state = 0
        currentValue
      } else {
        this.state = currentValue + 1
        currentValue
      }
    }

    private def missGetWithFoo(cost: Long, foo: Foo): Bar = {
      Thread.sleep(cost)
      Bar(m(foo.i))
    }

    private def cacheGetWithFoo(cost: Long, f: Foo): Bar =
      this.cache.get(f) match {
        case Some(bar) => bar
        case _ =>
          val bar: Bar = this.missGetWithFoo(cost, f)
          this.cache.putIfAbsent(foo, bar)
          bar
      }

    private def foo: Foo =
      Foo(this.getState)

    def missGet(cost: Long): Bar =
      this.missGetWithFoo(cost, foo)

    def cacheGet(cost: Long): Bar =
      this.cacheGetWithFoo(cost, foo)
  }

  @State(Scope.Benchmark)
  class LockSynchronizedBenchmarkState {
    @volatile
    private var state: Int = 0

    private val cache: Cache.SimpleMutableCache[Foo, Bar] =
      Cache.lockConcurrentSimpleMutableCacheDefaults

    private def getState: Int = {
      val currentValue: Int = this.state
      if (currentValue == (barCount - 1)) {
        this.state = 0
        currentValue
      } else {
        this.state = currentValue + 1
        currentValue
      }
    }

    private def missGetWithFoo(cost: Long, foo: Foo): Bar = {
      Thread.sleep(cost)
      Bar(m(foo.i))
    }

    private def cacheGetWithFoo(cost: Long, f: Foo): Bar =
      this.cache.get(f) match {
        case Some(bar) => bar
        case _ =>
          val bar: Bar = this.missGetWithFoo(cost, f)
          this.cache.putIfAbsent(foo, bar)
          bar
      }

    private def foo: Foo =
      Foo(this.getState)

    def missGet(cost: Long): Bar =
      this.missGetWithFoo(cost, foo)

    def cacheGet(cost: Long): Bar =
      this.cacheGetWithFoo(cost, foo)
  }

  @State(Scope.Benchmark)
  class CatsSynchronizedBenchmarkState {
    @volatile
    private var state: Int = 0

    private val cache: Cache.MutableCache[Foo, Bar, IO] =
      CatsCache.concurrentSimpleCatsCacheDefaults[Foo, Bar, IO]

    private def getState: Int = {
      val currentValue: Int = this.state
      if (currentValue == (barCount - 1)) {
        this.state = 0
        currentValue
      } else {
        this.state = currentValue + 1
        currentValue
      }
    }

    private def missGetWithFoo(cost: Long, foo: Foo): Bar = {
      Thread.sleep(cost)
      Bar(m(foo.i))
    }

    private def cacheGetWithFoo(cost: Long, f: Foo): IO[Bar] =
      this.cache.get(f).flatMap{
        case Some(bar) => IO.pure(bar)
        case _ =>
          val bar: Bar = this.missGetWithFoo(cost, f)
          this.cache.putIfAbsent(foo, bar).map(Function.const(bar))
      }

    private def foo: Foo =
      Foo(this.getState)

    def missGet(cost: Long): Bar =
      this.missGetWithFoo(cost, foo)

    def cacheGet(cost: Long): IO[Bar] =
      this.cacheGetWithFoo(cost, foo)
  }

  @State(Scope.Benchmark)
  class CatsLockSynchronizedBenchmarkState {
    @volatile
    private var state: Int = 0

    private val cache: Cache.MutableCache[Foo, Bar, IO] =
      CatsCache.lockConcurrentSimpleCatsCacheDefaults

    private def getState: Int = {
      val currentValue: Int = this.state
      if (currentValue == (barCount - 1)) {
        this.state = 0
        currentValue
      } else {
        this.state = currentValue + 1
        currentValue
      }
    }

    private def missGetWithFoo(cost: Long, foo: Foo): Bar = {
      Thread.sleep(cost)
      Bar(m(foo.i))
    }

    private def cacheGetWithFoo(cost: Long, f: Foo): IO[Bar] =
      this.cache.get(f).flatMap{
        case Some(bar) => IO.pure(bar)
        case _ =>
          val bar: Bar = this.missGetWithFoo(cost, f)
          this.cache.putIfAbsent(foo, bar).map(Function.const(bar))
      }

    private def foo: Foo =
      Foo(this.getState)

    def missGet(cost: Long): Bar =
      this.missGetWithFoo(cost, foo)

    def cacheGet(cost: Long): IO[Bar] =
      this.cacheGetWithFoo(cost, foo)
  }
}
