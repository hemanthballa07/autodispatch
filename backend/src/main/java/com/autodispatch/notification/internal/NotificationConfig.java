package com.autodispatch.notification.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableConfigurationProperties(WhatsAppProperties.class)
class NotificationConfig {

    /**
     * Webhook handlers must answer fast: command execution is handed off to
     * this executor after parse + idempotency claim. Tests set
     * notification.webhook.async=false to run on the caller thread.
     */
    @Bean(name = "whatsappWebhookExecutor")
    Executor whatsappWebhookExecutor(@Value("${notification.webhook.async:true}") boolean async) {
        if (!async) {
            return Runnable::run;
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("wa-webhook-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnProperty(name = "whatsapp.mode", havingValue = "LIVE")
    WhatsAppCloudApiGateway whatsAppCloudApiGateway(WhatsAppProperties properties) {
        return new WhatsAppCloudApiGateway(properties);
    }
}
