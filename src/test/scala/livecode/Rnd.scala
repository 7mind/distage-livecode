package livecode

import izumi.functional.bio.{BIO, F}
import org.scalacheck.Gen.Parameters
import org.scalacheck.{Arbitrary, Prop}
import zio.{IO, ZIO}

trait Rnd[F[_, _]] {
  type Env = { def rnd: Rnd[F] }

  def apply[A: Arbitrary]: F[Nothing, A]
}

object Rnd extends Rnd[ZIO[Rnd[IO]#Env, ?, ?]] {
  final class Impl[F[+_, +_]: BIO] extends Rnd[F] {
    override def apply[A: Arbitrary]: F[Nothing, A] = {
      F.sync {
        val (p, s) = Prop.startSeed(Parameters.default)
        Arbitrary.arbitrary[A].pureApply(p, s)
      }
    }
  }

  override def apply[A: Arbitrary] = ZIO.accessM(_.rnd.apply[A])
}
