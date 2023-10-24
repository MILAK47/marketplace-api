package onlydust.com.marketplace.api.od.rust.api.client.adapter;

import lombok.AllArgsConstructor;
import onlydust.com.marketplace.api.domain.model.RequestRewardCommand;
import onlydust.com.marketplace.api.domain.port.output.RewardStoragePort;
import onlydust.com.marketplace.api.od.rust.api.client.adapter.dto.RequestRewardDTO;
import onlydust.com.marketplace.api.od.rust.api.client.adapter.dto.RequestRewardResponseDTO;
import onlydust.com.marketplace.api.od.rust.api.client.adapter.mapper.RewardMapper;
import onlydust.com.marketplace.api.rest.api.adapter.authentication.hasura.HasuraAuthentication;

import java.util.Optional;

@AllArgsConstructor
public class OdRustApiClientAdapter implements RewardStoragePort<HasuraAuthentication> {

    private final OdRustApiHttpClient httpClient;

    @Override
    public void requestPayment(final HasuraAuthentication authentication, RequestRewardCommand requestRewardCommand) {
        final RequestRewardDTO requestRewardDTO = RewardMapper.mapCreateRewardCommandToDTO(requestRewardCommand);
        httpClient.sendRequest(requestRewardDTO, RequestRewardResponseDTO.class, authentication.getJwt(),
                Optional.ofNullable(authentication.getImpersonationHeader()));
    }
}
