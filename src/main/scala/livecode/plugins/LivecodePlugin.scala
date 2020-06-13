package livecode.plugins

import distage.TagKK
import distage.plugins.PluginDef
import doobie.util.transactor.Transactor
import izumi.distage.config.ConfigModuleDef
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.distage.roles.bundled.BundledRolesModule
import izumi.fundamentals.platform.integration.PortCheck
import livecode.code.Postgres.{PgIntegrationCheck, PostgresCfg, PostgresPortCfg}
import livecode.code._
import org.http4s.dsl.Http4sDsl
import zio.IO

import scala.concurrent.duration._

object LivecodePlugin extends PluginDef {
  include(modules.roles[IO])
  include(modules.api[IO])
  include(modules.repoProd[IO])
  include(modules.repoDummy[IO])
  include(modules.configs)

  object modules {
    def roles[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
      // The `livecode` app
      make[LivecodeRole[F]]

      // Bundled roles: `help` & `configwriter`
      include(BundledRolesModule[F[Throwable, ?]](version = "1.0.0-SNAPSHOT"))
    }

    def api[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
      make[LivecodeRole[F]]

      make[HttpApi[F]].from[HttpApi.Impl[F]]
      make[Ranks[F]].from[Ranks.Impl[F]]

      make[Http4sDsl[F[Throwable, ?]]]
    }

    def repoProd[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
      tag(Repo.Prod)

      make[Ladder[F]].fromResource[Ladder.Postgres[F]]
      make[Profiles[F]].fromResource[Profiles.Postgres[F]]

      make[SQL[F]].from[SQL.Impl[F]]

      make[Transactor[F[Throwable, ?]]].fromResource(Postgres.resource[F[Throwable, ?]] _)
      make[PgIntegrationCheck]
      make[PortCheck].from(new PortCheck(3.milliseconds))
    }

    def repoDummy[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
      tag(Repo.Dummy)

      make[Ladder[F]].fromResource[LadderDummy[F]]
      make[Profiles[F]].fromResource[ProfilesDummy[F]]
    }

    val configs: ConfigModuleDef = new ConfigModuleDef {
      makeConfig[PostgresCfg]("postgres")
      makeConfig[PostgresPortCfg]("postgres")
    }
  }
}
