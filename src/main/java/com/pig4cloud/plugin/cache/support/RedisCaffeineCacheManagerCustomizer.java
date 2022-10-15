package com.pig4cloud.plugin.cache.support;

/**
 * 修改 {@link RedisCaffeineCacheManager} 的回调
 *
 * @author FlyInWind
 */
@FunctionalInterface
public interface RedisCaffeineCacheManagerCustomizer {

	/**
	 * 修改 {@link RedisCaffeineCacheManager}
	 * @param cacheManager cacheManager
	 */
	void customize(RedisCaffeineCacheManager cacheManager);

}
