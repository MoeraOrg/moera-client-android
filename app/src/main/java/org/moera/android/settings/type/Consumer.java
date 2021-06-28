package org.moera.android.settings.type;

// Replaces standard Consumer interface that requires API level 24
public interface Consumer<T> {

    void accept(T t);

}
