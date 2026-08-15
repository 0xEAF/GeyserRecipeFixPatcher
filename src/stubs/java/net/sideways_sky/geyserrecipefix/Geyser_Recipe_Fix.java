package net.sideways_sky.geyserrecipefix;

import java.util.logging.Logger;

/**
 * Compile-only stub, used ONLY to compile GeyserRecipeFixPatcher's injected
 * helper classes against.
 *
 * This is NOT part of Geyser Recipe Fix and is never packaged, bundled, or
 * shipped anywhere - it exists purely so javac can resolve
 * `Geyser_Recipe_Fix.instance` and `Geyser_Recipe_Fix.logger` by the exact
 * same field name + type the real class uses. The JVM links field/method
 * references by name and descriptor, not by which source produced the
 * class, so our injected classes work correctly against the real,
 * official Geyser_Recipe_Fix.class at runtime - this file is deleted from
 * the build output before packaging (see build.gradle) and reproduces none
 * of the original plugin's logic, only the two field declarations needed.
 */
public class Geyser_Recipe_Fix {
    public static Geyser_Recipe_Fix instance;
    public static Logger logger;
}
