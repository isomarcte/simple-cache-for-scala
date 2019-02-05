package io.isomarcte.simple.cache.jmh

import cats.effect._
import io.isomarcte.simple.cache.cats._
import io.isomarcte.simple.cache.core._
import java.util.concurrent._
import org.openjdk.jmh.annotations._

@Warmup(iterations = 10, time = 20)
@Measurement(iterations = 15, time = 30)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
class CacheMissBenchmark {
  import CacheMissBenchmark._

  @Benchmark
  def ioFullySynchronizedCached(state: IOSynchronizedBenchmarkState): Bar =
    state.cacheGet(cacheMissCost).unsafeRunSync

  @Benchmark
  def ioLockCached(state: IOLockSynchronizedBenchmarkState): Bar =
    state.cacheGet(cacheMissCost).unsafeRunSync

  @Benchmark
  def lockCached(state: LockSynchronizedBenchmarkState): Bar =
    state.cacheGet(cacheMissCost)

  @Benchmark
  def cached(state: SynchronizedBenchmarkState): Bar =
    state.cacheGet(cacheMissCost)

  @Benchmark
  def uncached(state: SynchronizedBenchmarkState): Bar =
    state.missGet(cacheMissCost)
}

object CacheMissBenchmark {
  final case class Foo(i: Int)
  final case class Bar(s: String)

  // Many Foos, Just a few Bars

  val fooCount: Int = 10000
  val barCount: Int = 10
  val cacheMissCost: Long = 10L

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

  trait BenchmarkState[F[_]] {
    def cacheGet(missCost: Long): F[Bar]
    def missGet(missCost: Long): Bar
  }

  abstract class AbstractBenchmarkState[F[_]] extends BenchmarkState[F] {
    @volatile private var state: Int = 0
    protected def cache: Cache.MutableCache[Foo, Bar, F]
    protected def pure[A](a: A): F[A]
    protected def bind[A, B](fa: F[A], f: A => F[B]): F[B]

    protected def fmap[A, B](fa: F[A], f: A => B): F[B] =
      bind(fa, (a: A) => pure(f(a)))

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

    private def missGetWithFoo(cost: Long, foo: Foo): Bar = {
      Thread.sleep(cost)
      Bar(m(foo.i))
    }

    private def cacheGetWithFoo(cost: Long, f: Foo): F[Bar] =
      this.bind(
        this.cache.get(f),
        (ob: Option[Bar]) => ob match {
          case Some(bar) => this.pure(bar)
          case _ =>
            val bar: Bar = this.missGetWithFoo(cost, f)
            this.fmap(this.cache.putIfAbsent(f, bar), Function.const(bar))
        }
      )

    private def nextFoo(): Foo =
      Foo(this.getAndUpdateState)

    override final def cacheGet(cost: Long): F[Bar] =
      this.cacheGetWithFoo(cost, nextFoo)

    override final def missGet(cost: Long): Bar =
      this.missGetWithFoo(cost, nextFoo)
  }

  abstract class IdAbstractBenchmarkState extends AbstractBenchmarkState[Id] {
    override protected final def pure[A](a: A): Id[A] = a
    override protected final def bind[A, B](fa: Id[A], f: A => Id[B]): Id[B] = f(fa)
  }

  @State(Scope.Benchmark)
  class SynchronizedBenchmarkState extends IdAbstractBenchmarkState {
    override protected final val cache: Cache.MutableCache[Foo, Bar, Id] =
      Cache.synchronizedMutableCache
  }

  @State(Scope.Benchmark)
  class LockSynchronizedBenchmarkState extends IdAbstractBenchmarkState {
    override protected final val cache: Cache.MutableCache[Foo, Bar, Id] =
      Cache.lockBasedMutableCache
  }

  abstract class IOAbstractBenchmarkState extends AbstractBenchmarkState[IO] {
    override final def pure[A](a: A): IO[A] = IO.pure(a)
    override final def bind[A, B](fa: IO[A], f: A => IO[B]): IO[B] =
      fa.flatMap(f)
  }

  @State(Scope.Benchmark)
  class IOSynchronizedBenchmarkState extends IOAbstractBenchmarkState {
    override protected final val cache: Cache.MutableCache[Foo, Bar, IO] =
      CatsCache.synchronizedMutableCache
  }

  @State(Scope.Benchmark)
  class IOLockSynchronizedBenchmarkState extends IOAbstractBenchmarkState {
    override protected final val cache: Cache.MutableCache[Foo, Bar, IO] =
      CatsCache.lockBasedMutableCache
  }
}
