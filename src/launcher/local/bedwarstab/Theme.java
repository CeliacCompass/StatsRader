package local.bedwarstab;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Shared desktop palette; all controls retain native Swing keyboard/accessibility behavior. */
final class Theme {
    static final Color BG = new Color(0x070D16), CARD = new Color(0x101C2A), INPUT = new Color(0x09131F);
    static final Color LINE = new Color(0x22364A), TEXT = new Color(0xECF5FF), MUTED = new Color(0x92A9BF);
    static final Color CYAN = new Color(0x00D6EE), BLUE = new Color(0x007AFF);
    static final Font BODY = new Font("Segoe UI", Font.PLAIN, 13);
    static BufferedImage logo() {
        try { return ImageIO.read(Theme.class.getResourceAsStream("/statsrader-logo.png")); }
        catch (Exception e) { throw new IllegalStateException("StatsRader-Logo fehlt im Programmpaket", e); }
    }
    static void install() {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) { }
        for (String key : new String[]{"Label", "Button", "TextField", "PasswordField", "ComboBox", "CheckBox", "TextArea", "OptionPane"}) {
            UIManager.put(key + ".font", BODY); UIManager.put(key + ".foreground", TEXT); UIManager.put(key + ".background", CARD);
        }
        UIManager.put("Panel.background", BG); UIManager.put("OptionPane.background", BG);
        UIManager.put("ToolTip.background", CARD); UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ComboBox.selectionBackground", LINE); UIManager.put("ComboBox.selectionForeground", TEXT);
        UIManager.put("ScrollBar.thumb", LINE); UIManager.put("ScrollBar.track", INPUT);
        UIManager.put("ScrollBar.width", 10);
    }
    static JPanel plain(LayoutManager layout) { JPanel p = new JPanel(layout); p.setOpaque(false); return p; }
    static JLabel label(String text, int size, Color color, boolean bold) {
        JLabel label = new JLabel(text); label.setFont(BODY.deriveFont(bold ? Font.BOLD : Font.PLAIN, (float)size)); label.setForeground(color); return label;
    }
    static JPanel column() { JPanel p = plain(null); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); return p; }
    static void add(JPanel column, JComponent child) { child.setAlignmentX(Component.LEFT_ALIGNMENT); column.add(child); }
    static void gap(JPanel p, int height) { p.add(Box.createVerticalStrut(height)); }
    static JPanel card() {
        JPanel p = new Surface(CARD); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); p.setBorder(new EmptyBorder(20, 22, 20, 22)); return p;
    }
    static JPanel field(String title, JComponent input) {
        JPanel p = plain(new BorderLayout(0, 8)); JLabel name = label(title, 12, MUTED, true); name.setLabelFor(input);
        input.getAccessibleContext().setAccessibleName(title);
        p.add(name, BorderLayout.NORTH); p.add(input); p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 69)); return p;
    }
    static void input(JTextField field) {
        field.setBackground(INPUT); field.setForeground(TEXT); field.setCaretColor(CYAN); field.setSelectionColor(new Color(0x155278));
        field.setFont(BODY); field.setPreferredSize(new Dimension(80, 40));
        field.setBorder(new CompoundBorder(new LineBorder(LINE, 1, true), new EmptyBorder(8, 12, 8, 12)));
        field.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) { field.setBorder(new CompoundBorder(new LineBorder(CYAN, 1, true), new EmptyBorder(8,12,8,12))); }
            public void focusLost(FocusEvent e) { field.setBorder(new CompoundBorder(new LineBorder(LINE, 1, true), new EmptyBorder(8,12,8,12))); }
        });
    }
    static void combo(JComboBox<?> combo) {
        combo.setBackground(INPUT); combo.setForeground(TEXT); combo.setFont(BODY); combo.setPreferredSize(new Dimension(120, 40));
        combo.setBorder(new LineBorder(LINE, 1, true));
        combo.setUI(new BasicComboBoxUI() {
            protected JButton createArrowButton() {
                JButton b = new javax.swing.plaf.basic.BasicArrowButton(SwingConstants.SOUTH, INPUT, INPUT, CYAN, INPUT);
                b.setBorder(new EmptyBorder(0,0,0,0)); b.setPreferredSize(new Dimension(32,32)); return b;
            }
        });
        combo.setRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value == null ? "Minecraft noch nicht gestartet" : value, index, selected, focus);
                setBackground(selected ? LINE : INPUT); setForeground(TEXT); setBorder(new EmptyBorder(7, 10, 7, 10)); return this;
            }
        });
    }
    static final class Canvas extends JPanel implements Scrollable {
        Canvas() { super(new BorderLayout(22,20)); }
        public Dimension getPreferredScrollableViewportSize() { return new Dimension(1000,760); }
        public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) { return 24; }
        public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) { return 150; }
        public boolean getScrollableTracksViewportWidth() { return getParent().getWidth() >= 900; }
        public boolean getScrollableTracksViewportHeight() { return getParent().getHeight() >= 740; }
        public Dimension getPreferredSize() { return new Dimension(1000,760); }
    }
    static final class Surface extends JPanel {
        private final Color color;
        Surface(Color color) { this.color = color; setOpaque(false); }
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D)graphics.create(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color); g.fillRoundRect(0,0,getWidth()-1,getHeight()-1,20,20);
            g.setColor(LINE); g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,20,20); g.dispose(); super.paintComponent(graphics);
        }
    }
    static final class ActionButton extends JButton {
        private final boolean primary;
        ActionButton(String text, boolean primary) {
            super(text); this.primary = primary; setFont(BODY.deriveFont(Font.BOLD));
            setForeground(primary ? BG : TEXT); setBorder(new EmptyBorder(11,17,11,17));
            setContentAreaFilled(false); setFocusPainted(false); setOpaque(false); setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D)graphics.create(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (!isEnabled()) g.setColor(LINE);
            else if (primary) g.setPaint(new GradientPaint(0,0, getModel().isRollover() ? new Color(0x6DF0FF) : CYAN, getWidth(),getHeight(),new Color(0x009DDB)));
            else g.setColor(getModel().isRollover() ? new Color(0x233C52) : new Color(0x172A3C));
            g.fillRoundRect(0,0,getWidth(),getHeight(),12,12);
            if (hasFocus()) { g.setColor(Color.WHITE); g.drawRoundRect(2,2,getWidth()-5,getHeight()-5,10,10); }
            g.dispose(); super.paintComponent(graphics);
        }
    }
    static final class LogoPanel extends JPanel {
        private final BufferedImage image = logo();
        LogoPanel() { setOpaque(false); setPreferredSize(new Dimension(242, 250)); setMaximumSize(new Dimension(Integer.MAX_VALUE, 250)); }
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics); Graphics2D g = (Graphics2D)graphics.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int size = Math.min(getWidth(),getHeight()); g.drawImage(image,(getWidth()-size)/2,0,size,size,null); g.dispose();
        }
    }
}
