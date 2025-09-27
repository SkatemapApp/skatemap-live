import sbt.Command

object CiCommands {
  def ciBuild: Command = Command.command("ciBuild") { state =>
    "clean" :: "scalafmtCheckAll" :: "coverage" :: "test" :: "coverageReport" :: "coverageAggregate" ::
      state
  }
}