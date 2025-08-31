import sbt.*
import sbt.Keys.*

import scala.sys.process.*

/**
 * JacocoAgentPlugin (no aggregation/merge)
 * ---------------------------------------
 * - Attaches JaCoCo agent to forked JVMs per module (Test + optional IntegrationTest)
 * - Writes per-module .exec files (no merging)
 * - Generates per-module reports
 * - Provides root helpers: jacocoCleanAll / jacocoReportAll that just iterate modules (no merge)
 *
 * Typical:
 *   sbt "jacocoClean; test; jacocoReport"
 *   sbt "test; it:test; jacocoReport"              // if you also use IT
 *   sbt "test; jacocoReportAll"                    // at root: runs reports in all aggregated modules
 */
object FilteredJacocoAgentPlugin extends AutoPlugin {
  object autoImport {
    val jacocoVersion    = settingKey[String]("JaCoCo version")
    val jacocoExecFile   = settingKey[File]("Per-module JaCoCo .exec file (Test)")
    val jacocoItExecFile = settingKey[File]("Per-module JaCoCo .exec file (IntegrationTest)")
    val jacocoReportDir  = settingKey[File]("Per-module report directory")
    val jacocoIncludes   = settingKey[Seq[String]]("Include patterns (JaCoCo syntax)")
    val jacocoExcludes   = settingKey[Seq[String]]("Exclude patterns (JaCoCo syntax)")
    val jacocoAppend     = settingKey[Boolean]("Append to existing .exec instead of overwrite (default: false)")
    val jacocoFailOnMissingExec =
      settingKey[Boolean]("Fail jacocoReport if .exec is missing (default: false – warn & skip)")

    val jacocoClean   = taskKey[Unit]("Clean JaCoCo outputs and sbt-jacoco leftovers (per module)")
    val jacocoReport  = taskKey[File]("Generate JaCoCo HTML/XML/CSV report from this module's .exec")

    // Root-only helpers (NO MERGE): just run per-module tasks across aggregated projects
    val jacocoCleanAll  = taskKey[Unit]("Run jacocoClean in all aggregated modules (no merge)")
    val jacocoReportAll = taskKey[Unit]("Run jacocoReport in all aggregated modules (no merge)")

    val jacocoSetUserDirToBuildRoot = settingKey[Boolean]("Mimic non-forked runs by setting -Duser.dir to the build root for forked tests")

    val jmfCoreVersion     = settingKey[String]("JMF core library version")
    val Jmf                = config("jmf").hide
    val jmfRewrite         = taskKey[File]("Rewrite compiled classes using JMF tool; returns output dir")
    val jmfOutDir          = settingKey[File]("JMF output base dir")
    val jmfRulesFile       = settingKey[File]("JMF rules file")
    val jmfCliMain         = settingKey[String]("Main class of the JMF CLI")
    val jmfDryRun          = settingKey[Boolean]("Dry-run rewriter")
    val jmfEnabled         = settingKey[Boolean]("Enable JMF rewriting")
    val jmfPrepareForTests = taskKey[Unit]("Run JMF rewrite when enabled (no self-ref to test)")
  }
  import autoImport.*

  override def requires = plugins.JvmPlugin
  override def trigger  = noTrigger

  private def findOnCp(cp: Seq[Attributed[File]])(p: File => Boolean): Option[File] =
    cp.map(_.data).find(p)

  private def agentJar(cp: Seq[Attributed[File]]): File = {
    val files = cp.map(_.data)
    files.find(f => f.getName.startsWith("org.jacoco.agent-") && f.getName.contains("-runtime"))
      .orElse(files.find(f => f.getName.contains("jacoco") && f.getName.contains("agent") && f.getName.contains("runtime")))
      .orElse(files.find(f => f.getName.startsWith("org.jacoco.agent-") && f.getName.endsWith(".jar"))) // last resort
      .getOrElse(sys.error("JaCoCo runtime agent JAR not found on Test / dependencyClasspath"))
  }

  private def cliJar(cp: Seq[Attributed[File]]): File = {
    val files = cp.map(_.data)
    files.find(f => f.getName.startsWith("org.jacoco.cli-") && f.getName.contains("nodeps"))
      .orElse(files.find(_.getName.startsWith("org.jacoco.cli-"))) // fallback, but we won't use it
      .getOrElse(sys.error("org.jacoco.cli (nodeps) JAR not found on Test / dependencyClasspath"))
  }

  private val defaultIncludes = Seq("**")
  private val defaultExcludes = Seq("scala.*", "java.*", "sun.*", "jdk.*")

  override def projectSettings: Seq[Setting[_]] = Seq(
    // ---- coordinates
    jacocoVersion := "0.8.12",
    jmfCoreVersion := "0.1.7",
    libraryDependencies ++= Seq(
      // pull the agent with the runtime classifier (this is the actual -javaagent jar)
      ("org.jacoco" % "org.jacoco.agent" % jacocoVersion.value % Test).classifier("runtime"),
      ("org.jacoco" % "org.jacoco.cli"   % jacocoVersion.value % Test).classifier("nodeps"),
      "io.github.moranaapps" % "jacoco-method-filter-core_2.12" % jmfCoreVersion.value % Jmf.name,
    ),
    jacocoSetUserDirToBuildRoot := true,

    // ---- defaults
    jacocoExecFile   := target.value / "jacoco" / "jacoco.exec",
    jacocoReportDir  := target.value / "jacoco" / "report",
    jacocoIncludes   := defaultIncludes,
    jacocoExcludes   := defaultExcludes,
    jacocoAppend     := false,
    jacocoFailOnMissingExec := false,

    // --- JMF tool wiring
    ivyConfigurations += Jmf,

    jmfOutDir   := target.value / "jmf",
    jmfRulesFile:= (ThisBuild / baseDirectory).value / "jacoco-method-filter-rules.txt",
    jmfCliMain  := "io.moranaapps.jacocomethodfilter.CoverageRewriter",
    jmfDryRun   := false,
    jmfEnabled  := true,

    // the rewrite task (your code, lightly cleaned)
    jmfRewrite := {
      val _           = (Compile / compile).value            // ensure classes exist
      val classesIn   = (Compile / classDirectory).value
      if (!classesIn.exists) sys.error(s"[jmf] compiled classes not found: ${classesIn.getAbsolutePath}")
      val hasClasses  = (classesIn ** sbt.GlobFilter("*.class")).get.nonEmpty
      if (!hasClasses) sys.error(s"[jmf] no .class files under ${classesIn.getAbsolutePath}. Nothing to rewrite.")

      val log         = streams.value.log
      val outDir      = jmfOutDir.value / "classes-filtered"
      IO.delete(outDir); IO.createDirectory(outDir)

      val toolJars    = (Jmf / update).value.matching(artifactFilter(`type` = "jar")).distinct
      log.info("[jmf] tool CP:\n" + toolJars.map(f => s"  - ${f.getAbsolutePath}").mkString("\n"))

      val cpStr       = toolJars.mkString(java.io.File.pathSeparator)
      val args        = Seq(
        "java","-cp", cpStr, jmfCliMain.value,
        "--in",   classesIn.getAbsolutePath,
        "--out",  outDir.getAbsolutePath,
        "--rules",jmfRulesFile.value.getAbsolutePath
      ) ++ (if (jmfDryRun.value) Seq("--dry-run") else Seq())

      log.info(s"[jmf] rewrite: ${args.mkString(" ")}")
      val code = scala.sys.process.Process(args, baseDirectory.value).!
      if (code != 0) sys.error(s"[jmf] rewriter failed ($code)")
      outDir
    },

    // If disabled, do nothing. If enabled, add rewritten dir to classpath before tests run.
//    Test / unmanagedClasspath ++= Def.taskDyn {
//      if (jmfEnabled.value) Def.task { Seq(Attributed.blank(jmfRewrite.value)) }
//      else                  Def.task { Seq.empty[Attributed[File]] }
//    }.value,

    // 1) preparatory task (already defined earlier)
    jmfPrepareForTests := Def.taskDyn {
      if (jmfEnabled.value) Def.task { jmfRewrite.value; () }
      else                  Def.task { () }
    }.value,

    // 2) Hook into test + testQuick (regular tasks)
//    Test / test      := (Test / test).dependsOn(jmfPrepareForTests).value,
//    Test / testQuick := (Test / testQuick).dependsOn(jmfPrepareForTests).value,

    Test / fullClasspath := Def.taskDyn {
      // Gather the usual ingredients
      val testOut    = (Test / classDirectory).value                      // test classes dir
      val mainOut    = (Compile / classDirectory).value                   // original main classes dir
      val deps       = (Test / internalDependencyClasspath).value
      val ext        = (Test / externalDependencyClasspath).value
      val unmanaged  = (Test / unmanagedClasspath).value
      val scalaJars  = (Test / scalaInstance).value.allJars.map(Attributed.blank(_)).toVector
      val resources  = (Test / resourceDirectories).value.map(Attributed.blank)

      // Builder that places `rewritten` first, then testOut, then all the rest,
      // and finally the original mainOut (so rewritten shadows it).
      def build(rewritten: Option[File]) = Def.task {
        val prefix: Vector[Attributed[File]] =
          rewritten.toVector.map(Attributed.blank) :+ Attributed.blank(testOut)

        val rest = (deps ++ ext ++ scalaJars ++ resources ++ unmanaged)
          .filterNot(a =>
            a.data == mainOut ||
              a.data == testOut ||
              rewritten.exists(_ == a.data)
          )

        // Re-add original mainOut at the very end to preserve completeness
        (prefix ++ rest :+ Attributed.blank(mainOut))
      }

      if (jmfEnabled.value) build(Some(jmfRewrite.value))
      else                  build(None)
    }.value,

      // ---- fork so -javaagent is applied
    Test / fork := true,

    // Attach agent for Test
    Test / forkOptions := {
      val fo      = (Test / forkOptions).value
      val cp      = (Test / dependencyClasspath).value
      val agent   = agentJar(cp)
      val dest    = jacocoExecFile.value.getAbsolutePath
      val inc     = jacocoIncludes.value.mkString(":")
      val exc     = jacocoExcludes.value.mkString(":")
      val append  = if (jacocoAppend.value) "true" else "false"
      val agentOpt =
        s"-javaagent:${agent.getAbsolutePath}=destfile=$dest,append=$append,output=file,includes=$inc,excludes=$exc,inclbootstrapclasses=false,jmx=false"

      val rootDir = (LocalRootProject / baseDirectory).value // <- repo root
      // log a hint
      streams.value.log.info(s"[jacoco] agent jar: ${agent.getName}")
      streams.value.log.info(s"[jacoco] setting fork working dir to: $rootDir")

      fo
        .withWorkingDirectory(rootDir)                         // fixes your file: api/src/... lookups
        .withRunJVMOptions(fo.runJVMOptions :+ agentOpt)       // reliably add -javaagent
    },

    // Print one sanity line per test fork
    Test / testOptions += Tests.Setup { () =>
      val status =
        try {
          val rt = Class.forName("org.jacoco.agent.rt.RT")
          val m  = rt.getMethod("getAgent")
          m.invoke(null) // throws if not attached
          "attached"
        } catch {
          case _: ClassNotFoundException => "rt-jar-not-on-classpath"
          case _: Throwable              => "present-but-not-attached"
        }
      println(s"[jacoco] agent status: $status; user.dir=" + System.getProperty("user.dir"))
    },


    // ---- per-module clean
    jacocoClean := {
      val log    = streams.value.log
      val outDir = target.value / "jacoco"
      IO.delete(outDir)
      IO.createDirectory(outDir)
      IO.delete(jmfOutDir.value)

      // remove sbt-jacoco leftovers if they ever existed
      val instrDir = (Test / crossTarget).value / "jacoco" / "instrumented-classes"
      if (instrDir.exists) {
        log.info(s"[jacoco] removing sbt-jacoco leftovers: ${instrDir.getAbsolutePath}")
        IO.delete(instrDir)
      }
      log.info(s"[jacoco] cleaned: ${outDir.getAbsolutePath}")
    },

    // ---- per-module report (only this module, no merge)
    jacocoReport := {
      val log        = streams.value.log
      val execFile   = jacocoExecFile.value
      val reportDir  = jacocoReportDir.value
      IO.createDirectory(reportDir)

      // PRE-compute (avoid linter warnings)
      val moduleName   = name.value
      val baseDir      = baseDirectory.value
      val failOnMiss   = jacocoFailOnMissingExec.value
      val cp           = (Test / dependencyClasspath).value
      val cli          = cliJar(cp)

      // Class dirs (filter to existing)
      val mainClasses: File = Def.taskDyn {
        if (jmfEnabled.value) Def.task { jmfRewrite.value }
        else                  Def.task { (Compile / classDirectory).value }
      }.value
      val classDirs    = Seq(mainClasses).filter(_.exists)

      // Source dirs: unmanaged + managed (filter to existing)
      val unmanagedSrc = (Compile / unmanagedSourceDirectories).value
      val managedSrc   = (Compile / managedSourceDirectories).value
      val srcDirs      = (unmanagedSrc ++ managedSrc).filter(_.exists)

      if (!execFile.exists) {
        val msg = s"[jacoco] .exec not found for $moduleName: $execFile . Run tests first."
        if (failOnMiss) sys.error(msg) else { log.warn(msg); reportDir }
      } else {
        // Build args correctly: repeat flags per path
        val execArgs      = Seq(execFile.getAbsolutePath)
        val classArgs     = classDirs.flatMap(d => Seq("--classfiles", d.getAbsolutePath))
        val sourceArgs    = srcDirs.flatMap(d => Seq("--sourcefiles", d.getAbsolutePath))
        val outArgs       = Seq(
          "--html", reportDir.getAbsolutePath,
          "--xml",  (reportDir / "jacoco.xml").getAbsolutePath,
          "--csv",  (reportDir / "jacoco.csv").getAbsolutePath
        )

        log.info(s"[jacoco] cli jar: ${cli.getName}")
        log.info(s"[jacoco] exec: ${execFile.getAbsolutePath}")
        log.info(s"[jacoco] class dirs: ${classDirs.mkString(", ")}")
        log.info(s"[jacoco] source dirs: ${srcDirs.mkString(", ")}")

        val args = Seq("java", "-jar", cli.getAbsolutePath, "report") ++
          execArgs ++ classArgs ++ sourceArgs ++ outArgs

        val exit = scala.sys.process.Process(args, baseDir).!
        if (exit != 0) sys.error("JaCoCo report generation failed")
        log.info(s"[jacoco] per-module HTML: ${reportDir / "index.html"}")
        reportDir
      }
    },

    // Root-only helpers (NO MERGE): run per-module tasks across aggregated refs
    jacocoCleanAll := Def.taskDyn {
      val aggs: Seq[ProjectRef] = thisProject.value.aggregate
      if (aggs.isEmpty)
        Def.task { streams.value.log.warn("No aggregated modules under this project."); () }
      else
        Def.task { () }.dependsOn(aggs.map(ref => ref / jacocoClean): _*)
    }.value,

    jacocoReportAll := Def.taskDyn {
      val aggs: Seq[ProjectRef] = thisProject.value.aggregate
      if (aggs.isEmpty)
        Def.task { streams.value.log.warn("No aggregated modules under this project."); () }
      else
        Def.task { () }.dependsOn(aggs.map(ref => ref / jacocoReport): _*)
    }.value
  )
}
