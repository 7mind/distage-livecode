package livecode

import distage.{DIKey, ModuleDef}
import doobie.util.transactor.Transactor
import izumi.distage.docker.examples.PostgresDocker
import izumi.distage.framework.model.PluginSource
import izumi.distage.model.definition.Activation
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.distage.plugins.load.PluginLoader.PluginConfig
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.scalatest.DistageBIOSpecScalatest
import izumi.distage.testkit.services.DISyntaxZIOEnv
import livecode.code._
import zio.{IO, Task, ZIO}

abstract class LivecodeTest extends DistageBIOSpecScalatest[IO] with DISyntaxZIOEnv {
  override def config = TestConfig(
    pluginSource = Some(PluginSource(PluginConfig(packagesEnabled = Seq("livecode.plugins")))),
    activation   = Activation(Repo -> Repo.Prod),
    moduleOverrides = new ModuleDef {
      make[Rnd[IO]].from[Rnd.Impl[IO]]
      include(PostgresDockerModule)
    },
    memoizedKeys = Set(
      DIKey.get[Transactor[Task]],
      DIKey.get[Ladder[IO]],
      DIKey.get[Profiles[IO]],
      DIKey.get[PostgresDocker.Container],
    ),
  )
}

trait DummyTest extends LivecodeTest {
  override final def config = super.config.copy(
    activation = Activation(Repo -> Repo.Dummy),
  )
}

final class LadderTestDummy extends LadderTestPostgres with DummyTest
final class ProfilesTestDummy extends ProfilesTestPostgres with DummyTest
final class RanksTestDummy extends RanksTestPostgres with DummyTest

class LadderTestPostgres extends LivecodeTest with DummyTest {

  "Ladder" should {
    // this test gets dependencies through arguments
    "submit & get" in {
      (rnd: Rnd[IO], ladder: Ladder[IO]) =>
        for {
          user  <- rnd[UserId]
          score <- rnd[Score]
          _     <- ladder.submitScore(user, score)
          res   <- ladder.getScores.map(_.find(_._1 == user).map(_._2))
          _     = assert(res contains score)
        } yield ()
    }

    // other tests get dependencies via ZIO Env:
    "return higher score higher in the list" in {
      for {
        user1  <- Rnd[UserId]
        score1 <- Rnd[Score]
        user2  <- Rnd[UserId]
        score2 <- Rnd[Score]

        _      <- Ladder.submitScore(user1, score1)
        _      <- Ladder.submitScore(user2, score2)
        scores <- Ladder.getScores

        user1Rank = scores.indexWhere(_._1 == user1)
        user2Rank = scores.indexWhere(_._1 == user2)

        _ = if (score1 > score2) {
          assert(user1Rank < user2Rank)
        } else if (score2 > score1) {
          assert(user2Rank < user1Rank)
        }
      } yield ()
    }
  }

}

class ProfilesTestPostgres extends LivecodeTest {
  "Profiles" should {
    // that's what the env signature looks like for ZIO Env injection
    "set & get" in {
      val zioValue: ZIO[Profiles[IO]#Env with Rnd[IO]#Env, QueryFailure, Unit] = for {
        user    <- Rnd[UserId]
        name    <- Rnd[String]
        desc    <- Rnd[String]
        profile = UserProfile(name, desc)
        _       <- Profiles.setProfile(user, profile)
        res     <- Profiles.getProfile(user)
        _       = assert(res contains profile)
      } yield ()
      zioValue
    }
  }
}

class RanksTestPostgres extends LivecodeTest {
  "Ranks" should {
    "return None for a user with no score" in {
      for {
        user    <- Rnd[UserId]
        name    <- Rnd[String]
        desc    <- Rnd[String]
        profile = UserProfile(name, desc)
        _       <- Profiles.setProfile(user, profile)
        res1    <- Ranks.getRank(user)
        _       = assert(res1.isEmpty)
      } yield ()
    }

    "return None for a user with no profile" in {
      for {
        user  <- Rnd[UserId]
        score <- Rnd[Score]
        _     <- Ladder.submitScore(user, score)
        res1  <- Ranks.getRank(user)
        _     = assert(res1.isEmpty)
      } yield ()
    }

    "assign a higher rank to a user with more score" in {
      for {
        user1  <- Rnd[UserId]
        name1  <- Rnd[String]
        desc1  <- Rnd[String]
        score1 <- Rnd[Score]

        user2  <- Rnd[UserId]
        name2  <- Rnd[String]
        desc2  <- Rnd[String]
        score2 <- Rnd[Score]

        _ <- Profiles.setProfile(user1, UserProfile(name1, desc1))
        _ <- Ladder.submitScore(user1, score1)

        _ <- Profiles.setProfile(user2, UserProfile(name2, desc2))
        _ <- Ladder.submitScore(user2, score2)

        user1Rank <- Ranks.getRank(user1).map(_.get.rank)
        user2Rank <- Ranks.getRank(user2).map(_.get.rank)

        _ = if (score1 > score2) {
          assert(user1Rank < user2Rank)
        } else if (score2 > score1) {
          assert(user2Rank < user1Rank)
        }
      } yield ()
    }
  }
}
