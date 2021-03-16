package com.scalasci.hypersmac

import com.scalasci.hypersmac.implemented.{BudgetedSampleFunction, FlatConfigSpace, HyperSMAC, RandomSearch, SimpleCostFunction, UniformPrior, XGSMAC}
import FlatConfigSpace._
import com.scalasci.hypersmac.api.Optimizer
import com.scalasci.hypersmac.implemented.XGSMAC.XGSMACConfig
import com.scalasci.hypersmac.model.{TrialSetup, TrialWithResult}
import com.scalasci.hypersmac.summary.PlotGenerators.{plotBestVsBudg, savePlot}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should
import smile.base.mlp.{Layer, OutputFunction}
import smile.classification.mlp
import smile.read
import smile.data._
import smile.validation.{Accuracy, cv}

import java.awt.{Font, Rectangle}
import java.awt.image.BufferedImage
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

object IrisBenchmark {
  def apply(hs: Optimizer[FlatConfigSpace, Map[String, Double]]) = {

    // define a configuration space for the mlp parameters
    // this test defines a basic string/double config sample, but this library allows arbitrary config types like
    // case-classes or even fancy ADTs to not require relying on unstructured mappings.
    val space = FlatConfigSpace(
      distributions = Map("layerSize0" -> UniformPrior(8, 64),
        "layerSize1" -> UniformPrior(8, 64),
        "eta" -> UniformPrior(0, 1),
        "alpha" -> UniformPrior(0, 1),
        "lambda" -> UniformPrior(0, .1)))

    // obtain Iris dataset
    val df = read.arff("https://github.com/haifengl/smile/blob/master/shell/src/universal/data/weka/iris.arff?raw=true")

    val x = df.drop("class").toArray
    val y = df("class").toIntArray


    // define a cost function which trains the mlp for a number of epochs corresponding to the budget.
    // return the error rate on the validation splits
    val f = new SimpleCostFunction[Map[String,Double]] {
      override def evaluate(configuration: Map[String, Cost],
                            budget: Cost)(implicit executionContext: ExecutionContext): Future[Cost] = {
        Future {
          // define the layers per config
          val layers = Array(
            Layer.rectifier(configuration("layerSize0").toInt),
            Layer.mle(configuration("layerSize1").toInt, OutputFunction.SOFTMAX)
          )

          -cv.classification(2, x, y, new Accuracy()) { case (x, y) =>
            //Train an MLP per the sampled hyperparameters
            mlp(x, y,
              layers,
              epochs = budget.toInt,
              eta = configuration("eta"),
              alpha = configuration("alpha"),
              lambda = configuration("lambda"))
          }.sum
        }(executionContext).recover { case _ => -0.30 }(executionContext)
      }

    }

    // we convert the basic cost function to the batch/budget-oriented BudgetedSampleFunction.
    val g = BudgetedSampleFunction.fromSimpleCostFunction(f)
    // generate trial history
    val result = hs.produceTrials(space, g)

    Await.result(result, Duration.Inf)
  }
}

class RunIrisBenchmarks extends AnyFlatSpec with should.Matchers {
  "A hypersmac" should "not crash while running benchmarks" in {

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

    val xgResult = IrisBenchmark(xghs(R = 50)).map(a=>a.copy(trial = a.trial.copy(note = Some("xgHyperSMAC"))))
    val totalResource = xgResult.map(_.setup.budget).sum
    val maxBudget = xgResult.map(_.setup.budget).max
    val iterations = (totalResource / maxBudget).toInt * 2 //rand2x

    // run random search for comparison
    val rs = RandomSearch(maxBudget, iterations)

    val hsResult = IrisBenchmark(hs(R = 50)).map(a=>a.copy(trial = a.trial.copy(note = Some("hyperband"))))
    val rsResult = IrisBenchmark(rs).map(a=>a.copy(trial = a.trial.copy(note = Some("random"))))

    // create the plot. This code is a bit verbose due to a bug in smile plotting which truncates title/legend.
    // fix is on smile master, but not released.
    val bi = new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_ARGB)
    val g2d = bi.createGraphics
    g2d.clip(new Rectangle(0, 0, 2000, 1000))

    plotBestVsBudg(rsResult ++ hsResult ++ xgResult).canvas()
      .setTitle("Iris HP OTIM: MLP")
      .setAxisLabels("total budget", "min loss")
      .setTitleFont(new Font("Courier New", Font.BOLD, 40))
      .setLegendVisible(true).paint(g2d, 1000, 1000)

    savePlot(bi, new java.io.File("assets/benchmark/benchIris.png"))

  }
}