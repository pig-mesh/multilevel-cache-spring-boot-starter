package com.pig4cloud.plugin.cache;

import com.pig4cloud.plugin.cache.properties.CacheConfigProperties;
import com.pig4cloud.plugin.cache.support.CacheMessageListener;
import com.pig4cloud.plugin.cache.support.RedisCaffeineCacheManager;
import com.pig4cloud.plugin.cache.support.RedisCaffeineCacheManagerCustomizer;
import com.pig4cloud.plugin.cache.support.ServerIdGenerator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.Objects;

/**
 * @author fuwei.deng
 * @version 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(RedisAutoConfiguration.class)
@EnableConfigurationProperties(CacheConfigProperties.class)
public class MultilevelCacheAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(RedisTemplate.class)
	public RedisCaffeineCacheManager cacheManager(CacheConfigProperties cacheConfigProperties,
			@Qualifier("stringKeyRedisTemplate") RedisTemplate<Object, Object> stringKeyRedisTemplate,
			ObjectProvider<RedisCaffeineCacheManagerCustomizer> cacheManagerCustomizers,
			ObjectProvider<ServerIdGenerator> serverIdGenerators) {
		Object serverId = cacheConfigProperties.getServerId();
		if (serverId == null || "".equals(serverId)) {
			serverIdGenerators
					.ifAvailable(serverIdGenerator -> cacheConfigProperties.setServerId(serverIdGenerator.get()));
		}
		RedisCaffeineCacheManager cacheManager = new RedisCaffeineCacheManager(cacheConfigProperties,
				stringKeyRedisTemplate);
		cacheManagerCustomizers.orderedStream().forEach(customizer -> customizer.customize(cacheManager));
		return cacheManager;
	}

	/**
	 * 可自定义名称为stringKeyRedisTemplate的RedisTemplate覆盖掉默认RedisTemplate。
	 */
	@Bean
	@ConditionalOnMissingBean(name = "stringKeyRedisTemplate")
	public RedisTemplate<Object, Object> stringKeyRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
		RedisTemplate<Object, Object> template = new RedisTemplate<>();
		template.setConnectionFactory(redisConnectionFactory);
		template.setKeySerializer(new StringRedisSerializer());
		template.setHashKeySerializer(new StringRedisSerializer());
		return template;
	}

	@Bean
	@ConditionalOnMissingBean(name = "cacheMessageListenerContainer")
	public RedisMessageListenerContainer cacheMessageListenerContainer(CacheConfigProperties cacheConfigProperties,
			@Qualifier("stringKeyRedisTemplate") RedisTemplate<Object, Object> stringKeyRedisTemplate,
			@Qualifier("cacheMessageListener") CacheMessageListener cacheMessageListener) {
		RedisMessageListenerContainer redisMessageListenerContainer = new RedisMessageListenerContainer();
		redisMessageListenerContainer
				.setConnectionFactory(Objects.requireNonNull(stringKeyRedisTemplate.getConnectionFactory()));
		redisMessageListenerContainer.addMessageListener(cacheMessageListener,
				new ChannelTopic(cacheConfigProperties.getRedis().getTopic()));
		return redisMessageListenerContainer;
	}

	@Bean
	@SuppressWarnings("unchecked")
	@ConditionalOnMissingBean(name = "cacheMessageListener")
	public CacheMessageListener cacheMessageListener(
			@Qualifier("stringKeyRedisTemplate") RedisTemplate<Object, Object> stringKeyRedisTemplate,
			RedisCaffeineCacheManager redisCaffeineCacheManager) {
		return new CacheMessageListener((RedisSerializer<Object>) stringKeyRedisTemplate.getValueSerializer(),
				redisCaffeineCacheManager);
	}

}
