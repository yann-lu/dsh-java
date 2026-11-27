package cn.xiaofuge.deepseek.harness.trigger.service;

import cn.xiaofuge.deepseek.harness.api.IAgentApi;
import cn.xiaofuge.deepseek.harness.api.dto.AgentMessageRequestDTO;
import cn.xiaofuge.deepseek.harness.api.dto.AgentMessageResponseDTO;
import cn.xiaofuge.deepseek.harness.api.response.Response;
import cn.xiaofuge.deepseek.harness.cases.agent.IAgentUseCase;
import org.springframework.stereotype.Service;

@Service
public class AgentApi implements IAgentApi {

    private final IAgentUseCase agentUseCase;

    /**
     * 处理Agent API。
     * 流程：转换协议 → 调用应用门面 → 返回结果。
     * @param agentUseCase Agent Use Case参数。
     * @return 方法执行结果。
     */
    public AgentApi(IAgentUseCase agentUseCase) {
        this.agentUseCase = agentUseCase;
    }
    @Override
    public Response<AgentMessageResponseDTO> sendMessage(AgentMessageRequestDTO request) {
        return execute(() -> agentUseCase.sendMessage(request));
    }

    private <T> Response<T> execute(java.util.function.Supplier<T> action) {
        try {
            return Response.success(action.get());
        } catch (IllegalArgumentException exception) {
            return Response.invalidArgument(exception.getMessage());
        } catch (RuntimeException exception) {
            return Response.internalError(exception.getMessage());
        }
    }

}
