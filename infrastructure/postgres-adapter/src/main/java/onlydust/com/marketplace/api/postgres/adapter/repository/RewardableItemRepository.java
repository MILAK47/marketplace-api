package onlydust.com.marketplace.api.postgres.adapter.repository;

import onlydust.com.marketplace.api.postgres.adapter.entity.read.RewardableItemViewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RewardableItemRepository extends JpaRepository<RewardableItemViewEntity, String> {

    @Query(value = """
            select c.id as contribution_id,
                   c.type,
                   coalesce(cast(c.pull_request_id as text), cast(c.issue_id as text), c.code_review_id) id,
                   c.status                                             status,
                   c.github_number                                      number,
                   c.github_html_url                                    html_url,
                   c.github_title                                       title,
                   c.repo_name,
                   c.repo_id,
                   c.created_at                                         start_date,
                   c.completed_at                                       end_date,
                   pull_request.commit_count                            commits_count,
                   (select gprcc.commit_count
                    from indexer_exp.github_pull_request_commit_counts gprcc
                    where gprcc.pull_request_id = pull_request.id and gprcc.author_id = :githubUserId)      user_commits_count,
                   c.github_comments_count                                                                  comments_count,
                   ic.contribution_id is not null                                                           ignored
            from public.project_github_repos pgr
                     JOIN indexer_exp.github_repos gr on gr.id = pgr.github_repo_id
                     join indexer_exp.contributions c on c.repo_id = gr.id
                     left join indexer_exp.github_pull_requests pull_request on pull_request.id = c.pull_request_id
                     left join ignored_contributions ic on ic.contribution_id = c.id and ic.project_id = :projectId
            where pgr.project_id = :projectId
              and gr.visibility = 'PUBLIC'
              and c.contributor_id = :githubUserId
              and (:includeIgnoredItems is true or ic.contribution_id is null)
              and (coalesce(:contributionStatus) is null or c.status = cast(cast(:contributionStatus as text) as indexer_exp.contribution_status))
              and (coalesce(:contributionType) is null or c.type = cast(cast(:contributionType as text) as indexer_exp.contribution_type))
               and (coalesce(:search) is null
                    or c.github_title ilike '%' || cast(:search as text) || '%'
                    or cast(c.github_number as text) ilike '%' || cast(:search as text) || '%')
             order by c.created_at desc
              offset :offset limit :limit
              """, nativeQuery = true)
    List<RewardableItemViewEntity> findByProjectIdAndGithubUserId(final @Param("projectId") UUID projectId,
                                                                  final @Param("githubUserId") Long githubUserId,
                                                                  final @Param("contributionType") String contributionType,
                                                                  final @Param("contributionStatus") String contributionStatus,
                                                                  final @Param("search") String search,
                                                                  final @Param("offset") int offset,
                                                                  final @Param("limit") int limit,
                                                                  final @Param("includeIgnoredItems") boolean includeIgnoredItems);


    @Query(value = """
                        with get_pr as (select gpr.number,
                                   gpr.id,
                                   gpr.title,
                                   gpr.created_at                         start_date
                            from indexer_exp.github_pull_requests gpr),
                 get_issue as (select gi.number,
                                      gi.id,
                                      gi.title,
                                      gi.created_at start_date
                               from indexer_exp.github_issues gi),
                 get_code_review as (select gcr.number,
                                            gcr.id,
                                            gcr.title,
                                            gcr.requested_at   start_date
                                     from indexer_exp.github_code_reviews gcr)
            select count(c.id)
            from public.project_github_repos pgr
                     join indexer_exp.github_repos repo on repo.id = pgr.github_repo_id
                     join indexer_exp.contributions c on c.repo_id = repo.id
                     left join get_code_review code_review on code_review.id = c.code_review_id
                     left join get_issue issue on issue.id = c.issue_id
                     left join get_pr pull_request on pull_request.id = c.pull_request_id
                     left join ignored_contributions ic on ic.contribution_id = c.id and ic.project_id = :projectId
            where pgr.project_id = :projectId
              and repo.visibility = 'PUBLIC'
              and c.contributor_id = :githubUserId
              and (:includeIgnoredItems is true or ic.contribution_id is null)
              and (coalesce(:contributionStatus) is null or c.status = cast(cast(:contributionStatus as text) as indexer_exp.contribution_status))
              and (coalesce(:contributionType) is null or c.type = cast(cast(:contributionType as text) as indexer_exp.contribution_type))
               and (coalesce(:search) is null
                    or coalesce(pull_request.title, issue.title, code_review.title) ilike '%' || cast(:search as text) || '%'
                    or cast(coalesce(pull_request.number, issue.number, code_review.number) as text) ilike '%' || cast(:search as text) || '%')
              """, nativeQuery = true)
    Long countByProjectIdAndGithubUserId(final @Param("projectId") UUID projectId,
                                         final @Param("githubUserId") Long githubUserId,
                                         final @Param("contributionType") String contributionType,
                                         final @Param("contributionStatus") String contributionStatus,
                                         final @Param("search") String search,
                                         final @Param("includeIgnoredItems") boolean includeIgnoredItems);

    @Query(value = """
            with get_issue as (select gi.number,
                                      gi.id,
                                      gi.status,
                                      gi.html_url,
                                      gi.title,
                                      gi.created_at start_date,
                                      gi.closed_at  end_date,
                                      gi.comments_count,
                                      gi.repo_name,
                                      gi.repo_id,
                                      gi.repo_owner_login  repo_owner
                               from indexer_exp.github_issues gi)
            select issue.id,
                   NULL as contribution_id,
                   'ISSUE' as type,
                   cast(issue.status as text) status,
                   false                      draft,
                   issue.number,
                   issue.html_url,
                   issue.title,
                   issue.repo_name,
                   issue.repo_id,
                   issue.start_date,
                   issue.end_date,
                   issue.html_url,
                   NULL as                    commits_count,
                   NULL as                    user_commits_count,
                   issue.comments_count,
                   NULL as                    cr_outcome,
                   FALSE as                   ignored
            from get_issue issue
            where issue.repo_owner = :repoOwner
                and issue.repo_name = :repoName
                and issue.number = :issueNumber
              """, nativeQuery = true)
    Optional<RewardableItemViewEntity> findRewardableIssue(String repoOwner, String repoName, long issueNumber);

    @Query(value = """
            with get_pr as (select gpr.number,
                                   gpr.id,
                                   gpr.html_url,
                                   gpr.title,
                                   gpr.status,
                                   gpr.created_at                         start_date,
                                   coalesce(gpr.closed_at, gpr.merged_at) end_date,
                                   gpr.commit_count     commits_count,
                                   gpr.repo_name,
                                   gpr.repo_id,
                                   gpr.repo_owner_login  repo_owner
                            from indexer_exp.github_pull_requests gpr)
            select pr.id,
                   NULL as contribution_id,
                   'PULL_REQUEST' as type,
                   cast(pr.status as text) status,
                   false                   draft,
                   pr.number,
                   pr.html_url,
                   pr.title,
                   pr.repo_name,
                   pr.repo_id,
                   pr.start_date,
                   pr.end_date,
                   pr.html_url,
                   pr.commits_count,
                   NULL as                  user_commits_count,
                   NULL as                  comments_count,
                   NULL as                  cr_outcome,
                   FALSE as                 ignored
            from get_pr pr
            where pr.repo_owner = :repoOwner
                and pr.repo_name = :repoName
                and pr.number = :pullRequestNumber
              """, nativeQuery = true)
    Optional<RewardableItemViewEntity> findRewardablePullRequest(String repoOwner, String repoName,
                                                                 long pullRequestNumber);
}
