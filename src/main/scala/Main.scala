import org.kohsuke.github.*
import scala.jdk.CollectionConverters.*
import java.nio.file.{Files, Paths, StandardCopyOption}
import java.net.URL

object Main:

  def in(name: String): String =
    sys.env.getOrElse("INPUT_" + name.replace("-", "_").toUpperCase, "").trim

  def main(args: Array[String]): Unit =
    val sourceRepo = in("source_repo")
    val destRepo   = in("destination_repo")

    val token =
      if in("github_token").nonEmpty then in("github_token")
      else sys.env.getOrElse("GITHUB_TOKEN", "")

    if token.isEmpty then
      System.err.println("::error::No token provided")
      sys.exit(1)

    val tag =
      if in("tag").nonEmpty then in("tag")
      else sys.env.get("GITHUB_REF").map(_.split("/").last).getOrElse("")

    if tag.isEmpty then
      System.err.println("::error::No tag provided")
      sys.exit(1)


    val overrideBody = in("override_body")
    val overrideName = in("override_name")

    val overrideDraft      = in("override_draft")
    val overridePrerelease = in("override_prerelease")
    val skipAssets         = in("skip_assets").toLowerCase == "true"

    val gh = GitHubBuilder().withOAuthToken(token).build()

    val srcRepo     = gh.getRepository(sourceRepo)
    val destination = gh.getRepository(destRepo)

    val srcRelease =
      try srcRepo.getReleaseByTagName(tag)
      catch case e: Exception =>
        System.err.println(s"::error::No source release found for tag $tag")
        sys.exit(1)

    val finalBody =
      if overrideBody.nonEmpty then overrideBody
      else Option(srcRelease.getBody).getOrElse("")

    val finalName =
      if overrideName.nonEmpty then overrideName
      else Option(srcRelease.getName).getOrElse(tag)

    val finalDraft =
      if overrideDraft.nonEmpty then overrideDraft.toBoolean
      else srcRelease.isDraft

    val finalPrerelease =
      if overridePrerelease.nonEmpty then overridePrerelease.toBoolean
      else srcRelease.isPrerelease

    val destRelease: GHRelease =
      try
        val r = destination.getReleaseByTagName(tag)
        r.update()
          .name(finalName)
          .body(finalBody)
          .draft(finalDraft)
          .prerelease(finalPrerelease)
          .update()
        r
      catch case _ =>
        destination.createRelease(tag)
          .name(finalName)
          .body(finalBody)
          .draft(finalDraft)
          .prerelease(finalPrerelease)
          .create()

    if !skipAssets then
      srcRelease.listAssets().asScala.foreach { asset =>
        val downloadUrl = asset.getBrowserDownloadUrl
        val inStream = new URL(downloadUrl).openStream()

        val temp = Files.createTempFile("asset", asset.getName).toFile
        Files.copy(inStream, temp.toPath, StandardCopyOption.REPLACE_EXISTING)
        inStream.close()

        destRelease.uploadAsset(temp, asset.getContentType)
      }

    println("Release copied successfully.")