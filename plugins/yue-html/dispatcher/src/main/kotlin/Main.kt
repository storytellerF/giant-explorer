import com.storyteller_f.song.PackageSite
import com.storyteller_f.song.SongAction
import org.slf4j.LoggerFactory
import java.io.File

fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("dispatcher")

    // Try adding program arguments via Run/Debug configuration.
    // Learn more about running applications: https://www.jetbrains.com/help/idea/running-applications.html.
    logger.info("Program arguments: count:${args.size} -> [${args.joinToString(", ")}]")
    val path = args.firstOrNull() ?: "../"
    logger.debug("working ${File(path).absolutePath}")
    val suffix = listOf(".debug")

    SongAction(
        transferFiles = listOf(File(path, "build/yue-html.zip")),
        packageTargets = suffix.map {
            PackageSite("com.storyteller_f.giant_explorer$it", "files/plugins")
        },
        pathTargets = listOf(),
        outputName = "yue-html.zip",
        logger = logger
    ).dispatchToMultiDevices()
}