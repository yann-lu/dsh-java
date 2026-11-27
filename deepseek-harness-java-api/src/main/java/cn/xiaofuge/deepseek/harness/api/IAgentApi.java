package cn.xiaofuge.deepseek.harness.api;

import cn.xiaofuge.deepseek.harness.api.dto.AgentMessageRequestDTO;
import cn.xiaofuge.deepseek.harness.api.dto.AgentMessageResponseDTO;
import cn.xiaofuge.deepseek.harness.api.response.Response;

public interface IAgentApi {

    Response<AgentMessageResponseDTO> sendMessage(AgentMessageRequestDTO request);
    
}
