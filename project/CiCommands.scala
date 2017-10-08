import sbt.Command

object CiCommands {
  def ciBuild: Command = Command.command("ciBuild") { state =>
    "clean" :: "coverage" :: "test" :: "coverageReport" :: "coverageAggregate" ::
      "scalastyle" :: "test:scalastyle" ::
      state
  }
}
