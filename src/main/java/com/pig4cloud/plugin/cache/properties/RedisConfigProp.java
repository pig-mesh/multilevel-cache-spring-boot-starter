package com.pig4cloud.plugin.cache.properties;

import lombok.Data;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * @author lengleng
 * @date 2020/9/26
 * <p>
 * redis 相关配置
 */
@Data
public class RedisConfigProp {

	/**
	 * 全局过期时间，默认不过期
	 */
	private Duration defaultExpiration = Duration.ZERO;

	/**
	 * 全局空值过期时间，默认和有值的过期时间一致，一般设置空值过期时间较短
	 */
	private Duration defaultNullValuesExpiration = null;

	/**
	 * 每个cacheName的过期时间，优先级比defaultExpiration高
	 */
	private Map<String, Duration> expires = new HashMap<>();

	/**
	 * 缓存更新时通知其他节点的topic名称
	 */
	private String topic = "cache:redis:caffeine:topic";

	/**
	 * 生成当前节点id的key，当配置了spring.cache.multi.server-id时，该配置不生效
	 */
	private String serverIdGeneratorKey = "cache:redis:caffeine:server-id-sequence";

}
