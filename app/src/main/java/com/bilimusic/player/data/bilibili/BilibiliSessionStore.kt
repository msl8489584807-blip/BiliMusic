package com.bilimusic.player.data.bilibili

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/** Called by OkHttp on worker threads. Credentials never enter Room, logs or JSON backups. */
class BilibiliSessionStore(context:Context,private val storeName:String="bilibili_session",private val keyAlias:String="BiliMusicAccount"):CookieJar {
    private val preferences=context.getSharedPreferences(storeName,Context.MODE_PRIVATE)
    private val cookies=linkedMapOf<String,Cookie>()
    private var loaded=false
    private val generation=AtomicLong(0)
    private val requestGeneration=ThreadLocal<Long>()
    fun <T> duringRequest(block:()->T):T {
        requestGeneration.set(generation.get())
        return try{block()}finally{requestGeneration.remove()}
    }
    private fun identity(cookie:Cookie)="${cookie.domain}|${cookie.path}|${cookie.name}"
    @Synchronized override fun loadForRequest(url:HttpUrl):List<Cookie> {
        load()
        if(requestGeneration.get()?.let{it!=generation.get()}==true)return emptyList()
        return cookies.values.filter{it.expiresAt>System.currentTimeMillis()&&it.matches(url)}
    }
    @Synchronized override fun saveFromResponse(url:HttpUrl,values:List<Cookie>) {
        load()
        if(requestGeneration.get()?.let{it!=generation.get()}==true)return
        values.filter{it.domain=="bilibili.com"||it.domain.endsWith(".bilibili.com")}.forEach {
            if(it.expiresAt<=System.currentTimeMillis())cookies.remove(identity(it)) else cookies[identity(it)]=it
        }
        if(values.isNotEmpty())try{persist()}catch(e:Exception){throw IOException("无法安全保存本机登录，请解锁设备后重试",e)}
    }
    @Synchronized fun clear() {generation.incrementAndGet();cookies.clear();loaded=true;check(preferences.edit().clear().commit()){ "无法清除本机登录状态" }}
    private fun load() {
        if(loaded)return
        val encoded=preferences.getString("encrypted_cookies",null)
        if(encoded!=null)try {
            val parts=encoded.split(':',limit=2)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)))
            val text=cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)).toString(Charsets.UTF_8)
            val base=okhttp3.HttpUrl.Builder().scheme("https").host("www.bilibili.com").build()
            text.lineSequence().mapNotNull{Cookie.parse(base,it)}.forEach{cookies[identity(it)]=it}
        } catch(e:Exception) {
            preferences.edit().remove("encrypted_cookies").commit()
            android.util.Log.w("BilibiliSession","Saved login unavailable: ${e.javaClass.simpleName}")
        }
        loaded=true
    }
    private fun persist() {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
        val plaintext=cookies.values.filter{it.expiresAt>System.currentTimeMillis()}.joinToString("\n").toByteArray(Charsets.UTF_8)
        val encrypted=cipher.doFinal(plaintext)
        val encoded=Base64.encodeToString(cipher.iv,Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP)
        check(preferences.edit().putString("encrypted_cookies",encoded).commit()){ "无法保存本机登录状态" }
    }
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (store.getKey(keyAlias,null) as? SecretKey)?.let{return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{
            init(KeyGenParameterSpec.Builder(keyAlias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
}
