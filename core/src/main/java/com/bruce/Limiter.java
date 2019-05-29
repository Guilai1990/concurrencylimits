package com.bruce;

import java.util.Optional;

/**
 * @Author: Bruce
 * @Date: 2019/5/27 19:41
 * @Version 1.0
 */
public interface Limiter<ContextT> {

    interface Listener {
        void onSuccess();

        void onIgnore();

        void onDropped();
    }

    Optional<Listener> acquire(ContextT context);
}
