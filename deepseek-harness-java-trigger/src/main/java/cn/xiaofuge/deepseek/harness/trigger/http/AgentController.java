package cn.xiaofuge.deepseek.harness.trigger.http;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.xiaofuge.deepseek.harness.api.IAgentApi;
import cn.xiaofuge.deepseek.harness.api.dto.AgentMessageRequestDTO;
import cn.xiaofuge.deepseek.harness.api.dto.AgentMessageResponseDTO;
import cn.xiaofuge.deepseek.harness.api.response.Response;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    @Autowired
    private IAgentApi agentApi;

    @PostMapping("/message")
    public Response<AgentMessageResponseDTO> sendMessage(@RequestBody AgentMessageRequestDTO request) {
        return agentApi.sendMessage(request);
    }
}
