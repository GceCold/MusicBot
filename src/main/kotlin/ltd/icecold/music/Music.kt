package ltd.icecold.music

import com.google.gson.Gson
import com.google.gson.JsonParser
import ltd.icecold.music.musicUrlBean.MusicUrlBean
import ltd.icecold.music.searchMessageBean.SearchMessageBean
import ltd.icecold.orange.netease.NeteaseCrypto
import ltd.icecold.orange.netease.NeteaseRequest
import ltd.icecold.orange.netease.api.NeteaseSearchAPI
import ltd.icecold.orange.netease.api.NeteaseSongAPI
import ltd.icecold.orange.netease.api.NeteaseUserAPI
import ltd.icecold.orange.netease.bean.NeteaseRequestOptions
import ltd.icecold.orange.netease.bean.NeteaseResponseBody
import ltd.icecold.orange.network.Request
import net.mamoe.mirai.console.data.ReadOnlyPluginConfig
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.globalEventChannel
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.info
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object Music : KotlinPlugin(
    @OptIn(ConsoleExperimentalApi::class)
    JvmPluginDescription.loadFromResource()
) {
    var searchMusicTask: MutableMap<String, List<MusicMessage>> = mutableMapOf()
    override fun onEnable() {

        MusicSetting.reload()
        logger.info { "Music插件已加载，基于OrangeAPI https://github.com/GceCold/OrangeAPI" }

        val neteaseUserAPI = loginNetease()

        val executors = Executors.newScheduledThreadPool(1)
        val taskLogin = object : TimerTask() {
            override fun run() {
                neteaseUserAPI.loginRefresh()
            }
        }
        executors.scheduleWithFixedDelay(taskLogin, 1, 1, TimeUnit.HOURS)


        this.globalEventChannel().subscribeAlways<GroupMessageEvent> { event ->
            val message = event.message.content
            val senderId = event.sender.id
            val subject = event.subject
            val taskRemoveSenderId = object : TimerTask() {
                override fun run() {
                    searchMusicTask.remove(senderId.toString())
                }
            }

            val regexList = Regex("^\\d{1,2}\$").findAll(message).toList()
            if (regexList.isNotEmpty() && searchMusicTask.containsKey(senderId.toString())) {
                val musicList = searchMusicTask[senderId.toString()]
                if (musicList == null) {
                    subject.sendMessage("❌ 插件内部出现错误")
                    return@subscribeAlways
                }

                if (musicList.size < message.toInt()-1) {
                    subject.sendMessage("❌ 请输入范围内的选项数字")
                    return@subscribeAlways
                }

                val musicMessage = musicList[message.toInt()-1]

                if (!NeteaseSongAPI.checkMusic(musicMessage.musicId.toString(), neteaseUserAPI.cookie)) {
                    subject.sendMessage("❌ 歌曲没有版权或获取链接失败无法播放")
                    return@subscribeAlways
                }

                val musicUrlBean = Gson().fromJson(
                    NeteaseSongAPI.musicUrl(musicMessage.musicId.toString(), "999000", neteaseUserAPI.cookie).body,
                    MusicUrlBean::class.java
                )

                val musicUrl = musicUrlBean.data[0].url

                if (musicUrl == null){
                    subject.sendMessage("❌ 歌曲没有版权或获取链接失败无法播放")
                    return@subscribeAlways
                }

                val picUrl =
                    JsonParser().parse(musicDetail(musicMessage.musicId.toString(), neteaseUserAPI.cookie).body)
                        .asJsonObject
                        .get("songs").asJsonArray
                        .get(0).asJsonObject
                        .get("al").asJsonObject
                        .get("picUrl").asString

                subject.sendMessage(
                    MusicShare(
                        MusicKind.NeteaseCloudMusic,
                        musicMessage.musicName,
                        "${musicMessage.musicName} - ${musicMessage.musicArtists}",
                        musicUrl,
                        picUrl,
                        musicUrl
                    )
                )

            }

            if (message.startsWith("搜歌 ")) {
                val executor = Executors.newScheduledThreadPool(1)
                val musicName = message.replace("搜歌 ", "")
                val searchFromJson = Gson().fromJson(
                    NeteaseSearchAPI().search(musicName, 1, 6, 0, neteaseUserAPI.cookie).body,
                    SearchMessageBean::class.java
                )
                if (searchFromJson.result.songCount == 0) {
                    subject.sendMessage("❌ 未找到相关歌曲")
                    return@subscribeAlways
                }
                val musicList: MutableList<MusicMessage> = mutableListOf()
                searchFromJson.result.songs.forEach {
                    val musicIdEach = it.id
                    val musicNameEach = it.name
                    var musicArtistsEach = ""
                    it.artists.forEach { music -> musicArtistsEach = musicArtistsEach + music.name + " " }
                    musicList.add(MusicMessage(musicNameEach, musicArtistsEach, musicIdEach))
                }
                searchMusicTask[senderId.toString()] = musicList

                val message = buildMessageChain {
                    PlainText("搜索歌曲：${musicName} ")
                    if (musicList.size < 6) {
                        add("共搜索到 ${musicList.size} 个结果\n")
                    } else {
                        add("已选取前6个搜索结果\n")
                    }
                    musicList.forEachIndexed { index, value ->
                        add("${index + 1}. ${value.musicName} - ${value.musicArtists} \n")
                    }

                    add("\uD83D\uDCAC 请在10s内选择歌曲 有时会出现歌曲版权判断错误 点击小标签可以直接下载音乐")
                }
                subject.sendMessage(message)

                executor.schedule(taskRemoveSenderId, 13, TimeUnit.SECONDS)
            }

            if (message.startsWith("点歌 ")) {
                var musicName = message.replace("点歌 ", "")
                val search = NeteaseSearchAPI().search(musicName, 1, 1, 0, neteaseUserAPI.cookie)
                if (search.code != 200) {
                    subject.sendMessage("❌ 搜索出现了错误")
                    return@subscribeAlways
                }
                val searchFromJson = Gson().fromJson(search.body, SearchMessageBean::class.java)
                if (searchFromJson.result.songCount == 0) {
                    subject.sendMessage("❌ 未找到相关歌曲")
                    return@subscribeAlways
                }

                musicName = searchFromJson.result.songs[0].name
                val musicId = searchFromJson.result.songs[0].id
                var musicArtists = ""
                searchFromJson.result.songs[0].artists.forEach { musicArtists = musicArtists + it.name + " " }

                if (!NeteaseSongAPI.checkMusic(musicId.toString(), neteaseUserAPI.cookie)) {
                    subject.sendMessage("❌ 歌曲没有版权或获取链接失败无法播放")
                    return@subscribeAlways
                }

                val musicUrlBean = Gson().fromJson(
                    NeteaseSongAPI.musicUrl(musicId.toString(), "999000", neteaseUserAPI.cookie).body,
                    MusicUrlBean::class.java
                )

                val musicUrl = musicUrlBean.data[0].url

                if (musicUrl == null){
                    subject.sendMessage("❌ 歌曲没有版权或获取链接失败无法播放")
                    return@subscribeAlways
                }

                val picUrl = JsonParser().parse(musicDetail(musicId.toString(), neteaseUserAPI.cookie).body)
                    .asJsonObject
                    .get("songs").asJsonArray
                    .get(0).asJsonObject
                    .get("al").asJsonObject
                    .get("picUrl").asString

                subject.sendMessage(
                    MusicShare(
                        MusicKind.NeteaseCloudMusic,
                        musicName,
                        "$musicName - $musicArtists",
                        musicUrl,
                        picUrl,
                        musicUrl
                    )
                )
            }


        }

    }

    override fun onDisable() {

    }

    private fun loginNetease(): NeteaseUserAPI {
        val userName = MusicSetting.userName
        val password = MusicSetting.password
        val neteaseUserAPI = NeteaseUserAPI()

        logger.info { "网易云账号：" + MusicSetting.userName + " 正在登录" }

        val login = if (userName.contains("@")) {
            neteaseUserAPI.login(userName, password)
        } else {
            neteaseUserAPI.loginPhone(userName, password)
        }
        val loginState = JsonParser().parse(login.body).asJsonObject.get("code").asInt
        if (loginState != 200) logger.error("网易云账号登录失败，账号或密码错误")
        if (loginState == 200) logger.info("网易云账号登录成功！")

        return neteaseUserAPI
    }


    private fun musicDetail(musicId: String, cookie: Map<String, String>): NeteaseResponseBody {
        val data = mutableMapOf("c" to "[{\"id\":$musicId}]", "ids" to "[$musicId]")
        val neteaseRequestOptions = NeteaseRequestOptions(
            "https://music.163.com/weapi/v3/song/detail",
            NeteaseCrypto.CryptoType.WEAPI,
            cookie,
            Request.UserAgentType.PC
        )
        return NeteaseRequest.postRequest(neteaseRequestOptions, data)
    }
}

object MusicSetting : ReadOnlyPluginConfig("music") {
    @ValueDescription("网易云账号邮箱或手机号")
    val userName by value("icecold")

    @ValueDescription("32位md5 密码")
    val password by value("")
}
