package com.scalasci.hypersmac

import com.scalasci.hypersmac.api.RendersConfig
import com.scalasci.hypersmac.implemented.{
  BudgetedSampleFunction,
  FlatConfigSpace,
  GaussianPrior,
  HyperSMAC
}
import com.scalasci.hypersmac.model.Trial

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object HyperSMACFiddle extends App {
  val hs = new HyperSMAC[FlatConfigSpace, Map[String, Double]] {
    override def trainSurrogateModel(
      history: Seq[Trial[Map[String, Double]]]
    ): Map[String, Double] => Boolean = _ => true
  }

  val space = FlatConfigSpace(
    distributions = Map("mu" -> GaussianPrior(1, 0.1))
  )

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val rc: RendersConfig[FlatConfigSpace, Map[String, Double]] =
    new RendersConfig[FlatConfigSpace, Map[String, Double]] {

      /**
        * sample
        *
        * @param configSpace the space which can generate samples which can be rendered to vectors for smac
        * @param xElliptic   elliptic curve parameter to determine random sample
        * @return a sample from the config space
        */
      override def sample(configSpace: FlatConfigSpace,
                          xElliptic: Int): Map[String, Double] = {
        configSpace.distributions.map(a => {
          a._1 -> a._2.sample()
        })
      }

      /**
        * render a config sample into a smacable vector.
        *
        * @param config the config to render
        * @return the rendered vector
        */
      override def render(config: Map[String, Double]): Array[Double] = {
        config.toSeq.sortBy(_._1).map(_._2).toArray
      }
    }

  val f: BudgetedSampleFunction[Map[String, Double]] =
    new BudgetedSampleFunction[Map[String, Double]] {
      val mu = 2
      val sigma = 1
      override def apply(v1: Map[String, Double],
                         v2: Double): Future[Double] = {

        Future {
          val m = v1("mu")
          math.abs(mu - m)
        }
      }
    }

  val result = hs.apply(space, f)

  val resultSyn = Await.result(result, Duration.Inf)

  resultSyn.sortBy(a => -a.cost.getOrElse(Double.MaxValue)).foreach(println)

}
