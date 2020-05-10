package com.scalasci.hypersmac.api

trait SamplesConfig[ConfigSpace, Config] {

  /**
    * sample
    *
    * @param configSpace the space which can generate samples which can be rendered to vectors for smac
    * @param xElliptic   elliptic curve parameter to determine random sample
    * @return a sample from the config space
    */
  def sample(configSpace: ConfigSpace, xElliptic: Int): Config
}

trait RendersConfig[Config] {

  /**
    * render a config sample into a smacable vector.
    * @param config the config to render
    * @return the rendered vector
    */
  def render(config: Config): Array[Double]
}

trait RendersAndSamplesConfig[ConfigSpace, Config]
    extends SamplesConfig[ConfigSpace, Config]
    with RendersConfig[Config]
