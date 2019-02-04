package io.isomarcte.simple.cache.jmh

import io.isomarcte.simple.cache._
import org.openjdk.jmh.annotations._

// class TestModelBenchmark {
//   import TestModelBenchmark._

//   @Benchmark
//   def everythingIsACacheMiss(state: BenchmarkState): TestModel = {
//     TestModel.cached(TestModel.fromLong(state.getState))
//   }

//   @Benchmark
//   def uncachedTestModel(state: BenchmarkState): TestModel =
//     TestModel.fromLong(state.getState)
// }

// object TestModelBenchmark {

//   private final case class TestModel(l: Long, s: String)

//   private object TestModel {
//     def fromLong(long: Long): TestModel =
//       TestModel(long, long.toString)
//   }

//   @State(Scope.Thread)
//   class BenchmarkState {
//     @volatile
//     private var long: Long = Long.MinValue
//     private val cache: Cache.SimpleCache[TestModel, TestModel] =
//       Cache.concurrentSimpleCacheUnsafeDefaults

//     def cached(tm: TestModel): TestModel =
//       this.cache.get(tm).getOrElse{
//         cache.putIfAbsent(tm, tm)
//         cache.get(tm).getOrElse(tm)
//       }

//     def getState: Long = {
//       val currentValue: Long = this.long
//       this.long = currentValue + 1L
//       currentValue
//     }
//   }
// }
