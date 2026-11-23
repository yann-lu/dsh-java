package cn.xiaofuge.deepseek.harness.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 启动期环境信息打印：应用就绪后输出端口、激活 profile 与数据源地址。
 *
 * <p>设计语义（对齐原版）：app 模块只做"启动体检"，不写业务。用 {@link ApplicationRunner}
 * 而不是 @PostConstruct / @EventListener(ApplicationReadyEvent)，是因为 Runner 在容器
 * refresh 完成之后执行，此时配置与数据源都已就绪，打印出来的才是"运行时真相"；
 * 且 Runner 抛异常会阻断启动，配置错误能第一时间暴露。</p>
 */
// G1.3：教练代笔（2026-11-23），用户 teach-back 通过后才算完成——见 docs/learning-journal.md
@Component
public class StartupInfoRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupInfoRunner.class);

    private final Environment environment;

    public StartupInfoRunner(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String port = environment.getProperty("server.port", "8090");
        String[] profiles = environment.getActiveProfiles();
        String profile = profiles.length == 0 ? "default" : String.join(",", profiles);
        String dataSourceUrl = maskCredentials(environment.getProperty("spring.datasource.url", ""));
        log.info("Harness 启动完成：port={}，profile={}，dataSourceUrl={}", port, profile, dataSourceUrl);
    }

    /**
     * 脱敏 JDBC URL 中的凭据段（//user:password@host 里的 user:password），日志里只留 ***。
     */
    private String maskCredentials(String url) {
        return url.replaceAll("//[^@/]+@", "//***@");
    }
}
