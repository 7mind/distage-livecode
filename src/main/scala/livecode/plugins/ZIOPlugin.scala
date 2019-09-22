package livecode.plugins

import java.util.concurrent.ThreadPoolExecutor

import cats.effect.{Async, Blocker, Concurrent, ConcurrentEffect, ContextShift, Sync, Timer}
import distage.{Id, ModuleDef}
import distage.plugins.PluginDef
import izumi.distage.model.definition.StaticModuleDef
import izumi.distage.model.monadic.DIEffectRunner
import izumi.distage.monadic.modules.ZIODIEffectModule
import livecode.Bracket2
import livecode.plugins.ZIOPlugin.{addImplicit, make}
import logstage.LogBIO
import zio.interop.catz._
import zio.interop.catz.implicits._
import zio.{IO, Task}

import scala.concurrent.ExecutionContext

object ZIOPlugin extends PluginDef {
  include(new ZIODIEffectModule {} overridenBy new StaticModuleDef {
    make[DIEffectRunner[IO[Throwable, ?]]].stat[DIEffectRunner.BIOImpl[IO]]

    addImplicit[Bracket2[IO]]
    addImplicit[Sync[Task]]
    addImplicit[Async[Task]]
    addImplicit[Concurrent[Task]]
    addImplicit[ContextShift[Task]]
    addImplicit[Timer[Task]]
    make[ConcurrentEffect[Task]].from((runtime: zio.Runtime[Any]) => taskEffectInstance(runtime))

    make[Blocker].from {
      pool: ThreadPoolExecutor @Id("zio.io") =>
        Blocker.liftExecutionContext(ExecutionContext.fromExecutorService(pool))
    }

    make[LogBIO[IO]].from(LogBIO.fromLogger[IO] _)
  })
}
