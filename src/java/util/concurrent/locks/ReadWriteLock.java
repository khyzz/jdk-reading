package java.util.concurrent.locks;

@SuppressWarnings("all")
public interface ReadWriteLock {
    /**
     * 读锁
     */
    Lock readLock();

    /**
     * 写锁
     */
    Lock writeLock();
}
