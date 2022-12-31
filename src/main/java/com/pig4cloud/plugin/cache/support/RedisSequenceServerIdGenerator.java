package com.pig4cloud.plugin.cache.support;

import com.pig4cloud.plugin.cache.properties.CacheConfigProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 通过redis key生成serverId
 *
 * @author FlyInWind
 */
@RequiredArgsConstructor
public class RedisSequenceServerIdGenerator implements ServerIdGenerator {

	protected final RedisTemplate<Object, Object> stringKeyRedisTemplate;

	protected final CacheConfigProperties properties;

	@Override
	public Object get() {
		return stringKeyRedisTemplate.opsForValue().increment(properties.getRedis().getServerIdGeneratorKey());
	}

}
