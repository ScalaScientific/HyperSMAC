package com.scalasci.hypersmac.implemented

import com.scalasci.hypersmac.api.RendersAndSamplesConfig

sealed trait Distribution {
  def sample(): Double
}
case class GaussianPrior(mu: Double, sigma: Double) extends Distribution {
  override def sample(): Double = {
    (scala.util.Random.nextGaussian() * sigma) + mu
  }
}
case class UniformPrior(a: Double, b: Double) extends Distribution {
  override def sample(): Double = {
    a + (scala.util.Random.nextDouble() * (b - a))
  }
}
case class DiscretePrior(dimensions: Int) extends Distribution {
  override def sample(): Double = {
    scala.util.Random.nextInt(dimensions)
  }
}
case class FlatConfigSpace(distributions: Map[String, Distribution])
object FlatConfigSpace {
  implicit val rc
  : RendersAndSamplesConfig[FlatConfigSpace, Map[String, Double]] =
    new RendersAndSamplesConfig[FlatConfigSpace, Map[String, Double]] {

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
}