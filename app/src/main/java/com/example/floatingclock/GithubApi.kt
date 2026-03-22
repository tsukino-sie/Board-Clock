package com.example.floatingclock
import retrofit2.Call
import retrofit2.http.GET

data class Release(val tag_name: String, val assets: List<Asset>)
data class Asset(val browser_download_url: String)

interface GithubApi {
    @GET("repos/tsukino-sie/Board-Clock/releases/latest")
    fun getLatestRelease(): Call<Release>
}