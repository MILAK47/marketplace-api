package onlydust.com.marketplace.api.postgres.adapter.repository;

import onlydust.com.marketplace.api.postgres.adapter.entity.read.RewardStatsEntity;
import onlydust.com.marketplace.api.postgres.adapter.entity.write.old.type.CurrencyEnumEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.awt.print.Pageable;
import java.util.List;
import java.util.UUID;

public interface RewardStatsRepository extends JpaRepository<RewardStatsEntity, CurrencyEnumEntity> {
    @Query(value = """
            SELECT 
                pr.currency,
                COALESCE(SUM(pr.amount) FILTER ( WHERE COALESCE(p.total_paid, 0) >= pr.amount ), 0) AS processed_amount,
                COALESCE(SUM(pr.amount) FILTER ( WHERE COALESCE(p.total_paid, 0) >= pr.amount ), 0)  * CASE WHEN pr.currency = 'usd' THEN 1 ELSE cuq.price END AS processed_usd_amount,
                COALESCE(SUM(pr.amount) FILTER ( WHERE COALESCE(p.total_paid, 0) < pr.amount ), 0) AS pending_amount,
                COALESCE(SUM(pr.amount) FILTER ( WHERE COALESCE(p.total_paid, 0) < pr.amount ), 0) * CASE WHEN pr.currency = 'usd' THEN 1 ELSE cuq.price END AS pending_usd_amount,
                JSONB_AGG(pr.id) AS reward_ids,
                COALESCE(JSONB_AGG(wi.id) FILTER (WHERE wi.id IS NOT NULL), '[]') AS reward_item_ids,
                JSONB_AGG(pr.project_id) AS project_ids
            FROM payment_requests pr
            JOIN iam.users u on u.github_user_id = pr.recipient_id
            LEFT JOIN work_items wi ON pr.id = wi.payment_id
            LEFT JOIN ( 
                SELECT p.request_id, SUM(amount) as total_paid 
                FROM payments p 
                GROUP BY p.request_id 
            ) p ON p.request_id = pr.id
            LEFT JOIN crypto_usd_quotes cuq ON cuq.currency = pr.currency
            WHERE 
                u.id = :userId AND
                (COALESCE(:currencies) IS NULL OR CAST(pr.currency AS TEXT) IN (:currencies)) AND
                (COALESCE(:projectIds) IS NULL OR pr.project_id IN (:projectIds)) AND
                (:fromDate IS NULL OR pr.requested_at >= to_date(cast(:fromDate AS TEXT), 'YYYY-MM-DD')) AND
                (:toDate IS NULL OR pr.requested_at < to_date(cast(:toDate AS TEXT), 'YYYY-MM-DD') + 1)
            GROUP BY 
                pr.currency, cuq.price
            """, nativeQuery = true)
    List<RewardStatsEntity> findByUser(UUID userId, List<String> currencies, List<UUID> projectIds, String fromDate,
                                       String toDate);


    @Query(value = """
        SELECT DISTINCT pr.currency
        FROM payment_requests pr
        WHERE pr.recipient_id = :githubUserId
        ORDER BY pr.currency
        """, nativeQuery = true)
    List<CurrencyEnumEntity> listRewardCurrenciesByRecipient(Long githubUserId);
}
