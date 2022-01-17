package com.pig4cloud.plugin.cache.properties;

import com.pig4cloud.plugin.cache.enums.CaffeineStrength;
import lombok.Data;

import java.time.Duration;

/**
 * @author lengleng
 * @date 2020/9/26
 * <p>
 * Caffeine 相关配置
 */
@Data
public class CaffeineConfigProp {

	/**
	 * 访问后过期时间
	 */
	private Duration expireAfterAccess;

	/**
	 * 写入后过期时间
	 */
	private Duration expireAfterWrite;

	/**
	 * 写入后刷新时间
	 */
	private Duration refreshAfterWrite;

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
