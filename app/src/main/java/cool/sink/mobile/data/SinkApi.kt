package cool.sink.mobile.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

interface SinkApi {
    @GET("api/verify")
    suspend fun verify(): VerifyResponse

    @GET("api/link/list")
    suspend fun listLinks(
        @Query("limit") limit: Int = 30,
        @Query("cursor") cursor: String? = null,
        @Query("sort") sort: String = "newest",
        @Query("tag") tag: String? = null,
        @Query("status") status: String = "active",
    ): LinkList

    @GET("api/link/search")
    suspend fun searchLinks(
        @Query("q") query: String,
        @Query("limit") limit: Int = 50,
    ): List<SearchItem>

    @GET("api/link/tags")
    suspend fun tags(): List<TagCount>

    @GET("api/link/query")
    suspend fun queryLink(@Query("slug") slug: String): Link

    @POST("api/link/create")
    suspend fun createLink(@Body payload: CreateLinkPayload): LinkResponse

    @PUT("api/link/edit")
    suspend fun editLink(@Body payload: EditLinkPayload): LinkResponse

    @POST("api/link/delete")
    suspend fun deleteLink(@Body payload: DeleteLinkPayload)
}
