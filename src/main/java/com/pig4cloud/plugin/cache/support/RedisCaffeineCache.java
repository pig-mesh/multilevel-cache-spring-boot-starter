package com.pig4cloud.plugin.cache.support;

import com.github.benmanes.caffeine.cache.Cache;
import com.pig4cloud.plugin.cache.properties.CacheConfigProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.cache.support.NullValue;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author fuwei.deng
 * @version 1.0.0
 */
@Slf4j
@Getter
public class RedisCaffeineCache extends AbstractValueAdaptingCache {

	private final String name;

	private final Cache<Object, Object> caffeineCache;

	private final RedisTemplate<Object, Object> stringKeyRedisTemplate;

	private final String cachePrefix;

	private final Duration defaultExpiration;

	private final Duration defaultNullValuesExpiration;

	private final Map<String, Duration> expires;

	private final String topic;

	private final Object serverId;

	private final Map<String, ReentrantLock> keyLockMap = new ConcurrentHashMap<>();

	public RedisCaffeineCache(String name, RedisTemplate<Object, Object> stringKeyRedisTemplate,
			Cache<Object, Object> caffeineCache, CacheConfigProperties cacheConfigProperties) {
		super(cacheConfigProperties.isCacheNullValues());
		this.name = name;
		this.stringKeyRedisTemplate = stringKeyRedisTemplate;
		this.caffeineCache = caffeineCache;
		this.cachePrefix = cacheConfigProperties.getCachePrefix();
		this.defaultExpiration = cacheConfigProperties.getRedis().getDefaultExpiration();
		this.defaultNullValuesExpiration = cacheConfigProperties.getRedis().getDefaultNullValuesExpiration();
		this.expires = cacheConfigProperties.getRedis().getExpires();
		this.topic = cacheConfigProperties.getRedis().getTopic();
		this.serverId = cacheConfigProperties.getServerId();
	}

	@Override
	public Object getNativeCache() {
		return this;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T get(Object key, Callable<T> valueLoader) {
		Object value = lookup(key);
		if (value != null) {
			return (T) value;
		}

		ReentrantLock lock = keyLockMap.computeIfAbsent(key.toString(), s -> {
			log.trace("create lock for key : {}", s);
			return new ReentrantLock();
		});

		try {
			lock.lock();
			value = lookup(key);
			if (value != null) {
				return (T) value;
			}
			value = valueLoader.call();
			Object storeValue = toStoreValue(value);
			put(key, storeValue);
			return (T) value;
		}
		catch (Exception e) {
			throw new ValueRetrievalException(key, valueLoader, e.getCause());
		}
		finally {
			lock.unlock();
		}
	}

	@Override
	public void put(Object key, Object value) {
		if (!super.isAllowNullValues() && value == null) {
			this.evict(key);
			return;
		}
		doPut(key, value);
	}

	@Override
	public ValueWrapper putIfAbsent(Object key, Object value) {
		Object prevValue;
		// 考虑使用分布式锁，或者将redis的setIfAbsent改为原子性操作
		synchronized (key) {
			prevValue = getRedisValue(key);
			if (prevValue == null) {
				doPut(key, value);
			}
		}
		return toValueWrapper(prevValue);
	}

	private void doPut(Object key, Object value) {
		value = toStoreValue(value);
		Duration expire = getExpire(value);
		setRedisValue(key, value, expire);

		push(key);

		setCaffeineValue(key, value);
	}

	@Override
	public void evict(Object key) {
		// 先清除redis中缓存数据，然后清除caffeine中的缓存，避免短时间内如果先清除caffeine缓存后其他请求会再从redis里加载到caffeine中
		stringKeyRedisTemplate.delete(getKey(key));

		push(key);

		caffeineCache.invalidate(key);
	}

	@Override
	public void clear() {
		// 先清除redis中缓存数据，然后清除caffeine中的缓存，避免短时间内如果先清除caffeine缓存后其他请求会再从redis里加载到caffeine中
		Set<Object> keys = stringKeyRedisTemplate.keys(this.name.concat(":*"));

		if (!CollectionUtils.isEmpty(keys)) {
			stringKeyRedisTemplate.delete(keys);
		}

		push(null);

		caffeineCache.invalidateAll();
	}

	@Override
	protected Object lookup(Object key) {
		Object cacheKey = getKey(key);
		Object value = getCaffeineValue(key);
		if (value != null) {
			log.debug("get cache from caffeine, the key is : {}", cacheKey);
			return value;
		}

		value = getRedisValue(key);

		if (value != null) {
			log.debug("get cache from redis and put in caffeine, the key is : {}", cacheKey);
			setCaffeineValue(key, value);
		}
		return value;
	}

	protected Object getKey(Object key) {
		return this.name.concat(":").concat(
				StringUtils.hasLength(cachePrefix) ? cachePrefix.concat(":").concat(key.toString()) : key.toString());
	}

	protected Duration getExpire(Object value) {
		Duration cacheNameExpire = expires.get(this.name);
		if (cacheNameExpire == null) {
			cacheNameExpire = defaultExpiration;
		}
		if ((value == null || value == NullValue.INSTANCE) && this.defaultNullValuesExpiration != null) {
			cacheNameExpire = this.defaultNullValuesExpiration;
		}
		return cacheNameExpire;
	}

	protected void push(Object key) {
		push(new CacheMessage(this.serverId, this.name, key));
	}

	/**
	 * @param message
	 * @description 缓存变更时通知其他节点清理本地缓存
	 * @author fuwei.deng
	 * @date 2018年1月31日 下午3:20:28
	 * @version 1.0.0
	 */
	protected void push(CacheMessage message) {
		stringKeyRedisTemplate.convertAndSend(topic, message);
	}

	/**
	 * @param key
	 * @description 清理本地缓存
	 * @author fuwei.deng
	 * @date 2018年1月31日 下午3:15:39
	 * @version 1.0.0
	 */
	public void clearLocal(Object key) {
		log.debug("clear local cache, the key is : {}", key);
		if (key == null) {
			caffeineCache.invalidateAll();
		}
		else {
			caffeineCache.invalidate(key);
		}
	}

	protected void setRedisValue(Object key, Object value, Duration expire) {
		if (!expire.isNegative() && !expire.isZero()) {
			stringKeyRedisTemplate.opsForValue().set(getKey(key), value, expire);
		}
		else {
			stringKeyRedisTemplate.opsForValue().set(getKey(key), value);
		}
	}

	protected Object getRedisValue(Object key) {
		return stringKeyRedisTemplate.opsForValue().get(getKey(key));
	}

	protected void setCaffeineValue(Object key, Object value) {
		caffeineCache.put(key, value);
	}

	protected Object getCaffeineValue(Object key) {
		return caffeineCache.getIfPresent(key);
	}

}
