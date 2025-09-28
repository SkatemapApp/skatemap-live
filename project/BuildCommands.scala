import sbt.Command

object BuildCommands {
  def ciBuild: Command = Command.command("ciBuild") { state =>
    "clean" :: "scalafmtCheckAll" :: "coverage" :: "test" :: "coverageReport" :: "coverageAggregate" ::
      state
  }

  def devBuild: Command = Command.command("devBuild") { state =>
    "clean" :: "scalafmtAll" :: "test" :: state
  }
}