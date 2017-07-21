package com.github.fitzoh.slackprchecker

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.body
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.CompletableFuture

data class SlashCommand(var token: String?,
                        var team_id: String?,
                        var team_domain: String?,
                        var channel_id: String?,
                        var channel_name: String?,
                        var user_id: String?,
                        var user_name: String?,
                        var command: String?,
                        var text: String?,
                        var response_url: String?)


/**
 * Don't judge me, quick and dirty
 */
@RestController
class SlashCommandController(val pullRequestService: PullRequestService) {

    val webClient: WebClient = WebClient.create()
    val log: Logger = LoggerFactory.getLogger(SlashCommandController::class.java)

    fun getProjectName(pr: GitHubPullRequest) = pr.url.split("/")[5]

    fun formatSinglePr(pr: GitHubPullRequest) = "${getProjectName(pr)} <${pr.url}|${pr.title}>"

    fun formatPullRequestList(prs: List<GitHubPullRequest>) = prs.map(this::formatSinglePr).joinToString("\n")

    fun convertToSlackField(prs: UserPullRequests) = mapOf("title" to prs.username, "value" to formatPullRequestList(prs.pullRequests))

    @RequestMapping("/")
    fun handleSlashCommand(command: SlashCommand): Mono<Map<String, Any>> {
        log.info("slash command: {}", command)
        CompletableFuture.runAsync {

            val fields = pullRequestService
                    .getTeamPullRequests()
                    .filter { it.pullRequests.isNotEmpty() }
                    .map { this::convertToSlackField }
                    .collectList().block(Duration.ofMinutes(1))

            val message = mapOf(
                    "response_type" to "in_channel",
                    "text" to "Squid Squad Pull Requests",
                    "attachments" to listOf(mapOf(
                            "fields" to fields)))

            webClient.post().uri(command.response_url)
                    .body(Mono.just(message))
                    .exchange()
                    .block(Duration.ofMinutes(1))
        }

        return Mono.just(mapOf("text" to "processing request"))
    }

}