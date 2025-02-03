package org.sagacity.sqltoy.callback;

@FunctionalInterface
public interface ThreeBiConsumer<T1, T2, T3> {

    void accept(T1 t, T2 t2, T3 t3);
}
