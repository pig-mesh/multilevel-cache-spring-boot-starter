package com.pig4cloud.plugin.cache.support;

import com.github.benmanes.caffeine.cache.Cache;

public interface CacheManager extends org.springframework.cache.CacheManager {

	/**
	 * 实现 caffeine 的 {@link Cache} 接口的缓存
	 * @param name 缓存名
	 * @return {@link Cache} 的实现
	 */
	<K, V> Cache<K, V> getCaffeineCache(String name);

}
