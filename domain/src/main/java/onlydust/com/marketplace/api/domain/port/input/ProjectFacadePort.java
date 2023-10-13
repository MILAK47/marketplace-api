package onlydust.com.marketplace.api.domain.port.input;

import onlydust.com.marketplace.api.domain.model.CreateProjectCommand;
import onlydust.com.marketplace.api.domain.view.Page;
import onlydust.com.marketplace.api.domain.view.ProjectCardView;
import onlydust.com.marketplace.api.domain.view.ProjectDetailsView;

import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.UUID;

public interface ProjectFacadePort {
    ProjectDetailsView getById(UUID projectId);

    ProjectDetailsView getBySlug(String slug);

    Page<ProjectCardView> getByTechnologiesSponsorsUserIdSearchSortBy(List<String> technology, List<String> sponsor,
                                                                      UUID userId, String search, ProjectCardView.SortBy sort);

    UUID createProject(CreateProjectCommand createProjectCommand);

    URL saveLogoImage(InputStream imageInputStream);
}
