package onlydust.com.marketplace.api.domain.model.notification;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ProjectLeaderAssigned extends Notification {
    UUID projectId;
    UUID leaderId;
    Date assignedAt;
}
