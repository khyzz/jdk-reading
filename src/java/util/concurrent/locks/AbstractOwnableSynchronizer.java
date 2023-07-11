package java.util.concurrent.locks;

@SuppressWarnings("all")
public abstract class AbstractOwnableSynchronizer
    implements java.io.Serializable {

    /** Use serial ID even though all fields transient. */
    private static final long serialVersionUID = 3737899427754241961L;

    /**
     * 空参构造器
     */
    protected AbstractOwnableSynchronizer() { }

    /**
     * 独占锁的线程
     */
    private transient Thread exclusiveOwnerThread;

    /**
     * 设置获取锁的线程
     */
    protected final void setExclusiveOwnerThread(Thread thread) {
        exclusiveOwnerThread = thread;
    }

    /**
     * 返回独占锁的线程
     */
    protected final Thread getExclusiveOwnerThread() {
        return exclusiveOwnerThread;
    }
}
