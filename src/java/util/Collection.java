package java.util;

import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * 集合的顶部接口
 */
@SuppressWarnings("all")
public interface Collection<E> extends Iterable<E> {


    /**
     * 集合里元素的个数
     */
    int size();

    /**
     * 判断集合是否为空
     */
    boolean isEmpty();

    /**
     * 集合是否包含指定元素
     */
    boolean contains(Object o);

    /**
     * 迭代器
     */
    Iterator<E> iterator();

    /**
     * 将集合中的元素转成Object数组
     */
    Object[] toArray();

    /**
     * 将集合中的元素转成指定类型的数组
     */
    <T> T[] toArray(T[] a);

    /**
     * 添加元素
     */
    boolean add(E e);

    /**
     * 移除某个元素
     */
    boolean remove(Object o);


    /**
     * 集合是否包含传参集合的全部元素
     */
    boolean containsAll(Collection<?> c);

    /**
     * 将传参集合全部元素添加到集合中
     */
    boolean addAll(Collection<? extends E> c);

    /**
     * 移除传参集合中的全部元素
     */
    boolean removeAll(Collection<?> c);

    /**
     * 根据筛选条件移除集合中的全部元素
     */
    default boolean removeIf(Predicate<? super E> filter) {
        Objects.requireNonNull(filter);
        boolean removed = false;
        final Iterator<E> each = iterator();
        while (each.hasNext()) {
            if (filter.test(each.next())) {
                each.remove();
                removed = true;
            }
        }
        return removed;
    }

    /**
     * 删除指定集合中不存在的元素（取交集）
     */
    boolean retainAll(Collection<?> c);

    /**
     * 移除集合中的全部元素
     */
    void clear();


    /**
     * 判断是否相等
     */
    boolean equals(Object o);

    /**
     * hashCode方法
     */
    int hashCode();

    /**
     *
     */
    @Override
    default Spliterator<E> spliterator() {
        return Spliterators.spliterator(this, 0);
    }

    /**
     * 非并行流
     */
    default Stream<E> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    /**
     * 并行流
     */
    default Stream<E> parallelStream() {
        return StreamSupport.stream(spliterator(), true);
    }
}
