package me.micartey.jation.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Distribution {

    Guarantee value();

    enum Guarantee {
        EXACTLY_ONCE,
        AT_LEAST_ONCE,
    }
}
