package com.bilimusic.player.data.bilibili

import android.content.Context
import androidx.core.text.HtmlCompat
import com.bilimusic.player.data.readBounded
import com.bilimusic.player.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class OnlineSong(val bv:String,val title:String,val artist:String,val durationMs:Long=0,val cover:String?=null,val page:Int=1)
data class SearchPage(val songs:List<OnlineSong>,val page:Int,val pages:Int)
data class BiliAccount(val loggedIn:Boolean=false,val mid:Long=0,val name:String="未登录",val avatar:String?=null)
data class LoginQr(val url:String,val key:String)
data class FavoriteFolder(val id:Long,val title:String,val count:Int)
private data class CachedAudio(val url:String,val until:Long)

private interface BiliHttpApi {
    @GET suspend fun bootstrap(@Url url:String):Response<ResponseBody>
    @GET("x/web-interface/search/type") suspend fun search(@Query("keyword")keyword:String,@Query("page")page:Int,@Query("search_type")type:String="video",@Query("tids")category:Int=3):Response<ResponseBody>
    @GET("x/web-interface/view") suspend fun view(@Query("bvid")bv:String):Response<ResponseBody>
    @GET("x/player/playurl") suspend fun play(@Query("bvid")bv:String,@Query("cid")cid:Long,@Query("fnval")fnval:Int=16,@Query("qn")qn:Int=64,@Query("fourk")fourk:Int=0):Response<ResponseBody>
    @GET("x/web-interface/nav") suspend fun account():Response<ResponseBody>
    @GET("https://passport.bilibili.com/x/passport-login/web/qrcode/generate") suspend fun generateQr():Response<ResponseBody>
    @GET("https://passport.bilibili.com/x/passport-login/web/qrcode/poll") suspend fun poll(@Query("qrcode_key")key:String):Response<ResponseBody>
    @GET("x/v3/fav/folder/created/list-all") suspend fun folders(@Query("up_mid")mid:Long):Response<ResponseBody>
    @GET("x/v3/fav/resource/list") suspend fun favorites(@Query("media_id")id:Long,@Query("pn")page:Int,@Query("ps")size:Int=20):Response<ResponseBody>
}

class BilibiliClient(context:Context) {
    private val session=BilibiliSessionStore(context)
    private val http=OkHttpClient.Builder().cookieJar(session).connectTimeout(12,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).callTimeout(25,TimeUnit.SECONDS).followRedirects(false)
        .addInterceptor{chain->session.duringRequest{chain.proceed(chain.request().newBuilder().header("User-Agent",USER_AGENT).header("Referer",REFERER).build())}}.build()
    private val api=Retrofit.Builder().baseUrl("https://api.bilibili.com/").client(http).build().create(BiliHttpApi::class.java)
    private val bootstrapLock=Mutex()
    private var bootstrapped=false
    private val audioCache=ConcurrentHashMap<String,CachedAudio>()
    private suspend fun bootstrap()=bootstrapLock.withLock {
        if(!bootstrapped) {
            var url="https://www.bilibili.com/"
            repeat(3) {
                val response=api.bootstrap(url)
                val next=response.headers()["Location"]
                response.body()?.close();response.errorBody()?.close()
                if(response.isSuccessful){bootstrapped=true;return@withLock}
                if(response.code() in 300..399 && next!=null) {
                    val uri=java.net.URI(url).resolve(next)
                    require(uri.scheme=="https"&&uri.host in setOf("www.bilibili.com","m.bilibili.com")&&uri.userInfo==null){"B站返回不受支持的跳转"}
                    url=uri.toString()
                } else error("无法连接 B站（HTTP ${response.code()}），请稍后重试")
            }
            error("B站跳转次数过多，请稍后重试")
        }
    }
    suspend fun search(keyword:String,page:Int=1):SearchPage=withContext(Dispatchers.IO) {
        require(keyword.isNotBlank()&&keyword.length<=200&&page in 1..1000)
        bootstrap()
        val data=decode(api.search(keyword.trim(),page))
        val list=data.optJSONArray("result")
        val songs=(0 until (list?.length()?:0)).mapNotNull { i->
            val item=list!!.getJSONObject(i);val bv=item.optString("bvid")
            if(runCatching{BiliParser.parse(bv)}.isFailure)null else OnlineSong(bv,plain(item.optString("title")),plain(item.optString("author")),OnlineRules.durationMs(item.optString("duration")),image(item.optString("pic")))
        }
        SearchPage(songs,page,data.optInt("numPages",1).coerceIn(1,1000))
    }
    suspend fun metadata(ref:BiliReference):OnlineSong=withContext(Dispatchers.IO) {
        bootstrap();val d=decode(api.view(ref.bv));val pages=d.optJSONArray("pages")
        require(ref.page<= (pages?.length()?:1)){ "此视频没有第 ${ref.page} P" }
        val page=pages?.getJSONObject(ref.page-1)
        OnlineSong(ref.bv,plain(d.optString("title"))+(if(ref.page>1)" · ${page?.optString("part")}" else ""),d.optJSONObject("owner")?.optString("name").orEmpty(),(page?.optLong("duration")?:d.optLong("duration"))*1000,image(d.optString("pic")),ref.page)
    }
    suspend fun resolve(ref:BiliReference):String=withContext(Dispatchers.IO) {
        val key="${ref.bv}:${ref.page}";val now=System.currentTimeMillis()
        audioCache[key]?.takeIf{it.until>now}?.let{return@withContext it.url}
        bootstrap();val metadata=decode(api.view(ref.bv));val pages=metadata.optJSONArray("pages")
        require(ref.page<= (pages?.length()?:1)){ "分 P 不存在" }
        val cid=pages?.getJSONObject(ref.page-1)?.getLong("cid")?:metadata.getLong("cid")
        val data=decode(api.play(ref.bv,cid));val list=data.optJSONObject("dash")?.optJSONArray("audio")
        val candidates=(0 until (list?.length()?:0)).map{list!!.getJSONObject(it)}
        val audio=candidates.filter{it.optString("codecs").startsWith("mp4a")}.minByOrNull{it.optLong("bandwidth",Long.MAX_VALUE)}
            ?:candidates.firstOrNull()
        val url=audio?.let{it.optString("baseUrl").ifBlank{it.optString("base_url")}}
        require(!url.isNullOrBlank()){ "此视频没有返回可用音轨，请登录后重试或选择其他视频" }
        require(OnlineRules.isStreamUrl(url)){ "播放来源暂不支持，请更换视频或稍后重试" }
        audioCache[key]=CachedAudio(url,OnlineRules.validUntil(url,now));url
    }
    fun invalidate(ref:BiliReference){audioCache.remove("${ref.bv}:${ref.page}")}
    suspend fun account():BiliAccount=withContext(Dispatchers.IO) {
        bootstrap();val d=decode(api.account(),allowGuest=true)
        if(!d.optBoolean("isLogin"))BiliAccount() else BiliAccount(true,d.getLong("mid"),d.optString("uname"),image(d.optString("face")))
    }
    suspend fun generateQr():LoginQr=withContext(Dispatchers.IO) {
        val d=decode(api.generateQr());val url=d.getString("url")
        val uri=java.net.URI(url);require(uri.scheme=="https"&&uri.host in setOf("passport.bilibili.com","account.bilibili.com")&&uri.userInfo==null){ "登录地址无效" }
        LoginQr(url,d.getString("qrcode_key"))
    }
    suspend fun pollQr(key:String):Int=withContext(Dispatchers.IO){decode(api.poll(key)).getInt("code")}
    suspend fun logout()=withContext(Dispatchers.IO){session.clear();audioCache.clear();bootstrapped=false}
    suspend fun folders(mid:Long):List<FavoriteFolder> = withContext(Dispatchers.IO) {
        require(mid>0);val list=decode(api.folders(mid)).optJSONArray("list")
        (0 until(list?.length()?:0)).map{list!!.getJSONObject(it).let{j->FavoriteFolder(j.getLong("id"),plain(j.optString("title")),j.optInt("media_count"))}}
    }
    suspend fun favorites(id:Long,page:Int):SearchPage=withContext(Dispatchers.IO) {
        require(id>0&&page in 1..1000);val d=decode(api.favorites(id,page));val list=d.optJSONArray("medias")
        val songs=(0 until(list?.length()?:0)).mapNotNull{i->val j=list!!.getJSONObject(i);val bv=j.optString("bvid");if(runCatching{BiliParser.parse(bv)}.isFailure)null else OnlineSong(bv,plain(j.optString("title")),j.optJSONObject("upper")?.optString("name").orEmpty(),j.optLong("duration")*1000,image(j.optString("cover")))}
        SearchPage(songs,page,if(d.optBoolean("has_more"))page+1 else page)
    }
    private fun decode(response:Response<ResponseBody>,allowGuest:Boolean=false):JSONObject {
        val body=if(response.isSuccessful)response.body() else response.errorBody()
        if(!response.isSuccessful){body?.close();throw IllegalStateException(if(response.code() in setOf(403,412,429))"B站要求验证或请求过于频繁，请登录后稍后重试" else "B站连接失败（HTTP ${response.code()}）")}
        val json=body?.use{JSONObject(it.byteStream().readBounded(3_000_000).toString(Charsets.UTF_8))}?:error("B站没有返回数据")
        val code=json.optInt("code",-1)
        if(code==-101&&allowGuest)return JSONObject().put("isLogin",false)
        check(code==0){when(code){-101->"请先登录 B站账号";-352,-412->"B站要求验证，请稍后重试或重新登录";else->"B站返回错误 $code：${plain(json.optString("message")).take(120)}"}}
        return json.optJSONObject("data")?:JSONObject()
    }
    private fun plain(value:String)=HtmlCompat.fromHtml(value,HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim().take(500)
    private fun image(value:String):String?=when{value.startsWith("https://")->value;value.startsWith("//")->"https:$value";value.startsWith("http://")->"https://"+value.removePrefix("http://");else->null}
    companion object {
        // The web API returns PC DASH audio. Keep the API and media requests on
        // the same application profile; an Android web profile expects HTML5 video.
        const val USER_AGENT="BiliMusic/0.2.0"
        const val REFERER="https://www.bilibili.com/"
    }
}
