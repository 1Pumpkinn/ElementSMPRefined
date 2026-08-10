package hs.elementSMPRefined.elements.example;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.elements.ElementBuilder;
import hs.elementSMPRefined.elements.Element;
import hs.elementSMPRefined.elements.ElementType;
import hs.elementSMPRefined.elements.abilities.Ability;
import hs.elementSMPRefined.elements.abilities.impl.ExampleAbility;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * Example demonstrating how easy it is to create new elements using the new ElementBuilder API.
 * This shows the improved, data-driven approach to element creation.
 */
public class ExampleNewElement {
    /**
     * Create a new element using the builder pattern
     * This is much cleaner than extending BaseElement and implementing all methods manually
     */
    public static Element createLightningElement(ElementSMPRefined plugin) {
        Ability lightningStrike = new ExampleAbility(plugin); // You'd implement this

        return new ElementBuilder(plugin)
                .type(ElementType.AIR) // You'd add LIGHTNING to ElementType enum
                .displayName("Lightning")
                .description("Masters of storms and electricity. Lightning users can strike enemies with bolts of lightning and move with incredible speed.")
                .color(ChatColor.YELLOW)
                .isBasic(false)
                .passiveEffect(PotionEffectType.SPEED, 1) // Permanent Speed I
                .passiveEffect((player, level) -> {
                    // Custom passive effect - deals lightning damage to attackers
                    // This could be implemented in a listener
                })
                .ability1(lightningStrike, "Lightning Strike", "Strike a target with a bolt of lightning")
                .ability2(lightningStrike, "Thunder Storm", "Create a storm of lightning in an area")
                .clearEffect(PotionEffectType.SPEED)
                .build();
    }

    /**
     * Alternative: Create element with the @RegisterElement annotation
     * This enables automatic discovery and registration
     */
    /*
    @RegisterElement(
        value = ElementType.LIGHTNING,
        isBasic = false,
        displayName = "Lightning",
        description = "Masters of storms and electricity",
        color = "YELLOW"
    )
    public class LightningElement extends BaseElement {
        public LightningElement(ElementSMPRefined plugin) {
            super(plugin);
        }

        @Override
        public ElementType getType() {
            return ElementType.LIGHTNING;
        }

        @Override
        public void applyUpsides(Player player, int upgradeLevel) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                PotionEffectType.SPEED, Integer.MAX_VALUE, 1, true, false
            ));
        }

        @Override
        protected boolean executeAbility1(ElementContext context) {
            // Implement lightning strike ability
            return true;
        }

        @Override
        protected boolean executeAbility2(ElementContext context) {
            // Implement thunder storm ability
            return true;
        }

        @Override
        public void clearEffects(Player player) {
            player.removePotionEffect(PotionEffectType.SPEED);
        }

        @Override
        public String getDisplayName() {
            return ChatColor.YELLOW + "Lightning";
        }

        @Override
        public String getDescription() {
            return ChatColor.GRAY + "Masters of storms and electricity";
        }

        @Override
        public String getAbility1Name() {
            return "Lightning Strike";
        }

        @Override
        public String getAbility1Description() {
            return "Strike a target with a bolt of lightning";
        }

        @Override
        public String getAbility2Name() {
            return "Thunder Storm";
        }

        @Override
        public String getAbility2Description() {
            return "Create a storm of lightning in an area";
        }
    }
    */
}