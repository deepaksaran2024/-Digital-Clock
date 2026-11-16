/*
 * =====================================================================
 *  ENHANCED DIGITAL CLOCK  -  Smart Desktop Productivity Suite
 * =====================================================================
 *  100% pure Java: Swing + AWT + Graphics2D + java.time + javax.sound.
 *  No HTML, no CSS, no external libraries, no extra files needed.
 *
 *  Everything lives in this ONE file (DigitalClock.java) so it is easy to
 *  run in VS Code.  The file is organised in numbered sections:
 *
 *   1. Theme / Settings / Data storage / Sound / Ticker (core services)
 *   2. Ui helpers (fonts, icons, glow text, table styling)
 *   3. Custom graphical widgets (buttons, cards, rings, clocks ...)
 *   4. Timer models (stopwatch, countdown, pomodoro)
 *   5. Dialogs (popups, alarm editor, task editor)
 *   6. Pages (dashboard, clock, world, alarms, ... help)
 *   7. Full screen / screensaver / splash screen
 *   8. Main window (sidebar, navigation, shortcuts, master tick)
 * =====================================================================
 */
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.TextAttribute;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class DigitalClock extends JFrame {

    // =================================================================
    // 1A. THEME  (all colours of the application live here)
    // =================================================================
    static class Theme {
        final String name;
        final boolean dark;
        final Color bg1, bg2, side, card, cardHi, text, sub, accent, accent2, accent3, line;
        private final int[] raw;

        Theme(String name, boolean dark, int... v) {
            this.name = name; this.dark = dark; this.raw = v;
            bg1 = new Color(v[0]);   bg2 = new Color(v[1]);   side = new Color(v[2]);
            card = new Color(v[3]);  cardHi = new Color(v[4]);
            text = new Color(v[5]);  sub = new Color(v[6]);
            accent = new Color(v[7]); accent2 = new Color(v[8]); accent3 = new Color(v[9]);
            line = dark ? new Color(255, 255, 255, 30) : new Color(0, 0, 0, 34);
        }

        Theme withAccent(int rgb) { int[] v = raw.clone(); v[7] = rgb; return new Theme(name, dark, v); }

        // order: bg1, bg2, sidebar, card, cardHighlight, text, subtext, accent, accent2, accent3
        static final Theme[] ALL = {
            new Theme("Dark Navy",     true,  0x0A1024, 0x111B3D, 0x0C1330, 0x141E40, 0x1E2B55, 0xE8EEFF, 0x8E9BC2, 0x00E5FF, 0x8B5CF6, 0x3B82F6),
            new Theme("Light",         false, 0xEEF2FA, 0xDCE6F6, 0xF8FAFE, 0xFFFFFF, 0xE9EFFA, 0x18213A, 0x66728F, 0x0A84FF, 0x7C4DFF, 0x00A7B5),
            new Theme("Cyberpunk",     true,  0x0B0614, 0x1B0A30, 0x0E0818, 0x1A0F2E, 0x2A1648, 0xF4F0FF, 0xA38FCB, 0xFF2BD6, 0x00F0FF, 0xFCEE09),
            new Theme("Midnight Blue", true,  0x050B1E, 0x0B1A40, 0x060E26, 0x0E1D45, 0x17295E, 0xE6F0FF, 0x7F95C4, 0x4CC9F0, 0x4895EF, 0x4361EE),
            new Theme("Purple Neon",   true,  0x11061F, 0x25093F, 0x0F0519, 0x200F3C, 0x2E1859, 0xF6EEFF, 0xAE94D6, 0xC77DFF, 0x9D4EDD, 0xFF6AD5)
        };
        static Theme cur = ALL[0];

        static Theme byName(String n) {
            for (Theme t : ALL) if (t.name.equals(n)) return t;
            return ALL[0];
        }
        /** Re-computes the active theme from the saved settings. */
        static void apply() {
            Theme base = byName(Settings.theme);
            cur = Settings.customAccent ? base.withAccent(Settings.accentRgb) : base;
        }
    }

    // =================================================================
    // 1B. SETTINGS  (saved to ~/.enhanced-digital-clock/settings.properties)
    // =================================================================
    static class Settings {
        static String theme = "Dark Navy";
        static boolean customAccent = false;
        static int accentRgb = 0x00E5FF;
        static int fontSize = 1;          // 0 small, 1 medium, 2 large
        static int clockStyle = 0;        // 0 Neon, 1 Flip, 2 Minimal, 3 Retro
        static boolean sound = true, anim = true, h24 = false, chime = false, onTop = false;
        static int saver = 0;             // minutes of idle time before screensaver (0 = off)
        static int pomWork = 25, pomShort = 5, pomLong = 15;
        static String zone = "Asia/Kolkata";

        static float scale() { return fontSize == 0 ? 0.9f : fontSize == 2 ? 1.15f : 1f; }

        static ZoneId zoneId() {
            try { return ZoneId.of(zone); } catch (Exception e) { return ZoneId.of("Asia/Kolkata"); }
        }
        static File dir() {
            File d = new File(System.getProperty("user.home"), ".enhanced-digital-clock");
            if (!d.exists()) d.mkdirs();
            return d;
        }
        static int geti(Properties p, String k, int d, int lo, int hi) {
            try { return Math.max(lo, Math.min(hi, Integer.parseInt(p.getProperty(k, "" + d)))); }
            catch (NumberFormatException e) { return d; }
        }
        static void load() {
            File f = new File(dir(), "settings.properties");
            if (!f.exists()) return;
            Properties p = new Properties();
            try (InputStream in = new FileInputStream(f)) { p.load(in); } catch (IOException e) { return; }
            theme = p.getProperty("theme", theme);
            customAccent = Boolean.parseBoolean(p.getProperty("customAccent", "false"));
            accentRgb = geti(p, "accentRgb", accentRgb, 0, 0xFFFFFF);
            fontSize = geti(p, "fontSize", 1, 0, 2);
            clockStyle = geti(p, "clockStyle", 0, 0, 3);
            sound = Boolean.parseBoolean(p.getProperty("sound", "true"));
            anim = Boolean.parseBoolean(p.getProperty("anim", "true"));
            h24 = Boolean.parseBoolean(p.getProperty("h24", "false"));
            chime = Boolean.parseBoolean(p.getProperty("chime", "false"));
            onTop = Boolean.parseBoolean(p.getProperty("onTop", "false"));
            saver = geti(p, "saver", 0, 0, 60);
            pomWork = geti(p, "pomWork", 25, 1, 180);
            pomShort = geti(p, "pomShort", 5, 1, 60);
            pomLong = geti(p, "pomLong", 15, 1, 120);
            zone = p.getProperty("zone", zone);
        }
        static void save() {
            Properties p = new Properties();
            p.setProperty("theme", theme); p.setProperty("customAccent", "" + customAccent);
            p.setProperty("accentRgb", "" + accentRgb); p.setProperty("fontSize", "" + fontSize);
            p.setProperty("clockStyle", "" + clockStyle); p.setProperty("sound", "" + sound);
            p.setProperty("anim", "" + anim); p.setProperty("h24", "" + h24);
            p.setProperty("chime", "" + chime); p.setProperty("onTop", "" + onTop);
            p.setProperty("saver", "" + saver); p.setProperty("pomWork", "" + pomWork);
            p.setProperty("pomShort", "" + pomShort); p.setProperty("pomLong", "" + pomLong);
            p.setProperty("zone", zone);
            try (OutputStream out = new FileOutputStream(new File(dir(), "settings.properties"))) {
                p.store(out, "Enhanced Digital Clock settings");
            } catch (IOException e) { System.err.println("Could not save settings: " + e.getMessage()); }
        }
    }

    // =================================================================
    // 1C. DATA MODELS + FILE STORAGE (plain text, tab separated)
    // =================================================================
    static class Alarm {
        static final String[] REPEAT = {"Once", "Daily", "Weekdays", "Weekends", "Custom"};
        static final String[] DAYS = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        String name = "Alarm", desc = "";
        LocalTime time = LocalTime.of(7, 0);
        LocalDate date = LocalDate.now();
        int repeat = 0;          // index into REPEAT
        int days = 0;            // bit mask for Custom (bit0 = Monday ... bit6 = Sunday)
        boolean enabled = true;
        int sound = 0;           // index into Sound.NAMES
        String zone = "Asia/Kolkata";
        LocalDate lastFired;     // prevents ringing twice on the same day
        ZonedDateTime snoozeUntil;   // not saved

        ZoneId zoneId() { try { return ZoneId.of(zone); } catch (Exception e) { return ZoneId.systemDefault(); } }

        boolean matchesDay(LocalDate d) {
            int dow = d.getDayOfWeek().getValue();   // 1 = Monday ... 7 = Sunday
            switch (repeat) {
                case 0: return d.equals(date);
                case 1: return true;
                case 2: return dow <= 5;
                case 3: return dow >= 6;
                default: return (days & (1 << (dow - 1))) != 0;
            }
        }
        /** True during the first 60 seconds after the alarm time (checked every second). */
        boolean shouldFire(ZonedDateTime now) {
            if (!enabled) return false;
            ZonedDateTime n = now.withZoneSameInstant(zoneId());
            LocalDate today = n.toLocalDate();
            if (today.equals(lastFired) || !matchesDay(today)) return false;
            long diff = Duration.between(ZonedDateTime.of(today, time, zoneId()), n).getSeconds();
            return diff >= 0 && diff < 60;
        }
        /** Next moment this alarm will ring (including a pending snooze), or null. */
        ZonedDateTime next(ZonedDateTime now) {
            ZonedDateTime best = null;
            if (enabled) {
                ZoneId z = zoneId();
                ZonedDateTime n = now.withZoneSameInstant(z);
                for (int i = 0; i <= 8 && best == null; i++) {
                    LocalDate d = n.toLocalDate().plusDays(i);
                    if (!matchesDay(d)) continue;
                    ZonedDateTime t = ZonedDateTime.of(d, time, z);
                    if (t.isAfter(n)) best = t;
                }
            }
            if (snoozeUntil != null && (best == null || snoozeUntil.isBefore(best))) best = snoozeUntil;
            return best;
        }
        String repeatText() {
            if (repeat == 0) return date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH));
            if (repeat != 4) return REPEAT[repeat];
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 7; i++) if ((days & (1 << i)) != 0) sb.append(sb.length() > 0 ? "," : "").append(DAYS[i]);
            return sb.length() == 0 ? "Custom (none)" : sb.toString();
        }
        Alarm copy() { return fromLine(toLine()); }
        String toLine() {
            return String.join("\t", Store.esc(name), Store.esc(desc), time.toString(), date.toString(), "" + repeat,
                "" + days, "" + enabled, "" + sound, Store.esc(zone), lastFired == null ? "" : lastFired.toString());
        }
        static Alarm fromLine(String line) {
            try {
                String[] p = line.split("\t", -1);
                if (p.length < 9) return null;
                Alarm a = new Alarm();
                a.name = Store.unesc(p[0]); a.desc = Store.unesc(p[1]);
                a.time = LocalTime.parse(p[2]); a.date = LocalDate.parse(p[3]);
                a.repeat = Integer.parseInt(p[4]); a.days = Integer.parseInt(p[5]);
                a.enabled = Boolean.parseBoolean(p[6]); a.sound = Integer.parseInt(p[7]);
                a.zone = Store.unesc(p[8]);
                if (p.length > 9 && !p[9].isEmpty()) a.lastFired = LocalDate.parse(p[9]);
                return a;
            } catch (RuntimeException e) { return null; }
        }
    }

    static class Task {
        static final String[] PRI = {"High", "Medium", "Low"};
        static final String[] CATS = {"General", "Study", "Work", "Personal", "Health", "Shopping"};
        String title = "", cat = "General";
        int pri = 1;
        LocalDateTime due;
        boolean done;
        LocalDate created = LocalDate.now(), doneOn;

        String toLine() {
            return String.join("\t", Store.esc(title), Store.esc(cat), "" + pri, due == null ? "" : due.toString(),
                "" + done, created.toString(), doneOn == null ? "" : doneOn.toString());
        }
        static Task fromLine(String line) {
            try {
                String[] p = line.split("\t", -1);
                if (p.length < 6) return null;
                Task t = new Task();
                t.title = Store.unesc(p[0]); t.cat = Store.unesc(p[1]); t.pri = Integer.parseInt(p[2]);
                t.due = p[3].isEmpty() ? null : LocalDateTime.parse(p[3]);
                t.done = Boolean.parseBoolean(p[4]); t.created = LocalDate.parse(p[5]);
                if (p.length > 6 && !p[6].isEmpty()) t.doneOn = LocalDate.parse(p[6]);
                return t;
            } catch (RuntimeException e) { return null; }
        }
    }

    static class Store {
        static final ArrayList<Alarm> alarms = new ArrayList<>();
        static final ArrayList<Task> tasks = new ArrayList<>();
        /** Pomodoro statistics: date -> {sessions, minutes} */
        static final TreeMap<String, int[]> focus = new TreeMap<>();

        static File file(String n) { return new File(Settings.dir(), n); }
        static String esc(String s) {
            StringBuilder b = new StringBuilder();
            for (char c : s.toCharArray()) {
                if (c == '\\') b.append("\\\\"); else if (c == '\t') b.append("\\t");
                else if (c == '\n') b.append("\\n"); else if (c != '\r') b.append(c);
            }
            return b.toString();
        }
        static String unesc(String s) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '\\' && i + 1 < s.length()) { char n = s.charAt(++i); b.append(n == 't' ? '\t' : n == 'n' ? '\n' : n); }
                else b.append(c);
            }
            return b.toString();
        }
        static List<String> read(String name) {
            File f = file(name);
            if (!f.exists()) return null;
            try { return Files.readAllLines(f.toPath(), StandardCharsets.UTF_8); } catch (IOException e) { return null; }
        }
        static void write(String name, List<String> lines) {
            try { Files.write(file(name).toPath(), lines, StandardCharsets.UTF_8); }
            catch (IOException e) { System.err.println("Could not save " + name + ": " + e.getMessage()); }
        }

        static void load() {
            List<String> l = read("alarms.txt");
            if (l == null) {                                   // first run: one example alarm (switched off)
                Alarm a = new Alarm(); a.name = "Morning alarm"; a.desc = "Example alarm - switch it on!";
                a.repeat = 2; a.enabled = false; a.zone = Settings.zone; alarms.add(a);
            } else for (String s : l) { Alarm a = Alarm.fromLine(s); if (a != null) alarms.add(a); }
            l = read("tasks.txt");
            if (l == null) {                                   // first run: a few example tasks
                String[][] demo = {{"Explore the dashboard", "General", "1"}, {"Plan study session", "Study", "0"}, {"Finish project report", "Work", "1"}};
                for (String[] d : demo) {
                    Task t = new Task(); t.title = d[0]; t.cat = d[1]; t.pri = Integer.parseInt(d[2]); tasks.add(t);
                }
            } else for (String s : l) { Task t = Task.fromLine(s); if (t != null) tasks.add(t); }
            l = read("stats.txt");
            if (l != null) for (String s : l) {
                String[] p = s.split("\t");
                try { if (p.length == 3) focus.put(p[0], new int[]{Integer.parseInt(p[1]), Integer.parseInt(p[2])}); }
                catch (NumberFormatException ignored) { }
            }
        }
        static void saveAlarms() {
            alarms.sort(Comparator.comparing((Alarm a) -> a.time).thenComparing(a -> a.name));
            List<String> l = new ArrayList<>();
            for (Alarm a : alarms) l.add(a.toLine());
            write("alarms.txt", l);
        }
        static void saveTasks() {
            List<String> l = new ArrayList<>();
            for (Task t : tasks) l.add(t.toLine());
            write("tasks.txt", l);
        }
        static void saveStats() {
            List<String> l = new ArrayList<>();
            for (Map.Entry<String, int[]> e : focus.entrySet()) l.add(e.getKey() + "\t" + e.getValue()[0] + "\t" + e.getValue()[1]);
            write("stats.txt", l);
        }
        static void saveAll() { Settings.save(); saveAlarms(); saveTasks(); saveStats(); }

        static int[] day(LocalDate d) { int[] v = focus.get(d.toString()); return v == null ? new int[]{0, 0} : v; }
        static void addFocus(int minutes) {
            int[] v = focus.computeIfAbsent(LocalDate.now().toString(), k -> new int[2]);
            v[0]++; v[1] += minutes;
            saveStats();
        }
        /** {done, total} for "today's tasks" = tasks due today or without a due date. */
        static int[] todayStats() {
            LocalDate today = LocalDate.now(); int done = 0, total = 0;
            for (Task t : tasks) {
                boolean todays = t.due == null ? !t.done || today.equals(t.doneOn) : t.due.toLocalDate().equals(today);
                if (todays) { total++; if (t.done) done++; }
            }
            return new int[]{done, total};
        }
        static int doneToday() {
            int n = 0; LocalDate today = LocalDate.now();
            for (Task t : tasks) if (t.done && today.equals(t.doneOn)) n++;
            return n;
        }
        /** The next n alarms as {Alarm, ZonedDateTime}, soonest first. */
        static List<Object[]> upcoming(int n) {
            ZonedDateTime now = ZonedDateTime.now();
            List<Object[]> r = new ArrayList<>();
            for (Alarm a : alarms) { ZonedDateTime x = a.next(now); if (x != null) r.add(new Object[]{a, x}); }
            r.sort((p, q) -> ((ZonedDateTime) p[1]).toInstant().compareTo(((ZonedDateTime) q[1]).toInstant()));
            return r.size() > n ? new ArrayList<>(r.subList(0, n)) : r;
        }
    }

    // =================================================================
    // 1D. SOUND  (tones are generated in code - no audio files needed)
    // =================================================================
    static class Sound {
        static final String[] NAMES = {"Classic Beep", "Digital Pulse", "Soft Chime", "Siren", "Melody"};
        private static volatile int gen = 0;

        static void play(int idx, boolean loop, boolean force) {
            if (!Settings.sound && !force) return;
            final int my = ++gen;
            final int s = Math.max(0, Math.min(NAMES.length - 1, idx));
            Thread th = new Thread(() -> {
                try { run(s, loop, my); }
                catch (Exception e) { if (gen == my) Toolkit.getDefaultToolkit().beep(); }
            }, "clock-sound");
            th.setDaemon(true);
            th.start();
        }
        static void stop() { gen++; }

        // each step: {startHz, endHz, milliseconds, silence after (ms)}
        private static double[][] pattern(int i) {
            switch (i) {
                case 0: return new double[][]{{880, 880, 180, 120}, {880, 880, 180, 120}, {880, 880, 180, 500}};
                case 1: return new double[][]{{1200, 1200, 90, 40}, {1600, 1600, 90, 40}, {1200, 1200, 90, 40}, {1600, 1600, 90, 450}};
                case 2: return new double[][]{{523, 523, 320, 20}, {659, 659, 320, 20}, {784, 784, 520, 600}};
                case 3: return new double[][]{{600, 1000, 450, 0}, {1000, 600, 450, 300}};
                default: return new double[][]{{523, 523, 160, 20}, {659, 659, 160, 20}, {784, 784, 160, 20}, {1047, 1047, 300, 60}, {784, 784, 160, 20}, {1047, 1047, 450, 600}};
            }
        }
        private static void run(int idx, boolean loop, int my) throws Exception {
            AudioFormat fmt = new AudioFormat(44100f, 16, 1, true, false);
            SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
            line.open(fmt);
            line.start();
            try {
                long t0 = System.currentTimeMillis();
                do {
                    for (double[] p : pattern(idx)) {
                        if (gen != my) return;
                        byte[] b = tone(p[0], p[1], (int) p[2], (int) p[3]);
                        line.write(b, 0, b.length);
                    }
                } while (loop && gen == my && System.currentTimeMillis() - t0 < 30000);
                if (gen == my) line.drain();
            } finally {
                line.stop();
                line.close();
            }
        }
        private static byte[] tone(double f0, double f1, int ms, int gap) {
            int rate = 44100, n = rate * ms / 1000, g = rate * gap / 1000;
            byte[] b = new byte[(n + g) * 2];
            double phase = 0;
            for (int i = 0; i < n; i++) {
                double f = f0 + (f1 - f0) * i / (double) n;
                phase += 2 * Math.PI * f / rate;
                double env = Math.min(1.0, Math.min(i / (rate * 0.01), (n - i) / (rate * 0.03)));
                short v = (short) (Math.sin(phase) * env * 0.4 * 32767);
                b[2 * i] = (byte) (v & 0xff);
                b[2 * i + 1] = (byte) ((v >> 8) & 0xff);
            }
            return b;
        }
    }

    // =================================================================
    // 1E. TICKER - the ONE master Swing Timer of the whole application
    // =================================================================
    static class Ticker {
        interface Tick { void tick(boolean secondChanged); }
        private static final ArrayList<Tick> list = new ArrayList<>();
        private static Timer timer;
        private static long lastSec = -1;

        static void add(Tick t) { if (!list.contains(t)) list.add(t); }
        static void remove(Tick t) { list.remove(t); }
        static void start() {
            if (timer != null) return;
            timer = new Timer(33, e -> {                       // about 30 frames per second
                long s = System.currentTimeMillis() / 1000;
                boolean sec = s != lastSec;
                lastSec = s;
                for (Tick t : new ArrayList<>(list)) {
                    try { t.tick(sec); } catch (RuntimeException ex) { ex.printStackTrace(); }
                }
            });
            timer.setCoalesce(true);
            timer.start();
        }
        static void stop() {
            if (timer != null) { timer.stop(); timer = null; }
            list.clear();
        }
    }

    // =================================================================
    // 2. UI HELPERS  (fonts, colours, icons, glow text, tables ...)
    // =================================================================
    /** Widgets that must refresh their font/colours when the theme or font size changes. */
    interface Styled { void restyle(); }

    static class Ui {
        static final String SANS = pick("Segoe UI", "SF Pro Display", "Helvetica Neue", "Ubuntu", "Noto Sans", "Open Sans", "Dialog");
        static final String MONO = pick("Consolas", "Menlo", "DejaVu Sans Mono", "Monospaced");

        static String pick(String... names) {
            HashSet<String> have = new HashSet<>();
            for (String f : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) have.add(f);
            for (String n : names) if (have.contains(n)) return n;
            return names[names.length - 1];
        }
        /** Font that follows the user's font-size setting. */
        static Font font(int style, float size) { return new Font(SANS, style, 12).deriveFont(size * Settings.scale()); }
        /** Font with an exact pixel size (used when text is fitted to available space). */
        static Font px(int style, float size) { return new Font(SANS, style, 12).deriveFont(size); }
        static Font mono(int style, float size) { return new Font(MONO, style, 12).deriveFont(size); }

        static void aa(Graphics2D g) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        }
        static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a))); }
        static Color mix(Color a, Color b, float t) {
            t = Math.max(0f, Math.min(1f, t));
            return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t), Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
        }
        static boolean isLight(Color c) { return 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue() > 150; }

        static void shadow(Graphics2D g, double x, double y, double w, double h, double arc) {
            int a = Theme.cur.dark ? 14 : 7;
            for (int i = 6; i >= 1; i--) {
                g.setColor(new Color(0, 0, 0, a));
                g.fill(new RoundRectangle2D.Double(x - i, y - i + 4, w + 2 * i, h + 2 * i, arc + 2 * i, arc + 2 * i));
            }
        }
        static void text(Graphics2D g, String s, double x, double y, Font f, Color c) {
            g.setFont(f); g.setColor(c); g.drawString(s, (float) x, (float) y);
        }
        /** Text centred horizontally on cx and vertically on cy. */
        static void textC(Graphics2D g, String s, double cx, double cy, Font f, Color c) {
            g.setFont(f); FontMetrics fm = g.getFontMetrics(); g.setColor(c);
            g.drawString(s, (float) (cx - fm.stringWidth(s) / 2.0), (float) (cy + (fm.getAscent() - fm.getDescent()) / 2.0));
        }
        /** Right aligned text; (rx, cy) is the right edge / vertical centre. */
        static void textR(Graphics2D g, String s, double rx, double cy, Font f, Color c) {
            g.setFont(f); FontMetrics fm = g.getFontMetrics(); g.setColor(c);
            g.drawString(s, (float) (rx - fm.stringWidth(s)), (float) (cy + (fm.getAscent() - fm.getDescent()) / 2.0));
        }
        /** Text on a baseline with a soft neon glow around it. */
        static void glowText(Graphics2D g, String s, double x, double y, Font f, Color c, int rad, float k) {
            g.setFont(f);
            for (int r = rad; r >= 1; r--) {
                g.setColor(alpha(c, (int) (16 * k + 22 * k / r)));
                for (int a = 0; a < 8; a++) {
                    double an = a * Math.PI / 4;
                    g.drawString(s, (float) (x + Math.cos(an) * r), (float) (y + Math.sin(an) * r));
                }
            }
            g.setColor(c); g.drawString(s, (float) x, (float) y);
        }
        /** Returns a copy of base whose size makes s fit into maxW pixels (never bigger than maxSize). */
        static Font fit(Graphics2D g, String s, Font base, float maxW, float maxSize) {
            FontMetrics fm = g.getFontMetrics(base.deriveFont(100f));
            float sz = Math.min(maxSize, 100f * maxW / Math.max(1, fm.stringWidth(s)));
            return base.deriveFont(Math.max(6f, sz));
        }
        static String countdown(Duration d) {
            long s = Math.max(0, d.getSeconds()), days = s / 86400; s %= 86400;
            long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
            return days > 0 ? String.format("%dd %02dh %02dm", days, h, m) : String.format("%02d:%02d:%02d", h, m, sec);
        }
        static String fmtMs(long ms, boolean hundredths) {
            long h = ms / 3600000, m = (ms / 60000) % 60, s = (ms / 1000) % 60, f = ms % 1000;
            String frac = hundredths ? String.format("%02d", f / 10) : String.format("%03d", f);
            return h > 0 ? String.format("%02d:%02d:%02d.%s", h, m, s, frac) : String.format("%02d:%02d.%s", m, s, frac);
        }

        /** Circular progress ring (used by Ring component and several dashboard cards). */
        static void ring(Graphics2D g, double cx, double cy, double r, float sw, double value, Color c1, Color c2, boolean ticks) {
            Theme t = Theme.cur; Stroke old = g.getStroke();
            g.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(alpha(t.text, t.dark ? 26 : 32));
            g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
            if (ticks) {
                g.setStroke(new BasicStroke(1.5f));
                double r1 = r - sw * 1.2, r2 = r1 - Math.max(4, sw * 0.45);
                for (int i = 0; i < 60; i++) {
                    double a = i * Math.PI / 30, sn = Math.sin(a), cs = Math.cos(a), rr = i % 5 == 0 ? r2 - 5 : r2;
                    g.setColor(alpha(t.text, i % 5 == 0 ? 120 : 55));
                    g.draw(new Line2D.Double(cx + sn * r1, cy - cs * r1, cx + sn * rr, cy - cs * rr));
                }
            }
            value = Math.max(0, Math.min(1, value));
            if (value > 0.0005) {
                Arc2D arc = new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, 90, -360 * value, Arc2D.OPEN);
                for (int i = 3; i >= 1; i--) {
                    g.setStroke(new BasicStroke(sw + i * 4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.setColor(alpha(c1, 16)); g.draw(arc);
                }
                g.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setPaint(new GradientPaint((float) (cx - r), (float) (cy - r), c1, (float) (cx + r), (float) (cy + r), c2));
                g.draw(arc);
                double ang = Math.toRadians(90 - 360 * value), kx = cx + Math.cos(ang) * r, ky = cy - Math.sin(ang) * r;
                g.setColor(Color.WHITE);
                g.fill(new Ellipse2D.Double(kx - sw * 0.3, ky - sw * 0.3, sw * 0.6, sw * 0.6));
            }
            g.setStroke(old);
        }

        // ---------------- vector icons (drawn with Graphics2D) ----------------
        static final int HOME = 0, CLOCK = 1, WORLD = 2, ALARM = 3, STOPWATCH = 4, TIMER = 5, POMO = 6, TODO = 7, CAL = 8,
            GEAR = 9, HELP = 10, PLAY = 11, PAUSE = 12, RESET = 13, FLAG = 14, PLUS = 15, TRASH = 16, EDIT = 17,
            FULL = 18, SUN = 19, CLOUD = 20, MOON = 21, CHECK = 22, SKIP = 23, EXPORT = 24;

        static void icon(Graphics2D g0, int type, double cx, double cy, double s, Color col) {
            if (type < 0) return;
            Graphics2D g = (Graphics2D) g0.create(); aa(g); g.setColor(col);
            float sw = (float) Math.max(1.5, s * 0.09);
            g.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double x = cx - s / 2, y = cy - s / 2;
            switch (type) {
                case HOME: {
                    Path2D roof = new Path2D.Double(); roof.moveTo(x + s * .08, y + s * .5); roof.lineTo(cx, y + s * .12); roof.lineTo(x + s * .92, y + s * .5); g.draw(roof);
                    Path2D body = new Path2D.Double(); body.moveTo(x + s * .2, y + s * .44); body.lineTo(x + s * .2, y + s * .88);
                    body.lineTo(x + s * .8, y + s * .88); body.lineTo(x + s * .8, y + s * .44); g.draw(body);
                    g.draw(new Rectangle2D.Double(x + s * .42, y + s * .62, s * .16, s * .26)); break;
                }
                case CLOCK:
                    g.draw(new Ellipse2D.Double(x + s * .1, y + s * .1, s * .8, s * .8));
                    g.draw(new Line2D.Double(cx, cy, cx, y + s * .27)); g.draw(new Line2D.Double(cx, cy, x + s * .7, cy + s * .08)); break;
                case WORLD:
                    g.draw(new Ellipse2D.Double(x + s * .1, y + s * .1, s * .8, s * .8));
                    g.draw(new Ellipse2D.Double(x + s * .32, y + s * .1, s * .36, s * .8));
                    g.draw(new Line2D.Double(x + s * .1, cy, x + s * .9, cy)); break;
                case ALARM: {
                    Path2D b = new Path2D.Double(); b.moveTo(x + s * .18, y + s * .74);
                    b.curveTo(x + s * .26, y + s * .6, x + s * .26, y + s * .45, x + s * .3, y + s * .36);
                    b.curveTo(x + s * .35, y + s * .12, x + s * .65, y + s * .12, x + s * .7, y + s * .36);
                    b.curveTo(x + s * .74, y + s * .45, x + s * .74, y + s * .6, x + s * .82, y + s * .74); b.closePath(); g.draw(b);
                    g.draw(new Arc2D.Double(cx - s * .1, y + s * .78, s * .2, s * .16, 180, 180, Arc2D.OPEN)); break;
                }
                case STOPWATCH:
                    g.draw(new Ellipse2D.Double(x + s * .14, y + s * .24, s * .72, s * .72));
                    g.draw(new Line2D.Double(cx - s * .1, y + s * .08, cx + s * .1, y + s * .08)); g.draw(new Line2D.Double(cx, y + s * .08, cx, y + s * .24));
                    g.draw(new Line2D.Double(cx, y + s * .6, cx + s * .16, y + s * .46)); break;
                case TIMER: {
                    Path2D p = new Path2D.Double(); p.moveTo(x + s * .25, y + s * .1); p.lineTo(x + s * .75, y + s * .1); p.lineTo(cx, cy);
                    p.lineTo(x + s * .75, y + s * .9); p.lineTo(x + s * .25, y + s * .9); p.lineTo(cx, cy); p.closePath(); g.draw(p); break;
                }
                case POMO:
                    g.draw(new Ellipse2D.Double(x + s * .12, y + s * .26, s * .76, s * .68));
                    g.draw(new Line2D.Double(cx, y + s * .27, cx - s * .14, y + s * .1)); g.draw(new Line2D.Double(cx, y + s * .27, cx + s * .16, y + s * .1));
                    g.draw(new Line2D.Double(cx - s * .16, cy + s * .08, cx - s * .04, cy + s * .2)); break;
                case TODO: {
                    g.draw(new RoundRectangle2D.Double(x + s * .16, y + s * .12, s * .68, s * .78, s * .16, s * .16));
                    Path2D c = new Path2D.Double(); c.moveTo(x + s * .32, y + s * .5); c.lineTo(x + s * .44, y + s * .62); c.lineTo(x + s * .68, y + s * .36); g.draw(c);
                    g.draw(new Line2D.Double(x + s * .34, y + s * .78, x + s * .66, y + s * .78)); break;
                }
                case CAL:
                    g.draw(new RoundRectangle2D.Double(x + s * .12, y + s * .2, s * .76, s * .68, s * .14, s * .14));
                    g.draw(new Line2D.Double(x + s * .12, y + s * .4, x + s * .88, y + s * .4));
                    g.draw(new Line2D.Double(x + s * .32, y + s * .1, x + s * .32, y + s * .28)); g.draw(new Line2D.Double(x + s * .68, y + s * .1, x + s * .68, y + s * .28));
                    g.fill(new Ellipse2D.Double(cx - s * .05, cy + s * .08, s * .1, s * .1)); break;
                case GEAR:
                    g.draw(new Ellipse2D.Double(x + s * .32, y + s * .32, s * .36, s * .36));
                    for (int i = 0; i < 8; i++) {
                        double a = i * Math.PI / 4;
                        g.draw(new Line2D.Double(cx + Math.cos(a) * s * .3, cy + Math.sin(a) * s * .3, cx + Math.cos(a) * s * .44, cy + Math.sin(a) * s * .44));
                    }
                    g.draw(new Ellipse2D.Double(x + s * .2, y + s * .2, s * .6, s * .6)); break;
                case HELP:
                    g.draw(new Ellipse2D.Double(x + s * .1, y + s * .1, s * .8, s * .8));
                    textC(g, "?", cx, cy + s * .02, new Font(SANS, Font.BOLD, Math.max(8, (int) (s * .55))), col); break;
                case PLAY: {
                    Path2D p = new Path2D.Double(); p.moveTo(x + s * .3, y + s * .16); p.lineTo(x + s * .84, cy); p.lineTo(x + s * .3, y + s * .84); p.closePath(); g.fill(p); break;
                }
                case PAUSE:
                    g.fill(new RoundRectangle2D.Double(x + s * .22, y + s * .16, s * .2, s * .68, 4, 4));
                    g.fill(new RoundRectangle2D.Double(x + s * .58, y + s * .16, s * .2, s * .68, 4, 4)); break;
                case RESET: {
                    g.draw(new Arc2D.Double(x + s * .16, y + s * .16, s * .68, s * .68, 70, 290, Arc2D.OPEN));
                    double px = cx + Math.cos(Math.toRadians(70)) * s * .34, py = cy - Math.sin(Math.toRadians(70)) * s * .34;
                    g.draw(new Line2D.Double(px, py, px + s * .2, py - s * .02)); g.draw(new Line2D.Double(px, py, px + s * .02, py - s * .22)); break;
                }
                case FLAG: {
                    g.draw(new Line2D.Double(x + s * .26, y + s * .1, x + s * .26, y + s * .9));
                    Path2D p = new Path2D.Double(); p.moveTo(x + s * .26, y + s * .14); p.lineTo(x + s * .84, y + s * .3); p.lineTo(x + s * .26, y + s * .52); p.closePath(); g.fill(p); break;
                }
                case PLUS: g.draw(new Line2D.Double(cx, y + s * .2, cx, y + s * .8)); g.draw(new Line2D.Double(x + s * .2, cy, x + s * .8, cy)); break;
                case TRASH: {
                    g.draw(new Line2D.Double(x + s * .18, y + s * .28, x + s * .82, y + s * .28));
                    Path2D h = new Path2D.Double(); h.moveTo(x + s * .4, y + s * .28); h.lineTo(x + s * .4, y + s * .14); h.lineTo(x + s * .6, y + s * .14); h.lineTo(x + s * .6, y + s * .28); g.draw(h);
                    Path2D b = new Path2D.Double(); b.moveTo(x + s * .26, y + s * .28); b.lineTo(x + s * .3, y + s * .88); b.lineTo(x + s * .7, y + s * .88); b.lineTo(x + s * .74, y + s * .28); g.draw(b); break;
                }
                case EDIT:
                    g.setStroke(new BasicStroke(sw * 2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(new Line2D.Double(x + s * .34, y + s * .66, x + s * .74, y + s * .26));
                    g.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(new Line2D.Double(x + s * .16, y + s * .86, x + s * .3, y + s * .7)); break;
                case FULL: {
                    double a = s * .14, b = s * .38;
                    for (int i = 0; i < 4; i++) {
                        double px = (i % 2 == 0) ? x + a : x + s - a, py = (i < 2) ? y + a : y + s - a, dx = (i % 2 == 0) ? 1 : -1, dy = (i < 2) ? 1 : -1;
                        g.draw(new Line2D.Double(px, py, px + dx * (b - a), py)); g.draw(new Line2D.Double(px, py, px, py + dy * (b - a)));
                    }
                    break;
                }
                case SUN:
                    g.fill(new Ellipse2D.Double(x + s * .3, y + s * .3, s * .4, s * .4));
                    for (int i = 0; i < 8; i++) {
                        double a = i * Math.PI / 4;
                        g.draw(new Line2D.Double(cx + Math.cos(a) * s * .32, cy + Math.sin(a) * s * .32, cx + Math.cos(a) * s * .46, cy + Math.sin(a) * s * .46));
                    }
                    break;
                case CLOUD: {
                    Area a = new Area(new Ellipse2D.Double(x + s * .08, y + s * .4, s * .4, s * .4));
                    a.add(new Area(new Ellipse2D.Double(x + s * .28, y + s * .2, s * .46, s * .5)));
                    a.add(new Area(new Ellipse2D.Double(x + s * .52, y + s * .4, s * .4, s * .4)));
                    a.add(new Area(new Rectangle2D.Double(x + s * .28, y + s * .52, s * .42, s * .28))); g.fill(a); break;
                }
                case MOON: {
                    Area a = new Area(new Ellipse2D.Double(x + s * .15, y + s * .12, s * .7, s * .7));
                    a.subtract(new Area(new Ellipse2D.Double(x + s * .38, y + s * .02, s * .6, s * .6))); g.fill(a); break;
                }
                case CHECK: {
                    Path2D c = new Path2D.Double(); c.moveTo(x + s * .2, y + s * .52); c.lineTo(x + s * .42, y + s * .74); c.lineTo(x + s * .8, y + s * .28); g.draw(c); break;
                }
                case SKIP: {
                    Path2D p = new Path2D.Double(); p.moveTo(x + s * .18, y + s * .2); p.lineTo(x + s * .66, cy); p.lineTo(x + s * .18, y + s * .8); p.closePath(); g.fill(p);
                    g.fill(new RoundRectangle2D.Double(x + s * .72, y + s * .2, s * .12, s * .6, 3, 3)); break;
                }
                case EXPORT:
                    g.draw(new Line2D.Double(cx, y + s * .12, cx, y + s * .62));
                    g.draw(new Line2D.Double(cx - s * .16, y + s * .28, cx, y + s * .12)); g.draw(new Line2D.Double(cx + s * .16, y + s * .28, cx, y + s * .12));
                    g.draw(new Line2D.Double(x + s * .2, y + s * .62, x + s * .2, y + s * .88)); g.draw(new Line2D.Double(x + s * .8, y + s * .62, x + s * .8, y + s * .88));
                    g.draw(new Line2D.Double(x + s * .2, y + s * .88, x + s * .8, y + s * .88)); break;
                default: break;
            }
            g.dispose();
        }

        /** Animated weather picture (kind: 0 partly cloudy, 1 sunny, 2 rain). */
        static void weatherIcon(Graphics2D g, int kind, double cx, double cy, double s) {
            long ms = System.currentTimeMillis();
            Color sun = new Color(255, 196, 61), cloud = Theme.cur.dark ? new Color(190, 205, 235) : new Color(150, 170, 205);
            if (kind == 0 || kind == 1) {
                double sx = kind == 1 ? cx : cx - s * .16, sy = kind == 1 ? cy : cy - s * .14, r = s * (kind == 1 ? .2 : .17);
                double rot = Settings.anim ? (ms % 20000) / 20000.0 * Math.PI * 2 : 0;
                for (int i = 0; i < 12; i++) {
                    double a = rot + i * Math.PI / 6;
                    g.setStroke(new BasicStroke((float) (s * .028), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.setColor(alpha(sun, 190));
                    g.draw(new Line2D.Double(sx + Math.cos(a) * r * 1.35, sy + Math.sin(a) * r * 1.35, sx + Math.cos(a) * r * 1.75, sy + Math.sin(a) * r * 1.75));
                }
                g.setColor(alpha(sun, 60)); g.fill(new Ellipse2D.Double(sx - r * 1.3, sy - r * 1.3, r * 2.6, r * 2.6));
                g.setColor(sun); g.fill(new Ellipse2D.Double(sx - r, sy - r, r * 2, r * 2));
            }
            if (kind == 0 || kind == 2) {
                double bob = Settings.anim ? Math.sin(ms / 900.0) * s * .015 : 0, x = cx - s * .32, y = cy - s * .1 + bob + (kind == 2 ? -s * .06 : s * .08);
                Area a = new Area(new Ellipse2D.Double(x, y + s * .2, s * .3, s * .3));
                a.add(new Area(new Ellipse2D.Double(x + s * .14, y, s * .36, s * .4)));
                a.add(new Area(new Ellipse2D.Double(x + s * .34, y + s * .18, s * .32, s * .32)));
                a.add(new Area(new Rectangle2D.Double(x + s * .14, y + s * .3, s * .38, s * .2)));
                g.setColor(cloud); g.fill(a);
            }
            if (kind == 2) {
                g.setStroke(new BasicStroke((float) (s * .028), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(90, 170, 255));
                for (int i = 0; i < 5; i++) {
                    double dx = cx - s * .26 + i * s * .13, ph = ((ms / (Settings.anim ? 9.0 : 1e12)) + i * 41) % 60 / 60.0, dy = cy + s * .12 + ph * s * .3;
                    g.draw(new Line2D.Double(dx, dy, dx - s * .03, dy + s * .08));
                }
            }
        }

        /** Last 7 days of focus minutes as a bar chart. */
        static void weekly(Graphics2D g, int w, int h) {
            Theme t = Theme.cur; LocalDate today = LocalDate.now(); int[] v = new int[7]; int max = 30;
            for (int i = 0; i < 7; i++) { v[i] = Store.day(today.minusDays(6 - i))[1]; max = Math.max(max, v[i]); }
            double bw = w / 7.0, base = h - 18, top = 16;
            g.setColor(alpha(t.text, 22)); g.setStroke(new BasicStroke(1f)); g.draw(new Line2D.Double(0, base, w, base));
            for (int i = 0; i < 7; i++) {
                double bh = (base - top) * v[i] / (double) max, x = i * bw + bw * .2, bwid = bw * .6;
                if (v[i] > 0) {
                    g.setPaint(new GradientPaint(0, (float) (base - bh), t.accent, 0, (float) base, t.accent2));
                    g.fill(new RoundRectangle2D.Double(x, base - bh, bwid, bh, 8, 8));
                    textC(g, "" + v[i], x + bwid / 2, base - bh - 8, px(Font.BOLD, 10), t.sub);
                } else {
                    g.setColor(alpha(t.text, 30)); g.fill(new RoundRectangle2D.Double(x, base - 4, bwid, 4, 3, 3));
                }
                String d = i == 6 ? "Today" : today.minusDays(6 - i).getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                textC(g, d, i * bw + bw / 2, h - 6, px(i == 6 ? Font.BOLD : Font.PLAIN, 10), i == 6 ? t.accent : t.sub);
            }
        }

        // ---------------- label / scroll / table helpers ----------------
        static JLabel label(String text, float size, boolean bold, String role) {
            JLabel l = new JLabel(text);
            l.putClientProperty("fs", size); l.putClientProperty("fb", bold); l.putClientProperty("role", role);
            styleLabel(l);
            return l;
        }
        static void styleLabel(JLabel l) {
            Object fs = l.getClientProperty("fs");
            if (fs == null) return;
            l.setFont(font(Boolean.TRUE.equals(l.getClientProperty("fb")) ? Font.BOLD : Font.PLAIN, (Float) fs));
            Object role = l.getClientProperty("role");
            l.setForeground("sub".equals(role) ? Theme.cur.sub : "accent".equals(role) ? Theme.cur.accent : Theme.cur.text);
        }
        static JScrollPane scroll(Component c) {
            JScrollPane sp = new JScrollPane(c);
            sp.setBorder(null); sp.setOpaque(false); sp.getViewport().setOpaque(false);
            sp.getVerticalScrollBar().setUI(new SlimScrollUI()); sp.getHorizontalScrollBar().setUI(new SlimScrollUI());
            sp.getVerticalScrollBar().setOpaque(false); sp.getHorizontalScrollBar().setOpaque(false);
            sp.getVerticalScrollBar().setUnitIncrement(18);
            if (c instanceof JTable) {                       // show the table header and keep it transparent
                sp.setColumnHeaderView(((JTable) c).getTableHeader());
                sp.getColumnHeader().setOpaque(false);
            }
            return sp;
        }
        static JTable table(AbstractTableModel m) {
            JTable tb = new JTable(m);
            tb.setOpaque(false); tb.setShowGrid(false); tb.setIntercellSpacing(new Dimension(0, 0));
            tb.setFillsViewportHeight(true); tb.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            tb.setRowHeight(Math.round(36 * Settings.scale()));
            JTableHeader h = tb.getTableHeader();
            h.setOpaque(false); h.setReorderingAllowed(false); h.setDefaultRenderer(new HeaderCell());
            tb.setDefaultRenderer(Object.class, new Cell());
            return tb;
        }
        static Color rowBg(int row, boolean sel) {
            Theme t = Theme.cur;
            if (sel) return mix(t.card, t.accent, 0.28f);
            return row % 2 == 1 ? mix(t.card, t.text, 0.045f) : t.card;
        }
        static class Cell extends DefaultTableCellRenderer {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, v, false, false, row, col);
                setOpaque(true); setBackground(rowBg(row, sel)); setForeground(Theme.cur.text);
                setFont(font(Font.PLAIN, 14)); setBorder(new EmptyBorder(0, 12, 0, 12));
                return this;
            }
        }
        static class HeaderCell extends DefaultTableCellRenderer {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, v, false, false, row, col);
                setOpaque(true); setBackground(mix(Theme.cur.card, Theme.cur.text, 0.09f)); setForeground(Theme.cur.sub);
                setFont(font(Font.BOLD, 12)); setBorder(new EmptyBorder(0, 12, 0, 12));
                return this;
            }
        }

        /** Re-applies fonts/colours to every standard component below c (called when theme / font size changes). */
        static void restyle(Component c) {
            Theme t = Theme.cur;
            if (c instanceof JLabel) styleLabel((JLabel) c);
            else if (c instanceof JTextArea) { c.setFont(font(Font.PLAIN, 14)); c.setForeground(t.text); }
            else if (c instanceof JTable) {
                JTable tb = (JTable) c; tb.setRowHeight(Math.round(36 * Settings.scale()));
                tb.getTableHeader().setFont(font(Font.BOLD, 12)); tb.setFont(font(Font.PLAIN, 14));
            }
            if (c instanceof Styled) ((Styled) c).restyle();
            if (c instanceof Container) for (Component ch : ((Container) c).getComponents()) restyle(ch);
        }

        static Image appIcon() {
            BufferedImage im = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = im.createGraphics(); aa(g);
            g.setPaint(new GradientPaint(0, 0, new Color(0x111B3D), 64, 64, new Color(0x3B1D7A)));
            g.fill(new RoundRectangle2D.Double(2, 2, 60, 60, 18, 18));
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(0x00E5FF)); g.draw(new Ellipse2D.Double(12, 12, 40, 40));
            g.draw(new Line2D.Double(32, 32, 32, 19)); g.setColor(new Color(0xC77DFF)); g.draw(new Line2D.Double(32, 32, 42, 38));
            g.dispose();
            return im;
        }
    }

    /** Thin rounded scroll bars that follow the theme. */
    static class SlimScrollUI extends BasicScrollBarUI {
        private JButton zero() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0)); b.setMinimumSize(new Dimension(0, 0)); b.setMaximumSize(new Dimension(0, 0));
            return b;
        }
        @Override protected JButton createDecreaseButton(int o) { return zero(); }
        @Override protected JButton createIncreaseButton(int o) { return zero(); }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) { }
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = (Graphics2D) g.create(); Ui.aa(g2);
            g2.setColor(Ui.alpha(Theme.cur.text, isThumbRollover() ? 120 : 75));
            g2.fill(new RoundRectangle2D.Double(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 8, 8));
            g2.dispose();
        }
        @Override public Dimension getPreferredSize(JComponent c) { return new Dimension(11, 11); }
    }

    // =================================================================
    // 3. CUSTOM GRAPHICAL WIDGETS  (all painted with Graphics2D)
    // =================================================================

    /** Rounded card with shadow, optional title/icon and an optional custom painter. */
    static class Card extends JPanel {
        interface Painter { void paint(Graphics2D g, int w, int h); }
        String title, right = "";
        int icon, reserve;
        Painter painter;
        Runnable click;
        boolean hover;

        Card(String title, int icon) {
            this.title = title; this.icon = icon;
            setOpaque(false); setLayout(new BorderLayout());
            setBorder(new EmptyBorder(title == null ? 20 : 50, 22, 20, 22));
        }
        Card painter(Painter p) { painter = p; return this; }
        Card reserve(int r) { reserve = r; return this; }
        Card onClick(Runnable r) {
            click = r; setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) { if (click != null) click.run(); }
            });
            return this;
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); Ui.aa(g);
            Theme t = Theme.cur; int w = getWidth(), h = getHeight();
            double bx = 6, by = 4, bw = w - 12, bh = h - 12;
            Ui.shadow(g, bx, by, bw, bh, 22);
            RoundRectangle2D.Double shape = new RoundRectangle2D.Double(bx, by, bw, bh, 22, 22);
            g.setPaint(new GradientPaint(0, 0, Ui.mix(t.card, t.cardHi, hover ? 0.7f : 0.35f), 0, h, t.card));
            g.fill(shape);
            g.setColor(hover ? Ui.alpha(t.accent, 150) : t.line); g.setStroke(new BasicStroke(1.2f)); g.draw(shape);
            if (title != null) {
                Ui.icon(g, icon, bx + 28, by + 24, 17, t.accent);
                Ui.text(g, title.toUpperCase(), bx + 44, by + 29, Ui.font(Font.BOLD, 12), t.sub);
                if (!right.isEmpty()) Ui.textR(g, right, bx + bw - 18, by + 24, Ui.font(Font.PLAIN, 12), t.sub);
            }
            if (painter != null) {
                Insets in = getInsets();
                int pw = w - in.left - in.right, ph = h - in.top - in.bottom - reserve;
                if (pw > 0 && ph > 0) {
                    Graphics2D pg = (Graphics2D) g.create(in.left, in.top, pw, ph);
                    Ui.aa(pg);
                    try { painter.paint(pg, pw, ph); } finally { pg.dispose(); }
                }
            }
            g.dispose();
        }
    }

    /** Rounded button with icon, hover animation and several looks. */
    static class GButton extends JComponent {
        static final int NORMAL = 0, PRIMARY = 1, NAV = 2, DANGER = 3, GHOST = 4;
        String text; int icon, kind;
        boolean selected, over, down;
        float hover;
        final Timer anim;
        final ArrayList<ActionListener> listeners = new ArrayList<>();

        GButton(String text, int icon, int kind) {
            this.text = text; this.icon = icon; this.kind = kind;
            setOpaque(false); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            anim = new Timer(16, e -> {
                float target = over ? 1f : 0f;
                hover += (target - hover) * 0.3f;
                if (Math.abs(target - hover) < 0.03f) { hover = target; ((Timer) e.getSource()).stop(); }
                repaint();
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { if (isEnabled()) { over = true; kick(); } }
                @Override public void mouseExited(MouseEvent e) { over = false; down = false; kick(); }
                @Override public void mousePressed(MouseEvent e) { if (isEnabled()) { down = true; repaint(); } }
                @Override public void mouseReleased(MouseEvent e) {
                    boolean fire = down && over && isEnabled();
                    down = false; repaint();
                    if (fire) fire();
                }
            });
        }
        GButton(String text, int icon) { this(text, icon, NORMAL); }

        void kick() {
            if (Settings.anim) { if (!anim.isRunning()) anim.start(); } else { hover = over ? 1f : 0f; repaint(); }
        }
        void fire() {
            ActionEvent ev = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, text == null ? "" : text);
            for (ActionListener l : new ArrayList<>(listeners)) l.actionPerformed(ev);
        }
        GButton onClick(Runnable r) { listeners.add(e -> r.run()); return this; }
        void setText(String s) { if (!java.util.Objects.equals(text, s)) { text = s; revalidate(); repaint(); } }
        void setIcon(int i) { if (icon != i) { icon = i; repaint(); } }
        void setSelected(boolean b) { if (selected != b) { selected = b; repaint(); } }

        float fs() { return kind == NAV ? 14f : 13f; }
        int isz() { return Math.round(18 * Settings.scale()); }
        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(Ui.font(Font.BOLD, fs()));
            int h = Math.round((kind == NAV ? 42 : 38) * Settings.scale()), w = 28;
            if (text == null) return new Dimension(h, h);
            if (icon >= 0) w += isz() + 8;
            return new Dimension(w + fm.stringWidth(text) + (kind == NAV ? 12 : 0), h);
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); Ui.aa(g);
            Theme t = Theme.cur; int w = getWidth(), h = getHeight();
            if (down) g.translate(0, 1);
            double arc = kind == NAV ? 14 : 16;
            RoundRectangle2D.Double shape = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, arc, arc);
            Color fg;
            switch (kind) {
                case PRIMARY: {
                    g.setPaint(new GradientPaint(0, 0, Ui.mix(t.accent, Color.WHITE, hover * .2f), w, h, Ui.mix(t.accent2, Color.WHITE, hover * .2f)));
                    g.fill(shape); fg = Ui.isLight(t.accent) ? new Color(10, 16, 32) : Color.WHITE; break;
                }
                case DANGER: {
                    g.setPaint(new GradientPaint(0, 0, Ui.mix(new Color(0xE5484D), Color.WHITE, hover * .2f), w, h, Ui.mix(new Color(0xFF7A59), Color.WHITE, hover * .2f)));
                    g.fill(shape); fg = Color.WHITE; break;
                }
                case NAV: {
                    if (selected) {
                        g.setColor(Ui.alpha(t.accent, 38)); g.fill(shape);
                        g.setColor(t.accent); g.fill(new RoundRectangle2D.Double(0, h * .2, 4, h * .6, 4, 4));
                        fg = t.accent;
                    } else {
                        if (hover > 0) { g.setColor(Ui.alpha(t.text, (int) (20 * hover))); g.fill(shape); }
                        fg = Ui.mix(t.sub, t.text, hover);
                    }
                    break;
                }
                case GHOST: {
                    if (hover > 0) { g.setColor(Ui.alpha(t.accent, (int) (36 * hover))); g.fill(shape); }
                    g.setColor(Ui.alpha(t.accent, 120)); g.setStroke(new BasicStroke(1.2f)); g.draw(shape); fg = t.accent; break;
                }
                default: {
                    g.setColor(Ui.mix(t.cardHi, t.text, 0.05f + 0.09f * hover)); g.fill(shape);
                    g.setColor(t.line); g.setStroke(new BasicStroke(1f)); g.draw(shape); fg = t.text;
                }
            }
            if (!isEnabled()) fg = Ui.alpha(fg, 90);
            int is = isz(); Font f = Ui.font(Font.BOLD, fs());
            FontMetrics fm = g.getFontMetrics(f);
            int tw = text == null ? 0 : fm.stringWidth(text);
            int total = (icon >= 0 ? is : 0) + (icon >= 0 && text != null ? 8 : 0) + tw;
            double x = kind == NAV ? 16 : (w - total) / 2.0;
            if (icon >= 0) { Ui.icon(g, icon, x + is / 2.0, h / 2.0, is, fg); x += is + 8; }
            if (text != null) {
                g.setFont(f); g.setColor(fg);
                g.drawString(text, (float) x, (float) (h / 2.0 + (fm.getAscent() - fm.getDescent()) / 2.0));
            }
            if (!isEnabled()) { g.setColor(Ui.alpha(t.card, 90)); g.fill(shape); }
            g.dispose();
        }
    }

    /** Numeric "-  value  +" selector (mouse click or wheel). */
    static class GStepper extends JComponent {
        int value, min, max, digits = 2, step = 1;
        boolean wrap;
        String suffix = "";
        Runnable onChange;

        GStepper(int value, int min, int max, boolean wrap) {
            this.value = value; this.min = min; this.max = max; this.wrap = wrap;
            setOpaque(false); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (!isEnabled()) return;
                    if (e.getX() < getHeight()) change(-step); else if (e.getX() > getWidth() - getHeight()) change(step);
                }
            });
            addMouseWheelListener(e -> { if (isEnabled()) change(-e.getWheelRotation() * step); });
        }
        GStepper digits(int d) { digits = d; return this; }
        GStepper suffix(String s) { suffix = s; return this; }
        GStepper onChange(Runnable r) { onChange = r; return this; }
        GStepper step(int s) { step = s; return this; }
        int getValue() { return value; }
        void setValue(int v) { value = Math.max(min, Math.min(max, v)); repaint(); }
        void change(int d) {
            int v = value + d;
            if (v > max) v = wrap ? min : max;
            if (v < min) v = wrap ? max : min;
            if (v != value) { value = v; repaint(); if (onChange != null) onChange.run(); }
        }
        @Override public Dimension getPreferredSize() { int h = Math.round(38 * Settings.scale()); return new Dimension(Math.round(112 * Settings.scale()), h); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); Ui.aa(g);
            Theme t = Theme.cur; int w = getWidth(), h = getHeight();
            RoundRectangle2D.Double shape = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 14, 14);
            g.setColor(Ui.mix(t.cardHi, t.text, 0.05f)); g.fill(shape);
            g.setColor(t.line); g.draw(shape);
            Color c = isEnabled() ? t.accent : Ui.alpha(t.sub, 100);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.setColor(c);
            double m = h / 2.0, a = h * 0.13;
            g.draw(new Line2D.Double(m - a, m, m + a, m));
            g.draw(new Line2D.Double(w - m - a, m, w - m + a, m)); g.draw(new Line2D.Double(w - m, m - a, w - m, m + a));
            String s = String.format("%0" + digits + "d", value) + suffix;
            Ui.textC(g, s, w / 2.0, h / 2.0, Ui.font(Font.BOLD, 16), isEnabled() ? t.text : t.sub);
            g.dispose();
        }
    }

    /** Segmented selector ("pills") for a small list of options. */
    static class GSegment extends JComponent {
        final String[] items; int sel; final IntConsumer onChange;
        GSegment(String[] items, int sel, IntConsumer onChange) {
            this.items = items; this.sel = sel; this.onChange = onChange;
            setOpaque(false); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    double[] x = segBounds();
                    for (int i = 0; i < items.length; i++) if (e.getX() >= x[i] && e.getX() < x[i + 1]) {
                        if (i != GSegment.this.sel) { GSegment.this.sel = i; repaint(); if (GSegment.this.onChange != null) GSegment.this.onChange.accept(i); }
                    }
                }
            });
        }
        void setSelected(int i) { sel = i; repaint(); }
        private int prefW(int i) { return getFontMetrics(Ui.font(Font.BOLD, 13)).stringWidth(items[i]) + 30; }
        double[] segBounds() {
            int total = 0; for (int i = 0; i < items.length; i++) total += prefW(i);
            double k = getWidth() > 0 ? (double) getWidth() / total : 1, x = 0; double[] b = new double[items.length + 1];
            for (int i = 0; i < items.length; i++) { b[i] = x; x += prefW(i) * k; }
            b[items.length] = x; return b;
        }
        @Override public Dimension getPreferredSize() {
            int total = 0; for (int i = 0; i < items.length; i++) total += prefW(i);
            return new Dimension(total + 6, Math.round(38 * Settings.scale()));
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); Ui.aa(g);
            Theme t = Theme.cur; int w = getWidth(), h = getHeight();
            RoundRectangle2D.Double shape = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, h * .5, h * .5);
            g.setColor(Ui.mix(t.cardHi, t.text, 0.04f)); g.fill(shape); g.setColor(t.line); g.draw(shape);
            double[] b = segBounds();
            for (int i = 0; i < items.length; i++) {
                if (i == sel) {
                    g.setPaint(new GradientPaint((float) b[i], 0, t.accent, (float) b[i + 1], h, t.accent2));
                    g.fill(new RoundRectangle2D.Double(b[i] + 3, 3, b[i + 1] - b[i] - 6, h - 6, h * .5 - 3, h * .5 - 3));
                }
                Color fg = i == sel ? (Ui.isLight(t.accent) ? new Color(10, 16, 32) : Color.WHITE) : t.sub;
                Ui.textC(g, items[i], (b[i] + b[i + 1]) / 2, h / 2.0, Ui.font(Font.BOLD, 13), fg);
            }
            g.dispose();
        }
    }

    /** Multi-select chips (used for the custom alarm weekdays). */
    static class GChips extends JComponent {
        final String[] items; final boolean[] on;
        GChips(String[] items) {
            this.items = items; on = new boolean[items.length];
            setOpaque(false); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    int i = (int) (e.getX() / (getWidth() / (double) GChips.this.items.length));
                    if (i >= 0 && i < on.length) { on[i] = !on[i]; repaint(); }
                }
            });
        }
        int mask() { int m = 0; for (int i = 0; i < on.length; i++) if (on[i]) m |= 1 << i; return m; }
        void setMask(int m) { for (int i = 0; i < on.length; i++) on[i] = (m & (1 << i)) != 0; repaint(); }
        @Override public Dimension getPreferredSize() { return new Dimension(380, Math.round(36 * Settings.scale())); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); Ui.aa(g); Theme t = Theme.cur;
            double cw = getWidth() / (double) items.length;
            for (int i = 0; i < items.length; i++) {
                RoundRectangle2D.Double r = new RoundRectangle2D.Double(i * cw + 2, 1, cw - 4, getHeight() - 2, 12, 12);
                if (on[i]) { g.setPaint(new GradientPaint(0, 0, t.accent, 0, getHeight(), t.accent2)); g.fill(r); }
                else { g.setColor(Ui.mix(t.cardHi, t.text, 0.04f)); g.fill(r); g.setColor(t.line); g.draw(r); }
                Ui.textC(g, items[i], i * cw + cw / 2, getHeight() / 2.0, Ui.font(Font.BOLD, 12), on[i] ? (Ui.isLight(t.accent) ? new Color(10, 16, 32) : Color.WHITE) : t.sub);
            }
            g.dispose();
        }
    }

    /** Animated on/off switch. */
    static class GSwitch extends JComponent {
        boolean on; float knob; final Timer anim; Consumer<Boolean> onChange;
        GSwitch(boolean on, Consumer<Boolean> onChange) {
            this.on = on; this.knob = on ? 1f : 0f; this.onChange = onChange;
            setOpaque(false); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            anim = new Timer(16, e -> {
                float tgt = this.on ? 1f : 0f; knob += (tgt - knob) * 0.35f;
                if (Math.abs(tgt - knob) < 0.04f) { knob = tgt; ((Timer) e.getSource()).stop(); }
                repaint();
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { toggle(); }
            });
        }
        void toggle() { setOn(!on); if (onChange != null) onChange.accept(on); }
        void setOn(boolean b) {
            on = b;
            if (Settings.anim) { if (!anim.isRunning()) anim.start(); } else { knob = b ? 1f : 0f; repaint(); }
        }
        @Override public Dimension getPreferredSize() { return new Dimension(52, 28); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); Ui.aa(g); Theme t = Theme.cur;
            int w = getWidth(), h = getHeight();
            RoundRectangle2D.Double r = new RoundRectangle2D.Double(1, 1, w - 2, h - 2, h - 2, h - 2);
            g.setPaint(new GradientPaint(0, 0, Ui.mix(Ui.mix(t.cardHi, t.text, .12f), t.accent, knob), w, 0, Ui.mix(Ui.mix(t.cardHi, t.text, .12f), t.accent2, knob)));
            g.fill(r); g.setColor(t.line); g.draw(r);
            double d = h - 8, x = 4 + knob * (w - 8 - d);
            g.setColor(Color.WHITE); g.fill(new Ellipse2D.Double(x, 4, d, d));
            g.dispose();
        }
    }

    /** Rounded text field. */
    static class GField extends JTextField implements Styled {
        String hint = "";
        GField(int cols, String hint) {
            super(cols); this.hint = hint;
            setOpaque(false); setBorder(new EmptyBorder(6, 14, 6, 14));
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }
                @Override public void focusLost(FocusEvent e) { repaint(); }
            });
            restyle();
        }
        @Override public void restyle() {
            Theme t = Theme.cur; setFont(Ui.font(Font.PLAIN, 14)); setForeground(t.text);
            setCaretColor(t.accent); setSelectionColor(Ui.alpha(t.accent, 110)); setSelectedTextColor(t.text);
        }
        @Override public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize(); d.height = Math.max(d.height, Math.round(38 * Settings.scale())); return d;
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); Ui.aa(g); Theme t = Theme.cur;
            RoundRectangle2D.Double r = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 14, 14);
            g.setColor(Ui.mix(t.cardHi, t.text, 0.04f)); g.fill(r);
            g.setColor(isFocusOwner() ? t.accent : t.line); g.setStroke(new BasicStroke(isFocusOwner() ? 1.6f : 1f)); g.draw(r);
            g.dispose();
            super.paintComponent(g0);
            if (getText().isEmpty() && !isFocusOwner() && !hint.isEmpty()) {
                Graphics2D h = (Graphics2D) g0.create(); Ui.aa(h);
                Ui.text(h, hint, 14, getHeight() / 2.0 + (h.getFontMetrics(getFont()).getAscent() - h.getFontMetrics(getFont()).getDescent()) / 2.0, getFont(), Ui.alpha(t.sub, 170));
                h.dispose();
            }
        }
    }

    /** Drop-down list that follows the theme. */
    static class GCombo<E> extends JComboBox<E> implements Styled {
        GCombo(E[] items) {
            super(items);
            setOpaque(false); setUI(new ComboUI()); setRenderer(new Rend()); setMaximumRowCount(10); restyle();
        }
        @Override public void restyle() { setFont(Ui.font(Font.PLAIN, 14)); setForeground(Theme.cur.text); setBackground(new Color(0, 0, 0, 0)); }
        @Override public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize(); d.height = Math.round(38 * Settings.scale()); d.width = Math.max(d.width, 140); return d;
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); Ui.aa(g); Theme t = Theme.cur;
            RoundRectangle2D.Double r = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 14, 14);
            g.setColor(Ui.mix(t.cardHi, t.text, 0.05f)); g.fill(r); g.setColor(t.line); g.draw(r);
            g.dispose();
            super.paintComponent(g0);
        }
        static class Rend extends DefaultListCellRenderer {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foc) {
                super.getListCellRendererComponent(l, v, i, false, false);
                Theme t = Theme.cur; setFont(Ui.font(Font.PLAIN, 14)); setBorder(new EmptyBorder(7, 12, 7, 12));
                if (i >= 0) { setOpaque(true); setBackground(sel ? Ui.mix(t.card, t.accent, 0.3f) : t.card); setForeground(sel ? t.accent : t.text); }
                else { setOpaque(false); setForeground(t.text); }
                return this;
            }
        }
        static class ComboUI extends BasicComboBoxUI {
            @Override protected JButton createArrowButton() {
                JButton b = new JButton() {
                    @Override protected void paintComponent(Graphics g0) {
                        Graphics2D g = (Graphics2D) g0.create(); Ui.aa(g);
                        double cx = getWidth() / 2.0, cy = getHeight() / 2.0;
                        Path2D p = new Path2D.Double(); p.moveTo(cx - 5, cy - 2.5); p.lineTo(cx + 5, cy - 2.5); p.lineTo(cx, cy + 3.5); p.closePath();
                        g.setColor(Theme.cur.accent); g.fill(p); g.dispose();
                    }
                };
                b.setBorder(null); b.setContentAreaFilled(false); b.setFocusable(false); b.setPreferredSize(new Dimension(30, 30));
                return b;
            }
            @Override public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) { }
            @Override protected ComboPopup createPopup() {
                BasicComboPopup p = new BasicComboPopup(comboBox) {
                    @Override protected JScrollPane createScroller() {
                        JScrollPane sp = super.createScroller();
                        sp.setBorder(null); sp.getVerticalScrollBar().setUI(new SlimScrollUI());
                        return sp;
                    }
                    @Override protected void paintComponent(Graphics g) { g.setColor(Theme.cur.card); g.fillRect(0, 0, getWidth(), getHeight()); }
                };
                p.setBorder(new AbstractBorder() {
                    @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) { g.setColor(Theme.cur.accent); g.drawRect(x, y, w - 1, h - 1); }
                    @Override public Insets getBorderInsets(Component c) { return new Insets(1, 1, 1, 1); }
                });
                return p;
            }
        }
    }

    /** Horizontal progress bar with smooth animation. */
    static class GBar extends JComponent {
        double value, shown;
        GBar() { setOpaque(false); }
        void setValue(double v) { value = Math.max(0, Math.min(1, v)); repaint(); }
        @Override public Dimension getPreferredSize() { return new Dimension(200, 16); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); Ui.aa(g); Theme t = Theme.cur;
            if (Settings.anim) { shown += (value - shown) * 0.18; if (Math.abs(value - shown) > 0.002) repaint(); else shown = value; } else shown = value;
            double h = Math.min(getHeight(), 14), y = (getHeight() - h) / 2, w = getWidth();
            g.setColor(Ui.alpha(t.text, t.dark ? 28 : 36)); g.fill(new RoundRectangle2D.Double(0, y, w, h, h, h));
            if (shown > 0.003) {
                double fw = Math.max(h, w * shown);
                g.setPaint(new GradientPaint(0, 0, t.accent, (float) w, 0, t.accent2));
                g.fill(new RoundRectangle2D.Double(0, y, fw, h, h, h));
            }
            g.dispose();
        }
    }

    /** Circular progress with centre text (stopwatch, timer, pomodoro). */
    static class Ring extends JComponent {
        double value; String big = "", small = ""; boolean ticks; Color c1, c2; double shown;
        boolean ease = true;
        Ring(boolean ticks) { this.ticks = ticks; setOpaque(false); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); Ui.aa(g); Theme t = Theme.cur;
            if (ease && Settings.anim) { shown += (value - shown) * 0.2; if (Math.abs(value - shown) > 0.001) repaint(); else shown = value; } else shown = value;
            double size = Math.min(getWidth(), getHeight()), cx = getWidth() / 2.0, cy = getHeight() / 2.0, sw = Math.max(8, size * 0.045), r = size / 2 - sw * 1.6;
            Ui.ring(g, cx, cy, r, (float) sw, shown, c1 == null ? t.accent : c1, c2 == null ? t.accent2 : c2, ticks);
            Font bf = Ui.fit(g, big, Ui.mono(Font.BOLD, 40), (float) (r * 1.5), (float) (r * 0.42));
            Ui.textC(g, big, cx, cy - (small.isEmpty() ? 0 : r * .09), bf, t.text);
            if (!small.isEmpty()) Ui.textC(g, small, cx, cy + r * .3, Ui.px(Font.PLAIN, (float) Math.max(11, r * .1)), t.sub);
            g.dispose();
        }
    }

    /** Analog clock face with moving hour, minute and second hands. */
    static class AnalogClock extends JComponent {
        ZoneId zone; final boolean mini;
        AnalogClock(ZoneId zone, boolean mini) { this.zone = zone; this.mini = mini; setOpaque(false); }

        private void hand(Graphics2D g, double cx, double cy, double ang, double len, double tail, float w, Color c, boolean glow) {
            double dx = Math.sin(ang), dy = -Math.cos(ang);
            Line2D l = new Line2D.Double(cx - dx * tail, cy - dy * tail, cx + dx * len, cy + dy * len);
            if (glow) { g.setStroke(new BasicStroke(w * 3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.setColor(Ui.alpha(c, 38)); g.draw(l); }
            g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.setColor(c); g.draw(l);
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); Ui.aa(g); Theme t = Theme.cur;
            int style = Settings.clockStyle;
            double size = Math.min(getWidth(), getHeight()) - (mini ? 6 : 12), cx = getWidth() / 2.0, cy = getHeight() / 2.0, R = size / 2;
            if (R < 10) { g.dispose(); return; }
            ZonedDateTime now = ZonedDateTime.now(zone);
            // face
            g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy - R * .2), (float) R * 1.2f, new float[]{0f, 1f},
                new Color[]{Ui.mix(t.cardHi, t.text, t.dark ? .06f : 0f), Ui.mix(t.bg1, t.card, .5f)}));
            g.fill(new Ellipse2D.Double(cx - R, cy - R, 2 * R, 2 * R));
            float rw = (float) Math.max(2, R * 0.035);
            if (style == 0) for (int i = 3; i >= 1; i--) { g.setStroke(new BasicStroke(rw + i * 3)); g.setColor(Ui.alpha(t.accent, 18)); g.draw(new Ellipse2D.Double(cx - R, cy - R, 2 * R, 2 * R)); }
            g.setStroke(new BasicStroke(rw)); g.setPaint(new GradientPaint((float) (cx - R), (float) (cy - R), t.accent, (float) (cx + R), (float) (cy + R), t.accent2));
            g.draw(new Ellipse2D.Double(cx - R, cy - R, 2 * R, 2 * R));
            // ticks
            for (int i = 0; i < 60; i++) {
                boolean big = i % 5 == 0;
                if (mini && !big) continue;
                if (style == 2 && !(i % 15 == 0)) continue;
                double a = i * Math.PI / 30, r1 = R * (big ? .80 : .88), r2 = R * .93;
                g.setStroke(new BasicStroke(big ? (float) Math.max(1.5, R * .03) : 1.1f, style == 3 ? BasicStroke.CAP_BUTT : BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(big ? t.accent : Ui.alpha(t.text, 90));
                g.draw(new Line2D.Double(cx + Math.sin(a) * r1, cy - Math.cos(a) * r1, cx + Math.sin(a) * r2, cy - Math.cos(a) * r2));
            }
            if (!mini && style != 2) {
                Font nf = style == 3 ? Ui.mono(Font.BOLD, (float) (R * .15)) : Ui.px(Font.BOLD, (float) (R * .15));
                for (int n = 1; n <= 12; n++) {
                    double a = n * Math.PI / 6;
                    Ui.textC(g, "" + n, cx + Math.sin(a) * R * .68, cy - Math.cos(a) * R * .68, nf, t.text);
                }
            }
            if (!mini) {
                String dt = now.format(DateTimeFormatter.ofPattern("EEE dd", Locale.ENGLISH)).toUpperCase();
                Font df = Ui.px(Font.BOLD, (float) (R * .09)); FontMetrics fm = g.getFontMetrics(df);
                double bw = fm.stringWidth(dt) + R * .12, bh = R * .15;
                g.setColor(Ui.alpha(t.text, 22)); g.fill(new RoundRectangle2D.Double(cx - bw / 2, cy + R * .34, bw, bh, 8, 8));
                Ui.textC(g, dt, cx, cy + R * .34 + bh / 2, df, t.sub);
            }
            // hands
            double sec = now.getSecond() + (Settings.anim ? now.getNano() / 1e9 : 0), min = now.getMinute() + sec / 60.0, hr = (now.getHour() % 12) + min / 60.0;
            boolean glow = style == 0;
            hand(g, cx, cy, hr / 12 * 2 * Math.PI, R * .5, R * .08, (float) (R * (mini ? .08 : .06)), t.accent2, glow);
            hand(g, cx, cy, min / 60 * 2 * Math.PI, R * .76, R * .1, (float) (R * (mini ? .06 : .042)), t.accent, glow);
            hand(g, cx, cy, sec / 60 * 2 * Math.PI, R * .86, R * .2, (float) Math.max(1.3, R * .016), t.accent3, glow);
            g.setColor(t.accent3); g.fill(new Ellipse2D.Double(cx - R * .05, cy - R * .05, R * .1, R * .1));
            g.setColor(t.card); g.fill(new Ellipse2D.Double(cx - R * .02, cy - R * .02, R * .04, R * .04));
            g.dispose();
        }
    }

    /** Large digital clock: Neon, Flip cards, Minimal, Retro; AM/PM; animated seconds bar. */
    static class DigitalDisplay extends JComponent {
        ZoneId zone; boolean showDate;
        DigitalDisplay(ZoneId zone, boolean showDate) { this.zone = zone; this.showDate = showDate; setOpaque(false); }

        @Override public Dimension getPreferredSize() { return new Dimension(420, 150); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); Ui.aa(g); Theme t = Theme.cur;
            int w = getWidth(), h = getHeight(), style = Settings.clockStyle;
            if (w < 40 || h < 40) { g.dispose(); return; }
            ZonedDateTime now = ZonedDateTime.now(zone);
            int hh = now.getHour(), hd = Settings.h24 ? hh : (hh % 12 == 0 ? 12 : hh % 12);
            String txt = String.format("%02d:%02d:%02d", hd, now.getMinute(), now.getSecond());
            double bar = Math.max(16, h * .13), dateH = showDate ? Math.max(24, h * .15) : 0, th = h - bar - dateH;
            Font base = Ui.mono(style == 2 ? Font.PLAIN : Font.BOLD, 100);
            FontMetrics fm0 = g.getFontMetrics(base);
            double dw = fm0.charWidth('0') / 100.0, cw = dw * .5, gap = style == 1 ? .16 : .03, ap = Settings.h24 ? 0 : .8;
            double em = 6 * (dw + gap) + 2 * (cw + gap) + ap + .1;
            double s = Math.min(th * (style == 1 ? .62 : .8), w * .96 / em);
            Font f = base.deriveFont((float) s); FontMetrics fm = g.getFontMetrics(f);
            double x = (w - em * s) / 2, cy = th / 2.0, baseline = cy + (fm.getAscent() - fm.getDescent()) / 2.0;
            double pulse = Settings.anim ? .85 + .15 * Math.sin(System.currentTimeMillis() / 500.0) : 1;
            double colon = !Settings.anim || now.getNano() < 600_000_000 ? 1 : .3;
            int glowR = (int) Math.max(2, Math.min(9, s / 12));
            for (int i = 0; i < txt.length(); i++) {
                char ch = txt.charAt(i); boolean isC = ch == ':', secs = i >= 6;
                double cell = (isC ? cw : dw) * s;
                Color c = style == 0 ? (secs ? t.accent2 : t.accent) : style == 3 ? t.accent3 : style == 1 ? (secs ? t.accent : t.text) : (secs ? t.sub : t.text);
                if (isC) c = Ui.alpha(c, (int) (255 * colon));
                if (style == 1 && !isC) {
                    double tw = cell + gap * s * .6, thh = s * 1.22, tx = x - gap * s * .3, ty = cy - thh / 2;
                    RoundRectangle2D.Double tile = new RoundRectangle2D.Double(tx, ty, tw, thh, s * .16, s * .16);
                    g.setPaint(new GradientPaint(0, (float) ty, Ui.mix(t.cardHi, t.text, .1f), 0, (float) (ty + thh), Ui.mix(t.card, t.bg1, .5f)));
                    g.fill(tile); g.setColor(t.line); g.draw(tile);
                    g.setColor(Ui.alpha(Color.BLACK, 110)); g.fill(new Rectangle2D.Double(tx, cy - 1.2, tw, 2.4));
                }
                double gx = x + (cell - fm.charWidth(ch)) / 2;
                if (style == 0 || style == 3) Ui.glowText(g, "" + ch, gx, baseline, f, c, glowR, (float) pulse * (style == 3 ? .6f : 1f));
                else Ui.text(g, "" + ch, gx, baseline, f, c);
                x += cell + gap * s;
            }
            if (style == 3) {                                  // retro scan lines
                g.setColor(new Color(0, 0, 0, t.dark ? 60 : 25));
                for (int yy = 0; yy < th; yy += 3) g.fillRect(0, yy, w, 1);
            }
            if (!Settings.h24) {                               // AM / PM indicator
                boolean pm = hh >= 12; Font af = Ui.px(Font.BOLD, (float) Math.max(11, s * .2));
                double ax = x + s * .08;
                Ui.text(g, "AM", ax, cy - s * .04, af, pm ? Ui.alpha(t.sub, 120) : t.accent);
                Ui.text(g, "PM", ax, cy + s * .22, af, pm ? t.accent : Ui.alpha(t.sub, 120));
            }
            // animated seconds indicator
            double sec = now.getSecond() + (Settings.anim ? now.getNano() / 1e9 : 0), bw = w * .86, bx = (w - bw) / 2, by = th + bar * .5, step = bw / 60.0;
            for (int i = 0; i < 60; i++) {
                double tx = bx + i * step, tw = Math.max(2, step * .56), hh2 = bar * (i % 5 == 0 ? .62 : .42);
                if (i < Math.floor(sec)) g.setColor(Ui.mix(t.accent, t.accent2, i / 59f));
                else if (i == (int) Math.floor(sec)) { g.setColor(Ui.alpha(t.accent3, 120 + (int) (135 * (sec - Math.floor(sec))))); hh2 = bar * .85; }
                else g.setColor(Ui.alpha(t.text, 30));
                g.fill(new RoundRectangle2D.Double(tx, by - hh2 / 2, tw, hh2, tw, tw));
            }
            if (showDate) {
                String d = now.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", Locale.ENGLISH)) + "   \u2022   " + zone.getId().replace('_', ' ') + "  (UTC" + now.getOffset().getId().replace("Z", "+00:00") + ")";
                Font df = Ui.fit(g, d, Ui.px(Font.PLAIN, 20), (float) (w * .94), (float) (dateH * .62));
                Ui.textC(g, d, w / 2.0, h - dateH / 2.0, df, t.sub);
            }
            g.dispose();
        }
    }

    /** Page background: gradient plus soft neon glows. */
    static class Backdrop extends JPanel {
        Backdrop() { setOpaque(true); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); Ui.aa(g); Theme t = Theme.cur; int w = getWidth(), h = getHeight();
            g.setPaint(new GradientPaint(0, 0, t.bg1, w, h, t.bg2)); g.fillRect(0, 0, w, h);
            if (t.dark) {
                g.setPaint(new RadialGradientPaint(new Point2D.Double(w * .85, h * .05), Math.max(w, h) * .45f, new float[]{0f, 1f}, new Color[]{Ui.alpha(t.accent2, 46), Ui.alpha(t.accent2, 0)}));
                g.fillRect(0, 0, w, h);
                g.setPaint(new RadialGradientPaint(new Point2D.Double(w * .2, h * .95), Math.max(w, h) * .4f, new float[]{0f, 1f}, new Color[]{Ui.alpha(t.accent, 34), Ui.alpha(t.accent, 0)}));
                g.fillRect(0, 0, w, h);
            }
            g.dispose();
        }
    }

    /** Panel that fades in its children (used for the page transition). */
    static class FadePanel extends JPanel {
        float fade = 1f;
        FadePanel(LayoutManager lm) { super(lm); setOpaque(false); }
        @Override protected void paintChildren(Graphics g) {
            if (fade >= 0.99f) { super.paintChildren(g); return; }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, fade)));
            super.paintChildren(g2);
            g2.dispose();
        }
    }

    /** Panel for scroll panes that always matches the width of the viewport. */
    static class ScrollPanel extends JPanel implements Scrollable {
        ScrollPanel(LayoutManager lm) { super(lm); setOpaque(false); }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 20; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 100; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** Day / month / year (+ hour / minute) selector built from steppers. */
    static class DateTimeInput extends JPanel {
        final GStepper d, m, y, hh, mm;
        final boolean time;
        DateTimeInput(LocalDateTime init, boolean time) {
            this.time = time;
            setOpaque(false);
            d = new GStepper(init.getDayOfMonth(), 1, 31, true);
            m = new GStepper(init.getMonthValue(), 1, 12, true);
            y = new GStepper(init.getYear(), 2020, 2100, false).digits(4);
            hh = new GStepper(init.getHour(), 0, 23, true);
            mm = new GStepper(init.getMinute(), 0, 59, true);
            Dimension sz = new Dimension(Math.round(94 * Settings.scale()), Math.round(38 * Settings.scale()));
            for (GStepper s : new GStepper[]{d, m, hh, mm}) s.setPreferredSize(sz);
            y.setPreferredSize(new Dimension(Math.round(112 * Settings.scale()), sz.height));
            Component[] comps = time ? new Component[]{d, m, y, hh, mm} : new Component[]{d, m, y};
            String[] names = time ? new String[]{"Day", "Month", "Year", "Hour", "Min"} : new String[]{"Day", "Month", "Year"};
            setLayout(new GridLayout(2, comps.length, 6, 3));
            for (String n : names) add(Ui.label(n, 11, false, "sub"));
            for (Component c : comps) add(c);
        }
        LocalDateTime get() {
            int yr = y.getValue(), mo = m.getValue(), day = Math.min(d.getValue(), YearMonth.of(yr, mo).lengthOfMonth());
            return LocalDateTime.of(yr, mo, day, time ? hh.getValue() : 0, time ? mm.getValue() : 0);
        }
        void set(LocalDateTime v) { d.setValue(v.getDayOfMonth()); m.setValue(v.getMonthValue()); y.setValue(v.getYear()); hh.setValue(v.getHour()); mm.setValue(v.getMinute()); }
        @Override public void setEnabled(boolean en) { super.setEnabled(en); for (Component c : getComponents()) c.setEnabled(en); }
    }

    // =================================================================
    // 4. TIMER MODELS  (time is measured with System.nanoTime - no drift)
    // =================================================================
    static class StopwatchModel {
        boolean running; long startNs, accMs, lastLapTotal;
        final ArrayList<long[]> laps = new ArrayList<>();      // {lapMs, totalMs}
        long elapsed() { return running ? accMs + (System.nanoTime() - startNs) / 1_000_000 : accMs; }
        void start() { if (!running) { running = true; startNs = System.nanoTime(); } }
        void pause() { if (running) { accMs = elapsed(); running = false; } }
        void reset() { running = false; accMs = 0; lastLapTotal = 0; laps.clear(); }
        void lap() {
            if (!running) return;
            long t = elapsed();
            laps.add(new long[]{t - lastLapTotal, t});
            lastLapTotal = t;
        }
    }

    static class CountdownModel {
        long totalMs = 5 * 60000L, remMs = 5 * 60000L, endNs;
        boolean running, finished;
        long remaining() { return running ? Math.max(0, (endNs - System.nanoTime()) / 1_000_000) : remMs; }
        void set(long ms) { totalMs = ms; remMs = ms; running = false; finished = false; }
        void start() {
            if (remMs <= 0) remMs = totalMs;
            if (remMs <= 0) return;
            running = true; finished = false; endNs = System.nanoTime() + remMs * 1_000_000;
        }
        void pause() { if (running) { remMs = remaining(); running = false; } }
        void reset() { running = false; finished = false; remMs = totalMs; }
        /** @return true exactly once, when the countdown reaches zero */
        boolean tick() {
            if (running && remaining() <= 0) { running = false; remMs = 0; finished = true; return true; }
            return false;
        }
    }

    static class PomodoroModel {
        static final String[] MODES = {"Focus", "Short Break", "Long Break"};
        int mode;                 // 0 focus, 1 short break, 2 long break
        int cycle;                // finished focus sessions in the current cycle (4 -> long break)
        boolean running; long endNs, remMs;
        PomodoroModel() { remMs = totalMs(); }
        long totalMs() { return 60000L * (mode == 0 ? Settings.pomWork : mode == 1 ? Settings.pomShort : Settings.pomLong); }
        long remaining() { return running ? Math.max(0, (endNs - System.nanoTime()) / 1_000_000) : remMs; }
        void applyDurations() { if (!running) remMs = totalMs(); }
        void setMode(int m) { mode = m; running = false; remMs = totalMs(); }
        void start() { if (remMs <= 0) remMs = totalMs(); running = true; endNs = System.nanoTime() + remMs * 1_000_000; }
        void pause() { if (running) { remMs = remaining(); running = false; } }
        void reset() { running = false; remMs = totalMs(); }
        void skip() { nextPhase(); }
        private void nextPhase() { setMode(mode == 0 ? (cycle > 0 && cycle % 4 == 0 ? 2 : 1) : 0); }
        /** @return 0 normally, or (finishedMode + 1) when a session has just finished */
        int tick() {
            if (running && remaining() <= 0) {
                int fin = mode; running = false;
                if (fin == 0) { cycle++; Store.addFocus(Settings.pomWork); }
                nextPhase();
                return fin + 1;
            }
            return 0;
        }
    }

    static final StopwatchModel SW = new StopwatchModel();
    static final CountdownModel CD = new CountdownModel();
    static final PomodoroModel PM = new PomodoroModel();

    // =================================================================
    // 5. DIALOGS  (themed popups, alarm editor, task editor)
    // =================================================================
    private static String[] zoneCache;
    static String[] zones() {
        if (zoneCache == null) {
            ArrayList<String> l = new ArrayList<>();
            for (String s : ZoneId.getAvailableZoneIds())
                if (s.contains("/") && !s.startsWith("Etc/") && !s.startsWith("SystemV/") && !s.startsWith("US/")) l.add(s);
            l.add("UTC");
            Collections.sort(l);
            zoneCache = l.toArray(new String[0]);
        }
        return zoneCache;
    }

    static class Dialogs {
        static int open = 0;
        static Window owner(Component c) {
            Window w = c == null ? null : SwingUtilities.getWindowAncestor(c);
            return w != null ? w : app;
        }

        /** Rounded themed message box with buttons. */
        static class Box extends JDialog implements Ticker.Tick {
            int result = -1; final int icon; final boolean pulse; final long t0 = System.currentTimeMillis(); final JPanel iconPanel;
            Box(Window owner, String title, String msg, int icon, boolean modal, boolean pulse, String[] btns, int primary, IntConsumer cb) {
                super(owner, modal ? ModalityType.APPLICATION_MODAL : ModalityType.MODELESS);
                this.icon = icon; this.pulse = pulse;
                setUndecorated(true);
                JPanel root = new JPanel(new BorderLayout(18, 14)) {
                    @Override protected void paintComponent(Graphics g0) {
                        Graphics2D g = (Graphics2D) g0; Theme t = Theme.cur;
                        g.setPaint(new GradientPaint(0, 0, t.cardHi, 0, getHeight(), t.card)); g.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
                root.setBorder(new EmptyBorder(22, 24, 18, 24));
                iconPanel = new JPanel() {
                    @Override protected void paintComponent(Graphics g0) {
                        Graphics2D g = (Graphics2D) g0.create(); Ui.aa(g); Theme t = Theme.cur;
                        double cx = getWidth() / 2.0, cy = getHeight() / 2.0, ph = ((System.currentTimeMillis() - Box.this.t0) % 1400) / 1400.0;
                        if (Box.this.pulse && Settings.anim) {
                            g.setColor(Ui.alpha(t.accent, (int) (90 * (1 - ph)))); double r = 26 + 18 * ph; g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
                        }
                        g.setPaint(new GradientPaint(0, 0, t.accent, getWidth(), getHeight(), t.accent2)); g.fill(new Ellipse2D.Double(cx - 28, cy - 28, 56, 56));
                        double sh = Box.this.pulse && Settings.anim ? Math.sin(ph * Math.PI * 8) * 0.12 : 0;
                        g.rotate(sh, cx, cy); Ui.icon(g, Box.this.icon, cx, cy, 30, Ui.isLight(t.accent) ? new Color(10, 16, 32) : Color.WHITE);
                        g.dispose();
                    }
                };
                iconPanel.setOpaque(false); iconPanel.setPreferredSize(new Dimension(84, 84));
                JPanel iconWrap = new JPanel(new BorderLayout()); iconWrap.setOpaque(false); iconWrap.add(iconPanel, BorderLayout.NORTH);
                JPanel text = new JPanel(new BorderLayout(0, 6)); text.setOpaque(false);
                text.add(Ui.label(title, 20, true, "text"), BorderLayout.NORTH);
                JTextArea ta = new JTextArea(msg); ta.setEditable(false); ta.setLineWrap(true); ta.setWrapStyleWord(true); ta.setOpaque(false);
                ta.setBorder(null); ta.setColumns(26); ta.setFont(Ui.font(Font.PLAIN, 14)); ta.setForeground(Theme.cur.sub); ta.setFocusable(false);
                text.add(ta, BorderLayout.CENTER);
                JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); row.setOpaque(false);
                for (int i = 0; i < btns.length; i++) {
                    final int idx = i;
                    row.add(new GButton(btns[i], -1, i == primary ? GButton.PRIMARY : GButton.NORMAL).onClick(() -> {
                        result = idx; if (cb != null) cb.accept(idx); dispose();
                    }));
                }
                root.add(iconWrap, BorderLayout.WEST); root.add(text, BorderLayout.CENTER); root.add(row, BorderLayout.SOUTH);
                getRootPane().setBorder(BorderFactory.createLineBorder(Theme.cur.accent, 2));
                setContentPane(root); pack();
                setLocationRelativeTo(owner);
                if (pulse) { Ticker.add(this); setAlwaysOnTop(true); }
            }
            @Override public void tick(boolean sec) { if (isShowing()) iconPanel.repaint(); }
            @Override public void dispose() { Ticker.remove(this); open = Math.max(0, open - 1); super.dispose(); }
        }

        static void info(Component parent, String title, String msg, int icon) {
            new Box(owner(parent), title, msg, icon, true, false, new String[]{"OK"}, 0, null).setVisible(true);
        }
        static boolean confirm(Component parent, String title, String msg, String yes) {
            Box b = new Box(owner(parent), title, msg, Ui.TRASH, true, false, new String[]{"Cancel", yes}, 1, null);
            b.setVisible(true);
            return b.result == 1;
        }
        /** Non-blocking popup shown when a timer / pomodoro finishes. */
        static void notifyPopup(String title, String msg, int icon) {
            Box b = new Box(app, title, msg, icon, false, true, new String[]{"OK"}, 0, i -> Sound.stop());
            place(b); b.setVisible(true); b.toFront();
        }
        /** Non-blocking alarm popup with snooze buttons. */
        static void alarmPopup(Alarm a) {
            String msg = (a.desc.isEmpty() ? "" : a.desc + "\n") + "Alarm time: " + a.time.format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "  (" + a.zone + ")";
            Box b = new Box(app, a.name, msg, Ui.ALARM, false, true, new String[]{"Snooze 5 min", "Snooze 10 min", "Snooze 15 min", "Dismiss"}, 3, i -> {
                Sound.stop();
                if (i < 3) a.snoozeUntil = ZonedDateTime.now().plusMinutes(new int[]{5, 10, 15}[i]);
                Store.saveAlarms();
            });
            place(b); b.setVisible(true); b.toFront();
        }
        private static void place(Box b) {
            int off = (open++ % 4) * 30;
            b.setLocation(b.getX() + off, b.getY() + off);
        }
    }

    /** Base class for the Alarm and Task editors. */
    static class FormDialog extends JDialog {
        final JPanel form = new JPanel();
        FormDialog(Window owner, String title) {
            super(owner, title, ModalityType.APPLICATION_MODAL);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS)); form.setOpaque(false);
            JPanel root = new JPanel(new BorderLayout(0, 12)) {
                @Override protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0; g.setPaint(new GradientPaint(0, 0, Theme.cur.bg2, 0, getHeight(), Theme.cur.card)); g.fillRect(0, 0, getWidth(), getHeight());
                }
            };
            root.setBorder(new EmptyBorder(18, 22, 16, 22));
            root.add(Ui.label(title, 22, true, "text"), BorderLayout.NORTH);
            root.add(form, BorderLayout.CENTER);
            setContentPane(root);
        }
        JPanel row(String label, Component c) {
            JPanel r = new JPanel(new BorderLayout(12, 0)); r.setOpaque(false);
            JLabel l = Ui.label(label, 13, true, "sub"); l.setPreferredSize(new Dimension(Math.round(105 * Settings.scale()), 38)); l.setVerticalAlignment(SwingConstants.CENTER);
            JPanel wrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); wrap.setOpaque(false); wrap.add(c);
            r.add(l, BorderLayout.WEST); r.add(wrap, BorderLayout.CENTER);
            r.setBorder(new EmptyBorder(5, 0, 5, 0)); r.setAlignmentX(Component.LEFT_ALIGNMENT);
            form.add(r);
            return r;
        }
        void buttons(String saveText, Runnable onSave) {
            JPanel b = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0)); b.setOpaque(false);
            b.add(new GButton("Cancel", -1).onClick(this::dispose));
            b.add(new GButton(saveText, Ui.CHECK, GButton.PRIMARY).onClick(onSave));
            getContentPane().add(b, BorderLayout.SOUTH);
        }
        void open(Component parent) { pack(); setLocationRelativeTo(Dialogs.owner(parent)); setVisible(true); }
    }

    static class AlarmDialog extends FormDialog {
        Alarm result; final Alarm src;
        final GField name = new GField(26, "Alarm name"), desc = new GField(26, "Description (optional)");
        final GStepper hr, mn, sc; final DateTimeInput date; final GSegment repeat; final GChips chips = new GChips(Alarm.DAYS);
        final GCombo<String> sound = new GCombo<>(Sound.NAMES), zone = new GCombo<>(zones());
        JPanel dateRow, chipRow;

        static Alarm edit(Component parent, Alarm src) {
            AlarmDialog d = new AlarmDialog(Dialogs.owner(parent), src);
            d.open(parent);
            return d.result;
        }
        AlarmDialog(Window owner, Alarm src) {
            super(owner, src == null ? "New alarm" : "Edit alarm");
            this.src = src;
            Alarm a = src != null ? src : defaults();
            name.setText(a.name); desc.setText(a.desc);
            hr = new GStepper(a.time.getHour(), 0, 23, true).suffix(" h"); mn = new GStepper(a.time.getMinute(), 0, 59, true).suffix(" m"); sc = new GStepper(a.time.getSecond(), 0, 59, true).suffix(" s");
            date = new DateTimeInput(LocalDateTime.of(a.date, LocalTime.MIDNIGHT), false);
            repeat = new GSegment(Alarm.REPEAT, a.repeat, i -> updateVisibility());
            chips.setMask(a.days);
            sound.setSelectedIndex(Math.max(0, Math.min(Sound.NAMES.length - 1, a.sound))); zone.setSelectedItem(a.zone);
            zone.setPreferredSize(new Dimension(Math.round(260 * Settings.scale()), zone.getPreferredSize().height));
            JPanel time = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); time.setOpaque(false); time.add(hr); time.add(mn); time.add(sc);
            JPanel snd = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); snd.setOpaque(false); snd.add(sound);
            snd.add(new GButton("Test", Ui.PLAY).onClick(() -> Sound.play(sound.getSelectedIndex(), false, true)));
            row("Name", name); row("Description", desc); row("Time (24h)", time);
            dateRow = row("Date", date); row("Repeat", repeat); chipRow = row("Days", chips); row("Time zone", zone); row("Sound", snd);
            buttons("Save alarm", this::save);
            updateVisibility();
        }
        static Alarm defaults() {
            Alarm a = new Alarm();
            LocalDateTime n = LocalDateTime.now(Settings.zoneId()).plusMinutes(2);
            a.time = n.toLocalTime().withSecond(0).withNano(0); a.date = n.toLocalDate(); a.zone = Settings.zone; a.name = "Alarm";
            return a;
        }
        void updateVisibility() {
            dateRow.setVisible(repeat.sel == 0); chipRow.setVisible(repeat.sel == 4);
            pack();
        }
        void save() {
            Alarm a = new Alarm();
            a.name = name.getText().trim().isEmpty() ? "Alarm" : name.getText().trim();
            a.desc = desc.getText().trim(); a.time = LocalTime.of(hr.getValue(), mn.getValue(), sc.getValue());
            a.date = date.get().toLocalDate(); a.repeat = repeat.sel; a.days = chips.mask();
            a.sound = sound.getSelectedIndex(); a.zone = (String) zone.getSelectedItem();
            if (a.repeat == 0 && !ZonedDateTime.of(a.date, a.time, a.zoneId()).isAfter(ZonedDateTime.now())) {
                Dialogs.info(this, "Pick a future time", "A one-time alarm needs a date and time in the future.", Ui.ALARM); return;
            }
            if (a.repeat == 4 && a.days == 0) { Dialogs.info(this, "Choose days", "Select at least one weekday for a custom repeat.", Ui.CAL); return; }
            Alarm target = src != null ? src : a;
            if (src != null) { src.name = a.name; src.desc = a.desc; src.time = a.time; src.date = a.date; src.repeat = a.repeat; src.days = a.days; src.sound = a.sound; src.zone = a.zone; }
            target.enabled = true; target.lastFired = null; target.snoozeUntil = null;
            result = target; dispose();
        }
    }

    static class TaskDialog extends FormDialog {
        Task result; final Task src;
        final GField title = new GField(26, "What needs to be done?");
        final GSegment pri; final GCombo<String> cat = new GCombo<>(Task.CATS); final GSwitch hasDue; DateTimeInput due;

        static Task edit(Component parent, Task src) {
            TaskDialog d = new TaskDialog(Dialogs.owner(parent), src);
            d.open(parent);
            return d.result;
        }
        TaskDialog(Window owner, Task src) {
            super(owner, src == null ? "New task" : "Edit task");
            this.src = src;
            Task t = src != null ? src : new Task();
            title.setText(t.title);
            pri = new GSegment(Task.PRI, t.pri, null);
            cat.setSelectedItem(t.cat);
            hasDue = new GSwitch(t.due != null, on -> due.setEnabled(on));
            due = new DateTimeInput(t.due != null ? t.due : LocalDateTime.now().plusHours(3).withMinute(0), true);
            due.setEnabled(t.due != null);
            JPanel dueRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0)); dueRow.setOpaque(false); dueRow.add(hasDue); dueRow.add(due);
            row("Task", title); row("Priority", pri); row("Category", cat); row("Due date", dueRow);
            buttons("Save task", this::save);
        }
        void save() {
            String s = title.getText().trim();
            if (s.isEmpty()) { Dialogs.info(this, "Task needs a title", "Please type what needs to be done.", Ui.TODO); return; }
            Task t = src != null ? src : new Task();
            t.title = s; t.pri = pri.sel; t.cat = (String) cat.getSelectedItem(); t.due = hasDue.on ? due.get() : null;
            result = t; dispose();
        }
    }

    // =================================================================
    // 6. PAGES
    // =================================================================
    static void toggleStopwatch() { if (SW.running) SW.pause(); else SW.start(); }
    static void toggleTimer() { if (CD.running) CD.pause(); else { Sound.stop(); CD.start(); } }
    static void togglePomodoro() { if (PM.running) PM.pause(); else { Sound.stop(); PM.start(); } }

    /** Base class of every page: title, subtitle, body area and tick hook. */
    static abstract class Page extends JPanel implements Ticker.Tick {
        final JPanel body = new JPanel();
        Page(String title, String sub) {
            setOpaque(false); setLayout(new BorderLayout(0, 8)); setBorder(new EmptyBorder(16, 20, 14, 20));
            JPanel head = new JPanel(new GridLayout(2, 1, 0, 0)); head.setOpaque(false); head.setBorder(new EmptyBorder(0, 6, 4, 0));
            head.add(Ui.label(title, 28, true, "text")); head.add(Ui.label(sub, 13, false, "sub"));
            add(head, BorderLayout.NORTH);
            body.setOpaque(false); add(body, BorderLayout.CENTER);
            Ticker.add(this);
        }
        @Override public void tick(boolean sec) { if (isShowing()) onTick(sec); }
        void onTick(boolean sec) { }
        void onShow() { }

        static void place(JPanel p, JComponent c, int x, int y, int w, double wx, double wy) {
            GridBagConstraints g = new GridBagConstraints();
            g.gridx = x; g.gridy = y; g.gridwidth = w; g.weightx = wx; g.weighty = wy; g.fill = GridBagConstraints.BOTH; g.insets = new Insets(3, 3, 3, 3);
            c.setPreferredSize(new Dimension(10, 10));
            p.add(c, g);
        }
        static void place(JPanel p, JComponent c, int x, int y, int w, int h, double wx, double wy) {
            GridBagConstraints g = new GridBagConstraints();
            g.gridx = x; g.gridy = y; g.gridwidth = w; g.gridheight = h; g.weightx = wx; g.weighty = wy; g.fill = GridBagConstraints.BOTH; g.insets = new Insets(3, 3, 3, 3);
            c.setPreferredSize(new Dimension(10, 10));
            p.add(c, g);
        }
        static void fitText(Graphics2D g, String s, double x, double y, double maxW, float size, int style, Color c) {
            Font f = Ui.fit(g, s, Ui.px(style, size * Settings.scale()), (float) Math.max(10, maxW), size * Settings.scale());
            Ui.text(g, s, x, y, f, c);
        }
        static JPanel flow(int align, Component... cs) {
            JPanel p = new JPanel(new FlowLayout(align, 8, 0)); p.setOpaque(false);
            for (Component c : cs) p.add(c);
            return p;
        }
    }

    // ---------------------------------------------------------------- DASHBOARD
    static class DashboardPage extends Page {
        static final String[] WC = {"Partly cloudy", "Sunny", "Light rain"};
        static final int[] WT = {29, 33, 24}, WH = {68, 45, 86}, WW = {12, 9, 17};
        final DigitalDisplay dd = new DigitalDisplay(Settings.zoneId(), false);
        final AnalogClock ac = new AnalogClock(Settings.zoneId(), false);
        final GButton swBtn = new GButton("Start", Ui.PLAY, GButton.PRIMARY), tmBtn = new GButton("Start", Ui.PLAY, GButton.PRIMARY), pmBtn = new GButton("Start", Ui.PLAY, GButton.PRIMARY);
        final Card digital, analog;
        int weather = 0; String lastZone = Settings.zone;

        DashboardPage() {
            super("Dashboard", "Your day at a glance");
            body.setLayout(new GridBagLayout());
            digital = new Card("Digital clock", Ui.CLOCK); digital.add(dd, BorderLayout.CENTER); digital.right = Settings.zone;
            analog = new Card("Analog clock", Ui.CLOCK); analog.add(ac, BorderLayout.CENTER);
            Card date = new Card("Today", Ui.CAL).painter(this::paintDate).onClick(() -> app.show("calendar"));
            Card wx = new Card("Weather (sample data)", Ui.CLOUD).painter(this::paintWeather).onClick(() -> weather = (weather + 1) % 3);
            Card next = new Card("Next alarm", Ui.ALARM).painter(this::paintNext).onClick(() -> app.show("alarms"));
            Card sw = new Card("Stopwatch", Ui.STOPWATCH).painter(this::paintSw).reserve(46).onClick(() -> app.show("stopwatch"));
            Card tm = new Card("Countdown timer", Ui.TIMER).painter(this::paintTm).reserve(46).onClick(() -> app.show("timer"));
            Card tk = new Card("Today's tasks", Ui.TODO).painter(this::paintTasks).onClick(() -> app.show("tasks"));
            Card pm = new Card("Pomodoro", Ui.POMO).painter(this::paintPomo).reserve(46).onClick(() -> app.show("pomodoro"));
            Card st = new Card("Productivity", Ui.CHECK).painter(this::paintStats).onClick(() -> app.show("pomodoro"));
            swBtn.onClick(DigitalClock::toggleStopwatch);
            sw.add(flow(FlowLayout.LEFT, swBtn, new GButton("Reset", Ui.RESET).onClick(SW::reset)), BorderLayout.SOUTH);
            tmBtn.onClick(DigitalClock::toggleTimer);
            tm.add(flow(FlowLayout.LEFT, tmBtn, new GButton("Reset", Ui.RESET).onClick(CD::reset)), BorderLayout.SOUTH);
            pmBtn.onClick(DigitalClock::togglePomodoro);
            pm.add(flow(FlowLayout.LEFT, pmBtn, new GButton("Skip", Ui.SKIP).onClick(PM::skip)), BorderLayout.SOUTH);
            place(body, digital, 0, 0, 2, 2, .42); place(body, analog, 2, 0, 1, 1, .42); place(body, date, 3, 0, 1, 1, .42);
            place(body, wx, 0, 1, 1, 1, .28); place(body, next, 1, 1, 1, 1, .28); place(body, sw, 2, 1, 1, 1, .28); place(body, tm, 3, 1, 1, 1, .28);
            place(body, tk, 0, 2, 1, 1, .30); place(body, pm, 1, 2, 1, 1, .30); place(body, st, 2, 2, 2, 2, .30);
        }
        @Override void onTick(boolean sec) {
            if (!lastZone.equals(Settings.zone)) { lastZone = Settings.zone; dd.zone = Settings.zoneId(); ac.zone = Settings.zoneId(); digital.right = Settings.zone; }
            swBtn.setText(SW.running ? "Pause" : SW.elapsed() > 0 ? "Resume" : "Start"); swBtn.setIcon(SW.running ? Ui.PAUSE : Ui.PLAY);
            tmBtn.setText(CD.running ? "Pause" : CD.remaining() < CD.totalMs && CD.remaining() > 0 ? "Resume" : "Start"); tmBtn.setIcon(CD.running ? Ui.PAUSE : Ui.PLAY);
            pmBtn.setText(PM.running ? "Pause" : PM.remaining() < PM.totalMs() ? "Resume" : "Start"); pmBtn.setIcon(PM.running ? Ui.PAUSE : Ui.PLAY);
            if (Settings.anim || sec || SW.running || CD.running || PM.running) repaint();
        }

        void paintDate(Graphics2D g, int w, int h) {
            Theme t = Theme.cur; LocalDate d = LocalDate.now(Settings.zoneId());
            String day = String.format("%02d", d.getDayOfMonth());
            Font f = Ui.fit(g, day, Ui.px(Font.BOLD, 100), w * .4f, h * .7f); FontMetrics fm = g.getFontMetrics(f);
            Ui.glowText(g, day, 0, h * .42 + (fm.getAscent() - fm.getDescent()) / 2.0, f, t.accent, 5, 1f);
            double tx = fm.stringWidth(day) + w * .07, mw = w - tx;
            fitText(g, d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH).toUpperCase(), tx, h * .26, mw, 18, Font.BOLD, t.text);
            fitText(g, d.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + d.getYear(), tx, h * .26 + 22 * Settings.scale(), mw, 15, Font.PLAIN, t.sub);
            fitText(g, "Week " + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) + "  \u2022  Day " + d.getDayOfYear(), tx, h * .26 + 42 * Settings.scale(), mw, 13, Font.PLAIN, t.sub);
            double prog = d.getDayOfYear() / (double) d.lengthOfYear();
            g.setColor(Ui.alpha(t.text, 30)); g.fill(new RoundRectangle2D.Double(0, h - 12, w, 8, 8, 8));
            g.setPaint(new GradientPaint(0, 0, t.accent, w, 0, t.accent2)); g.fill(new RoundRectangle2D.Double(0, h - 12, Math.max(8, w * prog), 8, 8, 8));
        }
        void paintWeather(Graphics2D g, int w, int h) {
            Theme t = Theme.cur;
            Ui.weatherIcon(g, weather, w * .24, h * .42, Math.min(w * .44, h * .95));
            double tx = w * .5, mw = w - tx;
            Font f = Ui.fit(g, WT[weather] + "\u00B0C", Ui.px(Font.BOLD, 100), (float) mw, h * .4f); FontMetrics fm = g.getFontMetrics(f);
            Ui.text(g, WT[weather] + "\u00B0C", tx, h * .12 + fm.getAscent(), f, t.text);
            fitText(g, WC[weather], tx, h * .12 + fm.getAscent() + 22 * Settings.scale(), mw, 15, Font.BOLD, t.accent);
            fitText(g, "Humidity " + WH[weather] + "%   Wind " + WW[weather] + " km/h", 0, h - 8, w, 12, Font.PLAIN, t.sub);
        }
        void paintNext(Graphics2D g, int w, int h) {
            Theme t = Theme.cur; List<Object[]> up = Store.upcoming(3);
            if (up.isEmpty()) {
                Ui.icon(g, Ui.ALARM, w * .14, h * .4, Math.min(h * .5, 56), Ui.alpha(t.sub, 170));
                fitText(g, "No active alarms", w * .3, h * .36, w * .68, 18, Font.BOLD, t.text);
                fitText(g, "Click to create one", w * .3, h * .36 + 22 * Settings.scale(), w * .68, 13, Font.PLAIN, t.sub);
                return;
            }
            Alarm a = (Alarm) up.get(0)[0]; ZonedDateTime when = (ZonedDateTime) up.get(0)[1];
            String cd = Ui.countdown(Duration.between(ZonedDateTime.now(), when));
            Font f = Ui.fit(g, cd, Ui.mono(Font.BOLD, 100), w * .98f, h * .38f); FontMetrics fm = g.getFontMetrics(f);
            Ui.glowText(g, cd, 0, fm.getAscent(), f, t.accent, 4, .9f);
            double y = fm.getAscent() + 24 * Settings.scale();
            fitText(g, a.name + "   " + when.format(DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ENGLISH)), 0, y, w, 14, Font.BOLD, t.text);
            for (int i = 1; i < up.size(); i++) {
                Alarm b = (Alarm) up.get(i)[0]; ZonedDateTime x = (ZonedDateTime) up.get(i)[1];
                y += 20 * Settings.scale();
                if (y < h) fitText(g, "then  " + b.name + "  " + x.format(DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ENGLISH)), 0, y, w, 12, Font.PLAIN, t.sub);
            }
        }
        void paintSw(Graphics2D g, int w, int h) {
            Theme t = Theme.cur; String s = Ui.fmtMs(SW.elapsed(), true);
            Font f = Ui.fit(g, s, Ui.mono(Font.BOLD, 100), w, h * .55f); FontMetrics fm = g.getFontMetrics(f);
            Ui.glowText(g, s, 0, h * .12 + fm.getAscent(), f, SW.running ? t.accent : t.text, 3, .8f);
            fitText(g, SW.running ? "RUNNING  \u2022  laps: " + SW.laps.size() : SW.elapsed() > 0 ? "PAUSED" : "READY", 0, h - 6, w, 12, Font.BOLD, t.sub);
        }
        void paintTm(Graphics2D g, int w, int h) {
            Theme t = Theme.cur; long rem = CD.remaining();
            String s = String.format("%02d:%02d:%02d", rem / 3600000, (rem / 60000) % 60, (rem / 1000) % 60);
            Font f = Ui.fit(g, s, Ui.mono(Font.BOLD, 100), w, h * .55f); FontMetrics fm = g.getFontMetrics(f);
            Ui.glowText(g, s, 0, h * .12 + fm.getAscent(), f, CD.running ? t.accent3 : t.text, 3, .8f);
            double p = CD.totalMs == 0 ? 0 : rem / (double) CD.totalMs;
            g.setColor(Ui.alpha(t.text, 30)); g.fill(new RoundRectangle2D.Double(0, h - 10, w, 6, 6, 6));
            g.setPaint(new GradientPaint(0, 0, t.accent3, w, 0, t.accent2)); g.fill(new RoundRectangle2D.Double(0, h - 10, Math.max(6, w * p), 6, 6, 6));
        }
        void paintTasks(Graphics2D g, int w, int h) {
            Theme t = Theme.cur; int[] s = Store.todayStats(); double pct = s[1] == 0 ? 0 : s[0] / (double) s[1];
            double r = Math.min(h * .45, w * .26), cx = r + 4, cy = h / 2.0;
            Ui.ring(g, cx, cy, r - 6, 10f, pct, t.accent, t.accent2, false);
            Ui.textC(g, Math.round(pct * 100) + "%", cx, cy, Ui.px(Font.BOLD, (float) (r * .55)), t.text);
            double tx = cx + r + 14, mw = w - tx;
            fitText(g, s[0] + " of " + s[1] + " done", tx, h * .34, mw, 17, Font.BOLD, t.text);
            int pend = 0; Task best = null;
            for (Task k : Store.tasks) if (!k.done) { pend++; if (k.due != null && (best == null || k.due.isBefore(best.due))) best = k; }
            fitText(g, pend + " pending", tx, h * .34 + 21 * Settings.scale(), mw, 13, Font.PLAIN, t.sub);
            if (best != null) fitText(g, "Next: " + best.title, tx, h * .34 + 42 * Settings.scale(), mw, 12, Font.PLAIN, t.accent);
        }
        void paintPomo(Graphics2D g, int w, int h) {
            Theme t = Theme.cur; double total = PM.totalMs(), rem = PM.remaining(), r = Math.min(h * .46, w * .27), cx = r + 4, cy = h / 2.0;
            boolean work = PM.mode == 0;
            Ui.ring(g, cx, cy, r - 6, 10f, 1 - rem / total, work ? t.accent : t.accent3, work ? t.accent2 : t.accent, false);
            long sec = (long) Math.ceil(rem / 1000.0);
            Ui.textC(g, String.format("%02d:%02d", sec / 60, sec % 60), cx, cy, Ui.mono(Font.BOLD, (float) (r * .4)), t.text);
            double tx = cx + r + 14, mw = w - tx;
            fitText(g, PomodoroModel.MODES[PM.mode], tx, h * .3, mw, 17, Font.BOLD, work ? t.accent : t.accent3);
            fitText(g, "Today: " + Store.day(LocalDate.now())[0] + " sessions", tx, h * .3 + 21 * Settings.scale(), mw, 13, Font.PLAIN, t.sub);
            int filled = PM.mode == 2 ? 4 : PM.cycle % 4;
            for (int i = 0; i < 4; i++) {
                g.setColor(i < filled ? t.accent : Ui.alpha(t.text, 40)); g.fill(new Ellipse2D.Double(tx + i * 18, h * .3 + 36 * Settings.scale(), 11, 11));
            }
        }
        void paintStats(Graphics2D g, int w, int h) {
            Theme t = Theme.cur; int[] today = Store.day(LocalDate.now()); int week = 0;
            for (int i = 0; i < 7; i++) week += Store.day(LocalDate.now().minusDays(i))[1];
            double lw = w * .36; @SuppressWarnings("unused")
            double y = 22 * Settings.scale();
            String[][] rows = {{"" + today[1] + " min", "focus time today"}, {"" + today[0], "pomodoro sessions"}, {"" + Store.doneToday(), "tasks completed today"}, {week + " min", "focus this week"}};
            double rh = h / 4.0; float ns = (float) Math.max(11, Math.min(20, rh * .5)) ;
            for (int i = 0; i < 4; i++) {
                double yy = i * rh + rh * .55;
                fitText(g, rows[i][0], 0, yy, lw * .42, ns, Font.BOLD, i % 2 == 0 ? t.accent : t.accent2);
                fitText(g, rows[i][1], lw * .44, yy, lw * .56, Math.min(12, ns * .65f), Font.PLAIN, t.sub);
            }
            Graphics2D sub = (Graphics2D) g.create((int) (w * .4), 0, (int) (w * .6), h); Ui.aa(sub);
            Ui.weekly(sub, (int) (w * .6), h); sub.dispose();
        }
    }

    // ---------------------------------------------------------------- CLOCK
    static class ClockPage extends Page {
        final DigitalDisplay dd = new DigitalDisplay(Settings.zoneId(), true);
        final AnalogClock ac = new AnalogClock(Settings.zoneId(), false);
        final GCombo<String> zoneBox = new GCombo<>(zones());
        final GSegment fmt, style; String lastZone = Settings.zone;

        ClockPage() {
            super("Clock", "Live time, time zones and display styles");
            body.setLayout(new GridBagLayout());
            Card c1 = new Card("Digital clock", Ui.CLOCK); c1.add(dd, BorderLayout.CENTER);
            Card c2 = new Card("Analog clock", Ui.CLOCK); c2.add(ac, BorderLayout.CENTER);
            Card c3 = new Card("Display options", Ui.GEAR);
            fmt = new GSegment(new String[]{"12-hour", "24-hour"}, Settings.h24 ? 1 : 0, i -> { Settings.h24 = i == 1; Settings.save(); });
            style = new GSegment(new String[]{"Neon glow", "Flip cards", "Minimal", "Retro"}, Settings.clockStyle, i -> { Settings.clockStyle = i; Settings.save(); });
            zoneBox.setSelectedItem(Settings.zone);
            zoneBox.setPreferredSize(new Dimension(Math.round(250 * Settings.scale()), zoneBox.getPreferredSize().height));
            zoneBox.addActionListener(e -> {
                String z = (String) zoneBox.getSelectedItem();
                if (z != null && !z.equals(Settings.zone)) { Settings.zone = z; Settings.save(); }
            });
            JPanel opts = new JPanel(new FlowLayout(FlowLayout.LEFT, 28, 0)); opts.setOpaque(false);
            opts.add(labelled("Time zone", zoneBox)); opts.add(labelled("Hour format", fmt)); opts.add(labelled("Clock style", style));
            c3.add(opts, BorderLayout.CENTER);
            place(body, c1, 0, 0, 3, 3, .62); place(body, c2, 3, 0, 2, 1, .62); place(body, c3, 0, 1, 5, 5, .21);
        }
        static JPanel labelled(String text, Component c) {
            JPanel p = new JPanel(new BorderLayout(0, 6)); p.setOpaque(false);
            p.add(Ui.label(text, 12, true, "sub"), BorderLayout.NORTH);
            JPanel w = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); w.setOpaque(false); w.add(c); p.add(w, BorderLayout.CENTER);
            return p;
        }
        @Override void onShow() { zoneBox.setSelectedItem(Settings.zone); fmt.setSelected(Settings.h24 ? 1 : 0); style.setSelected(Settings.clockStyle); }
        @Override void onTick(boolean sec) {
            if (!lastZone.equals(Settings.zone)) { lastZone = Settings.zone; dd.zone = Settings.zoneId(); ac.zone = Settings.zoneId(); }
            if (Settings.anim || sec) repaint();
        }
    }

    // ---------------------------------------------------------------- WORLD CLOCK
    static final String[][] CITIES = {{"India", "Asia/Kolkata"}, {"London", "Europe/London"}, {"New York", "America/New_York"}, {"Tokyo", "Asia/Tokyo"}, {"Sydney", "Australia/Sydney"}};

    static class CityCard extends Card {
        final String city; final ZoneId zone;
        CityCard(String city, String zid) {
            super(null, -1);
            this.city = city; zone = ZoneId.of(zid);
            add(new AnalogClock(zone, true), BorderLayout.CENTER);
            add(Box.createVerticalStrut(96), BorderLayout.SOUTH);
        }
        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create(); Ui.aa(g); Theme t = Theme.cur;
            int w = getWidth(), h = getHeight(); double top = h - 20 - 96, cx = w / 2.0;
            ZonedDateTime now = ZonedDateTime.now(zone);
            Ui.textC(g, city, cx, top + 10, Ui.font(Font.BOLD, 15), t.text);
            String ts = now.format(DateTimeFormatter.ofPattern(Settings.h24 ? "HH:mm:ss" : "hh:mm:ss a", Locale.ENGLISH));
            Ui.textC(g, ts, cx, top + 36, Ui.fit(g, ts, Ui.mono(Font.BOLD, 100), w * .78f, 26f * Settings.scale()), t.accent);
            Ui.textC(g, now.format(DateTimeFormatter.ofPattern("EEE, dd MMM", Locale.ENGLISH)), cx, top + 60, Ui.font(Font.PLAIN, 12), t.sub);
            int diff = (zone.getRules().getOffset(now.toInstant()).getTotalSeconds() - Settings.zoneId().getRules().getOffset(now.toInstant()).getTotalSeconds()) / 60;
            String off = diff == 0 ? "Same time as home" : String.format("%s%dh%s %s", diff > 0 ? "+" : "-", Math.abs(diff) / 60, Math.abs(diff) % 60 == 0 ? "" : String.format(" %02dm", Math.abs(diff) % 60), diff > 0 ? "ahead" : "behind");
            Ui.textC(g, off, cx, top + 80, Ui.font(Font.PLAIN, 12), t.sub);
            int hr = now.getHour(); boolean day = hr >= 6 && hr < 18;
            Ui.icon(g, day ? Ui.SUN : Ui.MOON, w - 28, 26, 18, day ? new Color(255, 196, 61) : new Color(170, 190, 255));
            g.dispose();
        }
    }

    static class WorldPage extends Page {
        WorldPage() {
            super("World Clock", "Five cities at once, compared with your home time zone");
            body.setLayout(new GridBagLayout());
            for (int i = 0; i < CITIES.length; i++) place(body, new CityCard(CITIES[i][0], CITIES[i][1]), i, 0, 1, 1, .6);
            Card cmp = new Card("World clock comparison (home day, hour by hour)", Ui.WORLD).painter(this::paintCompare);
            place(body, cmp, 0, 1, 5, 5, .4);
        }
        @Override void onTick(boolean sec) { if (Settings.anim || sec) repaint(); }

        void paintCompare(Graphics2D g, int w, int h) {
            Theme t = Theme.cur; ZoneId home = Settings.zoneId(); ZonedDateTime now = ZonedDateTime.now(home), base = now.truncatedTo(ChronoUnit.DAYS);
            double lab = Math.min(110, w * .16), cw = (w - lab) / 24.0, top = 20, rh = Math.max(16, Math.min(40, (h - top - 22) / CITIES.length));
            Font small = Ui.px(Font.PLAIN, (float) Math.max(8, Math.min(11, cw * .5)));
            for (int c = 0; c < 24; c++) Ui.textC(g, "" + c, lab + c * cw + cw / 2, 8, small, c == now.getHour() ? t.accent : t.sub);
            boolean[] all = new boolean[24]; for (int c = 0; c < 24; c++) all[c] = true;
            for (int r = 0; r < CITIES.length; r++) {
                ZoneId z = ZoneId.of(CITIES[r][1]); double y = top + r * rh;
                Ui.textC(g, "", 0, 0, small, t.sub);
                Ui.text(g, CITIES[r][0], 0, y + rh / 2 + 4, Ui.font(Font.BOLD, 13), t.text);
                for (int c = 0; c < 24; c++) {
                    int lh = base.plusHours(c).withZoneSameInstant(z).getHour();
                    Color col = (lh >= 9 && lh < 18) ? Ui.alpha(t.accent, 150) : (lh >= 7 && lh < 23) ? Ui.alpha(t.accent2, 80) : Ui.alpha(t.text, 22);
                    if (lh < 7 || lh >= 23) all[c] = false;
                    g.setColor(col); g.fill(new RoundRectangle2D.Double(lab + c * cw + 1, y + 2, cw - 2, rh - 4, 6, 6));
                    Ui.textC(g, "" + lh, lab + c * cw + cw / 2, y + rh / 2, small, (lh >= 9 && lh < 18) ? (Ui.isLight(t.accent) ? new Color(10, 16, 32) : Color.WHITE) : t.sub);
                }
            }
            double yb = top + CITIES.length * rh;
            for (int c = 0; c < 24; c++) if (all[c]) { g.setColor(new Color(0x2ECC71)); g.fill(new RoundRectangle2D.Double(lab + c * cw + 2, yb + 3, cw - 4, 5, 4, 4)); }
            double nx = lab + (now.getHour() * 60 + now.getMinute()) / 1440.0 * (24 * cw);
            g.setColor(t.accent3); g.setStroke(new BasicStroke(2f)); g.draw(new Line2D.Double(nx, top - 4, nx, yb + 2));
            Ui.text(g, "Green bars: everyone is awake (07:00-22:59)   |   Bright = working hours (09-17)   |   Line = now", lab, h - 2, Ui.px(Font.PLAIN, 11), t.sub);
        }
    }

    // ---------------------------------------------------------------- CALENDAR
    static class CalendarPage extends Page {
        YearMonth ym = YearMonth.now(); LocalDate sel = LocalDate.now();
        final Grid grid = new Grid(); final JLabel monthLbl = Ui.label("", 20, true, "text");

        CalendarPage() {
            super("Calendar", "Month view with your tasks and alarms");
            body.setLayout(new GridBagLayout());
            Card cal = new Card(null, -1);
            JPanel bar = new JPanel(new BorderLayout()); bar.setOpaque(false); bar.setBorder(new EmptyBorder(0, 0, 8, 0));
            bar.add(monthLbl, BorderLayout.WEST);
            bar.add(flow(FlowLayout.RIGHT, new GButton("Prev", -1).onClick(() -> { ym = ym.minusMonths(1); refreshView(); }),
                new GButton("Today", Ui.CAL, GButton.GHOST).onClick(() -> { ym = YearMonth.now(); sel = LocalDate.now(); refreshView(); }),
                new GButton("Next", -1).onClick(() -> { ym = ym.plusMonths(1); refreshView(); })), BorderLayout.EAST);
            cal.add(bar, BorderLayout.NORTH); cal.add(grid, BorderLayout.CENTER);
            Card det = new Card("Selected day", Ui.TODO).painter(this::paintDetails);
            place(body, cal, 0, 0, 3, 1, .65); place(body, det, 3, 0, 1, 1, .35);
            refreshView();
        }
        void refreshView() { monthLbl.setText(ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + ym.getYear()); repaint(); }
        @Override void onShow() { refreshView(); }

        class Grid extends JComponent {
            Grid() {
                setOpaque(false);
                addMouseListener(new MouseAdapter() {
                    @Override public void mousePressed(MouseEvent e) {
                        double cw = getWidth() / 7.0, hh = 26, rh = (getHeight() - hh) / 6.0;
                        int c = (int) (e.getX() / cw), r = (int) ((e.getY() - hh) / rh);
                        if (c < 0 || c > 6 || r < 0 || r > 5) return;
                        LocalDate d = start().plusDays(r * 7 + c); sel = d;
                        if (!YearMonth.from(d).equals(ym)) ym = YearMonth.from(d);
                        refreshView();
                    }
                });
            }
            LocalDate start() { LocalDate f = ym.atDay(1); return f.minusDays(f.getDayOfWeek().getValue() - 1); }
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create(); Ui.aa(g); Theme t = Theme.cur;
                double cw = getWidth() / 7.0, hh = 26, rh = (getHeight() - hh) / 6.0; LocalDate today = LocalDate.now(), st = start();
                for (int c = 0; c < 7; c++) Ui.textC(g, Alarm.DAYS[c].toUpperCase(), c * cw + cw / 2, hh / 2, Ui.font(Font.BOLD, 12), c >= 5 ? t.accent2 : t.sub);
                for (int r = 0; r < 6; r++) for (int c = 0; c < 7; c++) {
                    LocalDate d = st.plusDays(r * 7 + c); double x = c * cw + 3, y = hh + r * rh + 3;
                    RoundRectangle2D.Double cell = new RoundRectangle2D.Double(x, y, cw - 6, rh - 6, 14, 14);
                    boolean in = YearMonth.from(d).equals(ym), isSel = d.equals(sel);
                    if (isSel) { g.setPaint(new GradientPaint((float) x, (float) y, Ui.alpha(t.accent, 200), (float) (x + cw), (float) (y + rh), Ui.alpha(t.accent2, 200))); g.fill(cell); }
                    else { g.setColor(Ui.alpha(t.text, in ? 14 : 5)); g.fill(cell); }
                    if (d.equals(today)) { g.setColor(t.accent3); g.setStroke(new BasicStroke(2f)); g.draw(cell); }
                    Color fg = isSel ? (Ui.isLight(t.accent) ? new Color(10, 16, 32) : Color.WHITE) : in ? t.text : Ui.alpha(t.sub, 120);
                    Ui.text(g, "" + d.getDayOfMonth(), x + 10, y + 22 * Settings.scale(), Ui.font(Font.BOLD, 14), fg);
                    int dots = 0;
                    boolean hasTask = false, hasAlarm = false;
                    for (Task k : Store.tasks) if (k.due != null && k.due.toLocalDate().equals(d)) hasTask = true;
                    for (Alarm a : Store.alarms) if (a.enabled && !d.isBefore(today) && a.matchesDay(d)) hasAlarm = true;
                    if (hasTask) { g.setColor(isSel ? Color.WHITE : t.accent3); g.fill(new Ellipse2D.Double(x + 10 + dots++ * 11, y + rh - 18, 7, 7)); }
                    if (hasAlarm) { g.setColor(isSel ? Color.WHITE : t.accent); g.fill(new Ellipse2D.Double(x + 10 + dots * 11, y + rh - 18, 7, 7)); }
                }
                g.dispose();
            }
        }
        void paintDetails(Graphics2D g, int w, int h) {
            Theme t = Theme.cur; DateTimeFormatter hm = DateTimeFormatter.ofPattern("HH:mm");
            fitText(g, sel.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)), 0, 18, w, 20, Font.BOLD, t.text);
            fitText(g, sel.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH)), 0, 18 + 22 * Settings.scale(), w, 14, Font.PLAIN, t.sub);
            double y = 66 * Settings.scale(); int n = 0;
            for (Task k : Store.tasks) if (k.due != null && k.due.toLocalDate().equals(sel)) {
                if (y > h - 10) break;
                g.setColor(k.pri == 0 ? new Color(0xFF5C7A) : k.pri == 1 ? new Color(0xFFB454) : new Color(0x4ADE80)); g.fill(new Ellipse2D.Double(0, y - 9, 8, 8));
                fitText(g, k.due.format(hm) + "  " + k.title, 16, y, w - 16, 13, Font.PLAIN, k.done ? Ui.alpha(t.sub, 150) : t.text); y += 22 * Settings.scale(); n++;
            }
            for (Alarm a : Store.alarms) if (a.enabled && !sel.isBefore(LocalDate.now()) && a.matchesDay(sel)) {
                if (y > h - 10) break;
                Ui.icon(g, Ui.ALARM, 5, y - 5, 13, t.accent);
                fitText(g, a.time.format(hm) + "  " + a.name, 16, y, w - 16, 13, Font.PLAIN, t.text); y += 22 * Settings.scale(); n++;
            }
            if (n == 0) fitText(g, "Nothing scheduled", 0, y, w, 13, Font.PLAIN, t.sub);
        }
    }

    // ---------------------------------------------------------------- ALARMS
    /** Table cell that draws a small on/off switch. */
    static class SwitchCell extends JComponent implements TableCellRenderer {
        boolean on, sel; int row;
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
            on = Boolean.TRUE.equals(v); sel = s; row = r; return this;
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); Ui.aa(g); Theme t = Theme.cur;
            g.setColor(Ui.rowBg(row, sel)); g.fillRect(0, 0, getWidth(), getHeight());
            double w = 38, h = 20, x = (getWidth() - w) / 2, y = (getHeight() - h) / 2;
            g.setColor(on ? t.accent : Ui.alpha(t.text, 55)); g.fill(new RoundRectangle2D.Double(x, y, w, h, h, h));
            g.setColor(Color.WHITE); g.fill(new Ellipse2D.Double(on ? x + w - h + 3 : x + 3, y + 3, h - 6, h - 6));
            g.dispose();
        }
    }

    static class AlarmPage extends Page {
        final AbstractTableModel model; final JTable table;
        AlarmPage() {
            super("Alarms", "Multiple alarms with repeat, snooze and custom sounds");
            body.setLayout(new GridBagLayout());
            final String[] cols = {"On", "Time", "Name", "Repeat", "Rings in"};
            model = new AbstractTableModel() {
                public int getRowCount() { return Store.alarms.size(); }
                public int getColumnCount() { return cols.length; }
                @Override public String getColumnName(int c) { return cols[c]; }
                public Object getValueAt(int r, int c) {
                    Alarm a = Store.alarms.get(r);
                    switch (c) {
                        case 0: return a.enabled;
                        case 1: return a.time.format(DateTimeFormatter.ofPattern(a.time.getSecond() == 0 ? "HH:mm" : "HH:mm:ss")) + (a.zone.equals(Settings.zone) ? "" : "  (" + a.zone.substring(a.zone.indexOf('/') + 1).replace('_', ' ') + ")");
                        case 2: return a.name;
                        case 3: return a.repeatText();
                        default: ZonedDateTime n = a.next(ZonedDateTime.now()); return n == null ? "-" : Ui.countdown(Duration.between(ZonedDateTime.now(), n));
                    }
                }
            };
            table = Ui.table(model);
            table.getColumnModel().getColumn(0).setCellRenderer(new SwitchCell());
            table.getColumnModel().getColumn(0).setMaxWidth(70);
            table.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    int r = table.rowAtPoint(e.getPoint()), c = table.columnAtPoint(e.getPoint());
                    if (r < 0) return;
                    if (c == 0) toggle(Store.alarms.get(r));
                    else if (e.getClickCount() == 2) edit();
                }
            });
            Card list = new Card("Your alarms", Ui.ALARM);
            list.add(Ui.scroll(table), BorderLayout.CENTER);
            list.add(flow(FlowLayout.LEFT, new GButton("Add alarm", Ui.PLUS, GButton.PRIMARY).onClick(this::addNew), new GButton("Edit", Ui.EDIT).onClick(this::edit),
                new GButton("Delete", Ui.TRASH, GButton.DANGER).onClick(this::delete)), BorderLayout.SOUTH);
            ((JPanel) list.getComponent(1)).setBorder(new EmptyBorder(10, 0, 0, 0));
            Card info = new Card("Countdown & upcoming", Ui.TIMER).painter(this::paintInfo);
            place(body, list, 0, 0, 3, 1, .62); place(body, info, 3, 0, 2, 1, .38);
        }
        void toggle(Alarm a) {
            if (!a.enabled && a.repeat == 0 && !ZonedDateTime.of(a.date, a.time, a.zoneId()).isAfter(ZonedDateTime.now())) {
                Dialogs.info(this, "Alarm time has passed", "Edit this one-time alarm and choose a future date and time.", Ui.ALARM); return;
            }
            a.enabled = !a.enabled; a.lastFired = null; Store.saveAlarms(); model.fireTableDataChanged();
        }
        void addNew() {
            Alarm a = AlarmDialog.edit(this, null);
            if (a != null) { Store.alarms.add(a); Store.saveAlarms(); model.fireTableDataChanged(); }
        }
        void edit() {
            int r = table.getSelectedRow();
            if (r < 0) { Dialogs.info(this, "Select an alarm", "Click an alarm in the list first.", Ui.ALARM); return; }
            if (AlarmDialog.edit(this, Store.alarms.get(r)) != null) { Store.saveAlarms(); model.fireTableDataChanged(); }
        }
        void delete() {
            int r = table.getSelectedRow();
            if (r < 0) { Dialogs.info(this, "Select an alarm", "Click an alarm in the list first.", Ui.ALARM); return; }
            if (Dialogs.confirm(this, "Delete alarm?", "\"" + Store.alarms.get(r).name + "\" will be removed.", "Delete")) {
                Store.alarms.remove(r); Store.saveAlarms(); model.fireTableDataChanged();
            }
        }
        @Override void onShow() { model.fireTableDataChanged(); }
        @Override void onTick(boolean sec) { if (sec) repaint(); }

        void paintInfo(Graphics2D g, int w, int h) {
            Theme t = Theme.cur; List<Object[]> up = Store.upcoming(5);
            if (up.isEmpty()) { Ui.textC(g, "No upcoming alarms", w / 2.0, h * .4, Ui.font(Font.BOLD, 18), t.sub); return; }
            ZonedDateTime when = (ZonedDateTime) up.get(0)[1]; Alarm a = (Alarm) up.get(0)[0];
            Duration d = Duration.between(ZonedDateTime.now(), when);
            double r = Math.min(w * .4, h * .3), cx = w / 2.0, cy = r + 8;
            Ui.ring(g, cx, cy, r, 11f, 1 - Math.min(1, d.getSeconds() / 86400.0), t.accent, t.accent2, false);
            Ui.textC(g, Ui.countdown(d), cx, cy - 6, Ui.fit(g, Ui.countdown(d), Ui.mono(Font.BOLD, 100), (float) (r * 1.5), (float) (r * .4)), t.text);
            Ui.textC(g, a.name, cx, cy + r * .38, Ui.px(Font.PLAIN, 13 * Settings.scale()), t.sub);
            double y = cy + r + 34;
            Ui.text(g, "UPCOMING", 0, y, Ui.font(Font.BOLD, 12), t.sub); y += 8;
            for (Object[] u : up) {
                y += 24 * Settings.scale(); if (y > h) break;
                Alarm x = (Alarm) u[0]; ZonedDateTime z = (ZonedDateTime) u[1];
                fitText(g, z.format(DateTimeFormatter.ofPattern("EEE dd MMM  HH:mm", Locale.ENGLISH)) + "   " + x.name, 0, y, w, 13, Font.PLAIN, t.text);
            }
        }
    }

    // ---------------------------------------------------------------- STOPWATCH
    static class StopwatchPage extends Page {
        final Ring ring = new Ring(true); final GButton startBtn, lapBtn;
        final AbstractTableModel lapModel; final JTable lapTable; final JLabel bestLbl = Ui.label("Best: -", 14, true, "accent"), slowLbl = Ui.label("Slowest: -", 14, true, "sub");
        StopwatchPage() {
            super("Stopwatch", "Millisecond precision with lap history");
            body.setLayout(new GridBagLayout());
            ring.ease = false;
            startBtn = new GButton("Start", Ui.PLAY, GButton.PRIMARY).onClick(this::toggle);
            lapBtn = new GButton("Lap", Ui.FLAG).onClick(this::lap);
            Card left = new Card("Stopwatch", Ui.STOPWATCH);
            left.add(ring, BorderLayout.CENTER);
            JPanel btns = flow(FlowLayout.CENTER, startBtn, lapBtn, new GButton("Reset", Ui.RESET, GButton.DANGER).onClick(this::reset));
            btns.setBorder(new EmptyBorder(10, 0, 0, 0)); left.add(btns, BorderLayout.SOUTH);
            final String[] cols = {"Lap", "Lap time", "Total", "Note"};
            lapModel = new AbstractTableModel() {
                public int getRowCount() { return SW.laps.size(); }
                public int getColumnCount() { return 4; }
                @Override public String getColumnName(int c) { return cols[c]; }
                public Object getValueAt(int r, int c) {
                    int i = SW.laps.size() - 1 - r; long[] l = SW.laps.get(i);
                    switch (c) {
                        case 0: return "Lap " + (i + 1);
                        case 1: return Ui.fmtMs(l[0], false);
                        case 2: return Ui.fmtMs(l[1], false);
                        default: return i == bestIdx() ? "Best" : i == slowIdx() ? "Slowest" : "";
                    }
                }
            };
            lapTable = Ui.table(lapModel);
            lapTable.setDefaultRenderer(Object.class, new Ui.Cell() {
                @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                    super.getTableCellRendererComponent(t, v, s, f, r, c);
                    int i = SW.laps.size() - 1 - r;
                    if (i == bestIdx()) setForeground(new Color(0x2ECC71)); else if (i == slowIdx()) setForeground(new Color(0xFF5C7A));
                    if (c >= 1) setFont(Ui.mono(Font.BOLD, 14 * Settings.scale()));
                    return this;
                }
            });
            Card right = new Card("Lap history", Ui.FLAG);
            JPanel sum = new JPanel(new GridLayout(1, 2, 10, 0)); sum.setOpaque(false); sum.setBorder(new EmptyBorder(0, 0, 8, 0)); sum.add(bestLbl); sum.add(slowLbl);
            right.add(sum, BorderLayout.NORTH); right.add(Ui.scroll(lapTable), BorderLayout.CENTER);
            place(body, left, 0, 0, 3, 1, .58); place(body, right, 3, 0, 2, 1, .42);
        }
        int bestIdx() { return lapIdx(true); }
        int slowIdx() { return lapIdx(false); }
        int lapIdx(boolean best) {
            if (SW.laps.size() < 2) return -1;
            int k = 0;
            for (int i = 1; i < SW.laps.size(); i++) if (best ? SW.laps.get(i)[0] < SW.laps.get(k)[0] : SW.laps.get(i)[0] > SW.laps.get(k)[0]) k = i;
            return k;
        }
        void toggle() { toggleStopwatch(); }
        void lap() { SW.lap(); lapModel.fireTableDataChanged(); summary(); }
        void reset() { SW.reset(); lapModel.fireTableDataChanged(); summary(); }
        void summary() {
            int b = bestIdx(), s = slowIdx();
            bestLbl.setText(b < 0 ? "Best: -" : "Best: Lap " + (b + 1) + "  " + Ui.fmtMs(SW.laps.get(b)[0], false));
            slowLbl.setText(s < 0 ? "Slowest: -" : "Slowest: Lap " + (s + 1) + "  " + Ui.fmtMs(SW.laps.get(s)[0], false));
        }
        @Override void onShow() { lapModel.fireTableDataChanged(); summary(); }
        @Override void onTick(boolean sec) {
            long ms = SW.elapsed();
            ring.value = (ms % 60000) / 60000.0; ring.big = Ui.fmtMs(ms, false);
            ring.small = SW.running ? "RUNNING" : ms > 0 ? "PAUSED" : "READY";
            startBtn.setText(SW.running ? "Pause" : ms > 0 ? "Resume" : "Start"); startBtn.setIcon(SW.running ? Ui.PAUSE : Ui.PLAY);
            lapBtn.setEnabled(SW.running);
            if (SW.running || sec) ring.repaint();
        }
    }

    // ---------------------------------------------------------------- COUNTDOWN TIMER
    static class TimerPage extends Page {
        final Ring ring = new Ring(false); final GButton startBtn;
        final GStepper hS = new GStepper(0, 0, 99, false), mS = new GStepper(5, 0, 59, false), sS = new GStepper(0, 0, 59, false);
        TimerPage() {
            super("Countdown Timer", "Presets, custom time and a sound alert");
            body.setLayout(new GridBagLayout());
            ring.c1 = Theme.cur.accent3;
            startBtn = new GButton("Start", Ui.PLAY, GButton.PRIMARY).onClick(DigitalClock::toggleTimer);
            Card c = new Card("Timer", Ui.TIMER); c.add(ring, BorderLayout.CENTER);
            String[] names = {"1 min", "5 min", "10 min", "15 min", "25 min", "60 min"}; int[] mins = {1, 5, 10, 15, 25, 60};
            JPanel presets = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0)); presets.setOpaque(false);
            for (int i = 0; i < names.length; i++) { final int m = mins[i]; presets.add(new GButton(names[i], -1, GButton.GHOST).onClick(() -> preset(m))); }
            Runnable custom = () -> {
                if (CD.running) return;
                long ms = (hS.getValue() * 3600L + mS.getValue() * 60L + sS.getValue()) * 1000;
                if (ms > 0) CD.set(ms);
            };
            hS.suffix(" h").onChange(custom); mS.suffix(" m").onChange(custom); sS.suffix(" s").onChange(custom);
            JPanel steps = flow(FlowLayout.CENTER, Ui.label("Custom:", 13, true, "sub"), hS, mS, sS);
            JPanel ctl = flow(FlowLayout.CENTER, startBtn, new GButton("Reset", Ui.RESET).onClick(CD::reset));
            JPanel south = new JPanel(new GridLayout(3, 1, 0, 10)); south.setOpaque(false); south.setBorder(new EmptyBorder(10, 0, 0, 0));
            south.add(presets); south.add(steps); south.add(ctl); c.add(south, BorderLayout.SOUTH);
            place(body, c, 0, 0, 1, 1, 1);
        }
        void preset(int m) { Sound.stop(); CD.set(m * 60000L); sync(); }
        void sync() { long s = CD.totalMs / 1000; hS.setValue((int) (s / 3600)); mS.setValue((int) ((s / 60) % 60)); sS.setValue((int) (s % 60)); }
        @Override void onShow() { if (!CD.running) sync(); }
        @Override void onTick(boolean sec) {
            long rem = CD.remaining();
            ring.c1 = Theme.cur.accent3;
            ring.value = CD.totalMs == 0 ? 0 : rem / (double) CD.totalMs;
            ring.big = String.format("%02d:%02d:%02d", rem / 3600000, (rem / 60000) % 60, (rem / 1000) % 60);
            ring.small = CD.running ? "RUNNING" : CD.finished ? "TIME IS UP" : rem < CD.totalMs ? "PAUSED" : "READY";
            startBtn.setText(CD.running ? "Pause" : rem < CD.totalMs && rem > 0 ? "Resume" : "Start"); startBtn.setIcon(CD.running ? Ui.PAUSE : Ui.PLAY);
            ring.repaint();
        }
    }

    // ---------------------------------------------------------------- POMODORO
    static class PomodoroPage extends Page {
        final Ring ring = new Ring(false); final GButton startBtn; final GSegment modes;
        final GStepper wS = new GStepper(Settings.pomWork, 1, 180, false), sS = new GStepper(Settings.pomShort, 1, 60, false), lS = new GStepper(Settings.pomLong, 1, 120, false);
        PomodoroPage() {
            super("Pomodoro", "Focus sessions, breaks and daily statistics");
            body.setLayout(new GridBagLayout());
            startBtn = new GButton("Start", Ui.PLAY, GButton.PRIMARY).onClick(DigitalClock::togglePomodoro);
            modes = new GSegment(PomodoroModel.MODES, 0, PM::setMode);
            Card left = new Card("Pomodoro timer", Ui.POMO);
            JPanel top = flow(FlowLayout.CENTER, modes); top.setBorder(new EmptyBorder(0, 0, 8, 0));
            left.add(top, BorderLayout.NORTH); left.add(ring, BorderLayout.CENTER);
            JPanel ctl = flow(FlowLayout.CENTER, startBtn, new GButton("Reset", Ui.RESET).onClick(PM::reset), new GButton("Skip", Ui.SKIP).onClick(PM::skip));
            ctl.setBorder(new EmptyBorder(10, 0, 0, 0)); left.add(ctl, BorderLayout.SOUTH);
            Card today = new Card("Today", Ui.CHECK).painter(this::paintToday);
            Card week = new Card("Last 7 days (focus minutes)", Ui.CAL).painter(Ui::weekly);
            Card cust = new Card("Custom durations (minutes)", Ui.GEAR);
            wS.onChange(this::save); sS.onChange(this::save); lS.onChange(this::save);
            JPanel rows = new JPanel(new GridLayout(1, 3, 10, 0)); rows.setOpaque(false);
            rows.add(ClockPage.labelled("Focus", wS)); rows.add(ClockPage.labelled("Short break", sS)); rows.add(ClockPage.labelled("Long break", lS));
            cust.add(rows, BorderLayout.CENTER);
            place(body, left, 0, 0, 3, 3, 3, 0); place(body, today, 3, 0, 2, 1, 2, .3); place(body, week, 3, 1, 2, 1, 2, .36); place(body, cust, 3, 2, 2, 1, 2, .34);
        }
        void save() { Settings.pomWork = wS.getValue(); Settings.pomShort = sS.getValue(); Settings.pomLong = lS.getValue(); Settings.save(); PM.applyDurations(); }
        void paintToday(Graphics2D g, int w, int h) {
            Theme t = Theme.cur; int[] d = Store.day(LocalDate.now());
            fitText(g, "" + d[0], 0, h * .62, w * .3, 54, Font.BOLD, t.accent);
            fitText(g, "sessions", 0, h * .62 + 18 * Settings.scale(), w * .3, 12, Font.PLAIN, t.sub);
            fitText(g, d[1] + " min", w * .38, h * .62, w * .3, 34, Font.BOLD, t.accent2);
            fitText(g, "focused", w * .38, h * .62 + 18 * Settings.scale(), w * .3, 12, Font.PLAIN, t.sub);
            int filled = PM.mode == 2 ? 4 : PM.cycle % 4;
            for (int i = 0; i < 4; i++) { g.setColor(i < filled ? t.accent : Ui.alpha(t.text, 40)); g.fill(new Ellipse2D.Double(w * .74 + i * 16, h * .5, 10, 10)); }
            Ui.text(g, "cycle", w * .74, h * .5 + 28, Ui.px(Font.PLAIN, 11), t.sub);
        }
        @Override void onShow() { wS.setValue(Settings.pomWork); sS.setValue(Settings.pomShort); lS.setValue(Settings.pomLong); }
        @Override void onTick(boolean sec) {
            double total = PM.totalMs(), rem = PM.remaining(); boolean work = PM.mode == 0;
            ring.c1 = work ? Theme.cur.accent : Theme.cur.accent3; ring.c2 = work ? Theme.cur.accent2 : Theme.cur.accent;
            ring.value = 1 - rem / total; long s = (long) Math.ceil(rem / 1000.0);
            ring.big = String.format("%02d:%02d", s / 60, s % 60);
            ring.small = PomodoroModel.MODES[PM.mode].toUpperCase() + (PM.running ? "" : "  -  PAUSED");
            if (modes.sel != PM.mode) modes.setSelected(PM.mode);
            startBtn.setText(PM.running ? "Pause" : rem < total ? "Resume" : "Start"); startBtn.setIcon(PM.running ? Ui.PAUSE : Ui.PLAY);
            ring.repaint(); if (sec) repaint();
        }
    }

    // ---------------------------------------------------------------- TO-DO LIST
    static class CheckCell extends JComponent implements TableCellRenderer {
        boolean done, sel; int row;
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
            done = ((Task) v).done; sel = s; row = r; return this;
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); Ui.aa(g); Theme t = Theme.cur;
            g.setColor(Ui.rowBg(row, sel)); g.fillRect(0, 0, getWidth(), getHeight());
            double d = 20, x = (getWidth() - d) / 2, y = (getHeight() - d) / 2;
            if (done) { g.setPaint(new GradientPaint(0, 0, t.accent, 20, 20, t.accent2)); g.fill(new Ellipse2D.Double(x, y, d, d)); Ui.icon(g, Ui.CHECK, x + d / 2, y + d / 2, 14, Ui.isLight(t.accent) ? new Color(10, 16, 32) : Color.WHITE); }
            else { g.setColor(Ui.alpha(t.sub, 200)); g.setStroke(new BasicStroke(2f)); g.draw(new Ellipse2D.Double(x, y, d, d)); }
            g.dispose();
        }
    }

    static class TaskPage extends Page {
        final ArrayList<Task> view = new ArrayList<>();
        final GField search = new GField(11, "Search tasks..."), quick = new GField(22, "Quick add - type a task and press Enter");
        final GSegment filter; final GBar bar = new GBar(); final JLabel progress = Ui.label("", 15, true, "text");
        final AbstractTableModel model; final JTable table;
        static final DateTimeFormatter DUE = DateTimeFormatter.ofPattern("dd MMM, HH:mm", Locale.ENGLISH);

        TaskPage() {
            super("To-Do List", "Priorities, due dates, categories and daily progress");
            body.setLayout(new GridBagLayout());
            filter = new GSegment(new String[]{"All", "Pending", "Completed"}, 0, i -> refresh());
            Card top = new Card("Daily progress", Ui.CHECK);
            JPanel pp = new JPanel(new BorderLayout(0, 8)); pp.setOpaque(false); pp.add(progress, BorderLayout.NORTH); pp.add(bar, BorderLayout.CENTER);
            top.add(pp, BorderLayout.CENTER);
            final String[] cols = {"", "Task", "Priority", "Category", "Due"};
            model = new AbstractTableModel() {
                public int getRowCount() { return view.size(); }
                public int getColumnCount() { return cols.length; }
                @Override public String getColumnName(int c) { return cols[c]; }
                public Object getValueAt(int r, int c) { return view.get(r); }
            };
            table = Ui.table(model);
            table.getColumnModel().getColumn(0).setCellRenderer(new CheckCell());
            table.getColumnModel().getColumn(0).setMaxWidth(60);
            Ui.Cell tc = new Ui.Cell() {
                @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                    Task k = (Task) v; super.getTableCellRendererComponent(t, "", s, f, r, c);
                    Theme th = Theme.cur; Font base = Ui.font(Font.PLAIN, 14); setForeground(k.done ? Ui.alpha(th.sub, 190) : th.text);
                    switch (c) {
                        case 1: setText(k.title);
                            if (k.done) { Map<TextAttribute, Object> m = new HashMap<>(); m.put(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON); base = base.deriveFont(m); }
                            break;
                        case 2: setText("\u25CF  " + Task.PRI[k.pri]); setForeground(k.pri == 0 ? new Color(0xFF5C7A) : k.pri == 1 ? new Color(0xFFB454) : new Color(0x4ADE80)); break;
                        case 3: setText(k.cat); break;
                        default: setText(k.due == null ? "-" : k.due.format(DUE));
                            if (k.due != null && !k.done && k.due.isBefore(LocalDateTime.now())) setForeground(new Color(0xFF5C7A));
                    }
                    setFont(base); return this;
                }
            };
            for (int i = 1; i < 5; i++) table.getColumnModel().getColumn(i).setCellRenderer(tc);
            table.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    int r = table.rowAtPoint(e.getPoint()), c = table.columnAtPoint(e.getPoint());
                    if (r < 0) return;
                    if (c == 0) { Task k = view.get(r); k.done = !k.done; k.doneOn = k.done ? LocalDate.now() : null; Store.saveTasks(); refresh(); }
                    else if (e.getClickCount() == 2) edit();
                }
            });
            onType(search, this::refresh);
            quick.addActionListener(e -> {
                String s = quick.getText().trim();
                if (!s.isEmpty()) { Task k = new Task(); k.title = s; Store.tasks.add(k); Store.saveTasks(); quick.setText(""); refresh(); }
            });
            JPanel tools = new JPanel(new BorderLayout(10, 0)); tools.setOpaque(false); tools.setBorder(new EmptyBorder(0, 0, 10, 0));
            tools.add(flow(FlowLayout.LEFT, search, filter), BorderLayout.WEST);
            tools.add(flow(FlowLayout.RIGHT, new GButton("Add task", Ui.PLUS, GButton.PRIMARY).onClick(this::addNew), new GButton("Edit", Ui.EDIT).onClick(this::edit),
                new GButton("Delete", Ui.TRASH, GButton.DANGER).onClick(this::delete), new GButton("CSV", Ui.EXPORT, GButton.GHOST).onClick(this::export)), BorderLayout.EAST);
            Card list = new Card("Tasks", Ui.TODO);
            JPanel north = new JPanel(new BorderLayout(0, 8)); north.setOpaque(false); north.add(tools, BorderLayout.NORTH); north.add(quick, BorderLayout.SOUTH);
            list.add(north, BorderLayout.NORTH); list.add(Ui.scroll(table), BorderLayout.CENTER);
            place(body, top, 0, 0, 1, 1, .17); place(body, list, 0, 1, 1, 1, .83);
            refresh();
        }
        static void onType(JTextField f, Runnable r) {
            f.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
            });
        }
        void refresh() {
            view.clear(); String q = search.getText().trim().toLowerCase(Locale.ROOT); int f = filter == null ? 0 : filter.sel;
            for (Task t : Store.tasks) {
                if (f == 1 && t.done) continue;
                if (f == 2 && !t.done) continue;
                if (!q.isEmpty() && !(t.title.toLowerCase(Locale.ROOT).contains(q) || t.cat.toLowerCase(Locale.ROOT).contains(q))) continue;
                view.add(t);
            }
            view.sort(Comparator.comparing((Task t) -> t.done).thenComparingInt(t -> t.pri)
                .thenComparing(t -> t.due == null ? LocalDateTime.MAX : t.due).thenComparing(t -> t.title.toLowerCase(Locale.ROOT)));
            model.fireTableDataChanged();
            int[] s = Store.todayStats(); int pct = s[1] == 0 ? 0 : (int) Math.round(100.0 * s[0] / s[1]);
            int pending = 0; for (Task t : Store.tasks) if (!t.done) pending++;
            progress.setText(pct + "% of today's tasks completed  (" + s[0] + " of " + s[1] + ")   \u2022   " + pending + " pending in total");
            bar.setValue(s[1] == 0 ? 0 : s[0] / (double) s[1]);
        }
        void addNew() { Task t = TaskDialog.edit(this, null); if (t != null) { Store.tasks.add(t); Store.saveTasks(); refresh(); } }
        void edit() {
            int r = table.getSelectedRow();
            if (r < 0) { Dialogs.info(this, "Select a task", "Click a task in the list first.", Ui.TODO); return; }
            if (TaskDialog.edit(this, view.get(r)) != null) { Store.saveTasks(); refresh(); }
        }
        void delete() {
            int r = table.getSelectedRow();
            if (r < 0) { Dialogs.info(this, "Select a task", "Click a task in the list first.", Ui.TODO); return; }
            Task t = view.get(r);
            if (Dialogs.confirm(this, "Delete task?", "\"" + t.title + "\" will be removed.", "Delete")) { Store.tasks.remove(t); Store.saveTasks(); refresh(); }
        }
        void export() {
            JFileChooser fc = new JFileChooser(); fc.setSelectedFile(new File("tasks.csv"));
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            File f = fc.getSelectedFile(); if (!f.getName().toLowerCase().endsWith(".csv")) f = new File(f.getPath() + ".csv");
            try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))) {
                w.write("Title,Priority,Category,Due,Status,Created\n");
                for (Task t : Store.tasks) w.write(csv(t.title) + "," + Task.PRI[t.pri] + "," + csv(t.cat) + "," + (t.due == null ? "" : t.due.toString().replace('T', ' ')) + "," + (t.done ? "Completed" : "Pending") + "," + t.created + "\n");
                Dialogs.info(this, "Export complete", "Saved " + Store.tasks.size() + " tasks to\n" + f.getName(), Ui.EXPORT);
            } catch (IOException ex) { Dialogs.info(this, "Export failed", ex.getMessage(), Ui.TRASH); }
        }
        static String csv(String s) { return "\"" + s.replace("\"", "\"\"") + "\""; }
        @Override void onShow() { refresh(); }
        @Override void onTick(boolean sec) { if (sec) table.repaint(); }
        void focusSearch() { search.requestFocusInWindow(); }
    }

    // ---------------------------------------------------------------- SETTINGS
    static class ThemeTile extends JComponent {
        final Theme th;
        ThemeTile(Theme th) {
            this.th = th; setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); setOpaque(false);
            addMouseListener(new MouseAdapter() { @Override public void mousePressed(MouseEvent e) { Settings.theme = ThemeTile.this.th.name; Settings.save(); app.applyTheme(); } });
        }
        @Override public Dimension getPreferredSize() { return new Dimension(Math.round(130 * Settings.scale()), Math.round(88 * Settings.scale())); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); Ui.aa(g); int w = getWidth(), h = getHeight(); boolean sel = Settings.theme.equals(th.name);
            RoundRectangle2D.Double r = new RoundRectangle2D.Double(2, 2, w - 4, h - 4, 16, 16);
            g.setPaint(new GradientPaint(0, 0, th.bg1, w, h, th.bg2)); g.fill(r);
            g.setColor(th.card); g.fill(new RoundRectangle2D.Double(10, 10, w - 20, h * .38, 10, 10));
            Color[] cs = {th.accent, th.accent2, th.accent3};
            for (int i = 0; i < 3; i++) { g.setColor(cs[i]); g.fill(new Ellipse2D.Double(14 + i * 16, 18, 10, 10)); }
            Ui.textC(g, th.name, w / 2.0, h - 16, Ui.font(Font.BOLD, 12), th.text);
            g.setStroke(new BasicStroke(sel ? 3f : 1f)); g.setColor(sel ? Theme.cur.accent : Theme.cur.line); g.draw(r);
            g.dispose();
        }
    }

    static class Swatches extends JComponent {
        static final int[] COLORS = {0x00E5FF, 0x3B82F6, 0x8B5CF6, 0xFF2BD6, 0xFF5C7A, 0xFFB454, 0x2ECC71, 0xFCEE09};
        Swatches() {
            setOpaque(false); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    int i = e.getX() / 40;
                    if (i >= 0 && i < COLORS.length) { Settings.customAccent = true; Settings.accentRgb = COLORS[i]; Settings.save(); app.applyTheme(); }
                }
            });
        }
        @Override public Dimension getPreferredSize() { return new Dimension(COLORS.length * 40, 36); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); Ui.aa(g);
            for (int i = 0; i < COLORS.length; i++) {
                g.setColor(new Color(COLORS[i])); g.fill(new Ellipse2D.Double(i * 40 + 4, 4, 28, 28));
                if (Settings.customAccent && Settings.accentRgb == COLORS[i]) { g.setColor(Theme.cur.text); g.setStroke(new BasicStroke(2.5f)); g.draw(new Ellipse2D.Double(i * 40 + 1, 1, 34, 34)); }
            }
            g.dispose();
        }
    }

    static class SettingsPage extends Page {
        final GSegment fontSeg, styleSeg; final GSwitch sound, anim, h24, chime, top; final GCombo<String> saver;
        static final int[] SAVER_MIN = {0, 1, 2, 5, 10};

        SettingsPage() {
            super("Settings", "Themes, accent colours and behaviour - changes apply instantly");
            body.setLayout(new BorderLayout());
            ScrollPanel sp = new ScrollPanel(new BorderLayout()); JPanel col = new JPanel(); col.setOpaque(false); col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
            // Appearance
            Card ap = new Card("Appearance", Ui.GEAR); JPanel apBody = new JPanel(); apBody.setOpaque(false); apBody.setLayout(new BoxLayout(apBody, BoxLayout.Y_AXIS));
            JPanel tiles = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4)); tiles.setOpaque(false);
            for (Theme t : Theme.ALL) tiles.add(new ThemeTile(t));
            JPanel acc = flow(FlowLayout.LEFT, new Swatches(), new GButton("Custom colour...", -1, GButton.GHOST).onClick(() -> {
                Color c = JColorChooser.showDialog(app, "Choose accent colour", Theme.cur.accent);
                if (c != null) { Settings.customAccent = true; Settings.accentRgb = c.getRGB() & 0xFFFFFF; Settings.save(); app.applyTheme(); }
            }), new GButton("Use theme colour", Ui.RESET).onClick(() -> { Settings.customAccent = false; Settings.save(); app.applyTheme(); }));
            fontSeg = new GSegment(new String[]{"Small", "Medium", "Large"}, Settings.fontSize, i -> { Settings.fontSize = i; Settings.save(); app.applyTheme(); });
            styleSeg = new GSegment(new String[]{"Neon glow", "Flip cards", "Minimal", "Retro"}, Settings.clockStyle, i -> { Settings.clockStyle = i; Settings.save(); });
            apBody.add(stacked("Theme", "Pick a look - the whole app changes immediately", tiles));
            apBody.add(stacked("Accent colour", "Pick a colour or use the one from the theme", acc));
            apBody.add(setting("Font size", "Scales text across the whole application", flow(FlowLayout.RIGHT, fontSeg)));
            apBody.add(setting("Clock style", "Digital and analog clock design", flow(FlowLayout.RIGHT, styleSeg)));
            ap.add(apBody, BorderLayout.CENTER);
            // Behaviour
            Card be = new Card("Behaviour", Ui.CLOCK); JPanel beBody = new JPanel(); beBody.setOpaque(false); beBody.setLayout(new BoxLayout(beBody, BoxLayout.Y_AXIS));
            sound = new GSwitch(Settings.sound, on -> { Settings.sound = on; Settings.save(); });
            anim = new GSwitch(Settings.anim, on -> { Settings.anim = on; Settings.save(); });
            h24 = new GSwitch(Settings.h24, on -> { Settings.h24 = on; Settings.save(); });
            chime = new GSwitch(Settings.chime, on -> { Settings.chime = on; Settings.save(); });
            top = new GSwitch(Settings.onTop, on -> app.setOnTop(on));
            saver = new GCombo<>(new String[]{"Off", "1 minute", "2 minutes", "5 minutes", "10 minutes"});
            saver.addActionListener(e -> { Settings.saver = SAVER_MIN[saver.getSelectedIndex()]; Settings.save(); });
            beBody.add(setting("Sound alerts", "Alarm, timer and pomodoro sounds", flow(FlowLayout.RIGHT, sound)));
            beBody.add(setting("Animations", "Smooth hands, glow, hover and fade effects", flow(FlowLayout.RIGHT, anim)));
            beBody.add(setting("24-hour format", "Show 13:00 instead of 01:00 PM", flow(FlowLayout.RIGHT, h24)));
            beBody.add(setting("Hourly chime", "A soft chime at the start of every hour", flow(FlowLayout.RIGHT, chime)));
            beBody.add(setting("Always on top", "Keep the clock above other windows", flow(FlowLayout.RIGHT, top)));
            beBody.add(setting("Screensaver", "Start the clock screensaver after this idle time", flow(FlowLayout.RIGHT, saver)));
            be.add(beBody, BorderLayout.CENTER);
            // Tools
            Card to = new Card("Tools", Ui.FULL);
            JPanel tb = flow(FlowLayout.LEFT, new GButton("Full-screen clock", Ui.FULL, GButton.PRIMARY).onClick(() -> app.openFullScreen(false)),
                new GButton("Preview screensaver", Ui.MOON).onClick(() -> app.openFullScreen(true)),
                new GButton("Reset settings", Ui.RESET, GButton.DANGER).onClick(this::resetAll));
            to.add(tb, BorderLayout.CENTER);
            for (JComponent c : new JComponent[]{ap, be, to}) { c.setAlignmentX(Component.LEFT_ALIGNMENT); col.add(c); }
            sp.add(col, BorderLayout.NORTH);
            body.add(Ui.scroll(sp), BorderLayout.CENTER);
            syncControls();
        }
        JPanel setting(String name, String hint, Component ctl) {
            JPanel r = new JPanel(new BorderLayout(16, 0)); r.setOpaque(false); r.setBorder(new EmptyBorder(8, 0, 8, 0));
            JPanel t = new JPanel(new GridLayout(2, 1)); t.setOpaque(false);
            t.add(Ui.label(name, 15, true, "text")); t.add(Ui.label(hint, 12, false, "sub"));
            r.add(t, BorderLayout.WEST); r.add(ctl, BorderLayout.EAST);
            r.setAlignmentX(Component.LEFT_ALIGNMENT);
            return r;
        }
        JPanel stacked(String name, String hint, Component ctl) {
            JPanel r = new JPanel(new BorderLayout(0, 8)); r.setOpaque(false); r.setBorder(new EmptyBorder(8, 0, 8, 0));
            JPanel t = new JPanel(new GridLayout(2, 1)); t.setOpaque(false);
            t.add(Ui.label(name, 15, true, "text")); t.add(Ui.label(hint, 12, false, "sub"));
            JPanel w = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); w.setOpaque(false); w.add(ctl);
            r.add(t, BorderLayout.NORTH); r.add(w, BorderLayout.CENTER); r.setAlignmentX(Component.LEFT_ALIGNMENT);
            return r;
        }
        void syncControls() {
            fontSeg.setSelected(Settings.fontSize); styleSeg.setSelected(Settings.clockStyle);
            sound.setOn(Settings.sound); anim.setOn(Settings.anim); h24.setOn(Settings.h24); chime.setOn(Settings.chime); top.setOn(Settings.onTop);
            for (int i = 0; i < SAVER_MIN.length; i++) if (SAVER_MIN[i] == Settings.saver) saver.setSelectedIndex(i);
        }
        void resetAll() {
            if (!Dialogs.confirm(this, "Reset settings?", "All preferences return to their defaults. Alarms and tasks are kept.", "Reset")) return;
            Settings.theme = "Dark Navy"; Settings.customAccent = false; Settings.fontSize = 1; Settings.clockStyle = 0; Settings.sound = true; Settings.anim = true;
            Settings.h24 = false; Settings.chime = false; Settings.onTop = false; Settings.saver = 0; Settings.save();
            app.setOnTop(false); app.applyTheme(); syncControls();
        }
        @Override void onShow() { syncControls(); }
    }

    // ---------------------------------------------------------------- HELP & ABOUT
    static class HelpPage extends Page {
        HelpPage() {
            super("Help & About", "Shortcuts, tips and information");
            body.setLayout(new GridBagLayout());
            String help = "GETTING AROUND\n  Use the sidebar to switch pages. Ctrl+1 ... Ctrl+9 open the first nine pages, Ctrl+0 opens Settings.\n\n"
                + "KEYBOARD SHORTCUTS\n  F11              Full-screen clock (Esc or click to leave)\n  Ctrl+Shift+S     Clock screensaver\n  Ctrl+T           Always on top on/off\n"
                + "  Ctrl+N           New alarm / new task (on those pages)\n  Ctrl+F           Search tasks\n  Ctrl+Space      Start / pause stopwatch, timer or pomodoro (current page)\n"
                + "  Ctrl+L           Record a lap (stopwatch page)\n  Ctrl+R           Reset stopwatch, timer or pomodoro (current page)\n  F1               This help page\n\n"
                + "ALARMS\n  Add as many alarms as you like. Repeat options: Once, Daily, Weekdays, Weekends or Custom days. Click the switch to enable/disable, double-click a row to edit. "
                + "When an alarm rings you can snooze for 5, 10 or 15 minutes.\n\n"
                + "STOPWATCH / TIMER / POMODORO\n  The three tools keep running while you visit other pages. The dashboard shows their live state.\n\n"
                + "TO-DO LIST\n  Click the circle to complete a task, double-click to edit. Tasks save automatically. Use Export CSV to open them in Excel.\n\n"
                + "SCREENSAVER\n  Settings > Screensaver starts a drifting clock after the chosen idle time.\n\n"
                + "YOUR DATA\n  Settings, alarms, tasks and statistics are stored as small text files in the folder\n  " + Settings.dir().getAbsolutePath() + "\n\n"
                + "WEATHER\n  The weather card shows sample data only (no internet connection is used). Click it to change the sample.";
            JTextArea ta = new JTextArea(help); ta.setEditable(false); ta.setLineWrap(true); ta.setWrapStyleWord(true); ta.setOpaque(false); ta.setBorder(new EmptyBorder(0, 0, 0, 8));
            ta.setFont(Ui.font(Font.PLAIN, 14)); ta.setForeground(Theme.cur.text);
            Card h = new Card("Help & shortcuts", Ui.HELP); h.add(Ui.scroll(ta), BorderLayout.CENTER);
            Card a = new Card("About", Ui.CLOCK).painter(this::paintAbout);
            place(body, h, 0, 0, 3, 1, .6); place(body, a, 3, 0, 2, 1, .4);
        }
        void paintAbout(Graphics2D g, int w, int h) {
            Theme t = Theme.cur; double r = Math.min(w * .22, h * .17), cx = w / 2.0, cy = r + 6;
            Ui.ring(g, cx, cy, r, 7f, (System.currentTimeMillis() % 60000) / 60000.0, t.accent, t.accent2, true);
            Ui.icon(g, Ui.CLOCK, cx, cy, r * .9, t.accent);
            double y = cy + r + 40 * Settings.scale();
            Ui.textC(g, "Enhanced Digital Clock", cx, y, Ui.fit(g, "Enhanced Digital Clock", Ui.px(Font.BOLD, 22), w * .95f, 22 * Settings.scale()), t.text); y += 26 * Settings.scale();
            Ui.textC(g, "Smart Desktop Productivity Suite  -  v2.0", cx, y, Ui.font(Font.PLAIN, 13), t.sub); y += 36 * Settings.scale();
            String[] lines = {"Clock, world clocks, alarms", "Stopwatch, timer, pomodoro", "To-do list and calendar", "Pure Java Swing + Graphics2D", "No external libraries"};
            for (String s : lines) { Ui.textC(g, s, cx, y, Ui.font(Font.PLAIN, 13), t.text); y += 22 * Settings.scale(); }
        }
        @Override void onTick(boolean sec) { repaint(); }
    }

    // =================================================================
    // 7. FULL SCREEN CLOCK, SCREENSAVER AND SPLASH SCREEN
    // =================================================================
    static class FullScreenWindow extends JFrame implements Ticker.Tick {
        final boolean saver; final DigitalDisplay dd = new DigitalDisplay(Settings.zoneId(), true); final JPanel panel;
        final Runnable onClose; final long born = System.currentTimeMillis(); final java.util.Random rnd = new java.util.Random();
        final double[][] stars = new double[110][4];   // x, y, speed, phase
        double x = 80, y = 80, vx = 1.1, vy = 0.8; Point mouse0; boolean closed;

        FullScreenWindow(boolean saver, Runnable onClose) {
            this.saver = saver; this.onClose = onClose;
            setUndecorated(true); setTitle("Clock");
            for (double[] s : stars) { s[0] = rnd.nextDouble(); s[1] = rnd.nextDouble(); s[2] = 0.0002 + rnd.nextDouble() * 0.0008; s[3] = rnd.nextDouble() * 6.28; }
            panel = new JPanel(null) {
                @Override protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create(); Ui.aa(g); Theme t = Theme.cur; int w = getWidth(), h = getHeight();
                    g.setPaint(new GradientPaint(0, 0, saver ? Color.BLACK : t.bg1, w, h, saver ? t.bg1 : t.bg2)); g.fillRect(0, 0, w, h);
                    long ms = System.currentTimeMillis();
                    for (double[] s : stars) {
                        double sx = ((s[0] + (ms % 100000) * s[2]) % 1.0) * w, sy = s[1] * h, a = 0.4 + 0.6 * Math.abs(Math.sin(ms / 900.0 + s[3]));
                        g.setColor(Ui.alpha(t.accent, (int) (150 * a))); g.fill(new Ellipse2D.Double(sx, sy, 2.2, 2.2));
                    }
                    g.dispose();
                }
            };
            panel.add(dd); setContentPane(panel);
            if (saver) setCursor(Toolkit.getDefaultToolkit().createCustomCursor(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), new Point(0, 0), "blank"));
            KeyAdapter ka = new KeyAdapter() { @Override public void keyPressed(KeyEvent e) { if (saver || e.getKeyCode() == KeyEvent.VK_ESCAPE || e.getKeyCode() == KeyEvent.VK_F11) close(); } };
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { close(); }
                @Override public void mouseMoved(MouseEvent e) {
                    if (!saver || System.currentTimeMillis() - born < 1200) return;
                    if (mouse0 == null) mouse0 = e.getPoint(); else if (mouse0.distance(e.getPoint()) > 12) close();
                }
            };
            for (Component c : new Component[]{this, panel, dd}) { c.addKeyListener(ka); c.addMouseListener(ma); c.addMouseMotionListener(ma); }
            setFocusable(true);
            GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            if (gd.isFullScreenSupported()) { gd.setFullScreenWindow(this); }
            else { setExtendedState(JFrame.MAXIMIZED_BOTH); setVisible(true); }
            requestFocus();
            layoutClock();
            Ticker.add(this);
        }
        void layoutClock() {
            int w = panel.getWidth(), h = panel.getHeight();
            if (w < 10) { Dimension d = Toolkit.getDefaultToolkit().getScreenSize(); w = d.width; h = d.height; }
            int cw = (int) Math.min(w * .84, h * 2.6), ch = (int) (cw * .34);
            if (saver) {
                cw = (int) (cw * .62); ch = (int) (ch * .62);
                x += vx; y += vy;
                if (x < 0 || x + cw > w) { vx = -vx; x = Math.max(0, Math.min(x, w - cw)); }
                if (y < 0 || y + ch > h) { vy = -vy; y = Math.max(0, Math.min(y, h - ch)); }
                dd.setBounds((int) x, (int) y, cw, ch);
            } else dd.setBounds((w - cw) / 2, (h - ch) / 2, cw, ch);
        }
        @Override public void tick(boolean sec) { layoutClock(); panel.repaint(); }
        void close() {
            if (closed) return;
            closed = true; Ticker.remove(this);
            GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            if (gd.getFullScreenWindow() == this) gd.setFullScreenWindow(null);
            dispose();
            if (onClose != null) onClose.run();
        }
    }

    static class Splash extends JWindow {
        final long t0 = System.currentTimeMillis(); final Runnable done; Timer timer;
        static final String[] STEPS = {"Loading settings...", "Reading alarms and tasks...", "Preparing the dashboard...", "Starting clocks..."};
        Splash(Runnable done) {
            this.done = done;
            JPanel p = new JPanel() {
                @Override protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create(); Ui.aa(g); Theme t = Theme.cur; int w = getWidth(), h = getHeight();
                    double prog = Math.min(1, (System.currentTimeMillis() - t0) / 1700.0);
                    g.setPaint(new GradientPaint(0, 0, t.bg1, w, h, t.bg2)); g.fillRect(0, 0, w, h);
                    g.setPaint(new RadialGradientPaint(new Point2D.Double(w * .8, 0), w * .7f, new float[]{0f, 1f}, new Color[]{Ui.alpha(t.accent2, 70), Ui.alpha(t.accent2, 0)})); g.fillRect(0, 0, w, h);
                    double cx = w / 2.0, cy = 105, r = 52;
                    Ui.ring(g, cx, cy, r, 8f, prog, t.accent, t.accent2, true);
                    double a = (System.currentTimeMillis() % 3000) / 3000.0 * Math.PI * 2;
                    g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.setColor(t.accent3);
                    g.draw(new Line2D.Double(cx, cy, cx + Math.sin(a) * r * .65, cy - Math.cos(a) * r * .65));
                    g.setColor(t.text); g.draw(new Line2D.Double(cx, cy, cx + Math.sin(a / 12) * r * .4, cy - Math.cos(a / 12) * r * .4));
                    Ui.textC(g, "ENHANCED DIGITAL CLOCK", cx, 195, Ui.px(Font.BOLD, 24), t.text);
                    Ui.textC(g, "Smart Desktop Productivity Suite", cx, 218, Ui.px(Font.PLAIN, 13), t.sub);
                    g.setColor(Ui.alpha(t.text, 30)); g.fill(new RoundRectangle2D.Double(60, 250, w - 120, 6, 6, 6));
                    g.setPaint(new GradientPaint(60, 0, t.accent, w - 60, 0, t.accent2)); g.fill(new RoundRectangle2D.Double(60, 250, Math.max(6, (w - 120) * prog), 6, 6, 6));
                    Ui.textC(g, STEPS[Math.min(STEPS.length - 1, (int) (prog * STEPS.length))], cx, 275, Ui.px(Font.PLAIN, 12), t.sub);
                    g.setColor(Ui.alpha(t.accent, 120)); g.setStroke(new BasicStroke(2f)); g.drawRect(1, 1, w - 3, h - 3);
                    g.dispose();
                }
            };
            p.setPreferredSize(new Dimension(540, 300)); setContentPane(p); pack(); setLocationRelativeTo(null);
        }
        void start() {
            setVisible(true);
            timer = new Timer(30, e -> {
                getContentPane().repaint();
                if (System.currentTimeMillis() - t0 > 1800) { timer.stop(); dispose(); done.run(); }
            });
            timer.start();
        }
    }

    // =================================================================
    // 8. MAIN WINDOW  (sidebar, navigation, shortcuts, master tick)
    // =================================================================
    static DigitalClock app;

    static final String[] KEYS = {"dashboard", "clock", "world", "alarms", "stopwatch", "timer", "pomodoro", "tasks", "calendar", "settings", "help"};
    static final String[] LABELS = {"Dashboard", "Clock", "World Clock", "Alarms", "Stopwatch", "Timer", "Pomodoro", "To-Do List", "Calendar", "Settings", "Help & About"};
    static final int[] ICONS = {Ui.HOME, Ui.CLOCK, Ui.WORLD, Ui.ALARM, Ui.STOPWATCH, Ui.TIMER, Ui.POMO, Ui.TODO, Ui.CAL, Ui.GEAR, Ui.HELP};

    final CardLayout cards = new CardLayout();
    final FadePanel content = new FadePanel(cards);
    final Page[] pages = new Page[KEYS.length];
    final GButton[] nav = new GButton[KEYS.length];
    String current = "dashboard";
    FullScreenWindow fs;
    long lastActivity = System.currentTimeMillis();
    int lastChimeKey = -1;
    AWTEventListener idleListener;

    public DigitalClock() {
        app = this;
        setTitle("Enhanced Digital Clock");
        setIconImage(Ui.appIcon());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1060, 720));
        setSize(1260, 800);
        setLocationRelativeTo(null);
        setAlwaysOnTop(Settings.onTop);

        Backdrop root = new Backdrop(); root.setLayout(new BorderLayout());
        setContentPane(root);
        root.add(buildSidebar(), BorderLayout.WEST);
        pages[0] = new DashboardPage(); pages[1] = new ClockPage(); pages[2] = new WorldPage(); pages[3] = new AlarmPage();
        pages[4] = new StopwatchPage(); pages[5] = new TimerPage(); pages[6] = new PomodoroPage(); pages[7] = new TaskPage();
        pages[8] = new CalendarPage(); pages[9] = new SettingsPage(); pages[10] = new HelpPage();
        for (int i = 0; i < pages.length; i++) content.add(pages[i], KEYS[i]);
        root.add(content, BorderLayout.CENTER);
        show("dashboard");
        installShortcuts();

        addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { shutdown(); } });
        idleListener = e -> lastActivity = System.currentTimeMillis();
        Toolkit.getDefaultToolkit().addAWTEventListener(idleListener, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
        Ticker.add(this::masterTick);
        Ticker.start();
    }

    // ---------------------------------------------------------------- sidebar
    private JPanel buildSidebar() {
        JPanel side = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create(); Ui.aa(g); Theme t = Theme.cur; int w = getWidth(), h = getHeight();
                g.setColor(Ui.alpha(t.side, 235)); g.fillRect(0, 0, w, h);
                g.setColor(t.line); g.fillRect(w - 1, 0, 1, h);
                // logo
                g.setPaint(new GradientPaint(16, 20, t.accent, 56, 60, t.accent2)); g.fill(new RoundRectangle2D.Double(18, 22, 40, 40, 14, 14));
                Ui.icon(g, Ui.CLOCK, 38, 42, 24, Ui.isLight(t.accent) ? new Color(10, 16, 32) : Color.WHITE);
                Ui.text(g, "CLOCK", 68, 38, Ui.font(Font.BOLD, 16), t.text);
                Ui.text(g, "Productivity Suite", 68, 56, Ui.font(Font.PLAIN, 11), t.sub);
                // mini clock at the bottom
                ZonedDateTime now = ZonedDateTime.now(Settings.zoneId());
                String ts = now.format(DateTimeFormatter.ofPattern(Settings.h24 ? "HH:mm:ss" : "hh:mm:ss a", Locale.ENGLISH));
                Ui.textC(g, ts, w / 2.0, h - 40, Ui.fit(g, ts, Ui.mono(Font.BOLD, 100), w - 40f, 20f * Settings.scale()), t.accent);
                Ui.textC(g, now.format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH)), w / 2.0, h - 18, Ui.font(Font.PLAIN, 11), t.sub);
                g.dispose();
            }
        };
        side.setOpaque(false);
        side.setPreferredSize(new Dimension(Math.round(224 * Settings.scale()), 100));
        side.add(Box.createVerticalStrut(86), BorderLayout.NORTH);
        JPanel list = new JPanel(); list.setOpaque(false); list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS)); list.setBorder(new EmptyBorder(0, 12, 0, 12));
        for (int i = 0; i < KEYS.length; i++) {
            final String key = KEYS[i];
            nav[i] = new GButton(LABELS[i], ICONS[i], GButton.NAV).onClick(() -> show(key));
            nav[i].setAlignmentX(Component.LEFT_ALIGNMENT);
            nav[i].setMaximumSize(new Dimension(Integer.MAX_VALUE, nav[i].getPreferredSize().height));
            list.add(nav[i]); list.add(Box.createVerticalStrut(2));
        }
        side.add(list, BorderLayout.CENTER);
        JPanel south = new JPanel(); south.setOpaque(false); south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS)); south.setBorder(new EmptyBorder(0, 12, 0, 12));
        GButton full = new GButton("Full screen (F11)", Ui.FULL, GButton.GHOST).onClick(() -> openFullScreen(false));
        full.setAlignmentX(Component.LEFT_ALIGNMENT); full.setMaximumSize(new Dimension(Integer.MAX_VALUE, full.getPreferredSize().height));
        south.add(full); south.add(Box.createVerticalStrut(70));
        side.add(south, BorderLayout.SOUTH);
        return side;
    }

    void show(String key) {
        current = key;
        cards.show(content, key);
        for (int i = 0; i < KEYS.length; i++) { nav[i].setSelected(KEYS[i].equals(key)); if (KEYS[i].equals(key)) pages[i].onShow(); }
        content.fade = Settings.anim ? 0f : 1f;
        content.repaint();
    }
    Page page(String key) { for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return pages[i]; return null; }

    /** Applies the current theme / font size to the whole application immediately. */
    void applyTheme() {
        Theme.apply();
        Ui.restyle(getContentPane());
        for (GButton b : nav) b.revalidate();
        getContentPane().revalidate();
        getContentPane().repaint();
        repaint();
    }
    void setOnTop(boolean on) { Settings.onTop = on; Settings.save(); setAlwaysOnTop(on); }
    void openFullScreen(boolean saver) {
        if (fs != null) return;
        fs = new FullScreenWindow(saver, () -> { fs = null; lastActivity = System.currentTimeMillis(); });
    }

    // ---------------------------------------------------------------- keyboard shortcuts
    private void bind(String keys, Runnable r) {
        JRootPane rp = getRootPane(); String id = "act-" + keys;
        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keys), id);
        rp.getActionMap().put(id, new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { r.run(); } });
    }
    private void installShortcuts() {
        for (int i = 0; i < 9; i++) { final String key = KEYS[i]; bind("ctrl " + (i + 1), () -> show(key)); }
        bind("ctrl 0", () -> show("settings")); bind("F1", () -> show("help"));
        bind("F11", () -> openFullScreen(false)); bind("ctrl shift S", () -> openFullScreen(true));
        bind("ctrl T", () -> { setOnTop(!Settings.onTop); ((SettingsPage) page("settings")).syncControls(); });
        Runnable primary = () -> {
            if (current.equals("stopwatch")) toggleStopwatch(); else if (current.equals("timer")) toggleTimer(); else if (current.equals("pomodoro")) togglePomodoro();
        };
        bind("ctrl SPACE", primary); bind("ctrl ENTER", primary);
        bind("ctrl L", () -> { if (current.equals("stopwatch")) ((StopwatchPage) page("stopwatch")).lap(); });
        bind("ctrl R", () -> {
            if (current.equals("stopwatch")) ((StopwatchPage) page("stopwatch")).reset(); else if (current.equals("timer")) CD.reset(); else if (current.equals("pomodoro")) PM.reset();
        });
        bind("ctrl N", () -> {
            if (current.equals("alarms")) ((AlarmPage) page("alarms")).addNew(); else if (current.equals("tasks")) ((TaskPage) page("tasks")).addNew();
        });
        bind("ctrl F", () -> { show("tasks"); ((TaskPage) page("tasks")).focusSearch(); });
    }

    // ---------------------------------------------------------------- master tick (alarms, timers, chime, screensaver)
    private void masterTick(boolean sec) {
        if (content.fade < 1f) { content.fade = Math.min(1f, content.fade + 0.14f); content.repaint(); }
        if (CD.tick()) {
            Sound.play(1, true, false);
            Dialogs.notifyPopup("Timer finished", "Your countdown has reached zero.", Ui.TIMER);
        }
        int p = PM.tick();
        if (p > 0) {
            Sound.play(2, true, false);
            Dialogs.notifyPopup(p == 1 ? "Focus session complete" : "Break is over",
                p == 1 ? "Great work! Time for a break. Sessions today: " + Store.day(LocalDate.now())[0] : "Ready for the next focus session?", Ui.POMO);
        }
        if (!sec) return;
        if (current.length() > 0) getContentPane().getComponent(0).repaint();           // sidebar clock
        ZonedDateTime now = ZonedDateTime.now(); boolean changed = false;
        for (Alarm a : new ArrayList<>(Store.alarms)) {
            if (a.snoozeUntil != null && !now.isBefore(a.snoozeUntil)) { a.snoozeUntil = null; ring(a); continue; }
            if (a.shouldFire(now)) {
                a.lastFired = now.withZoneSameInstant(a.zoneId()).toLocalDate();
                if (a.repeat == 0) a.enabled = false;
                changed = true; ring(a);
            }
        }
        if (changed) { Store.saveAlarms(); ((AlarmPage) page("alarms")).model.fireTableDataChanged(); }
        LocalTime lt = LocalTime.now();
        int key = LocalDate.now().getDayOfYear() * 100 + lt.getHour();
        if (Settings.chime && lt.getMinute() == 0 && lt.getSecond() < 2 && key != lastChimeKey) { lastChimeKey = key; Sound.play(2, false, false); }
        if (Settings.saver > 0 && fs == null && System.currentTimeMillis() - lastActivity > Settings.saver * 60000L) openFullScreen(true);
    }
    private void ring(Alarm a) {
        Sound.play(a.sound, true, false);
        Dialogs.alarmPopup(a);
    }

    private void shutdown() {
        Ticker.stop();                       // stops the master timer
        Sound.stop();
        if (idleListener != null) Toolkit.getDefaultToolkit().removeAWTEventListener(idleListener);
        if (fs != null) fs.close();
        Store.saveAll();
        dispose();
        System.exit(0);
    }

    // ---------------------------------------------------------------- main
    public static void main(String[] args) {
        Settings.load(); Store.load(); Theme.apply(); PM.applyDurations();
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) { }
            ToolTipManager.sharedInstance().setInitialDelay(300);
            new Splash(() -> new DigitalClock().setVisible(true)).start();
        });
    }
}