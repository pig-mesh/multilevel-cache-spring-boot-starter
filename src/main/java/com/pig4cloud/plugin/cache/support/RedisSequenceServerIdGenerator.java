package com.pig4cloud.plugin.cache.support;

import com.pig4cloud.plugin.cache.properties.CacheConfigProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 通过redis key生成serverId
 *
 * @author FlyInWind
 */
@Lazy
@Configuration(proxyBeanMethods = false)
@ConditionalOnMissingBean(ServerIdGenerator.class)
public class RedisSequenceServerIdGenerator implements ServerIdGenerator {

	protected final RedisTemplate<Object, Object> stringKeyRedisTemplate;

	protected final CacheConfigProperties properties;

	public RedisSequenceServerIdGenerator(
			@Qualifier("stringKeyRedisTemplate") RedisTemplate<Object, Object> stringKeyRedisTemplate,
			CacheConfigProperties properties) {
		this.stringKeyRedisTemplate = stringKeyRedisTemplate;
		this.properties = properties;
	}

	@Override
	public Object get() {
		return stringKeyRedisTemplate.opsForValue().increment(properties.getRedis().getServerIdGeneratorKey());
	}

}
