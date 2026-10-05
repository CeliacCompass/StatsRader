public class awv extends axu {
    public awv() { }
    public awv(String text) { a.text = text; }
    protected avw a = new avw();
    public int forwarded, typed;
    protected void a(char character, int key) {
        if (key == 28 || key == 156) { forwarded++; ave.A().a((axu) null); }
        else typed++;
    }
    public void submit(String message, int key) { a.text = message; a('\r', key); }
}
