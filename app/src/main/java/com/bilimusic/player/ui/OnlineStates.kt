package com.bilimusic.player.ui

import com.bilimusic.player.data.bilibili.*

data class OnlineSearchState(val loading:Boolean=false,val data:SearchPage?=null,val error:String?=null)
data class AccountState(val loading:Boolean=false,val account:BiliAccount?=null,val folders:List<FavoriteFolder> = emptyList(),val folder:FavoriteFolder?=null,val songs:SearchPage?=null,val error:String?=null)
data class QrState(val loading:Boolean=false,val qr:LoginQr?=null,val message:String="",val expired:Boolean=false)
