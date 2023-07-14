package java.util.concurrent.locks;
import java.util.concurrent.TimeUnit;
import java.util.Date;

@SuppressWarnings("all")
public interface Condition {

    /**
     * 等待
     */
    void await() throws InterruptedException;

    /**
     *
     */
    void awaitUninterruptibly();

    /**
     * 超时等待 ns
     */
    long awaitNanos(long nanosTimeout) throws InterruptedException;

    /**
     * 超时等待 ms
     */
    boolean await(long time, TimeUnit unit) throws InterruptedException;

    /**
     *
     */
    boolean awaitUntil(Date deadline) throws InterruptedException;

    /**
     * 唤醒
     */
    void signal();

    /**
     * 唤醒全部
     */
    void signalAll();
}
