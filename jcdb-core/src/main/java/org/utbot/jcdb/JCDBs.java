package org.utbot.jcdb;

import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import org.jetbrains.annotations.NotNull;
import org.utbot.jcdb.api.JCDB;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class JCDBs {

    private final JCDBSettings settings;

    private JCDBs(JCDBSettings settings) {
        this.settings = settings;
    }

    /**
     * create {@link JCDB} instance with default settings
     *
     @return not null {@link JCDB} instance or throws {@link IllegalStateException}
     */
    public static JCDB newDefault() {
        return new JCDBs(new JCDBSettings()).build();
    }

    /**
     * create instance based on settings
     *
     * @param settings jcdb settings
     * @return not null {@link JCDB} instance or throws {@link IllegalStateException}
     */
    public static JCDB newFrom(JCDBSettings settings) {
        return new JCDBs(settings).build();
    }

    public JCDB build() {
        AtomicReference<Object> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        JcdbKt.jcdb(settings, new Continuation<JCDB>() {

            @Override
            public void resumeWith(@NotNull Object out) {
                result.compareAndSet(null, out);
                latch.countDown();
            }

            @NotNull
            @Override
            public CoroutineContext getContext() {
                return EmptyCoroutineContext.INSTANCE;
            }
        });
        try {
            latch.await();
        } catch (Exception ignored) {
        }
        Object r = result.get();
        if (r instanceof JCDB) {
            return (JCDB) r;
        } else if (r instanceof Throwable) {
            throw new IllegalStateException("Can't create jcdb instance", (Throwable) r);
        }
        throw new IllegalStateException("Can't create jcdb instance. Received result: " + r);
    }

}
