package com.scalasci.hypersmac

import com.scalasci.hypersmac.implemented.{BudgetedSampleFunction, FlatConfigSpace, HyperSMAC, UniformPrior}
import com.scalasci.hypersmac.model.{TrialSetup, TrialWithResult}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class TestConstants
  extends AnyFlatSpec with should.Matchers {
  "a hyper search algorithm" should "be provide the correct iteration settings" in {

    val R = 81

    // define a hypersmac with an always true config selector. This is the same as hyperband.
    val hs = new HyperSMAC[FlatConfigSpace, Map[String, Double]] {
      override def trainSurrogateModel(
                                        history: Seq[TrialWithResult[Map[String, Double]]]
                                      ): Map[String, Double] => Boolean = _ => true
    }
    val f = new BudgetedSampleFunction[Map[String, Double]] {
      override def evaluate(configurations: Seq[TrialSetup[Map[String, Cost]]], budget: Cost)(implicit executionContext: ExecutionContext): Future[Seq[TrialWithResult[Map[String, Cost]]]] = {
        println("=====BUDGETED SAMP FUN EVAL=====")
        println(s"  n trials: ${configurations.length}")
        println(s"  budget:   ${budget}")
        assert(budget * configurations.length == R)
        Future(configurations.map(cfg => TrialWithResult(cfg, 0.0)))(executionContext)
      }
    }
    val space = FlatConfigSpace(
      distributions = Map("na" -> UniformPrior(8, 64))
    )
    for (r <- 1 to 10) {
      val cfg1 = Await.result(hs(eta = 3, R = R, runs = r).produceTrials(space, f, None), Duration.Inf)
      assert(cfg1.length / r == 190)
    }
  }
}
zm