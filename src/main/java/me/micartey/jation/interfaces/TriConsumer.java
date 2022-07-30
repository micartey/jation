package me.micartey.jation.interfaces;

public interface TriConsumer<R, T, V, U> {

    R accept(T t, V v, U u);

}
