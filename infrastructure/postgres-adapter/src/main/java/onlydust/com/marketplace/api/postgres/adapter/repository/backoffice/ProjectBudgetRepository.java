package onlydust.com.marketplace.api.postgres.adapter.repository.backoffice;

import onlydust.com.marketplace.api.postgres.adapter.entity.backoffice.read.ProjectBudgetEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ProjectBudgetRepository extends JpaRepository<ProjectBudgetEntity, ProjectBudgetEntity.Id> {


    @Query(value = """
            SELECT b.id                                                         ,
                   b.currency,
                   b.initial_amount,
                   b.remaining_amount,
                   b.initial_amount - b.remaining_amount                        AS spent_amount,
                   COALESCE(b.initial_amount * cuq.price, b.initial_amount)     AS initial_amount_dollars_equivalent,
                   COALESCE(b.remaining_amount * cuq.price, b.remaining_amount) AS remaining_amount_dollars_equivalent,
                   COALESCE((b.initial_amount - b.remaining_amount) * cuq.price,
                            (b.initial_amount - b.remaining_amount))            AS spent_amount_dollars_equivalent,
                   pb.project_id
            FROM budgets b
                     LEFT JOIN crypto_usd_quotes cuq ON cuq.currency = b.currency
                     JOIN projects_budgets pb ON pb.budget_id = b.id
            WHERE (COALESCE(:projectIds) IS NULL OR pb.project_id in (:projectIds))
            """, nativeQuery = true)
    Page<ProjectBudgetEntity> findAllByProjectIds(final Pageable pageable, final List<UUID> projectIds);
}
