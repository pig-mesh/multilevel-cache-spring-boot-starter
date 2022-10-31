package com.pig4cloud.plugin.cache.util;

import java.util.*;
import java.util.function.Function;

/**
 * 拷贝自 hutool
 *
 * @author xiaoleilu
 */
public class CollUtil {

	/**
	 * {@link Iterable}转为{@link Collection}<br>
	 * 首先尝试强转，强转失败则构建一个新的{@link ArrayList}
	 * @param <E> 集合元素类型
	 * @param iterable {@link Iterable}
	 * @return {@link Collection} 或者 {@link ArrayList}
	 */
	public static <E> Collection<E> toCollection(Iterable<E> iterable) {
		return (iterable instanceof Collection) ? (Collection<E>) iterable : newArrayList(iterable.iterator());
	}

	/**
	 * 新建一个ArrayList<br>
	 * 提供的参数为null时返回空{@link ArrayList}
	 * @param <T> 集合元素类型
	 * @param iterator {@link Iterator}
	 * @return ArrayList对象
	 */
	public static <T> List<T> newArrayList(Iterator<T> iterator) {
		final List<T> list = new ArrayList<>();
		if (null != iterator) {
			while (iterator.hasNext()) {
				list.add(iterator.next());
			}
		}
		return list;
	}

	/**
	 * 使用给定的转换函数，转换源集合为新类型的集合
	 * @param <F> 源元素类型
	 * @param <T> 目标元素类型
	 * @param collection 集合
	 * @param function 转换函数
	 * @return 新类型的集合
	 * @since 5.4.3
	 */
	public static <F, T> Collection<T> trans(Collection<F> collection, Function<? super F, ? extends T> function) {
		return new TransCollection<>(collection, function);
	}

}
