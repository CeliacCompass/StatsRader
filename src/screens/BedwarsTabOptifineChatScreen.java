import local.bedwarstab.bridge.Bridge;

/** Keep OptiFine's reload commands and chat behavior while handling our local menu. */
public final class BedwarsTabOptifineChatScreen extends net.optifine.gui.GuiChatOF {
    public BedwarsTabOptifineChatScreen(awv previous) { super(previous); }
    protected void a(char character, int key) {
        if (!Bridge.handleChat(this, key)) super.a(character, key);
    }
}
