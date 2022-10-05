package org.utbot.jcdb;

import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import org.jetbrains.annotations.NotNull;
import org.utbot.jcdb.api.JCDB;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class JCDBs {

    /**
     * create {@link JCDB} instance with default settings
     *
     @return not null {@link JCDB} instance or throws {@link IllegalStateException}
     */
    public static JCDB newDefault() {
        return build(new JCDBSettings());
    }

    /**
     * create instance based on settings
     *
     * @param settings jcdb settings
     * @return not null {@link JCDB} instance or throws {@link IllegalStateException}
     */
    public static JCDB newFrom(JCDBSettings settings) {
        return build(settings);
    }

    private static JCDB build(JCDBSettings settings) {
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
