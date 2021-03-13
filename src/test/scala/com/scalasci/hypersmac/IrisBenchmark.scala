package com.scalasci.hypersmac

import com.scalasci.hypersmac.implemented.{BudgetedSampleFunction, FlatConfigSpace, HyperSMAC, RandomSearch, UniformPrior, XGSMAC}
import com.scalasci.hypersmac.model.Trial
import FlatConfigSpace._
import com.scalasci.hypersmac.api.Optimizer
import com.scalasci.hypersmac.implemented.XGSMAC.XGSMACConfig
import com.scalasci.hypersmac.summary.PlotGenerators.{plotBestVsBudg, savePlot}
import org.scalatest.flatspec.AnyFlatSpec
import smile.base.mlp.{Layer, OutputFunction}
import smile.classification.mlp
import smile.read
import smile.data._
import smile.validation.{Accuracy, cv}

import java.awt.{Font, Rectangle}
import java.awt.image.BufferedImage
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object IrisBenchmark {
  def apply(hs: Optimizer[FlatConfigSpace, Map[String, Double]]) = {

    val space = FlatConfigSpace(
      distributions = Map("layerSize0" -> UniformPrior(8, 64),
        "layerSize1" -> UniformPrior(8, 64),
        "eta" -> UniformPrior(0, 1),
        "alpha" -> UniformPrior(0, 1),
        "lambda" -> UniformPrior(0, .1)))

    val df = read.arff("https://github.com/haifengl/smile/blob/master/shell/src/universal/data/weka/iris.arff?raw=true")

    val x = df.drop("class").toArray
    val y = df("class").toIntArray


    val f: BudgetedSampleFunction[Map[String, Double]] =
      (v1: Map[String, Double], v2: Double) => {

        Future {
          val layers = Array(Layer.rectifier(v1("layerSize0").toInt),
            Layer.mle(v1("layerSize1").toInt,
              OutputFunction.SOFTMAX))
          -cv.classification(2, x, y, new Accuracy()) { case (x, y) =>
            //Train an MLP per the sampled hyperparameters
            mlp(x, y,
              layers,
              epochs = v2.toInt,
              eta = v1("eta"),
              alpha = v1("alpha"),
              lambda = v1("lambda"))
          }.sum
        }.recover { case _ => -0.30 }
      }

    val result = hs(space, f)

    Await.result(result, Duration.Inf)
  }
}

object RunIrisBenchmarks extends AnyFlatSpec {

  val hs = new HyperSMAC[FlatConfigSpace, Map[String, Double]] {
    override def trainSurrogateModel(
                                      history: Seq[Trial[Map[String, Double]]]
                                    ): Map[String, Double] => Boolean = _ => true
  }

  val xghs =
    new HyperSMAC[FlatConfigSpace, Map[String, Double]] {
      override def trainSurrogateModel(
                                        history: Seq[Trial[Map[String, Double]]]
                                      ): Map[String, Double] => Boolean =
        XGSMAC[Map[String, Double]](XGSMACConfig())(renders = rc)(
          history
        )
    }
  val xgResult = IrisBenchmark(xghs(R = 100)).map(_.copy(note = Some("xgSmac")))
  val xgBest = xgResult.minBy(a => a.cost.getOrElse(0.0)).cost
  val totalResource = xgResult.map(_.budget).sum
  val maxBudget = xgResult.map(_.budget).max
  val iterations = (totalResource / maxBudget).toInt * 2 //rand2x

  val rs = RandomSearch(maxBudget, iterations)
  val hsResult = IrisBenchmark(hs(R = 100)).map(_.copy(note = Some("hyperSmac")))
  val rsResult = IrisBenchmark(rs).map(_.copy(note = Some("random")))

  val bi = new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_ARGB)
  val g2d = bi.createGraphics
    g2d.clip(new Rectangle(0, 0, 2000, 1000))

  val plot =  plotBestVsBudg(rsResult ++ hsResult ++ xgResult).canvas()
    .setTitle("Iris HP OTIM: MLP")
    .setAxisLabels( "total budget","min loss")
    .setTitleFont(new Font ("Courier New", Font.BOLD, 20))
    .setLegendVisible(true).paint(g2d, 1000, 1000)

  savePlot(bi,
    new java.io.File("assets/benchIris.png"))

}