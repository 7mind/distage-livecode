package livecode.plugins

import distage.TagKK
import distage.plugins.PluginDef
import doobie.util.transactor.Transactor
import izumi.distage.config.ConfigModuleDef
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.fundamentals.platform.integration.PortCheck
import livecode.code.Postgres.{PgIntegrationCheck, PostgresCfg, PostgresPortCfg}
import livecode.code._
import org.http4s.dsl.Http4sDsl
import zio.IO

object LivecodePlugin extends PluginDef {
  include(modules.api[IO])
  include(modules.repoProd[IO])
  include(modules.repoDummy[IO])
  include(modules.configProd)

  object modules {
    def api[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
      make[LivecodeRole[F]]

      make[HttpApi[F]].from[HttpApi.Impl[F]]
      make[Ranks[F]].from[Ranks.Impl[F]]

      make[Http4sDsl[F[Throwable, ?]]].from {
        new Http4sDsl[F[Throwable, ?]] {}
      }
    }

    def repoProd[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
      tag(Repo.Prod)

      make[Ladder[F]].fromResource[Ladder.Postgres[F]]
      make[Profiles[F]].fromResource[Profiles.Postgres[F]]

      make[SQL[F]].from[SQL.Impl[F]]

      make[Transactor[F[Throwable, ?]]].fromResource(Postgres.resource[F[Throwable, ?]] _)
      make[PgIntegrationCheck]
      make[PortCheck].from(new PortCheck(3))
    }

    def repoDummy[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
      tag(Repo.Dummy)

      make[Ladder[F]].fromResource[LadderDummy[F]]
      make[Profiles[F]].fromResource[ProfilesDummy[F]]
    }

    val configProd = new ConfigModuleDef {
      makeConfig[PostgresCfg]("postgres")
      makeConfig[PostgresPortCfg]("postgres")
    }
  }
}