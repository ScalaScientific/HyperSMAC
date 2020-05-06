package com.scalasci.hypersmac.implemented

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
case class FlatConfigSpace(distributions: Map[String, Distribution])
