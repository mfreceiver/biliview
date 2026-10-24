package cn.vectory.biliview.network

import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class GithubApiTest {
    @Test
    fun `get latest release build`() = runBlocking {
        println(GithubApi.getLatestReleaseBuild())
    }
}