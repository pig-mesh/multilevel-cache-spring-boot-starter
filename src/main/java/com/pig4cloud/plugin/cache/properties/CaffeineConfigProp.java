package com.pig4cloud.plugin.cache.properties;

import com.pig4cloud.plugin.cache.enums.CaffeineStrength;
import lombok.Data;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * @author lengleng
 * @date 2020/9/26
 * <p>
 * Caffeine 相关配置
 */
@Data
public class CaffeineConfigProp {

	/**
	 * 默认访问后过期时间
	 */
	private Duration expireAfterAccess;

	/**
	 * 每个cacheName的过访问后过期时间，优先级比expireAfterAccess高
	 */
	private Map<String, Duration> expireAfterAccesses = new HashMap<>();

	/**
	 * 默认写入后过期时间
	 */
	private Duration expireAfterWrite;

	/**
	 * 每个cacheName的写入后过期时间，优先级比expireAfterWrite高
	 */
	private Map<String, Duration> expireAfterWrites = new HashMap<>();

	/**
	 * 默认写入后刷新时间
	 */
	private Duration refreshAfterWrite;

	/**
	 * 每个cacheName的入后刷新时间，优先级比refreshAfterWrite高
	 */
	private Map<String, Duration> refreshAfterWrites = new HashMap<>();

	/**
	 * 初始化大小
	 */
	private int initialCapacity;

	/**
	 * 最大缓存对象个数，超过此数量时之前放入的缓存将失效
	 */
	private long maximumSize;

	/**
	 * key 强度
	 */
	private CaffeineStrength keyStrength;

	/**
	 * value 强度
	 */
	private CaffeineStrength valueStrength;

}
