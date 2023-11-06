package onlydust.com.marketplace.api.bootstrap.it;

import onlydust.com.marketplace.api.bootstrap.helper.HasuraUserHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static onlydust.com.marketplace.api.rest.api.adapter.authentication.AuthenticationFilter.BEARER_PREFIX;

@ActiveProfiles({"hasura_auth"})
public class MeGetContributionsApiIT extends AbstractMarketplaceApiIT {
    @Autowired
    HasuraUserHelper userHelper;

    @Test
    void should_get_my_contributions() {
        // Given
        final String jwt = userHelper.authenticateAnthony().jwt();

        // When
        client.get()
                .uri(getApiURI(ME_GET_CONTRIBUTIONS, Map.of("pageSize", "3")))
                .header("Authorization", BEARER_PREFIX + jwt)
                // Then
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.PARTIAL_CONTENT)
                .expectBody()
                .json("""
                        {
                          "contributions": [
                            {
                              "id": "94c28c8ac7b92df46aa30bab982815275abe0369fdaea61b342c15729e67a8ac",
                              "createdAt": "2022-06-15T11:08:47Z",
                              "completedAt": "2022-06-15T11:34:25Z",
                              "type": "PULL_REQUEST",
                              "status": "COMPLETED",
                              "githubNumber": 11,
                              "githubTitle": ":memo: Update README with installation instructions",
                              "githubHtmlUrl": "https://github.com/onlydustxyz/marketplace-frontend/pull/11",
                              "githubBody": null,
                              "projectName": "No sponsors",
                              "repoName": "marketplace-frontend",
                              "links": [],
                              "rewardIds": []
                            },
                            {
                              "id": "94c28c8ac7b92df46aa30bab982815275abe0369fdaea61b342c15729e67a8ac",
                              "createdAt": "2022-06-15T11:08:47Z",
                              "completedAt": "2022-06-15T11:34:25Z",
                              "type": "PULL_REQUEST",
                              "status": "COMPLETED",
                              "githubNumber": 11,
                              "githubTitle": ":memo: Update README with installation instructions",
                              "githubHtmlUrl": "https://github.com/onlydustxyz/marketplace-frontend/pull/11",
                              "githubBody": null,
                              "projectName": "No sponsors",
                              "repoName": "marketplace-frontend",
                              "links": [],
                              "rewardIds": []
                            },
                            {
                              "id": "94c28c8ac7b92df46aa30bab982815275abe0369fdaea61b342c15729e67a8ac",
                              "createdAt": "2022-06-15T11:08:47Z",
                              "completedAt": "2022-06-15T11:34:25Z",
                              "type": "PULL_REQUEST",
                              "status": "COMPLETED",
                              "githubNumber": 11,
                              "githubTitle": ":memo: Update README with installation instructions",
                              "githubHtmlUrl": "https://github.com/onlydustxyz/marketplace-frontend/pull/11",
                              "githubBody": null,
                              "projectName": "No sponsors",
                              "repoName": "marketplace-frontend",
                              "links": [],
                              "rewardIds": []
                            }
                          ],
                          "projects": [
                            {
                              "id": "298a547f-ecb6-4ab2-8975-68f4e9bf7b39",
                              "slug": "kaaper",
                              "name": "kaaper",
                              "shortDescription": "Documentation generator for Cairo projects.",
                              "logoUrl": null,
                              "visibility": "PUBLIC"
                            },
                            {
                              "id": "90fb751a-1137-4815-b3c4-54927a5db059",
                              "slug": "no-sponsors",
                              "name": "No sponsors",
                              "shortDescription": "afsasdas",
                              "logoUrl": null,
                              "visibility": "PUBLIC"
                            },
                            {
                              "id": "594ca5ca-48f7-49a8-9c26-84b949d4fdd9",
                              "slug": "mooooooonlight",
                              "name": "Mooooooonlight",
                              "shortDescription": "hello la team",
                              "logoUrl": "https://onlydust-app-images.s3.eu-west-1.amazonaws.com/1913921207486176664.jpg",
                              "visibility": "PUBLIC"
                            },
                            {
                              "id": "f39b827f-df73-498c-8853-99bc3f562723",
                              "slug": "qa-new-contributions",
                              "name": "QA new contributions",
                              "shortDescription": "QA new contributions",
                              "logoUrl": null,
                              "visibility": "PUBLIC"
                            }
                          ],
                          "repos": [
                            {
                              "id": 498695724,
                              "owner": "onlydustxyz",
                              "name": "marketplace-frontend",
                              "htmlUrl": null
                            },
                            {
                              "id": 493591124,
                              "owner": "onlydustxyz",
                              "name": "kaaper",
                              "htmlUrl": null
                            }
                          ],
                          "hasMore": true,
                          "totalPageNumber": 1394,
                          "totalItemNumber": 4182,
                          "nextPageIndex": 1
                        }
                        """);
    }

    @Test
    void should_get_my_rewards_with_project_filter() {
        // Given
        final String jwt = userHelper.authenticateAnthony().jwt();

        // When
        client.get()
                .uri(getApiURI(ME_GET_CONTRIBUTIONS, Map.of(
                        "projects", "f39b827f-df73-498c-8853-99bc3f562723,594ca5ca-48f7-49a8-9c26-84b949d4fdd9")
                ))
                .header("Authorization", BEARER_PREFIX + jwt)
                // Then
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.PARTIAL_CONTENT)
                .expectBody()
                .jsonPath("$.contributions.length()").isEqualTo(50)
                .jsonPath("$.projects.length()").isEqualTo(2)
                .jsonPath("$.projects[0].id").isEqualTo("594ca5ca-48f7-49a8-9c26-84b949d4fdd9")
                .jsonPath("$.projects[1].id").isEqualTo("f39b827f-df73-498c-8853-99bc3f562723")
                .jsonPath("$.repos.length()").isEqualTo(1)
                .jsonPath("$.repos[0].id").isEqualTo(498695724)
                .jsonPath("$.hasMore").isEqualTo(true)
                .jsonPath("$.totalPageNumber").isEqualTo(42)
                .jsonPath("$.totalItemNumber").isEqualTo(2082)
                .jsonPath("$.nextPageIndex").isEqualTo(1)
        ;
    }

    @Test
    void should_get_my_rewards_with_repos_filter() {
        // Given
        final String jwt = userHelper.authenticateAnthony().jwt();

        // When
        client.get()
                .uri(getApiURI(ME_GET_CONTRIBUTIONS, Map.of(
                        "repositories", "493591124")
                ))
                .header("Authorization", BEARER_PREFIX + jwt)
                // Then
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.contributions.length()").isEqualTo(18)
                .jsonPath("$.projects.length()").isEqualTo(1)
                .jsonPath("$.projects[0].id").isEqualTo("298a547f-ecb6-4ab2-8975-68f4e9bf7b39")
                .jsonPath("$.repos.length()").isEqualTo(1)
                .jsonPath("$.repos[0].id").isEqualTo(493591124)
                .jsonPath("$.hasMore").isEqualTo(false)
                .jsonPath("$.totalPageNumber").isEqualTo(1)
                .jsonPath("$.totalItemNumber").isEqualTo(18)
                .jsonPath("$.nextPageIndex").isEqualTo(0)
        ;
    }

    @Test
    void should_get_my_rewards_with_type_filter() {
        // Given
        final String jwt = userHelper.authenticateAnthony().jwt();

        // When
        client.get()
                .uri(getApiURI(ME_GET_CONTRIBUTIONS, Map.of(
                        "types", "ISSUE")
                ))
                .header("Authorization", BEARER_PREFIX + jwt)
                // Then
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.contributions.length()").isEqualTo(40)
                .jsonPath("$.contributions[0].type").isEqualTo("ISSUE")
                .jsonPath("$.hasMore").isEqualTo(false)
                .jsonPath("$.totalPageNumber").isEqualTo(1)
                .jsonPath("$.totalItemNumber").isEqualTo(40)
                .jsonPath("$.nextPageIndex").isEqualTo(0)
        ;
    }

    @Test
    void should_get_my_rewards_with_status_filter() {
        // Given
        final String jwt = userHelper.authenticateAnthony().jwt();

        // When
        client.get()
                .uri(getApiURI(ME_GET_CONTRIBUTIONS, Map.of(
                        "statuses", "IN_PROGRESS")
                ))
                .header("Authorization", BEARER_PREFIX + jwt)
                // Then
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.PARTIAL_CONTENT)
                .expectBody()
                .jsonPath("$.contributions.length()").isEqualTo(50)
                .jsonPath("$.contributions[0].status").isEqualTo("IN_PROGRESS")
                .jsonPath("$.hasMore").isEqualTo(true)
                .jsonPath("$.totalPageNumber").isEqualTo(11)
                .jsonPath("$.totalItemNumber").isEqualTo(545)
                .jsonPath("$.nextPageIndex").isEqualTo(1)
        ;
    }

    @Test
    void should_get_list_rewards_associated_to_a_contribution() {
        // Given
        final String jwt = userHelper.authenticateAnthony().jwt();

        // When
        client.get()
                .uri(getApiURI(ME_GET_CONTRIBUTIONS,
                        Map.of("pageSize", "1",
                                "page", "17",
                                "projects", "298a547f-ecb6-4ab2-8975-68f4e9bf7b39",
                                "repositories", "498695724",
                                "statuses", "COMPLETED",
                                "types", "PULL_REQUEST")))
                .header("Authorization", BEARER_PREFIX + jwt)
                // Then
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.PARTIAL_CONTENT)
                .expectBody()
                .json("""
                        {
                          "contributions": [
                            {
                              "rewardIds": [
                                "6587511b-3791-47c6-8430-8f793606c63a",
                                "0b275f04-bdb1-4d4f-8cd1-76fe135ccbdf",
                                "335e45a5-7f59-4519-8a12-1addc530214c",
                                "e9ebbe59-fb74-4a6c-9a51-6d9050412977",
                                "e33ea956-d2f5-496b-acf9-e2350faddb16",
                                "dd7d445f-6915-4955-9bae-078173627b05",
                                "d22f75ab-d9f5-4dc6-9a85-60dcd7452028",
                                "95e079c9-609c-4531-8c5c-13217306b299"
                              ]
                            }
                          ]
                        }

                        """)
        ;
    }
}
