package livecode

import distage._
import izumi.distage.LoggerHookDefaultImpl
import izumi.distage.commons.{TraitInitTool, UnboxingTool}
import izumi.distage.model.definition.{BootstrapModule => _, ModuleBase => _, ModuleDef => _, _}
import izumi.distage.model.monadic.{DIEffect, DIEffectRunner}
import izumi.distage.model.planning._
import izumi.distage.model.provisioning.strategies._
import izumi.distage.model.provisioning.{PlanInterpreter, ProvisioningFailureInterceptor, ProvisioningFailureInterceptorDefaultImpl}
import izumi.distage.model.reflection.universe.MirrorProvider
import izumi.distage.model.reflection.{DependencyKeyProvider, ReflectionProvider, SymbolIntrospector}
import izumi.distage.model.{LoggerHook, Planner}
import izumi.distage.planning.gc.TracingDIGC
import izumi.distage.planning._
import izumi.distage.plugins.load.PluginLoader.PluginConfig
import izumi.distage.provisioning.strategies._
import izumi.distage.provisioning.{PlanInterpreterDefaultRuntimeImpl, ProvisionOperationVerifier}
import izumi.distage.reflection.{DependencyKeyProviderDefaultImpl, ReflectionProviderDefaultImpl, SymbolIntrospectorDefaultImpl}
import izumi.distage.roles.model.{AppActivation, IntegrationCheck}
import izumi.distage.roles.services.ModuleProviderImpl.ContextOptions
import izumi.distage.roles.services.PluginSource.AllLoadedPlugins
import izumi.distage.roles.services.RoleAppPlanner.AppStartupPlans
import izumi.distage.roles.services._
import izumi.distage.roles.{BootstrapConfig, RoleAppLauncher, RoleAppMain}
import izumi.fundamentals.platform.cli.model.raw.RawRoleParams
import izumi.logstage.api.IzLogger
import livecode.code.LivecodeRole
import livecode.plugins.{LivecodePlugin, ZIOPlugin}
import zio.Task
import zio.interop.catz._

object Main
  extends RoleAppMain.Default[Task](
    launcher = new RoleAppLauncher.LauncherF[Task] {
      override val bootstrapConfig = BootstrapConfig(
        PluginConfig(
          debug            = false,
          packagesEnabled  = Nil,
          packagesDisabled = Nil,
        )
      )

      override protected def makePluginLoader(bootstrapConfig: BootstrapConfig): PluginSource = new PluginSource {
        override def load(): PluginSource.AllLoadedPlugins = AllLoadedPlugins(
          bootstrap = Nil,
          app       = Seq(LivecodePlugin, ZIOPlugin)
        )
      }

      override protected def makePlanner(options: ModuleProviderImpl.ContextOptions,
                                         bsModule: BootstrapModule,
                                         activation: AppActivation,
                                         lateLogger: IzLogger): RoleAppPlanner[Task] = {
        new RoleAppPlannerStatic[Task](options, bsModule, activation, lateLogger)
      }
    }
  ) {
  override val requiredRoles: Vector[RawRoleParams] = {
    Vector(RawRoleParams.empty(LivecodeRole.id))
  }
}

class RoleAppPlannerStatic[F[_]: TagK](
  options: ContextOptions,
  bsModule: BootstrapModule,
  activation: AppActivation,
  logger: IzLogger,
) extends RoleAppPlannerImpl[F](options, bsModule, activation, logger) {

  private val injector = Injector(
    StaticBootstrap.defaultBootstrap,
    bsModule.flatMap {
      case Binding.SetElementBinding(key, impl, _, _) if key.tpe =:= SafeType.get[Set[PlanningHook]] && impl.implType =:= SafeType.get[ResourceRewriter] =>
        println("removed resource rewriter")
        Nil
      case k => k :: Nil
    }
  )

  override def reboot(bsOverride: BootstrapModule): RoleAppPlannerStatic[F] = {
    new RoleAppPlannerStatic[F](options, bsModule overridenBy bsOverride, activation, logger)
  }

  override def makePlan(appMainRoots: Set[DIKey], appModule: ModuleBase): AppStartupPlans = {
    val fullAppModule = appModule
      .overridenBy(new ModuleDef {
        make[RoleAppPlanner[F]].fromValue(RoleAppPlannerStatic.this)
        make[ContextOptions].from(options)
        make[ModuleBase].named("application.module").from(appModule)
      })

    val runtimeGcRoots: Set[DIKey] = Set(
      DIKey.get[DIEffectRunner[F]],
      DIKey.get[DIEffect[F]],
    )
    val runtimePlan = injector.plan(PlannerInput(fullAppModule, runtimeGcRoots))

    val appPlan = injector.splitPlan(fullAppModule.drop(runtimeGcRoots), appMainRoots) {
      _.collectChildren[IntegrationCheck].map(_.target).toSet
    }

    val check = new PlanCircularDependencyCheck(options, logger)
    check.verify(runtimePlan)
    check.verify(appPlan.subplan)
    check.verify(appPlan.primary)

    AppStartupPlans(
      runtimePlan,
      appPlan.subplan,
      appPlan.subRoots,
      appPlan.primary,
      injector
    )
  }
}

object StaticBootstrap {
  final val defaultBootstrap: BootstrapContextModule = new BootstrapContextModuleDef with StaticDSL {
    make[ReflectionProvider.Runtime].stat[ReflectionProviderDefaultImpl.Runtime]
    make[SymbolIntrospector.Runtime].stat[SymbolIntrospectorDefaultImpl.Runtime]
    make[DependencyKeyProvider.Runtime].stat[DependencyKeyProviderDefaultImpl.Runtime]

    make[LoggerHook].stat[LoggerHookDefaultImpl]
    make[MirrorProvider].fromValue(MirrorProvider.Impl)

    make[UnboxingTool]
    make[TraitInitTool]
    make[ProvisionOperationVerifier].stat[ProvisionOperationVerifier.Default]

    make[DIGarbageCollector].fromValue(TracingDIGC)

    make[PlanAnalyzer].stat[PlanAnalyzerDefaultImpl]
    make[PlanMergingPolicy].stat[PlanMergingPolicyDefaultImpl]
    make[Boolean].named("distage.init-proxies-asap").fromValue(true)
    make[ForwardingRefResolver].stat[ForwardingRefResolverDefaultImpl]
    make[SanityChecker].stat[SanityCheckerDefaultImpl]
    make[Planner].stat[PlannerDefaultImpl]
    make[SetStrategy].stat[SetStrategyDefaultImpl]
    make[ProviderStrategy].stat[ProviderStrategyDefaultImpl]
    make[FactoryProviderStrategy].stat[FactoryProviderStrategyDefaultImpl]
    make[ImportStrategy].stat[ImportStrategyDefaultImpl]
    make[InstanceStrategy].stat[InstanceStrategyDefaultImpl]
    make[EffectStrategy].stat[EffectStrategyDefaultImpl]
    make[ResourceStrategy].stat[ResourceStrategyDefaultImpl]
    make[PlanInterpreter].stat[PlanInterpreterDefaultRuntimeImpl]
    make[ProvisioningFailureInterceptor].stat[ProvisioningFailureInterceptorDefaultImpl]

    many[PlanningObserver]
    many[PlanningHook]
    make[PlanningHook].stat[PlanningHookAggregate]
    make[PlanningObserver].stat[PlanningObserverAggregate]

    make[BindingTranslator].stat[BindingTranslatorImpl]

    make[ProxyProvider].stat[ProxyProviderFailingImpl]
    make[ClassStrategy].stat[ClassStrategyFailingImpl]
    make[ProxyStrategy].stat[ProxyStrategyFailingImpl]
    make[FactoryStrategy].stat[FactoryStrategyFailingImpl]
    make[TraitStrategy].stat[TraitStrategyFailingImpl]
  }
}
