package hs.elementSMPRefined.registry;

import hs.elementSMPRefined.elements.ElementType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark element classes for automatic registration.
 * Use this annotation on element classes to enable automatic discovery and registration.
 */
@Target(java.lang.annotation.ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RegisterElement {
    /**
     * The element type this class represents
     */
    ElementType value();

    /**
     * Whether this is a basic element (can be rolled initially)
     */
    boolean isBasic() default false;

    /**
     * The display name for this element
     */
    String displayName();

    /**
     * The description for this element
     */
    String description();

    /**
     * The color code for this element (e.g., "RED", "BLUE", "GOLD")
     */
    String color() default "WHITE";
}