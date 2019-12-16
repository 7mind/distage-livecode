package livecode

import izumi.distage.framework.model.PluginSource
import izumi.distage.model.definition.Activation
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.distage.plugins.load.PluginLoader.PluginConfig
import izumi.distage.roles.{RoleAppLauncher, RoleAppMain}
import izumi.fundamentals.platform.cli.model.raw.RawRoleParams
import livecode.code.LivecodeRole

object MainDummy extends MainBase(Activation(Repo -> Repo.Dummy))

/** To launch production configuration, you need postgres to be available at localhost:5432.
  * To set it up with Docker, execute the following command:
  *
  * {{{
  *   docker run -d -p 5432:5432 postgres:latest
  * }}}
  */
object MainProd extends MainBase(Activation(Repo -> Repo.Prod))

abstract class MainBase(activation: Activation)
  extends RoleAppMain.Default(
    launcher = new RoleAppLauncher.LauncherBIO[zio.IO] {
      override val pluginSource = PluginSource(
        PluginConfig(
          debug            = false,
          packagesEnabled  = Seq("livecode.plugins"),
          packagesDisabled = Nil,
        )
      )
      override val requiredActivations = activation
    }
  ) {
  override val requiredRoles: Vector[RawRoleParams] = {
    Vector(RawRoleParams(LivecodeRole.id))
  }
}
