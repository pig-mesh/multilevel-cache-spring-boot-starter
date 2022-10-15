package com.pig4cloud.plugin.cache.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.HashSet;
import java.util.Set;

/**
 * @author fuwei.deng
 * @version 1.0.0
 * <p>
 * 全局配置
 */
@Data
@ConfigurationProperties(prefix = "spring.cache.multi")
public class CacheConfigProperties {

	private Set<String> cacheNames = new HashSet<>();

	/**
	 * 是否存储空值，默认true，防止缓存穿透
	 */
	private boolean cacheNullValues = true;

	/**
	 * 是否动态根据cacheName创建Cache的实现，默认true
	 */
	private boolean dynamic = true;

	/**
	 * 缓存key的前缀
	 */
	private String cachePrefix;

	/**
	 * 当前节点id。来自当前节点的缓存更新通知不会被处理
	 */
	private Object serverId;

	@NestedConfigurationProperty
	private RedisConfigProp redis = new RedisConfigProp();

	@NestedConfigurationProperty
	private CaffeineConfigProp caffeine = new CaffeineConfigProp();

}
