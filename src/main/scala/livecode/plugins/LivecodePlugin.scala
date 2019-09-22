package livecode.plugins

import distage.plugins.PluginDef
import distage.{TagK, TagKK}
import doobie.util.transactor.Transactor
import izumi.distage.model.definition.DIResource.{DIResourceBase, ResourceTag}
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.distage.model.definition.dsl.ModuleDefDSL.BindDSLBase
import izumi.distage.model.definition.{ModuleDef, StaticModuleDef}
import izumi.distage.provisioning.AnyConstructor
import izumi.fundamentals.platform.integration.PortCheck
import livecode.code.Postgres.PgIntegrationCheck
import livecode.code._
import org.http4s.dsl.Http4sDsl
import zio.IO

object LivecodePlugin extends StaticModuleDef with PluginDef {
  stat[LivecodeRole]

  include(modules.api[IO])
  include(modules.repoProd[IO])
  include(modules.repoDummy[IO])

  implicit final class StaticResourceBindDSL[T, AfterBind](private val dsl: BindDSLBase[T, AfterBind]) extends AnyVal {
    def statResource[R <: DIResourceBase[Any, T]: ResourceTag: AnyConstructor]: AfterBind =
      dsl.fromResource(AnyConstructor[R].provider)
  }

  object modules {
    def api[F[+_, +_]: TagKK](implicit ev: TagK[F[Throwable, ?]]): ModuleDef = new ModuleDef {
      make[HttpApi[F]].stat[HttpApi.Impl[F]]
      make[Ranks[F]].stat[Ranks.Impl[F]]

      make[Http4sDsl[F[Throwable, ?]]].from {
        new Http4sDsl[F[Throwable, ?]] {}
      }
    }

    def repoProd[F[+_, +_]: TagKK](implicit ev: TagK[F[Throwable, ?]], ev2: TagK[F[Nothing, ?]]): ModuleDef = new ModuleDef {
      tag(Repo.Prod)

      make[Ladder[F]].statResource[Ladder.Postgres[F]]
      make[Profiles[F]].statResource[Profiles.Postgres[F]]

      make[SQL[F]].stat[SQL.Impl[F]]

      make[Transactor[F[Throwable, ?]]].fromResource(Postgres.resource[F[Throwable, ?]] _)
      stat[PgIntegrationCheck]
      make[PortCheck].from(new PortCheck(3))
    }

    def repoDummy[F[+_, +_]: TagKK](implicit ev: TagK[F[Throwable, ?]]): ModuleDef = new ModuleDef {
      tag(Repo.Dummy)

      make[Ladder[F]].statResource[LadderDummy[F]]
      make[Profiles[F]].statResource[ProfilesDummy[F]]
    }
  }
}
