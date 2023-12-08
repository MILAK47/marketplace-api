package onlydust.com.marketplace.api.postgres.adapter;

import lombok.AllArgsConstructor;
import onlydust.com.marketplace.api.domain.exception.OnlyDustException;
import onlydust.com.marketplace.api.domain.model.*;
import onlydust.com.marketplace.api.domain.port.output.ProjectStoragePort;
import onlydust.com.marketplace.api.domain.view.*;
import onlydust.com.marketplace.api.domain.view.pagination.Page;
import onlydust.com.marketplace.api.domain.view.pagination.PaginationHelper;
import onlydust.com.marketplace.api.domain.view.pagination.SortDirection;
import onlydust.com.marketplace.api.postgres.adapter.entity.read.*;
import onlydust.com.marketplace.api.postgres.adapter.entity.write.old.*;
import onlydust.com.marketplace.api.postgres.adapter.entity.write.old.type.CurrencyEnumEntity;
import onlydust.com.marketplace.api.postgres.adapter.mapper.*;
import onlydust.com.marketplace.api.postgres.adapter.repository.*;
import onlydust.com.marketplace.api.postgres.adapter.repository.old.ProjectIdRepository;
import onlydust.com.marketplace.api.postgres.adapter.repository.old.ProjectLeaderInvitationRepository;
import onlydust.com.marketplace.api.postgres.adapter.repository.old.ProjectRepoRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static onlydust.com.marketplace.api.postgres.adapter.mapper.ProjectMapper.moreInfosToEntities;

@AllArgsConstructor
public class PostgresProjectAdapter implements ProjectStoragePort {

    private static final int TOP_CONTRIBUTOR_COUNT = 3;
    private final ProjectRepository projectRepository;
    private final ProjectViewRepository projectViewRepository;
    private final ProjectIdRepository projectIdRepository;
    private final ProjectLeaderInvitationRepository projectLeaderInvitationRepository;
    private final ProjectRepoRepository projectRepoRepository;
    private final CustomProjectRepository customProjectRepository;
    private final CustomContributorRepository customContributorRepository;
    private final CustomProjectRewardRepository customProjectRewardRepository;
    private final CustomProjectBudgetRepository customProjectBudgetRepository;
    private final ProjectLeadViewRepository projectLeadViewRepository;
    private final CustomRewardRepository customRewardRepository;
    private final ProjectsPageRepository projectsPageRepository;
    private final ProjectsPageFiltersRepository projectsPageFiltersRepository;
    private final RewardableItemRepository rewardableItemRepository;
    private final CustomProjectRankingRepository customProjectRankingRepository;
    private final BudgetStatsRepository budgetStatsRepository;

    @Override
    @Transactional(readOnly = true)
    public ProjectDetailsView getById(UUID projectId) {
        final var projectEntity = projectViewRepository.findById(projectId)
                .orElseThrow(() -> OnlyDustException.notFound(format("Project %s not found", projectId)));
        return getProjectDetails(projectEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectDetailsView getBySlug(String slug) {
        final var projectEntity = projectViewRepository.findByKey(slug)
                .orElseThrow(() -> OnlyDustException.notFound(format("Project '%s' not found", slug)));
        return getProjectDetails(projectEntity);
    }

    @Override
    public String getProjectSlugById(UUID projectId) {
        return projectViewRepository.findById(projectId)
                .orElseThrow(() -> OnlyDustException.notFound(format("Project %s not found", projectId)))
                .getKey();
    }

    @Override
    public RewardableItemView getRewardableIssue(String repoOwner, String repoName, long issueNumber) {
        return rewardableItemRepository.findRewardableIssue(repoOwner, repoName, issueNumber)
                .map(RewardableItemMapper::itemToDomain)
                .orElseThrow(() -> OnlyDustException.notFound(format("Issue %s/%s#%d not found", repoOwner, repoName,
                        issueNumber)));
    }

    @Override
    public RewardableItemView getRewardablePullRequest(String repoOwner, String repoName, long pullRequestNumber) {
        return rewardableItemRepository.findRewardablePullRequest(repoOwner, repoName, pullRequestNumber)
                .map(RewardableItemMapper::itemToDomain)
                .orElseThrow(() -> OnlyDustException.notFound(format("Pull request %s/%s#%d not found", repoOwner,
                        repoName,
                        pullRequestNumber)));
    }

    @Override
    public Set<Long> removeUsedRepos(Collection<Long> repoIds) {
        final var usedRepos = projectRepoRepository.findAllByRepoId(repoIds).stream()
                .map(ProjectRepoEntity::getRepoId)
                .collect(Collectors.toUnmodifiableSet());

        return repoIds.stream()
                .filter(repoId -> !usedRepos.contains(repoId))
                .collect(Collectors.toSet());
    }

    @Override
    public boolean hasUserAccessToProject(UUID projectId, UUID userId) {
        return customProjectRepository.isProjectPublic(projectId) ||
               (userId != null && customProjectRepository.hasUserAccessToProject(projectId, userId));
    }

    @Override
    public boolean hasUserAccessToProject(String projectSlug, UUID userId) {
        return customProjectRepository.isProjectPublic(projectSlug) ||
               (userId != null && customProjectRepository.hasUserAccessToProject(projectSlug, userId));
    }

    private ProjectDetailsView getProjectDetails(ProjectViewEntity projectView) {
        final var topContributors = customContributorRepository.findProjectTopContributors(projectView.getId(),
                TOP_CONTRIBUTOR_COUNT);
        final var contributorCount = customContributorRepository.getProjectContributorCount(projectView.getId(), null);
        final var leaders = projectLeadViewRepository.findProjectLeadersAndInvitedLeaders(projectView.getId());
        final var sponsors = customProjectRepository.getProjectSponsors(projectView.getId());
        // TODO : migrate to multi-token
        final Boolean hasRemainingBudget = customProjectRepository.hasRemainingBudget(projectView.getId());
        return ProjectMapper.mapToProjectDetailsView(projectView, topContributors, contributorCount, leaders
                , sponsors, hasRemainingBudget);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProjectCardView> findByTechnologiesSponsorsUserIdSearchSortBy(List<String> technologies,
                                                                              List<UUID> sponsorIds, UUID userId,
                                                                              String search,
                                                                              ProjectCardView.SortBy sort,
                                                                              Boolean mine, Integer pageIndex,
                                                                              Integer pageSize) {
        final String sponsorsJsonPath = ProjectPageItemViewEntity.getSponsorsJsonPath(sponsorIds);
        final String technologiesJsonPath = ProjectPageItemViewEntity.getTechnologiesJsonPath(technologies);
        final Long count = projectsPageRepository.countProjectsForUserId(userId, mine, technologiesJsonPath,
                sponsorsJsonPath, search);
        final List<ProjectPageItemViewEntity> projectsForUserId =
                projectsPageRepository.findProjectsForUserId(userId, mine,
                        technologiesJsonPath, sponsorsJsonPath, search, isNull(sort) ?
                                ProjectCardView.SortBy.NAME.name() : sort.name(),
                        PaginationMapper.getPostgresOffsetFromPagination(pageSize, pageIndex), pageSize);
        final Map<String, Set<Object>> filters = ProjectPageItemFiltersViewEntity.entitiesToFilters(
                projectsPageFiltersRepository.findFiltersForUser(userId, mine, technologiesJsonPath, sponsorsJsonPath,
                        search));
        return Page.<ProjectCardView>builder()
                .content(projectsForUserId.stream().map(p -> p.toView(userId)).toList())
                .totalItemNumber(count.intValue())
                .totalPageNumber(PaginationHelper.calculateTotalNumberOfPage(pageSize, count.intValue()))
                .filters(filters)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProjectCardView> findByTechnologiesSponsorsSearchSortBy(List<String> technologies,
                                                                        List<UUID> sponsorIds, String search,
                                                                        ProjectCardView.SortBy sort,
                                                                        Integer pageIndex, Integer pageSize) {

        final String sponsorsJsonPath = ProjectPageItemViewEntity.getSponsorsJsonPath(sponsorIds);
        final String technologiesJsonPath = ProjectPageItemViewEntity.getTechnologiesJsonPath(technologies);
        final List<ProjectPageItemViewEntity> projectsForAnonymousUser =
                projectsPageRepository.findProjectsForAnonymousUser(technologiesJsonPath, sponsorsJsonPath, search,
                        isNull(sort) ?
                                ProjectCardView.SortBy.NAME.name() : sort.name(),
                        PaginationMapper.getPostgresOffsetFromPagination(pageSize, pageIndex), pageSize);
        final Long count = projectsPageRepository.countProjectsForAnonymousUser(technologiesJsonPath,
                sponsorsJsonPath, search);
        final Map<String, Set<Object>> filters = ProjectPageItemFiltersViewEntity.entitiesToFilters(
                projectsPageFiltersRepository.findFiltersForAnonymousUser(technologiesJsonPath, sponsorsJsonPath,
                        search));
        return Page.<ProjectCardView>builder()
                .content(projectsForAnonymousUser.stream().map(p -> p.toView(null)).toList())
                .totalItemNumber(count.intValue())
                .totalPageNumber(PaginationHelper.calculateTotalNumberOfPage(pageSize, count.intValue()))
                .filters(filters)
                .build();
    }

    @Override
    @Transactional
    public String createProject(UUID projectId, String name, String shortDescription, String longDescription,
                                Boolean isLookingForContributors, List<MoreInfoLink> moreInfos,
                                List<Long> githubRepoIds, UUID firstProjectLeaderId,
                                List<Long> githubUserIdsAsProjectLeads,
                                ProjectVisibility visibility, String imageUrl, ProjectRewardSettings rewardSettings) {
        final ProjectEntity projectEntity =
                ProjectEntity.builder()
                        .id(projectId)
                        .name(name)
                        .shortDescription(shortDescription)
                        .longDescription(longDescription)
                        .hiring(isLookingForContributors)
                        .logoUrl(imageUrl)
                        .visibility(ProjectMapper.projectVisibilityToEntity(visibility))
                        .ignorePullRequests(rewardSettings.getIgnorePullRequests())
                        .ignoreIssues(rewardSettings.getIgnoreIssues())
                        .ignoreCodeReviews(rewardSettings.getIgnoreCodeReviews())
                        .ignoreContributionsBefore(rewardSettings.getIgnoreContributionsBefore())
                        .repos(githubRepoIds == null ? null : githubRepoIds.stream()
                                .map(repoId -> new ProjectRepoEntity(projectId, repoId))
                                .collect(Collectors.toSet()))
                        .moreInfos(moreInfos == null ? null : moreInfosToEntities(moreInfos, projectId))
                        .projectLeaders(Set.of(new ProjectLeadEntity(projectId, firstProjectLeaderId)))
                        .projectLeaderInvitations(githubUserIdsAsProjectLeads == null ? null :
                                githubUserIdsAsProjectLeads.stream()
                                        .map(githubUserId -> new ProjectLeaderInvitationEntity(UUID.randomUUID(),
                                                projectId, githubUserId))
                                        .collect(Collectors.toSet()))
                        .rank(0)
                        .build();

        this.projectIdRepository.save(new ProjectIdEntity(projectId));
        this.projectRepository.save(projectEntity);

        return projectRepository.getKeyById(projectId);
    }

    @Override
    @Transactional
    public void updateProject(UUID projectId, String name, String shortDescription,
                              String longDescription,
                              Boolean isLookingForContributors, List<MoreInfoLink> moreInfos,
                              List<Long> githubRepoIds, List<Long> githubUserIdsAsProjectLeadersToInvite,
                              List<UUID> projectLeadersToKeep, String imageUrl,
                              ProjectRewardSettings rewardSettings) {
        final var project = this.projectRepository.findById(projectId)
                .orElseThrow(() -> OnlyDustException.notFound(format("Project %s not found", projectId)));
        project.setName(name);
        project.setShortDescription(shortDescription);
        project.setLongDescription(longDescription);
        project.setHiring(isLookingForContributors);
        project.setLogoUrl(imageUrl);

        if (!isNull(rewardSettings)) {
            project.setIgnorePullRequests(rewardSettings.getIgnorePullRequests());
            project.setIgnoreIssues(rewardSettings.getIgnoreIssues());
            project.setIgnoreCodeReviews(rewardSettings.getIgnoreCodeReviews());
            project.setIgnoreContributionsBefore(rewardSettings.getIgnoreContributionsBefore());
        }

        if (nonNull(moreInfos)) {
            if (nonNull(project.getMoreInfos())) {
                project.getMoreInfos().clear();
                project.getMoreInfos().addAll(moreInfosToEntities(moreInfos, projectId));
            } else {
                project.setMoreInfos(moreInfosToEntities(moreInfos, projectId));
            }
        }

        final var projectLeaderInvitations = project.getProjectLeaderInvitations();
        if (!isNull(githubUserIdsAsProjectLeadersToInvite)) {
            if (nonNull(projectLeaderInvitations)) {
                projectLeaderInvitations.clear();
                projectLeaderInvitations.addAll(githubUserIdsAsProjectLeadersToInvite.stream()
                        .map(githubUserId -> new ProjectLeaderInvitationEntity(UUID.randomUUID(), projectId,
                                githubUserId))
                        .collect(Collectors.toSet()));
            } else {
                project.setProjectLeaderInvitations(githubUserIdsAsProjectLeadersToInvite.stream()
                        .map(githubUserId -> new ProjectLeaderInvitationEntity(UUID.randomUUID(), projectId,
                                githubUserId))
                        .collect(Collectors.toSet()));
            }
        }

        final var projectLeaders = project.getProjectLeaders();
        if (!isNull(projectLeadersToKeep)) {
            projectLeaders.clear();
            projectLeaders.addAll(projectLeadersToKeep.stream()
                    .map(userId -> new ProjectLeadEntity(projectId, userId))
                    .collect(Collectors.toUnmodifiableSet()));
        }

        if (!isNull(githubRepoIds)) {
            if (nonNull(project.getRepos())) {
                project.getRepos().clear();
                project.getRepos().addAll(githubRepoIds.stream()
                        .map(repoId -> new ProjectRepoEntity(projectId, repoId))
                        .collect(Collectors.toSet()));
            } else {
                project.setRepos(githubRepoIds.stream()
                        .map(repoId -> new ProjectRepoEntity(projectId, repoId))
                        .collect(Collectors.toSet()));
            }
        }

        this.projectRepository.save(project);
    }


    @Override
    @Transactional(readOnly = true)
    public Page<ProjectContributorsLinkView> findContributors(UUID projectId, String login,
                                                              ProjectContributorsLinkView.SortBy sortBy,
                                                              SortDirection sortDirection,
                                                              int pageIndex, int pageSize) {
        final Integer count = customContributorRepository.getProjectContributorCount(projectId, login);
        final List<ProjectContributorsLinkView> projectContributorsLinkViews =
                customContributorRepository.getProjectContributorViewEntity(projectId, login, sortBy, sortDirection,
                                pageIndex, pageSize)
                        .stream().map(ProjectContributorsMapper::mapToDomainWithoutProjectLeadData)
                        .toList();
        return Page.<ProjectContributorsLinkView>builder()
                .content(projectContributorsLinkViews)
                .totalItemNumber(count)
                .totalPageNumber(PaginationHelper.calculateTotalNumberOfPage(pageSize, count))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProjectContributorsLinkView> findContributorsForProjectLead(UUID projectId, String login,
                                                                            ProjectContributorsLinkView.SortBy sortBy,
                                                                            SortDirection sortDirection,
                                                                            int pageIndex, int pageSize) {
        final Integer count = customContributorRepository.getProjectContributorCount(projectId, login);
        final List<ProjectContributorsLinkView> projectContributorsLinkViews =
                customContributorRepository.getProjectContributorViewEntity(projectId, login, sortBy, sortDirection,
                                pageIndex, pageSize)
                        .stream().map(ProjectContributorsMapper::mapToDomainWithProjectLeadData)
                        .toList();
        return Page.<ProjectContributorsLinkView>builder()
                .content(projectContributorsLinkViews)
                .totalItemNumber(count)
                .totalPageNumber(PaginationHelper.calculateTotalNumberOfPage(pageSize, count))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> getProjectLeadIds(UUID projectId) {
        return projectLeadViewRepository.findProjectLeaders(projectId)
                .stream()
                .map(ProjectLeadViewEntity::getId)
                .toList();
    }

    @Override
    public Set<Long> getProjectInvitedLeadIds(UUID projectId) {
        return projectLeaderInvitationRepository.findAllByProjectId(projectId)
                .stream()
                .map(ProjectLeaderInvitationEntity::getGithubUserId)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectRewardsPageView findRewards(UUID projectId, ProjectRewardView.Filters filters,
                                              ProjectRewardView.SortBy sortBy, SortDirection sortDirection,
                                              int pageIndex, int pageSize) {
        final var currency = nonNull(filters.getCurrency()) ? CurrencyEnumEntity.of(filters.getCurrency()) : null;
        final var format = new SimpleDateFormat("yyyy-MM-dd");
        final var fromDate = isNull(filters.getFrom()) ? null : format.format(filters.getFrom());
        final var toDate = isNull(filters.getTo()) ? null : format.format(filters.getTo());

        final Integer count = customProjectRewardRepository.getCount(projectId, currency, filters.getContributors(), fromDate, toDate);
        final List<ProjectRewardView> projectRewardViews = customProjectRewardRepository.getViewEntities(projectId, currency, filters.getContributors(),
                        fromDate, toDate,
                        sortBy, sortDirection, pageIndex, pageSize)
                .stream().map(ProjectRewardMapper::mapEntityToDomain)
                .toList();

        final var budgetStats = budgetStatsRepository.findByProject(projectId, nonNull(currency) ? currency.toString() : null, filters.getContributors(),
                fromDate, toDate);

        return ProjectRewardsPageView.builder().
                rewards(Page.<ProjectRewardView>builder()
                        .content(projectRewardViews)
                        .totalItemNumber(count)
                        .totalPageNumber(PaginationHelper.calculateTotalNumberOfPage(pageSize, count))
                        .build())
                .remainingBudget(new ProjectRewardsPageView.Money(budgetStats.getRemainingAmount(), filters.getCurrency(), budgetStats.getRemainingUsdAmount()))
                .spentAmount(new ProjectRewardsPageView.Money(budgetStats.getSpentAmount(), filters.getCurrency(), budgetStats.getSpentUsdAmount()))
                .sentRewardsCount(budgetStats.getRewardsCount())
                .rewardedContributionsCount(budgetStats.getRewardItemsCount())
                .rewardedContributorsCount(budgetStats.getRewardRecipientsCount())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectBudgetsView findBudgets(UUID projectId) {
        return ProjectBudgetsView.builder().budgets(customProjectBudgetRepository.findProjectBudgetByProjectId(projectId)
                        .stream().map(BudgetMapper::entityToDomain)
                        .toList())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public RewardView getProjectReward(UUID rewardId) {
        return RewardMapper.rewardToDomain(customRewardRepository.findProjectRewardViewEntityByd(rewardId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RewardItemView> getProjectRewardItems(UUID rewardId, int pageIndex, int pageSize) {
        final Integer count = customRewardRepository.countRewardItemsForRewardId(rewardId);
        final List<RewardItemView> rewardItemViews =
                customRewardRepository.findRewardItemsByRewardId(rewardId, pageIndex, pageSize)
                        .stream()
                        .map(RewardMapper::itemToDomain)
                        .toList();
        return Page.<RewardItemView>builder()
                .content(rewardItemViews)
                .totalItemNumber(count)
                .totalPageNumber(PaginationHelper.calculateTotalNumberOfPage(pageSize, count))
                .build();
    }

    @Override
    public Page<RewardableItemView> getProjectRewardableItemsByTypeForProjectLeadAndContributorId(UUID projectId,
                                                                                                  ContributionType contributionType,
                                                                                                  ContributionStatus contributionStatus,
                                                                                                  Long githubUserid,
                                                                                                  int pageIndex,
                                                                                                  int pageSize,
                                                                                                  String search,
                                                                                                  Boolean includeIgnoredItems) {

        final List<RewardableItemView> rewardableItemViews =
                rewardableItemRepository.findByProjectIdAndGithubUserId(projectId, githubUserid,
                                ContributionViewEntity.Type.fromViewToString(contributionType),
                                ContributionViewEntity.Status.fromViewToString(contributionStatus),
                                search,
                                PaginationMapper.getPostgresOffsetFromPagination(pageSize, pageIndex),
                                pageSize, includeIgnoredItems)
                        .stream()
                        .map(RewardableItemMapper::itemToDomain)
                        .toList();
        final Long count = rewardableItemRepository.countByProjectIdAndGithubUserId(projectId, githubUserid,
                ContributionViewEntity.Type.fromViewToString(contributionType),
                ContributionViewEntity.Status.fromViewToString(contributionStatus),
                search, includeIgnoredItems);
        return Page.<RewardableItemView>builder()
                .content(rewardableItemViews)
                .totalItemNumber(count.intValue())
                .totalPageNumber(PaginationHelper.calculateTotalNumberOfPage(pageSize, count.intValue()))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> getProjectRepoIds(UUID projectId) {
        final var project = projectRepository.getById(projectId);
        return project.getRepos() == null ? Set.of() : project.getRepos().stream()
                .map(ProjectRepoEntity::getRepoId)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional
    public void updateProjectsRanking() {
        customProjectRankingRepository.updateProjectsRanking();
    }
}
