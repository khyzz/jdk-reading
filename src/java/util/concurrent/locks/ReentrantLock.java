package java.util.concurrent.locks;
import java.util.concurrent.TimeUnit;
import java.util.Collection;

@SuppressWarnings("all")
public class ReentrantLock implements Lock, java.io.Serializable {
    private static final long serialVersionUID = 7373984872572414699L;
    /** Synchronizer providing all implementation mechanics */
    private final Sync sync;

    /**
     * 抽象锁父类
     */
    abstract static class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = -5179523762034025860L;

        /**
         * 上锁、具体实现由子类负责
         */
        abstract void lock();

        /**
         * 非公平地尝试获取锁
         */
        final boolean nonfairTryAcquire(int acquires) {
            final Thread current = Thread.currentThread();
            int c = getState();
            // 当前没有线程获取到锁
            if (c == 0) {
                // 如果能设置成功同步状态，则将独占锁的线程设置为本线程
                if (compareAndSetState(0, acquires)) {
                    setExclusiveOwnerThread(current);
                    return true;
                }
            }
            // 获得锁的线程是本线程
            else if (current == getExclusiveOwnerThread()) {
                int nextc = c + acquires;
                if (nextc < 0) // overflow
                    throw new Error("Maximum lock count exceeded");
                // 设置同步状态
                setState(nextc);
                return true;
            }
            return false;
        }

        /**
         * 尝试释放锁
         */
        protected final boolean tryRelease(int releases) {
            int c = getState() - releases;
            if (Thread.currentThread() != getExclusiveOwnerThread())
                throw new IllegalMonitorStateException();
            boolean free = false;
            if (c == 0) {
                free = true;
                setExclusiveOwnerThread(null);
            }
            setState(c);
            return free;
        }

        /**
         * 判断此线程是否是独占锁持有者
         */
        protected final boolean isHeldExclusively() {
            return getExclusiveOwnerThread() == Thread.currentThread();
        }

        final ConditionObject newCondition() {
            return new ConditionObject();
        }

        /**
         * 返回锁的持有者
         */
        final Thread getOwner() {
            return getState() == 0 ? null : getExclusiveOwnerThread();
        }

        /**
         * 获取此线程持有的同步状态数值
         */
        final int getHoldCount() {
            return isHeldExclusively() ? getState() : 0;
        }

        /**
         * 判断是否已经上锁
         */
        final boolean isLocked() {
            return getState() != 0;
        }

        /**
         * Reconstitutes the instance from a stream (that is, deserializes it).
         */
        private void readObject(java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
            s.defaultReadObject();
            setState(0); // reset to unlocked state
        }
    }

    /**
     * 非公平锁
     */
    static final class NonfairSync extends Sync {
        private static final long serialVersionUID = 7316153563782823691L;

        /**
         * 上锁
         */
        final void lock() {
            // 如果能设置同步状态成功
            if (compareAndSetState(0, 1))
                // 设置独占锁持有者
                setExclusiveOwnerThread(Thread.currentThread());
            else
                // AQS提供的模板方法
                acquire(1);
        }

        // 上面acquire用到，由父类Sync提供
        protected final boolean tryAcquire(int acquires) {
            return nonfairTryAcquire(acquires);
        }
    }

    /**
     * 公平锁
     */
    static final class FairSync extends Sync {
        private static final long serialVersionUID = -3000897897090466540L;

        /**
         * 不直接设置同步状态
         * 而是尝试获取锁、因为有可能有的线程在此线程之前入队
         */
        final void lock() {
            acquire(1);
        }

        /**
         * 尝试获取锁
         */
        protected final boolean tryAcquire(int acquires) {
            final Thread current = Thread.currentThread();
            int c = getState();
            // 没有线程获取到锁
            if (c == 0) {
                // 等待队列没有前驱节点，并且设置同步状态成功的线程
                if (!hasQueuedPredecessors() &&
                    compareAndSetState(0, acquires)) {
                    // 设置独占锁持有者
                    setExclusiveOwnerThread(current);
                    return true;
                }
            }
            // 持有者就是此线程
            else if (current == getExclusiveOwnerThread()) {
                int nextc = c + acquires;
                if (nextc < 0)
                    throw new Error("Maximum lock count exceeded");
                setState(nextc);
                return true;
            }
            return false;
        }
    }

    /**
     * 默认构造器：非公平锁
     */
    public ReentrantLock() {
        sync = new NonfairSync();
    }

    /**
     * true： 公平锁
     * false：非公平锁
     */
    public ReentrantLock(boolean fair) {
        sync = fair ? new FairSync() : new NonfairSync();
    }

    /**
     * 上锁，具体执行的，是创建对象时创建的sync对象的上锁方法
     */
    public void lock() {
        sync.lock();
    }

    /**
     *
     */
    public void lockInterruptibly() throws InterruptedException {
        sync.acquireInterruptibly(1);
    }

    /**
     * 非公平的获取锁
     */
    public boolean tryLock() {
        return sync.nonfairTryAcquire(1);
    }

    /**
     * 在指定时间内尝试上锁，超时取消
     */
    public boolean tryLock(long timeout, TimeUnit unit)
            throws InterruptedException {
        return sync.tryAcquireNanos(1, unit.toNanos(timeout));
    }

    /**
     * 释放锁
     */
    public void unlock() {
        sync.release(1);
    }

    /**
     * 条件
     */
    public Condition newCondition() {
        return sync.newCondition();
    }

    /**
     * 获取本线程持有同步状态的数值
     */
    public int getHoldCount() {
        return sync.getHoldCount();
    }

    /**
     * 判断锁是否被此线程持有
     */
    public boolean isHeldByCurrentThread() {
        return sync.isHeldExclusively();
    }

    /**
     * 判断是否已经上锁
     */
    public boolean isLocked() {
        return sync.isLocked();
    }

    /**
     * 判断是否是公平锁
     */
    public final boolean isFair() {
        return sync instanceof FairSync;
    }

    /**
     * 获取锁的持有者
     */
    protected Thread getOwner() {
        return sync.getOwner();
    }

    /**
     * 是否有等待的线程
     */
    public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    /**
     * 判断线程是否在等待队列里
     */
    public final boolean hasQueuedThread(Thread thread) {
        return sync.isQueued(thread);
    }

    /**
     * 等待队列长度
     */
    public final int getQueueLength() {
        return sync.getQueueLength();
    }

    /**
     * 获取等待队列里的线程
     */
    protected Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }

    /**
     *
     */
    public boolean hasWaiters(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.hasWaiters((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     *
     */
    public int getWaitQueueLength(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitQueueLength((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     *
     */
    protected Collection<Thread> getWaitingThreads(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitingThreads((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     * toString
     */
    public String toString() {
        Thread o = sync.getOwner();
        return super.toString() + ((o == null) ?
                                   "[Unlocked]" :
                                   "[Locked by thread " + o.getName() + "]");
    }
}
