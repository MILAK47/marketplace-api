package onlydust.com.marketplace.api.domain.service;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import onlydust.com.marketplace.api.domain.exception.OnlyDustException;
import onlydust.com.marketplace.api.domain.gateway.DateProvider;
import onlydust.com.marketplace.api.domain.model.*;
import onlydust.com.marketplace.api.domain.port.input.ProjectFacadePort;
import onlydust.com.marketplace.api.domain.port.input.ProjectObserverPort;
import onlydust.com.marketplace.api.domain.port.output.*;
import onlydust.com.marketplace.api.domain.view.*;
import onlydust.com.marketplace.api.domain.view.pagination.Page;
import onlydust.com.marketplace.api.domain.view.pagination.SortDirection;
import org.apache.commons.lang3.tuple.Pair;

import javax.transaction.Transactional;
import java.io.InputStream;
import java.net.URL;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@AllArgsConstructor
public class ProjectService implements ProjectFacadePort {

    private static final Pattern ISSUE_URL_REGEX = Pattern.compile(
            "https://github\\.com/([^/]+)/([^/]+)/issues/([0-9]+)/?");
    private static final Pattern PULL_REQUEST_URL_REGEX = Pattern.compile(
            "https://github\\.com/([^/]+)/([^/]+)/pull/([0-9]+)/?");
    private static final int STALE_CONTRIBUTION_THRESHOLD_IN_DAYS = 10;

    private final ProjectObserverPort projectObserverPort;
    private final ProjectStoragePort projectStoragePort;
    private final ImageStoragePort imageStoragePort;
    private final UUIDGeneratorPort uuidGeneratorPort;
    private final PermissionService permissionService;
    private final IndexerPort indexerPort;
    private final DateProvider dateProvider;
    private final EventStoragePort eventStoragePort;
    private final ContributionStoragePort contributionStoragePort;
    private final DustyBotStoragePort dustyBotStoragePort;
    private final GithubStoragePort githubStoragePort;

    @Override
    public ProjectDetailsView getById(UUID projectId, User caller) {
        final var userId = caller == null ? null : caller.getId();

        final ProjectDetailsView projectById = projectStoragePort.getById(projectId, caller);
        if (!permissionService.hasUserAccessToProject(projectId, userId)) {
            throw OnlyDustException.forbidden("Project %s is private and user %s cannot access it".formatted(projectId, userId));
        }
        return projectById;
    }

    @Override
    public ProjectDetailsView getBySlug(String slug, User caller) {
        final var userId = caller == null ? null : caller.getId();

        final ProjectDetailsView projectBySlug = projectStoragePort.getBySlug(slug, caller);
        if (!permissionService.hasUserAccessToProject(slug, userId)) {
            throw OnlyDustException.forbidden("Project %s is private and user %s cannot access it".formatted(slug, userId));
        }
        return projectBySlug;
    }

    @Override
    public Page<ProjectCardView> getByTechnologiesSponsorsUserIdSearchSortBy(List<String> technologies,
                                                                             List<UUID> sponsorIds, String search,
                                                                             ProjectCardView.SortBy sort, UUID userId
            , Boolean mine, Integer pageIndex, Integer pageSize) {
        return projectStoragePort.findByTechnologiesSponsorsUserIdSearchSortBy(technologies, sponsorIds, userId, search,
                sort, mine, pageIndex, pageSize);
    }

    @Override
    public Page<ProjectCardView> getByTechnologiesSponsorsSearchSortBy(List<String> technologies, List<UUID> sponsorIds,
                                                                       String search, ProjectCardView.SortBy sort,
                                                                       Integer pageIndex, Integer pageSize) {
        return projectStoragePort.findByTechnologiesSponsorsSearchSortBy(technologies, sponsorIds, search, sort,
                pageIndex, pageSize);
    }

    @Override
    @Transactional
    public Pair<UUID, String> createProject(CreateProjectCommand command) {
        if (command.getGithubUserIdsAsProjectLeadersToInvite() != null) {
            indexerPort.indexUsers(command.getGithubUserIdsAsProjectLeadersToInvite());
        }

        final UUID projectId = uuidGeneratorPort.generate();
        final String projectSlug = this.projectStoragePort.createProject(projectId, command.getName(),
                command.getShortDescription(), command.getLongDescription(),
                command.getIsLookingForContributors(), command.getMoreInfos(),
                command.getGithubRepoIds(),
                command.getFirstProjectLeaderId(),
                command.getGithubUserIdsAsProjectLeadersToInvite(),
                ProjectVisibility.PUBLIC,
                command.getImageUrl(),
                ProjectRewardSettings.defaultSettings(dateProvider.now()));

        eventStoragePort.saveEvent(new ProjectCreatedOldEvent(projectId));

        projectObserverPort.onProjectCreated(projectId);
        projectObserverPort.onLeaderAssigned(projectId, command.getFirstProjectLeaderId());
        if (nonNull(command.getGithubUserIdsAsProjectLeadersToInvite())) {
            command.getGithubUserIdsAsProjectLeadersToInvite().forEach(githubUserId ->
                    projectObserverPort.onLeaderInvited(projectId, githubUserId));
        }
        if (nonNull(command.getGithubRepoIds())) {
            projectObserverPort.onLinkedReposChanged(projectId, Set.copyOf(command.getGithubRepoIds()), Set.of());
        }
        return Pair.of(projectId, projectSlug);
    }

    @Override
    @Transactional
    public Pair<UUID, String> updateProject(UUID projectLeadId, UpdateProjectCommand command) {
        if (!permissionService.isUserProjectLead(command.getId(), projectLeadId)) {
            throw OnlyDustException.forbidden("Only project leads can update their projects");
        }
        if (command.getGithubUserIdsAsProjectLeadersToInvite() != null) {
            indexerPort.indexUsers(command.getGithubUserIdsAsProjectLeadersToInvite());
        }

        final Set<UUID> unassignedLeaderIds = getUnassignedLeaderIds(command);

        final Set<Long> invitedLeaderGithubIds = new HashSet<>();
        final Set<Long> invitationCancelledLeaderGithubIds = new HashSet<>();
        getLeaderInvitationsChanges(command, invitationCancelledLeaderGithubIds, invitedLeaderGithubIds);

        final Set<Long> linkedRepoIds = new HashSet<>();
        final Set<Long> unlinkedRepoIds = new HashSet<>();
        getLinkedReposChanges(command, linkedRepoIds, unlinkedRepoIds);

        this.projectStoragePort.updateProject(command.getId(),
                command.getName(),
                command.getShortDescription(), command.getLongDescription(),
                command.getIsLookingForContributors(), command.getMoreInfos(),
                command.getGithubRepoIds(),
                command.getGithubUserIdsAsProjectLeadersToInvite(),
                command.getProjectLeadersToKeep(), command.getImageUrl(),
                command.getRewardSettings());

        projectObserverPort.onProjectDetailsUpdated(command.getId());
        invitedLeaderGithubIds.forEach(leaderId -> projectObserverPort.onLeaderInvited(command.getId(), leaderId));
        invitationCancelledLeaderGithubIds.forEach(leaderId ->
                projectObserverPort.onLeaderInvitationCancelled(command.getId(), leaderId));
        unassignedLeaderIds.forEach(leaderId -> projectObserverPort.onLeaderUnassigned(command.getId(), leaderId));
        if (!isNull(command.getGithubRepoIds())) {
            projectObserverPort.onLinkedReposChanged(command.getId(), linkedRepoIds, unlinkedRepoIds);
        }
        if (!isNull(command.getRewardSettings())) {
            projectObserverPort.onRewardSettingsChanged(command.getId());
        }
        final String slug = this.projectStoragePort.getProjectSlugById(command.getId());
        return Pair.of(command.getId(), slug);
    }

    private void getLinkedReposChanges(UpdateProjectCommand command, Set<Long> linkedRepoIds,
                                       Set<Long> unlinkedRepoIds) {
        if (command.getGithubRepoIds() != null) {
            final var previousRepos = projectStoragePort.getProjectRepoIds(command.getId());
            unlinkedRepoIds.addAll(previousRepos.stream()
                    .filter(repoId -> !command.getGithubRepoIds().contains(repoId))
                    .collect(Collectors.toSet()));

            linkedRepoIds.addAll(command.getGithubRepoIds().stream()
                    .filter(repoId -> !previousRepos.contains(repoId))
                    .collect(Collectors.toSet()));
        }
    }

    private void getLeaderInvitationsChanges(UpdateProjectCommand command,
                                             Set<Long> invitationCancelledLeaderGithubIds,
                                             Set<Long> invitedLeaderGithubIds) {
        if (command.getGithubUserIdsAsProjectLeadersToInvite() != null) {
            final var projectInvitedLeadIds = projectStoragePort.getProjectInvitedLeadIds(command.getId());
            invitationCancelledLeaderGithubIds.addAll(projectInvitedLeadIds.stream()
                    .filter(leaderId -> !command.getGithubUserIdsAsProjectLeadersToInvite().contains(leaderId))
                    .toList());
            invitedLeaderGithubIds.addAll(command.getGithubUserIdsAsProjectLeadersToInvite().stream()
                    .filter(leaderId -> !projectInvitedLeadIds.contains(leaderId)).toList());
        }
    }

    private Set<UUID> getUnassignedLeaderIds(UpdateProjectCommand command) {
        if (command.getProjectLeadersToKeep() == null) {
            return Set.of();
        }

        final var projectLeadIds = projectStoragePort.getProjectLeadIds(command.getId());
        if (command.getProjectLeadersToKeep().stream()
                .anyMatch(userId -> projectLeadIds.stream()
                        .noneMatch(projectLeaderId -> projectLeaderId.equals(userId)))) {
            throw OnlyDustException.badRequest("Project leaders to keep must be a subset of current project " +
                                               "leaders");
        }
        return projectLeadIds.stream()
                .filter(leaderId -> !command.getProjectLeadersToKeep().contains(leaderId))
                .collect(Collectors.toSet());
    }

    @Override
    public URL saveLogoImage(InputStream imageInputStream) {
        return this.imageStoragePort.storeImage(imageInputStream);
    }

    @Override
    public Page<ProjectContributorsLinkView> getContributors(UUID projectId, String login,
                                                             ProjectContributorsLinkView.SortBy sortBy,
                                                             SortDirection sortDirection,
                                                             Integer pageIndex, Integer pageSize) {
        return projectStoragePort.findContributors(projectId, login, sortBy, sortDirection, pageIndex, pageSize);
    }

    @Override
    public Page<ProjectContributorsLinkView> getContributorsForProjectLeadId(UUID projectId, String login,
                                                                             UUID projectLeadId,
                                                                             ProjectContributorsLinkView.SortBy sortBy,
                                                                             SortDirection sortDirection,
                                                                             Integer pageIndex,
                                                                             Integer pageSize) {
        if (permissionService.isUserProjectLead(projectId, projectLeadId)) {
            return projectStoragePort.findContributorsForProjectLead(projectId, login, sortBy, sortDirection, pageIndex,
                    pageSize);
        } else {
            return projectStoragePort.findContributors(projectId, login, sortBy, sortDirection, pageIndex, pageSize);
        }
    }

    @Override
    public ProjectRewardsPageView getRewards(UUID projectId, UUID projectLeadId,
                                             ProjectRewardView.Filters filters,
                                             Integer pageIndex, Integer pageSize,
                                             ProjectRewardView.SortBy sortBy, SortDirection sortDirection) {
        if (permissionService.isUserProjectLead(projectId, projectLeadId)) {
            return projectStoragePort.findRewards(projectId, filters, sortBy, sortDirection, pageIndex, pageSize);
        } else {
            throw OnlyDustException.forbidden("Only project leads can read rewards on their projects");
        }
    }

    @Override
    public ProjectBudgetsView getBudgets(UUID projectId, UUID projectLeadId) {
        if (permissionService.isUserProjectLead(projectId, projectLeadId)) {
            return projectStoragePort.findBudgets(projectId);
        } else {
            throw OnlyDustException.forbidden("Only project leads can read budgets on their projects");
        }
    }

    @Override
    public RewardView getRewardByIdForProjectLead(UUID projectId, UUID rewardId, UUID projectLeadId) {
        if (permissionService.isUserProjectLead(projectId, projectLeadId)) {
            return projectStoragePort.getProjectReward(rewardId);
        } else {
            throw OnlyDustException.forbidden("Only project leads can read reward on their projects");
        }
    }

    @Override
    public Page<RewardItemView> getRewardItemsPageByIdForProjectLead(UUID projectId, UUID rewardId,
                                                                     UUID projectLeadId, int pageIndex, int pageSize) {
        if (permissionService.isUserProjectLead(projectId, projectLeadId)) {
            return projectStoragePort.getProjectRewardItems(rewardId, pageIndex, pageSize);
        } else {
            throw OnlyDustException.forbidden("Only project leads can read reward items on their projects");
        }
    }

    @Override
    public Page<RewardableItemView> getRewardableItemsPageByTypeForProjectLeadAndContributorId(UUID projectId,
                                                                                               ContributionType contributionType,
                                                                                               ContributionStatus contributionStatus,
                                                                                               UUID projectLeadId,
                                                                                               Long githubUserid,
                                                                                               int pageIndex,
                                                                                               int pageSize,
                                                                                               String search,
                                                                                               Boolean includeIgnoredItems) {
        if (permissionService.isUserProjectLead(projectId, projectLeadId)) {
            return projectStoragePort.getProjectRewardableItemsByTypeForProjectLeadAndContributorId(projectId,
                    contributionType, contributionStatus, githubUserid, pageIndex, pageSize, search,
                    includeIgnoredItems);
        } else {
            throw OnlyDustException.forbidden("Only project leads can read rewardable items on their projects");
        }
    }

    @Override
    public List<RewardableItemView> getAllCompletedRewardableItemsForProjectLeadAndContributorId(UUID projectId,
                                                                                                 UUID projectLeadId,
                                                                                                 Long githubUserId) {
        if (permissionService.isUserProjectLead(projectId, projectLeadId)) {
            final var allCompletedRewardableItems =
                    projectStoragePort.getProjectRewardableItemsByTypeForProjectLeadAndContributorId(projectId,
                            null, ContributionStatus.COMPLETED, githubUserId, 0, 1_000_000, null, false);
            return allCompletedRewardableItems != null ? allCompletedRewardableItems.getContent() : List.of();
        } else {
            throw OnlyDustException.forbidden("Only project leads can read rewardable items on their projects");
        }
    }

    @Override
    public RewardableItemView createAndCloseIssueForProjectIdAndRepositoryId(CreateAndCloseIssueCommand command) {
        if (permissionService.isUserProjectLead(command.getProjectId(), command.getProjectLeadId())) {
            if (permissionService.isRepoLinkedToProject(command.getProjectId(), command.getGithubRepoId())) {
                final var repo = githubStoragePort.findRepoById(command.getGithubRepoId()).orElseThrow(() ->
                        OnlyDustException.notFound("Repo not found"));
                final var openedIssue = dustyBotStoragePort.createIssue(repo, command.getTitle(),
                        command.getDescription());
                final RewardableItemView closedIssue = dustyBotStoragePort.closeIssue(repo, openedIssue.getNumber());
                indexerPort.indexIssue(repo.getOwner(), repo.getName(), closedIssue.getNumber());
                return closedIssue;
            } else {
                throw OnlyDustException.forbidden("Rewardable issue can only be created on repos linked to this " +
                                                  "project");
            }
        } else {
            throw OnlyDustException.forbidden("Only project leads can create rewardable issue on their projects");
        }
    }

    @Override
    public RewardableItemView addRewardableIssue(UUID projectId, UUID projectLeadId, String issueUrl) {
        if (!permissionService.isUserProjectLead(projectId, projectLeadId)) {
            throw OnlyDustException.forbidden("Only project leads can add other issues as rewardable items");
        }
        final var matcher = ISSUE_URL_REGEX.matcher(issueUrl);
        if (!matcher.matches()) {
            throw OnlyDustException.badRequest("Invalid issue url '%s'".formatted(issueUrl));
        }
        final var repoOwner = matcher.group(1);
        final var repoName = matcher.group(2);
        final var issueNumber = Long.parseLong(matcher.group(3));

        indexerPort.indexIssue(repoOwner, repoName, issueNumber);
        return projectStoragePort.getRewardableIssue(repoOwner, repoName, issueNumber);
    }

    @Override
    public RewardableItemView addRewardablePullRequest(UUID projectId, UUID projectLeadId, String pullRequestUrl) {
        if (!permissionService.isUserProjectLead(projectId, projectLeadId)) {
            throw OnlyDustException.forbidden("Only project leads can add other pull requests as rewardable items");
        }
        final var matcher = PULL_REQUEST_URL_REGEX.matcher(pullRequestUrl);
        if (!matcher.matches()) {
            throw OnlyDustException.badRequest("Invalid pull request url '%s'".formatted(pullRequestUrl));
        }
        final var repoOwner = matcher.group(1);
        final var repoName = matcher.group(2);
        final var pullRequestNumber = Long.parseLong(matcher.group(3));

        indexerPort.indexPullRequest(repoOwner, repoName, pullRequestNumber);
        return projectStoragePort.getRewardablePullRequest(repoOwner, repoName, pullRequestNumber);
    }

    @Override
    public Page<ContributionView> contributions(UUID projectId, User caller, ContributionView.Filters filters,
                                                ContributionView.Sort sort, SortDirection direction,
                                                Integer page, Integer pageSize) {
        if (!permissionService.isUserProjectLead(projectId, caller.getId())) {
            throw OnlyDustException.forbidden("Only project leads can list project contributions");
        }
        return contributionStoragePort.findContributions(caller.getGithubUserId(), filters, sort, direction, page,
                pageSize);
    }

    @Override
    public void updateProjectsRanking() {
        projectStoragePort.updateProjectsRanking();
    }

    @Override
    public Page<ContributionView> staledContributions(UUID projectId, User caller, Integer page, Integer pageSize) {
        final var filters = ContributionView.Filters.builder()
                .projects(List.of(projectId))
                .statuses(List.of(ContributionStatus.IN_PROGRESS))
                .to(Date.from(ZonedDateTime.now().minusDays(STALE_CONTRIBUTION_THRESHOLD_IN_DAYS).toInstant()))
                .build();

        return contributions(projectId, caller, filters, ContributionView.Sort.CREATED_AT, SortDirection.desc, page, pageSize);
    }

    @Override
    public Page<ChurnedContributorView> churnedContributors(UUID projectId, User caller, Integer page, Integer pageSize) {
        if (!permissionService.isUserProjectLead(projectId, caller.getId())) {
            throw OnlyDustException.forbidden("Only project leads can view project insights");
        }
        return projectStoragePort.getChurnedContributors(projectId, page, pageSize);
    }

    @Override
    public Page<NewcomerView> newcomers(UUID projectId, User caller, Integer page, Integer pageSize) {
        if (!permissionService.isUserProjectLead(projectId, caller.getId())) {
            throw OnlyDustException.forbidden("Only project leads can view project insights");
        }
        return projectStoragePort.getNewcomers(projectId, page, pageSize);
    }

    @Override
    public Page<ContributorActivityView> mostActives(UUID projectId, User caller, Integer page, Integer pageSize) {
        if (!permissionService.isUserProjectLead(projectId, caller.getId())) {
            throw OnlyDustException.forbidden("Only project leads can view project insights");
        }
        return projectStoragePort.getMostActivesContributors(projectId, page, pageSize);
    }
}
