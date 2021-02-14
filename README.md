# Mirai-MusicBot

第一次使用kotlin 一个小时写完的 重复代码较多 建议自行修改优化 使用[OrangeAPI](https://github.com/GceCold/OrangeAPI )获取歌曲相关信息

同时演示了如何自定义OrangeAPI中未封装的API

```kotlin
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
```

## 使用说明

指令：搜歌 [歌名]  点歌 [歌名]

点歌会直接返回搜索结果第一个的歌曲

搜歌可以进行选择