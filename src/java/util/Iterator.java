package java.util;

import java.util.function.Consumer;

/**
 迭代器
 */
public interface Iterator<E> {
    /**
     * 判断是否还有下一个元素
     */
    boolean hasNext();

    /**
     * 返回下一个元素
     */
    E next();

    /**
     * 移除元素
     */
    default void remove() {
        throw new UnsupportedOperationException("remove");
    }

    /**
     * 对剩余的元素操作
     */
    default void forEachRemaining(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        while (hasNext())
            action.accept(next());
    }
}
