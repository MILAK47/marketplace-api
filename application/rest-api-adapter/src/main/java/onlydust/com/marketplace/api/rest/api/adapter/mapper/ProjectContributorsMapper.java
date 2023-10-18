package onlydust.com.marketplace.api.rest.api.adapter.mapper;

import onlydust.com.marketplace.api.contract.model.ContributorListItemResponse;
import onlydust.com.marketplace.api.contract.model.ContributorPageResponse;
import onlydust.com.marketplace.api.domain.view.ProjectContributorsLinkView;
import onlydust.com.marketplace.api.domain.view.pagination.Page;

import static java.util.Objects.isNull;

public interface ProjectContributorsMapper {

    static ProjectContributorsLinkView.SortBy mapSortBy(String sort) {
        final ProjectContributorsLinkView.SortBy sortBy = switch (isNull(sort) ? "" : sort) {
            case "CONTRIBUTION_COUNT" -> ProjectContributorsLinkView.SortBy.contributionCount;
            case "EARNED" -> ProjectContributorsLinkView.SortBy.earned;
            case "REWARD_COUNT" -> ProjectContributorsLinkView.SortBy.rewardCount;
            case "TO_REWARD_COUNT" -> ProjectContributorsLinkView.SortBy.toRewardCount;
            default -> ProjectContributorsLinkView.SortBy.login;
        };
        return sortBy;
    }

    static ContributorPageResponse mapProjectContributorsLinkViewPageToResponse(final Page<ProjectContributorsLinkView> page) {
        final ContributorPageResponse contributorPageResponse = new ContributorPageResponse();
        contributorPageResponse.setTotalPageNumber(page.getTotalPageNumber());
        contributorPageResponse.setTotalItemNumber(page.getTotalItemNumber());
        contributorPageResponse.setContributors(page.getContent().stream()
                .map(ProjectContributorsMapper::mapProjectContributorsLinkViewToResponse).toList());
        return contributorPageResponse;
    }

    static ContributorListItemResponse mapProjectContributorsLinkViewToResponse(final ProjectContributorsLinkView projectContributorsLinkView) {
        final ContributorListItemResponse response = new ContributorListItemResponse();
        response.setAvatarUrl(projectContributorsLinkView.getAvatarUrl());
        response.setGithubUserId(projectContributorsLinkView.getGithubUserId());
        response.setLogin(projectContributorsLinkView.getLogin());
        response.setEarned(projectContributorsLinkView.getEarned());
        response.setContributionCount(projectContributorsLinkView.getContributionCount());
        response.setRewardCount(projectContributorsLinkView.getRewards());
        response.setContributionToRewardCount(projectContributorsLinkView.getTotalToReward());
        response.setPullRequestToReward(projectContributorsLinkView.getPullRequestsToRewardCount());
        response.setCodeReviewToReward(projectContributorsLinkView.getCodeReviewToRewardCount());
        response.setIssueToReward(projectContributorsLinkView.getIssuesToRewardCount());
        return response;
    }
}
