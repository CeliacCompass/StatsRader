package org.lwjgl.opengl;

/** Records the lighting state the stats label leaves behind. */
public class GL11 {
    public static boolean lighting;
    public static boolean glIsEnabled(int capability) { return capability == 2896 && lighting; }
}
