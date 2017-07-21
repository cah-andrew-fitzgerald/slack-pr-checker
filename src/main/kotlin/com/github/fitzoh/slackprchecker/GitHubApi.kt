package com.github.fitzoh.slackprchecker

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders.ACCEPT
import org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE
import org.springframework.stereotype.Service
import org.springframework.util.Base64Utils
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDateTime

data class GitHubTeam(val name: String, val id: String, val url: String)
data class GitHubUser(val login: String)
data class GitHubSearchResults(@JsonProperty("incomplete_results") val incompleteResults: String, val items: List<GitHubPullRequest>)
data class GitHubPullRequest(val url: String, val title: String, @JsonProperty("created_at") val createdAt: LocalDateTime)

@Service
class GitHubApi(@Qualifier("gitHubClient") val webClient: WebClient) {

    fun getTeamId(orgName: String, teamName: String): Mono<String> = webClient.get()
            .uri("/orgs/$orgName/teams?per_page=10000")
            .retrieve()
            .bodyToFlux(GitHubTeam::class.java)
            .filter { it.name == teamName }
            .next()
            .log("getTeamId")
            .map { it.id }

    fun getTeamMembers(teamId: String): Flux<GitHubUser> = webClient.get()
            .uri("/teams/$teamId/members")
            .retrieve()
            .bodyToFlux(GitHubUser::class.java)
            .log("getTeamMembers")

    fun getUserPullRequests(user: GitHubUser): Flux<GitHubPullRequest> = webClient.get()
            .uri("/search/issues?q=type:pr+state:open+user:cahcommercial+author:${user.login}&sort=created&order=asc")
            .retrieve()
            .bodyToMono(GitHubSearchResults::class.java)
            .flatMapIterable { it.items }
            .log("getUserPullRequests")

}


data class UserPullRequests(val username: String, val pullRequests: List<GitHubPullRequest>)

@Service
class PullRequestService(val gitHubApi: GitHubApi, gitHubProperties: GitHubProperties) {
    val teamId: String = gitHubApi.getTeamId(gitHubProperties.orgName, gitHubProperties.teamName).block()

    fun getUserPullRequests(user: GitHubUser): Mono<UserPullRequests> = gitHubApi.getUserPullRequests(user)
            .collectList()
            .map { pr -> UserPullRequests(user.login, pr) }

    fun getTeamPullRequests(): Flux<UserPullRequests> = gitHubApi.getTeamMembers(teamId)
            .flatMap { getUserPullRequests(it) }

}

@Configuration
@ConfigurationProperties(prefix = "github")
class GitHubProperties {
    var apiUrl = "https://api.github.com"
    lateinit var username: String
    lateinit var token: String
    lateinit var orgName: String
    lateinit var teamName: String

    fun authorization() = "Basic " + Base64Utils.encodeToString("$username:$token".toByteArray())

    @Bean
    @Qualifier("gitHubClient")
    fun webClient(): WebClient = WebClient.builder()
            .baseUrl(apiUrl)
            .defaultHeader("Authorization", authorization())
            .defaultHeader(ACCEPT, APPLICATION_JSON_UTF8_VALUE)
            .build()


}
