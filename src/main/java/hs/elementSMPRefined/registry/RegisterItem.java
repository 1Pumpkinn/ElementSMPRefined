package hs.elementSMPRefined.registry;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark item classes for automatic registration.
 * Use this annotation on item classes to enable automatic discovery and registration.
 */
@Target(java.lang.annotation.ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RegisterItem {
    /**
     * The unique ID for this item
     */
    String id();

    /**
     * The display name for this item
     */
    String displayName();

    /**
     * The description for this item
     */
    String description() default "";

    /**
     * Whether this item is consumable
     */
    boolean isConsumable() default false;

    /**
     * The maximum stack size for this item
     */
    int maxStackSize() default 64;

    /**
     * Whether this item requires permission to use
     */
    boolean requiresPermission() default false;
}