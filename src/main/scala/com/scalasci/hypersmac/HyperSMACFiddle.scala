package com.scalasci.hypersmac

import com.scalasci.hypersmac.api.RendersAndSamplesConfig
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
  import FlatConfigSpace._

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

  val result = hs.apply().apply(space,f)

  val resultSyn: Seq[Trial[Map[String, Double]]] = Await.result(result, Duration.Inf)

  resultSyn.sortBy(a => -a.cost.getOrElse(Double.MaxValue)).foreach(println)

}
