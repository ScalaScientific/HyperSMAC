package com.scalasci.hypersmac
import com.scalasci.hypersmac.implemented.{BernouliPrior, BudgetedSampleFunction, FlatConfigSpace, HyperSMAC, RandomSearch, SimpleCostFunction, UniformPrior, XGSMAC}
import com.scalasci.hypersmac.implemented.XGSMAC.XGSMACConfig
import com.scalasci.hypersmac.model.TrialWithResult
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import com.scalasci.hypersmac.implemented.FlatConfigSpace._
import com.scalasci.hypersmac.summary.PlotGenerators.{plotBestVsBudg, savePlot}

import java.awt.image.BufferedImage
import java.awt.{Font, Rectangle}

object CountingOnesBenchmark{
  val n = 32
  val R = 80

  def countOnesWithRegret(paramDiscrete: Array[Boolean], paramContinuous: Array[Double], budget: Int) = {
    val discreteSum = paramDiscrete.foldLeft(0.0)((d, b) => if (b) d + 1.0 else d)
    val continuous = paramContinuous.foldLeft(0.0) { (sum, x) =>
      sum + (Seq.tabulate(budget) { i => if (scala.util.Random.nextDouble() < x) 1.0 else 0.0 }.sum / budget.toDouble)
    }
    val d: Double = paramDiscrete.length + paramContinuous.length
    math.abs(d - (discreteSum + continuous)) / d
  }

  val f = new SimpleCostFunction[Map[String, Double]] {
    def evaluate(parameter: Map[String, Double],
                 budget: Double)(implicit executionContext: ExecutionContext): Future[Cost] = {
      Future(countOnesWithRegret(
        Array.tabulate(n / 2)(i => parameter("discrete_" + i) > 0.0),
        Array.tabulate(n / 2)(i => parameter("continuous_" + i)),
        budget.toInt
      ))(executionContext)
    }
  }

  val g = BudgetedSampleFunction.fromSimpleCostFunction(f)

  val space = FlatConfigSpace(
    distributions = (
      Seq.tabulate(n / 2)(i => ("discrete_" + i) -> BernouliPrior()) ++
        Seq.tabulate(n / 2)(i => ("continuous_" + i) -> UniformPrior(0.0, 1.0))).toMap
  )

}
class CountingOnesBenchmark extends AnyFlatSpec with should.Matchers {
  "a hyper search algorithm" should "be benchmarked on counting ones" in {

    import CountingOnesBenchmark._
    // define a hypersmac with an always true config selector. This is the same as hyperband.
    val hs = new HyperSMAC[FlatConfigSpace, Map[String, Double]] {
      override def trainSurrogateModel(
                                        history: Seq[TrialWithResult[Map[String, Double]]]
                                      ): Map[String, Double] => Boolean = _ => true
    }

    // define a hypersmac which uses XGB to select good configurations.
    val xghs =
      new HyperSMAC[FlatConfigSpace, Map[String, Double]] {
        override def trainSurrogateModel(
                                          history: Seq[TrialWithResult[Map[String, Double]]]
                                        ): Map[String, Double] => Boolean = {
          val xgs = XGSMAC[Map[String, Double]](XGSMACConfig())
          xgs(history)
        }
      }

    // run the searches
    println("run hyperband")
    val hyperband = Await.result(hs(R = R).produceTrials(space, g, Some("hyperband")), Duration.Inf)

    println("run XGHyperSMAC")
    val XGHyperSMAC = Await.result(xghs(R = R).produceTrials(space, g, Some("XGHyperSMAC")), Duration.Inf)

    val totalResource = hyperband.map(_.setup.budget).sum
    val maxBudget = hyperband.map(_.setup.budget).max
    val iterations = (totalResource / maxBudget).toInt * 2 //rand2x

    println("run random search")
    val rs = Await.result(RandomSearch(maxBudget, iterations).produceTrials(space, g, Some("Random")), Duration.Inf)

    // create the plot. This code is a bit verbose due to a bug in smile plotting which truncates title/legend.
    // fix is on smile master, but not released.
    val bi = new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_ARGB)
    val g2d = bi.createGraphics
    g2d.clip(new Rectangle(0, 0, 2000, 1000))

    plotBestVsBudg(rs ++ hyperband ++ XGHyperSMAC).canvas()
      .setTitle("Counting Ones HP OTIM")
      .setAxisLabels("total budget", "regret")
      .setTitleFont(new Font("Courier New", Font.BOLD, 40))
      .setLegendVisible(true).paint(g2d, 1000, 1000)

    savePlot(bi, new java.io.File("assets/benchmark/benchOnes.png"))
  }
}