package java.util.concurrent.locks;
import java.util.concurrent.TimeUnit;
import java.util.Collection;

@SuppressWarnings("all")
public class ReentrantReadWriteLock
        implements ReadWriteLock, java.io.Serializable {
    private static final long serialVersionUID = -6992448646407690164L;
    /** 内部类提供读锁 */
    private final ReentrantReadWriteLock.ReadLock readerLock;
    /** 内部类提供写锁 */
    private final ReentrantReadWriteLock.WriteLock writerLock;
    /** Performs all synchronization mechanics */
    final Sync sync;

    /**
     * 默认是非公平锁
     */
    public ReentrantReadWriteLock() {
        this(false);
    }

    /**
     * 根据入参创建公平锁或者非公平锁
     */
    public ReentrantReadWriteLock(boolean fair) {
        sync = fair ? new FairSync() : new NonfairSync();
        readerLock = new ReadLock(this);
        writerLock = new WriteLock(this);
    }

    public ReentrantReadWriteLock.WriteLock writeLock() { return writerLock; }
    public ReentrantReadWriteLock.ReadLock  readLock()  { return readerLock; }

    /**
     * 抽象锁父类
     */
    abstract static class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 6317671515068378041L;

        /** 高16位是读状态、低16位是写状态 */
        static final int SHARED_SHIFT   = 16;
        static final int SHARED_UNIT    = (1 << SHARED_SHIFT);
        /** 读或者写锁的最大数量 */
        static final int MAX_COUNT      = (1 << SHARED_SHIFT) - 1;
        /** 独占的掩码 */
        static final int EXCLUSIVE_MASK = (1 << SHARED_SHIFT) - 1;

        /** 共享的个数  */
        static int sharedCount(int c)    { return c >>> SHARED_SHIFT; }
        /** 独占的个数  */
        static int exclusiveCount(int c) { return c & EXCLUSIVE_MASK; }

        /**
         * 用于记录每个线程获取读锁的个数
         */
        static final class HoldCounter {
            int count = 0;
            // Use id, not reference, to avoid garbage retention
            final long tid = getThreadId(Thread.currentThread());
        }

        /**
         * 该类型的变量每个线程各自保存一份
         */
        static final class ThreadLocalHoldCounter
            extends ThreadLocal<HoldCounter> {
            public HoldCounter initialValue() {
                return new HoldCounter();
            }
        }

        /**
         * 用于记录读锁的个数
         */
        private transient ThreadLocalHoldCounter readHolds;

        /**
         * 缓存最近获取到读锁的线程的锁个数
         */
        private transient HoldCounter cachedHoldCounter;

        /**
         * 第一个获取读锁的线程
         */
        private transient Thread firstReader = null;
        /**
         * 记录上述线程持有的读锁数
         */
        private transient int firstReaderHoldCount;

        Sync() {
            readHolds = new ThreadLocalHoldCounter();
            setState(getState()); // ensures visibility of readHolds
        }


        /**
         * 判断读线程是否需要阻塞
         */
        abstract boolean readerShouldBlock();

        /**
         * 判断写线程是否需要阻塞
         */
        abstract boolean writerShouldBlock();


        /**
         * 尝试释放独占锁(写锁)
         */
        protected final boolean tryRelease(int releases) {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            int nextc = getState() - releases;
            /** 判断独占锁是否已经完全释放 */
            boolean free = exclusiveCount(nextc) == 0;
            if (free)
                setExclusiveOwnerThread(null);
            setState(nextc);
            return free;
        }

        /**
         * 尝试获取独占锁(写锁)
         */
        protected final boolean tryAcquire(int acquires) {

            Thread current = Thread.currentThread();
            // AQS中的state值
            int c = getState();
            // 获取此时的独占状态
            int w = exclusiveCount(c);
            // 证明此时锁已经被获取了，但尚未清楚是读锁还是写锁
            if (c != 0) {
                // w == 0 证明写锁尚未被获取； w != 0 需要判断是否是重入
                if (w == 0 || current != getExclusiveOwnerThread())
                    return false;
                // 超过最大个数了
                if (w + exclusiveCount(acquires) > MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
                // 设置state， 写锁是低16位，可以直接相加
                setState(c + acquires);
                return true;
            }
            // 判断此时的线程是否需要阻塞，具体实现交由子类，如果需要阻塞，证明写锁已经被抢占了，写锁是独占锁直接返回false
            if (writerShouldBlock() ||
                !compareAndSetState(c, c + acquires))
                return false;
            setExclusiveOwnerThread(current);
            return true;
        }

        /**
         * 尝试释放共享锁(读锁)
         */
        protected final boolean tryReleaseShared(int unused) {
            Thread current = Thread.currentThread();
            // 第一个获取读锁的线程就是本线程，线程持有的读锁直接 - 1
            if (firstReader == current) {
                // assert firstReaderHoldCount > 0;
                if (firstReaderHoldCount == 1)
                    firstReader = null;
                else
                    firstReaderHoldCount--;
            } else {
                // 获取缓存的线程获取读锁的个数（上一个获取读锁的线程）
                HoldCounter rh = cachedHoldCounter;
                // 如果不是当前线程，再从readHolds获取
                if (rh == null || rh.tid != getThreadId(current))
                    rh = readHolds.get();
                int count = rh.count;
                if (count <= 1) {
                    readHolds.remove();
                    if (count <= 0)
                        throw unmatchedUnlockException();
                }
                --rh.count;
            }
            for (;;) {
                int c = getState();
                // 读锁是高16位，因此 -1 相当于 -0x 1 00 00
                int nextc = c - SHARED_UNIT;
                if (compareAndSetState(c, nextc))
                    return nextc == 0;
            }
        }

        private IllegalMonitorStateException unmatchedUnlockException() {
            return new IllegalMonitorStateException(
                "attempt to unlock read lock, not locked by current thread");
        }

        /**
         * 尝试获取共享锁
         */
        protected final int tryAcquireShared(int unused) {

            Thread current = Thread.currentThread();
            int c = getState();
            // 写锁已经被其他线程给抢占了，返回-1 表示获取读锁失败
            if (exclusiveCount(c) != 0 &&
                getExclusiveOwnerThread() != current)
                return -1;
            int r = sharedCount(c);
            // 读锁不需要阻塞，并且没超过最大读锁个数
            if (!readerShouldBlock() &&
                r < MAX_COUNT &&
                compareAndSetState(c, c + SHARED_UNIT)) {
                // r == 0 说明还没有线程获取读锁
                if (r == 0) {
                    firstReader = current;
                    firstReaderHoldCount = 1;
                } else if (firstReader == current) { // 获取读锁的就是本线程
                    firstReaderHoldCount++;
                } else {
                    // 拿到缓存的线程
                    HoldCounter rh = cachedHoldCounter;
                    if (rh == null || rh.tid != getThreadId(current))
                        cachedHoldCounter = rh = readHolds.get();
                    else if (rh.count == 0)
                        readHolds.set(rh);
                    rh.count++;
                }
                return 1;
            }
            // 读锁需要阻塞
            return fullTryAcquireShared(current);
        }

        /**
         * 全量版获取读锁
         */
        final int fullTryAcquireShared(Thread current) {
            HoldCounter rh = null;
            for (;;) {
                int c = getState();
                if (exclusiveCount(c) != 0) {
                    if (getExclusiveOwnerThread() != current)
                        return -1;
                } else if (readerShouldBlock()) {   // 读线程也应当阻塞
                    // 当前线程是第一个拿到读锁的，什么也不做，等待后续获取读锁
                    if (firstReader == current) {
                        // assert firstReaderHoldCount > 0;
                    } else {
                        if (rh == null) {
                            // 上一个拿到读锁的
                            rh = cachedHoldCounter;
                            if (rh == null || rh.tid != getThreadId(current)) {
                                rh = readHolds.get();
                                if (rh.count == 0)
                                    // 当前线程没拿到读锁，就将其移除
                                    readHolds.remove();
                            }
                        }
                        // 仍然没有拿到读锁，返回-1表示失败
                        if (rh.count == 0)
                            return -1;
                    }
                }
                if (sharedCount(c) == MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
                if (compareAndSetState(c, c + SHARED_UNIT)) {
                    // 之前没有获取到读锁的，将此线程设置为第一个获取到读锁的
                    if (sharedCount(c) == 0) {
                        firstReader = current;
                        firstReaderHoldCount = 1;
                    } else if (firstReader == current) {
                        firstReaderHoldCount++;
                    } else {
                        if (rh == null)
                            rh = cachedHoldCounter;
                        if (rh == null || rh.tid != getThreadId(current))
                            rh = readHolds.get();
                        else if (rh.count == 0)
                            readHolds.set(rh);
                        rh.count++;
                        cachedHoldCounter = rh; // cache for release
                    }
                    return 1;
                }
            }
        }

        /**
         * 尝试上写锁，
         */
        final boolean tryWriteLock() {
            Thread current = Thread.currentThread();
            int c = getState();
            if (c != 0) {
                int w = exclusiveCount(c);
                if (w == 0 || current != getExclusiveOwnerThread())
                    return false;
                if (w == MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
            }
            if (!compareAndSetState(c, c + 1))
                return false;
            setExclusiveOwnerThread(current);
            return true;
        }

        /**
         * 尝试上读锁
         */
        final boolean tryReadLock() {
            Thread current = Thread.currentThread();
            for (;;) {
                int c = getState();
                // 不是当前线程拿到的写锁
                if (exclusiveCount(c) != 0 &&
                    getExclusiveOwnerThread() != current)
                    return false;
                int r = sharedCount(c);
                if (r == MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
                if (compareAndSetState(c, c + SHARED_UNIT)) {
                    if (r == 0) {
                        firstReader = current;
                        firstReaderHoldCount = 1;
                    } else if (firstReader == current) {
                        firstReaderHoldCount++;
                    } else {
                        HoldCounter rh = cachedHoldCounter;
                        if (rh == null || rh.tid != getThreadId(current))
                            cachedHoldCounter = rh = readHolds.get();
                        else if (rh.count == 0)
                            readHolds.set(rh);
                        rh.count++;
                    }
                    return true;
                }
            }
        }

        protected final boolean isHeldExclusively() {
            return getExclusiveOwnerThread() == Thread.currentThread();
        }

        // Methods relayed to outer class

        final ConditionObject newCondition() {
            return new ConditionObject();
        }

        final Thread getOwner() {
            // Must read state before owner to ensure memory consistency
            return ((exclusiveCount(getState()) == 0) ?
                    null :
                    getExclusiveOwnerThread());
        }

        final int getReadLockCount() {
            return sharedCount(getState());
        }

        final boolean isWriteLocked() {
            return exclusiveCount(getState()) != 0;
        }

        final int getWriteHoldCount() {
            return isHeldExclusively() ? exclusiveCount(getState()) : 0;
        }

        final int getReadHoldCount() {
            if (getReadLockCount() == 0)
                return 0;

            Thread current = Thread.currentThread();
            if (firstReader == current)
                return firstReaderHoldCount;

            HoldCounter rh = cachedHoldCounter;
            if (rh != null && rh.tid == getThreadId(current))
                return rh.count;

            int count = readHolds.get().count;
            if (count == 0) readHolds.remove();
            return count;
        }

        /**
         * Reconstitutes the instance from a stream (that is, deserializes it).
         */
        private void readObject(java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
            s.defaultReadObject();
            readHolds = new ThreadLocalHoldCounter();
            setState(0); // reset to unlocked state
        }

        final int getCount() { return getState(); }
    }

    /**
     * 公平锁
     */
    static final class NonfairSync extends Sync {
        private static final long serialVersionUID = -8159625535654395037L;
        final boolean writerShouldBlock() {
            return false; // writers can always barge
        }
        final boolean readerShouldBlock() {
            // 判断CLH队列中的第一个节点是不是共享节点
            return apparentlyFirstQueuedIsExclusive();
        }
    }

    /**
     * 公平锁
     */
    static final class FairSync extends Sync {
        private static final long serialVersionUID = -2274990926593161451L;

        /**
         * 判断写锁是否需要阻塞
         */
        final boolean writerShouldBlock() {
            // 等待队列里有节点
            return hasQueuedPredecessors();
        }
        /**
         * 判断读锁是否需要阻塞
         */
        final boolean readerShouldBlock() {
            return hasQueuedPredecessors();
        }
    }

    /**
     * 读锁
     */
    public static class ReadLock implements Lock, java.io.Serializable {
        private static final long serialVersionUID = -5992448646407690164L;
        private final Sync sync;

        /**
         * 构造器
         */
        protected ReadLock(ReentrantReadWriteLock lock) {
            sync = lock.sync;
        }

        /**
         * 加锁
         */
        public void lock() {
            sync.acquireShared(1);
        }

        /**
         * 加锁、可相应中断
         */
        public void lockInterruptibly() throws InterruptedException {
            sync.acquireSharedInterruptibly(1);
        }

        /**
         * 尝试上锁
         */
        public boolean tryLock() {
            return sync.tryReadLock();
        }

        /**
         * 尝试上锁，有最大等待时间
         */
        public boolean tryLock(long timeout, TimeUnit unit)
                throws InterruptedException {
            return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
        }

        /**
         * 解锁
         */
        public void unlock() {
            sync.releaseShared(1);
        }

        /**
         *
         */
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }

        /**
         * toString
         */
        public String toString() {
            int r = sync.getReadLockCount();
            return super.toString() +
                "[Read locks = " + r + "]";
        }
    }

    /**
     * 写锁
     */
    public static class WriteLock implements Lock, java.io.Serializable {
        private static final long serialVersionUID = -4992448646407690164L;
        private final Sync sync;

        /**
         * Constructor for use by subclasses
         *
         * @param lock the outer lock object
         * @throws NullPointerException if the lock is null
         */
        protected WriteLock(ReentrantReadWriteLock lock) {
            sync = lock.sync;
        }

        /**
         *
         */
        public void lock() {
            sync.acquire(1);
        }

        /**
         *
         */
        public void lockInterruptibly() throws InterruptedException {
            sync.acquireInterruptibly(1);
        }

        /**
         *
         */
        public boolean tryLock( ) {
            return sync.tryWriteLock();
        }

        /**
         *
         */
        public boolean tryLock(long timeout, TimeUnit unit)
                throws InterruptedException {
            return sync.tryAcquireNanos(1, unit.toNanos(timeout));
        }

        /**
         *
         */
        public void unlock() {
            sync.release(1);
        }

        /**
         *
         */
        public Condition newCondition() {
            return sync.newCondition();
        }

        /**
         *
         */
        public String toString() {
            Thread o = sync.getOwner();
            return super.toString() + ((o == null) ?
                                       "[Unlocked]" :
                                       "[Locked by thread " + o.getName() + "]");
        }

        /**
         *
         */
        public boolean isHeldByCurrentThread() {
            return sync.isHeldExclusively();
        }

        /**
         *
         */
        public int getHoldCount() {
            return sync.getWriteHoldCount();
        }
    }

    // Instrumentation and status

    /**
     * Returns {@code true} if this lock has fairness set true.
     *
     * @return {@code true} if this lock has fairness set true
     */
    public final boolean isFair() {
        return sync instanceof FairSync;
    }

    /**
     * Returns the thread that currently owns the write lock, or
     * {@code null} if not owned. When this method is called by a
     * thread that is not the owner, the return value reflects a
     * best-effort approximation of current lock status. For example,
     * the owner may be momentarily {@code null} even if there are
     * threads trying to acquire the lock but have not yet done so.
     * This method is designed to facilitate construction of
     * subclasses that provide more extensive lock monitoring
     * facilities.
     *
     * @return the owner, or {@code null} if not owned
     */
    protected Thread getOwner() {
        return sync.getOwner();
    }

    /**
     * Queries the number of read locks held for this lock. This
     * method is designed for use in monitoring system state, not for
     * synchronization control.
     * @return the number of read locks held
     */
    public int getReadLockCount() {
        return sync.getReadLockCount();
    }

    /**
     * Queries if the write lock is held by any thread. This method is
     * designed for use in monitoring system state, not for
     * synchronization control.
     *
     * @return {@code true} if any thread holds the write lock and
     *         {@code false} otherwise
     */
    public boolean isWriteLocked() {
        return sync.isWriteLocked();
    }

    /**
     * Queries if the write lock is held by the current thread.
     *
     * @return {@code true} if the current thread holds the write lock and
     *         {@code false} otherwise
     */
    public boolean isWriteLockedByCurrentThread() {
        return sync.isHeldExclusively();
    }

    /**
     * Queries the number of reentrant write holds on this lock by the
     * current thread.  A writer thread has a hold on a lock for
     * each lock action that is not matched by an unlock action.
     *
     * @return the number of holds on the write lock by the current thread,
     *         or zero if the write lock is not held by the current thread
     */
    public int getWriteHoldCount() {
        return sync.getWriteHoldCount();
    }

    /**
     * Queries the number of reentrant read holds on this lock by the
     * current thread.  A reader thread has a hold on a lock for
     * each lock action that is not matched by an unlock action.
     *
     * @return the number of holds on the read lock by the current thread,
     *         or zero if the read lock is not held by the current thread
     * @since 1.6
     */
    public int getReadHoldCount() {
        return sync.getReadHoldCount();
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire the write lock.  Because the actual set of threads may
     * change dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive lock monitoring facilities.
     *
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedWriterThreads() {
        return sync.getExclusiveQueuedThreads();
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire the read lock.  Because the actual set of threads may
     * change dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive lock monitoring facilities.
     *
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedReaderThreads() {
        return sync.getSharedQueuedThreads();
    }

    /**
     * Queries whether any threads are waiting to acquire the read or
     * write lock. Note that because cancellations may occur at any
     * time, a {@code true} return does not guarantee that any other
     * thread will ever acquire a lock.  This method is designed
     * primarily for use in monitoring of the system state.
     *
     * @return {@code true} if there may be other threads waiting to
     *         acquire the lock
     */
    public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    /**
     * Queries whether the given thread is waiting to acquire either
     * the read or write lock. Note that because cancellations may
     * occur at any time, a {@code true} return does not guarantee
     * that this thread will ever acquire a lock.  This method is
     * designed primarily for use in monitoring of the system state.
     *
     * @param thread the thread
     * @return {@code true} if the given thread is queued waiting for this lock
     * @throws NullPointerException if the thread is null
     */
    public final boolean hasQueuedThread(Thread thread) {
        return sync.isQueued(thread);
    }

    /**
     * Returns an estimate of the number of threads waiting to acquire
     * either the read or write lock.  The value is only an estimate
     * because the number of threads may change dynamically while this
     * method traverses internal data structures.  This method is
     * designed for use in monitoring of the system state, not for
     * synchronization control.
     *
     * @return the estimated number of threads waiting for this lock
     */
    public final int getQueueLength() {
        return sync.getQueueLength();
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire either the read or write lock.  Because the actual set
     * of threads may change dynamically while constructing this
     * result, the returned collection is only a best-effort estimate.
     * The elements of the returned collection are in no particular
     * order.  This method is designed to facilitate construction of
     * subclasses that provide more extensive monitoring facilities.
     *
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }

    /**
     * Queries whether any threads are waiting on the given condition
     * associated with the write lock. Note that because timeouts and
     * interrupts may occur at any time, a {@code true} return does
     * not guarantee that a future {@code signal} will awaken any
     * threads.  This method is designed primarily for use in
     * monitoring of the system state.
     *
     * @param condition the condition
     * @return {@code true} if there are any waiting threads
     * @throws IllegalMonitorStateException if this lock is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this lock
     * @throws NullPointerException if the condition is null
     */
    public boolean hasWaiters(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.hasWaiters((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     * Returns an estimate of the number of threads waiting on the
     * given condition associated with the write lock. Note that because
     * timeouts and interrupts may occur at any time, the estimate
     * serves only as an upper bound on the actual number of waiters.
     * This method is designed for use in monitoring of the system
     * state, not for synchronization control.
     *
     * @param condition the condition
     * @return the estimated number of waiting threads
     * @throws IllegalMonitorStateException if this lock is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this lock
     * @throws NullPointerException if the condition is null
     */
    public int getWaitQueueLength(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitQueueLength((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     * Returns a collection containing those threads that may be
     * waiting on the given condition associated with the write lock.
     * Because the actual set of threads may change dynamically while
     * constructing this result, the returned collection is only a
     * best-effort estimate. The elements of the returned collection
     * are in no particular order.  This method is designed to
     * facilitate construction of subclasses that provide more
     * extensive condition monitoring facilities.
     *
     * @param condition the condition
     * @return the collection of threads
     * @throws IllegalMonitorStateException if this lock is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this lock
     * @throws NullPointerException if the condition is null
     */
    protected Collection<Thread> getWaitingThreads(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitingThreads((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     * Returns a string identifying this lock, as well as its lock state.
     * The state, in brackets, includes the String {@code "Write locks ="}
     * followed by the number of reentrantly held write locks, and the
     * String {@code "Read locks ="} followed by the number of held
     * read locks.
     *
     * @return a string identifying this lock, as well as its lock state
     */
    public String toString() {
        int c = sync.getCount();
        int w = Sync.exclusiveCount(c);
        int r = Sync.sharedCount(c);

        return super.toString() +
            "[Write locks = " + w + ", Read locks = " + r + "]";
    }

    /**
     * Returns the thread id for the given thread.  We must access
     * this directly rather than via method Thread.getId() because
     * getId() is not final, and has been known to be overridden in
     * ways that do not preserve unique mappings.
     */
    static final long getThreadId(Thread thread) {
        return UNSAFE.getLongVolatile(thread, TID_OFFSET);
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long TID_OFFSET;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> tk = Thread.class;
            TID_OFFSET = UNSAFE.objectFieldOffset
                (tk.getDeclaredField("tid"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
