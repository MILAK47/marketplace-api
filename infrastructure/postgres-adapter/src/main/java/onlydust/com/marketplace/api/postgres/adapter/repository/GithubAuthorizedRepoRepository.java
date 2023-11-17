package onlydust.com.marketplace.api.postgres.adapter.repository;

import onlydust.com.marketplace.api.postgres.adapter.entity.read.indexer.exposition.GithubAuthorizedRepoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GithubAuthorizedRepoRepository extends JpaRepository<GithubAuthorizedRepoEntity, GithubAuthorizedRepoEntity.Id> {
}
