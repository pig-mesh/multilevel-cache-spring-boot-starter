package com.pig4cloud.plugin.cache.support;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Policy;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.pig4cloud.plugin.cache.enums.CacheOperation;
import com.pig4cloud.plugin.cache.properties.CacheConfigProperties;
import com.pig4cloud.plugin.cache.util.CollUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.cache.support.NullValue;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * @author fuwei.deng
 * @version 1.0.0
 */
@Slf4j
@Getter
public class RedisCaffeineCache extends AbstractValueAdaptingCache implements Cache<Object, Object> {

	private final String name;

	private final Cache<Object, Object> caffeineCache;

	private final RedisTemplate<Object, Object> stringKeyRedisTemplate;

	private final String cachePrefix;

	private final String getKeyPrefix;

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
		if (StringUtils.hasLength(cachePrefix)) {
			this.getKeyPrefix = name + ":" + cachePrefix + ":";
		}
		else {
			this.getKeyPrefix = name + ":";
		}
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

		push((Object) null);

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
		return this.getKeyPrefix + key;
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
		push(key, CacheOperation.EVICT);
	}

	protected void push(Object key, CacheOperation operation) {
		push(new CacheMessage(this.serverId, this.name, operation, key));
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

	public void clearLocalBatch(Iterable<Object> keys) {
		log.debug("clear local cache, the keys is : {}", keys);
		caffeineCache.invalidateAll(keys);
	}

	protected void setRedisValue(Object key, Object value, Duration expire) {
		setRedisValue(key, value, expire, stringKeyRedisTemplate.opsForValue());
	}

	protected void setRedisValue(Object key, Object value, Duration expire,
			ValueOperations<Object, Object> valueOperations) {
		if (!expire.isNegative() && !expire.isZero()) {
			valueOperations.set(getKey(key), value, expire);
		}
		else {
			valueOperations.set(getKey(key), value);
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

	// ---------- 对 Caffeine Cache 接口的实现

	@Override
	public @Nullable Object getIfPresent(@NonNull Object key) {
		ValueWrapper valueWrapper = get(key);
		if (valueWrapper == null) {
			return null;
		}
		return valueWrapper.get();
	}

	@Override
	public @Nullable Object get(@NonNull Object key, @NonNull Function<? super Object, ?> mappingFunction) {
		return get(key, (Callable<Object>) () -> mappingFunction.apply(key));
	}

	@Override
	@SuppressWarnings("unchecked")
	public @NonNull Map<@NonNull Object, @NonNull Object> getAllPresent(@NonNull Iterable<@NonNull ?> keys) {
		GetAllContext context = new GetAllContext((Iterable<Object>) keys);
		doGetAll(context);
		Map<Object, Object> cachedKeyValues = context.cachedKeyValues;
		Map<Object, Object> result = new HashMap<>(cachedKeyValues.size(), 1);
		cachedKeyValues.forEach((k, v) -> result.put(k, fromStoreValue(v)));
		return result;
	}

	@Override
	@SuppressWarnings("unchecked")
	public @NonNull Map<Object, Object> getAll(Iterable<?> keys,
			Function<? super Set<?>, ? extends Map<?, ?>> mappingFunction) {
		GetAllContext context = new GetAllContext((Iterable<Object>) keys);
		context.saveRedisAbsentKeys = true;
		doGetAll(context);
		int redisAbsentCount = context.redisAbsentCount;
		Map<Object, Object> cachedKeyValues = context.cachedKeyValues;
		if (redisAbsentCount == 0) {
			// 所有 key 全部命中缓存
			Map<Object, Object> result = new HashMap<>(cachedKeyValues.size(), 1);
			cachedKeyValues.forEach((k, v) -> result.put(k, fromStoreValue(v)));
			return result;
		}
		// 从 mappingFunction 中获取值
		Map<?, ?> mappingKeyValues = mappingFunction.apply(context.redisAbsentKeys);
		putAll(mappingKeyValues);
		Map<Object, Object> result = new HashMap<>(cachedKeyValues.size() + mappingKeyValues.size(), 1);
		cachedKeyValues.forEach((k, v) -> result.put(k, fromStoreValue(v)));
		result.putAll(mappingKeyValues);
		return result;
	}

	@SuppressWarnings("unchecked")
	protected void doGetAll(GetAllContext context) {
		context.cachedKeyValues = caffeineCache.getAll(context.allKeys, keyIterable -> {
			Collection<Object> caffeineAbsentKeys = CollUtil.toCollection((Iterable<Object>) keyIterable);
			Collection<Object> redisKeys = CollUtil.trans(caffeineAbsentKeys, this::getKey);
			// 从 redis 批量获取
			List<Object> redisValues = stringKeyRedisTemplate.opsForValue().multiGet(redisKeys);
			Objects.requireNonNull(redisValues);
			// 统计 redis 中没有的 key 数量
			int redisAbsentCount = 0;
			for (Object value : redisValues) {
				if (value == null) {
					redisAbsentCount++;
				}
			}
			context.redisAbsentCount = redisAbsentCount;
			HashMap<Object, Object> result = new HashMap<>(caffeineAbsentKeys.size() - redisAbsentCount, 1);
			boolean saveCacheAbsentKeys = context.saveRedisAbsentKeys;
			if (saveCacheAbsentKeys) {
				// mappingFunction 的参数
				context.redisAbsentKeys = new HashSet<>(redisAbsentCount, 1);
			}
			int index = 0;
			for (Object key : caffeineAbsentKeys) {
				Object redisValue = redisValues.get(index);
				if (redisValue != null) {
					result.put(key, redisValue);
				}
				else if (saveCacheAbsentKeys) {
					context.redisAbsentKeys.add(key);
				}
				index++;
			}
			return result;
		});
	}

	protected static class GetAllContext {

		public GetAllContext(Iterable<Object> allKeys) {
			this.allKeys = allKeys;
		}

		protected Iterable<Object> allKeys;

		/**
		 * 是否将redis未查询到的key保存到 {@link #redisAbsentKeys}
		 */
		protected boolean saveRedisAbsentKeys = false;

		/**
		 * redis中未查询到的key
		 */
		protected Set<Object> redisAbsentKeys;

		/**
		 * redis中未查询到的key数量
		 */
		protected int redisAbsentCount;

		/**
		 * caffeine和redis中缓存的键值，未经过{@link #fromStoreValue}转换
		 */
		protected Map<Object, Object> cachedKeyValues;

	}

	@Override
	public void putAll(@NonNull Map<?, ?> map) {
		stringKeyRedisTemplate.executePipelined(new SessionCallback<Object>() {
			@Override
			@SuppressWarnings("unchecked")
			public <K, V> Object execute(@NonNull RedisOperations<K, V> operations) throws DataAccessException {
				ValueOperations<Object, Object> valueOperations = (ValueOperations<Object, Object>) operations
						.opsForValue();
				map.forEach((k, v) -> {
					Object o = toStoreValue(v);
					Duration expire = getExpire(o);
					setRedisValue(k, o, expire, valueOperations);
					setCaffeineValue(k, o);
				});
				return null;
			}
		});
		push(new ArrayList<>(map.keySet()), CacheOperation.EVICT_BATCH);
	}

	@Override
	public void invalidate(@NonNull Object key) {
		evict(key);
	}

	@Override
	public void invalidateAll(@NonNull Iterable<@NonNull ?> keys) {
		Collection<?> keysColl = CollUtil.toCollection(keys);
		Collection<Object> redisKeys = CollUtil.trans(keysColl, this::getKey);
		stringKeyRedisTemplate.delete(redisKeys);
		push(keysColl, CacheOperation.EVICT_BATCH);
		caffeineCache.invalidateAll(keysColl);
	}

	@Override
	public void invalidateAll() {
		this.clear();
	}

	// ---------- 单纯的代理 caffeineCache

	@Override
	public @NonNegative long estimatedSize() {
		return caffeineCache.estimatedSize();
	}

	@Override
	public @NonNull CacheStats stats() {
		return caffeineCache.stats();
	}

	@Override
	public @NonNull ConcurrentMap<@NonNull Object, @NonNull Object> asMap() {
		return caffeineCache.asMap();
	}

	@Override
	public void cleanUp() {
		caffeineCache.cleanUp();
	}

	@Override
	public @NonNull Policy<Object, Object> policy() {
		return caffeineCache.policy();
	}

}
