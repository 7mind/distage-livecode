package livecode

import livecode.code._
import org.scalacheck.Arbitrary
import zio.{IO, ZIO}

import scala.language.reflectiveCalls

object zioenv {

  object ladder extends Ladder[ZIO[Ladder[IO]#Env, ?, ?]] {
    def submitScore(userId: UserId, score: Score) = ZIO.accessM(_.ladder.submitScore(userId, score))
    def getScores                                 = ZIO.accessM(_.ladder.getScores)
  }

  object profiles extends Profiles[ZIO[Profiles[IO]#Env, ?, ?]] {
    override def setProfile(userId: UserId, profile: UserProfile) = ZIO.accessM(_.profiles.setProfile(userId, profile))
    override def getProfile(userId: UserId)                       = ZIO.accessM(_.profiles.getProfile(userId))
  }

  object ranks extends Ranks[ZIO[Ranks[IO]#Env, ?, ?]] {
    override def getRank(userId: UserId) = ZIO.accessM(_.ranks.getRank(userId))
  }

  object rnd extends Rnd[ZIO[Rnd[IO]#Env, ?, ?]] {
    override def apply[A: Arbitrary] = ZIO.accessM(_.rnd.apply[A])
  }

}
