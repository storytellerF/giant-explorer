import com.storyteller_f.song.SongAction
import org.slf4j.LoggerFactory
import java.io.File

fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("dispatcher")
    val androidHome = System.getenv("ANDROID_HOME")
    val adbExtension = if (System.getProperty("os.name").lowercase().contains("windows")) {
        ".exe"
    } else ""
    val name = "platform-tools/adb$adbExtension"
    val adbPath = if (androidHome != null) {
        File(androidHome, name)
    } else {
        //如果没有设置环境变量，使用默认路径
        val userHome = System.getProperty("user.home")
        File(userHome, "Library/Android/sdk/$name")
    }.absolutePath

    // Try adding program arguments via Run/Debug configuration.
    // Learn more about running applications: https://www.jetbrains.com/help/idea/running-applications.html.
    logger.info("Program arguments: count:${args.size} -> [${args.joinToString(", ")}]")
    val path = args.firstOrNull() ?: "../"
    logger.debug(File(path).absolutePath)
    val suffix = listOf(".debug")

    SongAction(
        transferFiles = listOf(File(path, "build/yue-html.zip")),
        packageTargets = suffix.map {
            "com.storyteller_f.giant_explorer$it" to "files/plugins"
        },
        pathTargets = listOf(),
        adbPath = adbPath,
        "yue-html.zip",
        logger = logger
    ).dispatchToMultiDevices()
}