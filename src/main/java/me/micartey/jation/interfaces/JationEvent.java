package me.micartey.jation.interfaces;

import me.micartey.jation.JationObserver;

public interface JationEvent<T extends JationEvent<T>> {

    default T publish(JationObserver observer, Object... additional) {
        observer.publish(this, additional);
        return (T) this;
    }

    default T publish(Object... additional) {
        return publish(JationObserver.DEFAULT_OBSERVER, additional);
    }

    default T publishAsync(JationObserver observer, Object... additional) {
        observer.publishAsync(this, additional);
        return (T) this;
    }

    default T publishAsync(Object... additional) {
        return publishAsync(JationObserver.DEFAULT_OBSERVER, additional);
    }
}
