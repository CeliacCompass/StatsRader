import local.bedwarstab.bridge.Bridge;

public final class BedwarsTabChatScreen extends awv {
    public BedwarsTabChatScreen() { super(); }
    protected void a(char character, int key) {
        if (!Bridge.handleChat(this, key)) super.a(character, key);
    }
}
