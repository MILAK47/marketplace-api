package onlydust.com.marketplace.api.rest.api.adapter;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.Tags;
import lombok.AllArgsConstructor;
import onlydust.com.backoffice.api.contract.BackofficeApi;
import onlydust.com.backoffice.api.contract.model.BudgetPage;
import onlydust.com.backoffice.api.contract.model.GithubRepositoryPage;
import onlydust.com.backoffice.api.contract.model.ProjectLeadInvitationPage;
import onlydust.com.marketplace.api.domain.port.input.BackofficeFacadePort;
import onlydust.com.marketplace.api.domain.view.backoffice.ProjectBudgetView;
import onlydust.com.marketplace.api.domain.view.backoffice.ProjectLeadInvitationView;
import onlydust.com.marketplace.api.domain.view.backoffice.ProjectRepositoryView;
import onlydust.com.marketplace.api.domain.view.pagination.Page;
import onlydust.com.marketplace.api.domain.view.pagination.PaginationHelper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static onlydust.com.marketplace.api.rest.api.adapter.mapper.BackOfficeMapper.*;

@RestController
@Tags(@Tag(name = "Backoffice"))
@AllArgsConstructor
public class BackofficeRestApi implements BackofficeApi {

    private final BackofficeFacadePort backofficeFacadePort;

    @Override
    public ResponseEntity<GithubRepositoryPage> getGithubRepositoryPage(Integer pageIndex, Integer pageSize,
                                                                        List<UUID> projectIds) {
        final int sanitizedPageSize = PaginationHelper.sanitizePageSize(pageSize);
        final int sanitizedPageIndex = PaginationHelper.sanitizePageIndex(pageIndex);
        Page<ProjectRepositoryView> projectRepositoryViewPage =
                backofficeFacadePort.getProjectRepositoryPage(sanitizedPageIndex, sanitizedPageSize, projectIds);

        final GithubRepositoryPage githubRepositoryPage = mapGithubRepositoryPageToResponse(projectRepositoryViewPage
                , sanitizedPageIndex);

        return githubRepositoryPage.getTotalPageNumber() > 1 ?
                ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).body(githubRepositoryPage) :
                ResponseEntity.ok(githubRepositoryPage);
    }

    @Override
    public ResponseEntity<BudgetPage> getBudgetPage(Integer pageIndex, Integer pageSize, List<UUID> projectIds) {
        final int sanitizedPageSize = PaginationHelper.sanitizePageSize(pageSize);
        final int sanitizedPageIndex = PaginationHelper.sanitizePageIndex(pageIndex);
        Page<ProjectBudgetView> budgetViewPage =
                backofficeFacadePort.getBudgetPage(sanitizedPageIndex, sanitizedPageSize, projectIds);

        final BudgetPage budgetPage = mapBudgetPageToResponse(budgetViewPage
                , sanitizedPageIndex);

        return budgetPage.getTotalPageNumber() > 1 ?
                ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).body(budgetPage) :
                ResponseEntity.ok(budgetPage);
    }

    @Override
    public ResponseEntity<ProjectLeadInvitationPage> getProjectLeadInvitationPage(Integer pageIndex, Integer pageSize
            , List<UUID> ids) {
        final int sanitizedPageSize = PaginationHelper.sanitizePageSize(pageSize);
        final int sanitizedPageIndex = PaginationHelper.sanitizePageIndex(pageIndex);
        Page<ProjectLeadInvitationView> projectLeadInvitationViewPage =
                backofficeFacadePort.getProjectLeadInvitationPage(sanitizedPageIndex, sanitizedPageSize, ids);

        final ProjectLeadInvitationPage projectLeadInvitationPage =
                mapProjectLeadInvitationPageToContract(projectLeadInvitationViewPage
                        , sanitizedPageIndex);

        return projectLeadInvitationPage.getTotalPageNumber() > 1 ?
                ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).body(projectLeadInvitationPage) :
                ResponseEntity.ok(projectLeadInvitationPage);
    }
}
