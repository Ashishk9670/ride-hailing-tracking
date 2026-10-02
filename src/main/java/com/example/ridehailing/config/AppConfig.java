package com.example.ridehailing.config;

import com.example.ridehailing.service.DispatchEventSubscriber;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
@EnableScheduling
public class AppConfig {

    @Bean
    NewTopic driverLocationsTopic(AppProperties props) {
        return TopicBuilder.name(props.topics().driverLocations())
                .partitions(props.topics().partitions()).replicas(1).build();
    }

    @Bean
    RedisMessageListenerContainer redisListenerContainer(RedisConnectionFactory cf,
                                                         DispatchEventSubscriber subscriber,
                                                         AppProperties props) {
        RedisMessageListenerContainer c = new RedisMessageListenerContainer();
        c.setConnectionFactory(cf);
        c.addMessageListener(subscriber, new ChannelTopic(props.redis().dispatchChannel()));
        return c;
    }
}
