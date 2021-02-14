package ltd.icecold.music.searchMessageBean

data class Result(
    val hasMore: Boolean,
    val songCount: Int,
    val songs: List<Song>
)