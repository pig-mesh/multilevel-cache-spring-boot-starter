package com.pig4cloud.plugin.cache.metrics;

import com.pig4cloud.plugin.cache.support.RedisCaffeineCache;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import lombok.NoArgsConstructor;
import org.springframework.boot.actuate.metrics.cache.CacheMeterBinderProvider;

/**
 * RedisCaffeineCache meter
 *
 * @author L.cm
 */
@NoArgsConstructor
public class RedisCaffeineCacheMeterBinderProvider implements CacheMeterBinderProvider<RedisCaffeineCache> {

	@Override
	public MeterBinder getMeterBinder(RedisCaffeineCache cache, Iterable<Tag> tags) {
		return new CaffeineCacheMetrics(cache.getCaffeineCache(), cache.getName(), tags);
	}

}
