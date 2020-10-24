package com.pig4cloud.plugin.cache.support;

import com.github.benmanes.caffeine.cache.Cache;
import com.pig4cloud.plugin.cache.properties.CacheConfigProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author fuwei.deng
 * @version 1.0.0
 */
@Slf4j
public class RedisCaffeineCache extends AbstractValueAdaptingCache {

	@Getter
	private final String name;
	@Getter
	private final Cache<Object, Object> caffeineCache;

	private final RedisTemplate<Object, Object> stringKeyRedisTemplate;

	private final String cachePrefix;

	private final long defaultExpiration;

	private final Map<String, Long> expires;

	private final String topic;

	private final Map<String, ReentrantLock> keyLockMap = new ConcurrentHashMap<>();

	public RedisCaffeineCache(String name, RedisTemplate<Object, Object> stringKeyRedisTemplate,
			Cache<Object, Object> caffeineCache, CacheConfigProperties cacheConfigProperties) {
		super(cacheConfigProperties.isCacheNullValues());
		this.name = name;
		this.stringKeyRedisTemplate = stringKeyRedisTemplate;
		this.caffeineCache = caffeineCache;
		this.cachePrefix = cacheConfigProperties.getCachePrefix();
		this.defaultExpiration = cacheConfigProperties.getRedis().getDefaultExpiration();
		this.expires = cacheConfigProperties.getRedis().getExpires();
		this.topic = cacheConfigProperties.getRedis().getTopic();
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
		long expire = getExpire();
		if (expire > 0) {
			stringKeyRedisTemplate.opsForValue().set(getKey(key), toStoreValue(value), expire, TimeUnit.MILLISECONDS);
		}
		else {
			stringKeyRedisTemplate.opsForValue().set(getKey(key), toStoreValue(value));
		}

		push(new CacheMessage(this.name, key));

		caffeineCache.put(key, value);
	}

	@Override
	public ValueWrapper putIfAbsent(Object key, Object value) {
		Object cacheKey = getKey(key);
		Object prevValue = null;
		// 考虑使用分布式锁，或者将redis的setIfAbsent改为原子性操作
		synchronized (key) {
			prevValue = stringKeyRedisTemplate.opsForValue().get(cacheKey);
			if (prevValue == null) {
				long expire = getExpire();
				if (expire > 0) {
					stringKeyRedisTemplate.opsForValue().set(getKey(key), toStoreValue(value), expire,
							TimeUnit.MILLISECONDS);
				}
				else {
					stringKeyRedisTemplate.opsForValue().set(getKey(key), toStoreValue(value));
				}

				push(new CacheMessage(this.name, key));

				caffeineCache.put(key, toStoreValue(value));
			}
		}
		return toValueWrapper(prevValue);
	}

	@Override
	public void evict(Object key) {
		// 先清除redis中缓存数据，然后清除caffeine中的缓存，避免短时间内如果先清除caffeine缓存后其他请求会再从redis里加载到caffeine中
		stringKeyRedisTemplate.delete(getKey(key));

		push(new CacheMessage(this.name, key));

		caffeineCache.invalidate(key);
	}

	@Override
	public void clear() {
		// 先清除redis中缓存数据，然后清除caffeine中的缓存，避免短时间内如果先清除caffeine缓存后其他请求会再从redis里加载到caffeine中
		Set<Object> keys = stringKeyRedisTemplate.keys(this.name.concat(":*"));

		if (!CollectionUtils.isEmpty(keys)){
			stringKeyRedisTemplate.delete(keys);
		}

		push(new CacheMessage(this.name, null));

		caffeineCache.invalidateAll();
	}

	@Override
	protected Object lookup(Object key) {
		Object cacheKey = getKey(key);
		Object value = caffeineCache.getIfPresent(key);
		if (value != null) {
			log.debug("get cache from caffeine, the key is : {}", cacheKey);
			return value;
		}

		value = stringKeyRedisTemplate.opsForValue().get(cacheKey);

		if (value != null) {
			log.debug("get cache from redis and put in caffeine, the key is : {}", cacheKey);
			caffeineCache.put(key, value);
		}
		return value;
	}

	private Object getKey(Object key) {
		return this.name.concat(":").concat(
				StringUtils.isEmpty(cachePrefix) ? key.toString() : cachePrefix.concat(":").concat(key.toString()));
	}

	private long getExpire() {
		Long cacheNameExpire = expires.get(this.name);
		return cacheNameExpire == null ? defaultExpiration : cacheNameExpire;
	}

	/**
	 * @param message
	 * @description 缓存变更时通知其他节点清理本地缓存
	 * @author fuwei.deng
	 * @date 2018年1月31日 下午3:20:28
	 * @version 1.0.0
	 */
	private void push(CacheMessage message) {
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

}
