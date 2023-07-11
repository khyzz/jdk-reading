package java.util.concurrent.locks;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("all")
public interface Lock {

    /**
     * 上锁
     */
    void lock();

    /**
     * 获取锁、可中断
     */
    void lockInterruptibly() throws InterruptedException;

    /**
     * 尝试上锁
     */
    boolean tryLock();

    /**
     * 带时间的尝试上锁
     */
    boolean tryLock(long time, TimeUnit unit) throws InterruptedException;

    /**
     * 释放锁
     */
    void unlock();

    /**
     * Condition
     */
    Condition newCondition();
}
