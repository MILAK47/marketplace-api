package onlydust.com.marketplace.api.domain.service;

import com.github.javafaker.Faker;
import onlydust.com.marketplace.api.domain.mocks.DeterministicDateProvider;
import onlydust.com.marketplace.api.domain.model.*;
import onlydust.com.marketplace.api.domain.port.input.UserFacadePort;
import onlydust.com.marketplace.api.domain.port.output.UserStoragePort;
import onlydust.com.marketplace.api.domain.view.UserProfileView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class UserServiceTest {

    private final Faker faker = new Faker();
    private final DeterministicDateProvider dateProvider = new DeterministicDateProvider();

    @Test
    void should_find_user_given_a_github_id() {
        // Given
        final UserStoragePort userStoragePort = mock(UserStoragePort.class);
        final UserFacadePort userService = new UserService(userStoragePort, dateProvider);
        final GithubUserIdentity githubUserIdentity = GithubUserIdentity.builder()
                .githubUserId(faker.number().randomNumber())
                .githubAvatarUrl(faker.internet().avatar())
                .githubLogin(faker.hacker().verb())
                .build();
        final User user = User.builder()
                .id(UUID.randomUUID())
                .avatarUrl(githubUserIdentity.getGithubAvatarUrl())
                .githubUserId(githubUserIdentity.getGithubUserId())
                .login(githubUserIdentity.getGithubLogin())
                .hasAcceptedLatestTermsAndConditions(true)
                .hasSeenOnboardingWizard(true)
                .build();

        // When
        when(userStoragePort.getUserByGithubId(githubUserIdentity.getGithubUserId()))
                .thenReturn(Optional.of(user));
        final User userByGithubIdentity = userService.getUserByGithubIdentity(githubUserIdentity);

        // Then
        assertEquals(user, userByGithubIdentity);
    }

    @Test
    void should_create_user_on_the_fly_when_user_with_github_id_doesnt_exist() {
        // Given
        final UserStoragePort userStoragePort = mock(UserStoragePort.class);
        final UserFacadePort userService = new UserService(userStoragePort, dateProvider);
        final GithubUserIdentity githubUserIdentity = GithubUserIdentity.builder()
                .githubUserId(faker.number().randomNumber())
                .githubAvatarUrl(faker.internet().avatar())
                .githubLogin(faker.hacker().verb())
                .build();

        // When
        when(userStoragePort.getUserByGithubId(githubUserIdentity.getGithubUserId()))
                .thenReturn(Optional.empty());
        final User userByGithubIdentity = userService.getUserByGithubIdentity(githubUserIdentity);

        // Then
        assertThat(userByGithubIdentity.getId()).isNotNull();
        assertEquals(User.builder()
                .id(userByGithubIdentity.getId())
                .avatarUrl(githubUserIdentity.getGithubAvatarUrl())
                .githubUserId(githubUserIdentity.getGithubUserId())
                .login(githubUserIdentity.getGithubLogin())
                .roles(List.of(UserRole.USER))
                .hasAcceptedLatestTermsAndConditions(false)
                .hasSeenOnboardingWizard(false)
                .build(), userByGithubIdentity);
    }


    @Test
    void should_find_user_profile_given_an_id() {
        // Given
        final UserStoragePort userStoragePort = mock(UserStoragePort.class);
        final UserFacadePort userService = new UserService(userStoragePort, dateProvider);
        final UUID userId = UUID.randomUUID();
        final UserProfileView userProfileView = UserProfileView.builder()
                .id(userId)
                .avatarUrl(faker.pokemon().name())
                .githubId(faker.number().randomNumber())
                .login(faker.hacker().verb())
                .build();

        // When
        when(userStoragePort.getProfileById(userId))
                .thenReturn(userProfileView);
        final UserProfileView profileById = userService.getProfileById(userId);

        // Then
        assertEquals(userProfileView, profileById);
    }

    @Test
    void should_markUserAsOnboarded() {
        // Given
        dateProvider.setNow(faker.date().birthday(0, 1));
        final UserStoragePort userStoragePort = mock(UserStoragePort.class);
        final UserFacadePort userService = new UserService(userStoragePort, dateProvider);
        final UUID userId = UUID.randomUUID();

        // When
        userService.markUserAsOnboarded(userId);

        // Then
        verify(userStoragePort, times(1)).updateOnboardingWizardDisplayDate(userId, dateProvider.now());
    }

    @Test
    void should_updateTermsAndConditionsAcceptanceDate() {
        // Given
        dateProvider.setNow(faker.date().birthday(0, 1));
        final UserStoragePort userStoragePort = mock(UserStoragePort.class);
        final UserFacadePort userService = new UserService(userStoragePort, dateProvider);
        final UUID userId = UUID.randomUUID();

        // When
        userService.updateTermsAndConditionsAcceptanceDate(userId);

        // Then
        verify(userStoragePort, times(1)).updateTermsAndConditionsAcceptanceDate(userId, dateProvider.now());
    }

    @Test
    void should_accept_lead_invitation() {
        // Given
        final UserStoragePort userStoragePort = mock(UserStoragePort.class);
        final UserFacadePort userService = new UserService(userStoragePort, dateProvider);
        final UUID projectId = UUID.randomUUID();
        final Long githubUserId = faker.number().randomNumber();

        // When
        userService.acceptInvitationToLeadProject(githubUserId, projectId);

        // Then
        verify(userStoragePort, times(1)).acceptProjectLeaderInvitation(githubUserId, projectId);
    }

    @Test
    void should_apply_on_project() {
        // Given
        final UserStoragePort userStoragePort = mock(UserStoragePort.class);
        final UserFacadePort userService = new UserService(userStoragePort, dateProvider);
        final UUID projectId = UUID.randomUUID();
        final UUID userId = UUID.randomUUID();

        // When
        userService.applyOnProject(userId, projectId);

        // Then
        verify(userStoragePort, times(1)).createApplicationOnProject(userId, projectId);
    }

    @Test
    void should_update_profile() {
        // Given
        final UserStoragePort userStoragePort = mock(UserStoragePort.class);
        final UserFacadePort userService = new UserService(userStoragePort, dateProvider);
        final UUID userId = UUID.randomUUID();

        final UserProfile profile = UserProfile.builder()
                .bio(faker.lorem().sentence())
                .website(faker.internet().url())
                .location(faker.address().city())
                .cover(UserProfileCover.CYAN)
                .technologies(Map.of(faker.programmingLanguage().name(), faker.number().randomDigit(), faker.programmingLanguage().name(), faker.number().randomDigit()))
                .contacts(List.of(
                        Contact.builder()
                                .contact(faker.internet().url())
                                .channel(Contact.Channel.WHATSAPP)
                                .visibility(Contact.Visibility.PUBLIC)
                                .build(),
                        Contact.builder()
                                .contact(faker.internet().emailAddress())
                                .channel(Contact.Channel.EMAIL)
                                .visibility(Contact.Visibility.PRIVATE)
                                .build()
                ))
                .build();

        final UserProfileView userProfileView = UserProfileView.builder()
                .id(userId)
                .bio(profile.getBio())
                .build();

        // When
        when(userStoragePort.getProfileById(userId))
                .thenReturn(userProfileView);
        final UserProfileView updatedUser = userService.updateProfile(userId, profile);

        // Then
        verify(userStoragePort, times(1)).saveProfile(userId, profile);
        assertThat(updatedUser.getBio()).isEqualTo(userProfileView.getBio());
    }
}
