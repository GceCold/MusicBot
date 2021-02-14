package ltd.icecold.music.searchMessageBean

data class Song(
    val album: Album,
    val alias: List<String>,
    val artists: List<ArtistX>,
    val copyrightId: Int,
    val duration: Int,
    val fee: Int,
    val ftype: Int,
    val id: Long,
    val mark: Long,
    val mvid: Int,
    val name: String,
    val rUrl: Any,
    val rtype: Int,
    val status: Int
)