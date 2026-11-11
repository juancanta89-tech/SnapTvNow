package com.snaptvnow.nativeprobe;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONObject;
import org.json.JSONArray;
import android.accessibilityservice.AccessibilityServiceInfo;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Controls the installed official app through Android accessibility only. */
public final class NativeProbe extends Instrumentation {
    private static final String APP = "boxbr.apksrebrand.smarters.nointro";
    private Bundle arguments;
    private UiAutomation automation;

    @Override public void onCreate(Bundle input) {
        super.onCreate(input);
        arguments = input;
        start();
    }

    @Override public void onStart() {
        JSONObject report = new JSONObject();
        boolean playbackOpened = false;
        try {
            automation = getUiAutomation();
            AccessibilityServiceInfo info = automation.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            automation.setServiceInfo(info);
            String mode = arguments.getString("mode", "");
            report.put("mode", mode);
            if (mode.equals("audio_probe")) {
                ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
                try { tone.startTone(ToneGenerator.TONE_DTMF_5, 1800); SystemClock.sleep(2200); }
                finally { tone.release(); }
                report.put("status", "test_tone_completed");
            } else if (mode.equals("fgt_login")) {
                fgtLogin(arguments.getString("password", ""), report);
                report.put("status", "login_observed");
            } else if (mode.equals("fgt_live")) {
                String title = arguments.getString("title", "");
                if (title.isEmpty()) throw new ProbeFailure("missing_exact_title");
                launch();
                fgtSearch(title, report);
                if (mode.equals("series")) {
                    String episode = arguments.getString("episode_title", "");
                    if (episode.isEmpty()) throw new ProbeFailure("missing_exact_episode");
                    clickEpisode(episode, arguments.getString("episode_index", ""),
                            "35000".equals(arguments.getString("pre_play_wait_ms", "0")) ? 35000 : 0, report);
                    title = episode;
                }
                playbackOpened = true;
                report.put("start_action_sent", true);
                observePlayback(title, report);
            } else {
                throw new ProbeFailure("unknown_mode");
            }
        } catch (ProbeFailure failure) {
            put(report, "status", "not_verified");
            put(report, "reason", failure.code);
        } catch (Throwable failure) {
            put(report, "status", "not_verified");
            put(report, "reason", "native_probe_error");
        } finally {
            if (automation != null) {
                try { report.put("component", fgtComponent()); report.put("controls", fgtControls()); }
                catch (Exception ignored) { }
            }
            if ((playbackOpened || report.optBoolean("start_action_sent")) && automation != null) {
                automation.performGlobalAction(1);
            }
            Bundle output = new Bundle();
            output.putString("native_report", report.toString());
            finish(-1, output);
        }
    }

    private static void put(JSONObject report, String key, String value) {
        try { report.put(key, value); } catch (Exception ignored) { }
    }

    private void launch() throws Exception {
        Intent intent = getContext().getPackageManager().getLaunchIntentForPackage(APP);
        if (intent == null) throw new ProbeFailure("official_app_missing");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
    }

    private List<AccessibilityNodeInfo> nodes() {
        List<AccessibilityNodeInfo> result = new ArrayList<>();
        // The focused search field opens the IME. Its window can become the
        // active root; inspect the app's own interactive windows as well.
        for (AccessibilityWindowInfo window : automation.getWindows()) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root != null && root.getPackageName() != null && APP.contentEquals(root.getPackageName())) collect(root,result);
        }
        if (result.isEmpty()) {
            AccessibilityNodeInfo root = automation.getRootInActiveWindow();
            if (root != null) collect(root, result);
        }
        return result;
    }

    private void collect(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> result) {
        if (result.size() >= 3000 || result.contains(node)) return;
        if (node.isVisibleToUser()) result.add(node);
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child != null) collect(child, result);
        }
    }

    private String text(AccessibilityNodeInfo node) {
        return node.getText() == null ? "" : node.getText().toString().trim();
    }

    private boolean inApp(AccessibilityNodeInfo node) {
        return APP.contentEquals(node.getPackageName() == null ? "" : node.getPackageName());
    }

    private List<AccessibilityNodeInfo> editable() {
        List<AccessibilityNodeInfo> fields = new ArrayList<>();
        for (AccessibilityNodeInfo node : nodes()) {
            if (inApp(node) && node.isEditable() && node.isEnabled()) fields.add(node);
        }
        return fields;
    }

    private void fill(AccessibilityNodeInfo field, String value) throws ProbeFailure {
        Bundle input = new Bundle();
        input.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
        if (!field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, input)) {
            throw new ProbeFailure("native_text_entry_failed");
        }
    }

    private boolean matchesLabel(String value, String expected, boolean navigation) {
        if (value.equals(expected)) return true;
        if (!navigation || !value.endsWith(expected)) return false;
        String prefix = value.substring(0, value.length() - expected.length());
        return prefix.matches("[^\\p{L}\\p{N}]*");
    }

    private boolean label(String label, boolean navigation) {
        for (AccessibilityNodeInfo node : nodes()) {
            String value = text(node);
            if (inApp(node) && !node.isEditable() && (matchesLabel(value, label, navigation))) return true;
        }
        return false;
    }

    private void click(String label, boolean navigation, int timeoutSeconds) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + timeoutSeconds * 1000L;
        while (SystemClock.elapsedRealtime() < deadline) {
            List<AccessibilityNodeInfo> candidates = new ArrayList<>();
            for (AccessibilityNodeInfo node : nodes()) {
                String value = text(node);
                if (!inApp(node) || node.isEditable() || !(matchesLabel(value, label, navigation))) continue;
                AccessibilityNodeInfo clickable = node;
                while (clickable != null && !clickable.isClickable()) clickable = clickable.getParent();
                if (clickable == null || !clickable.isEnabled()) continue;
                boolean duplicate = false;
                for (AccessibilityNodeInfo existing : candidates) {
                    if (existing.equals(clickable)) duplicate = true;
                }
                if (!duplicate) candidates.add(clickable);
            }
            if (candidates.size() > 1) throw new ProbeFailure("ambiguous_native_selection");
            if (candidates.size() == 1
                    && candidates.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
            SystemClock.sleep(250);
        }
        throw new ProbeFailure("native_control_not_found");
    }

    private void login(String password) throws Exception {
        if (password.isEmpty()) throw new ProbeFailure("secure_login_not_bound");
        launch();
        long deadline = SystemClock.elapsedRealtime() + 12000;
        List<AccessibilityNodeInfo> fields = editable();
        while (fields.size() != 2 && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(250);
            fields = editable();
        }
        if (fields.size() != 2) throw new ProbeFailure("login_form_not_found");
        AccessibilityNodeInfo username = null;
        AccessibilityNodeInfo secret = null;
        for (AccessibilityNodeInfo field : fields) {
            if (field.isPassword()) secret = field; else username = field;
        }
        if (username == null || secret == null) throw new ProbeFailure("login_fields_not_identified");
        fill(username, "dieguito2026");
        fill(secret, password);
        click("Entrar", false, 3);
        deadline = SystemClock.elapsedRealtime() + 40000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (label("No se pudo conectar", false)) throw new ProbeFailure("app_login_failed");
            if (label("No se pudo guardar la sesión", false)) throw new ProbeFailure("app_session_not_saved");
            if (label("TV en vivo", true) && editable().isEmpty()) return;
            SystemClock.sleep(350);
        }
        throw new ProbeFailure("app_login_not_observed_" + loginUiState());
    }

    private String loginUiState() {
        if (label("TV en vivo", true) && editable().isEmpty()) return "home";
        if (label("Más tarde", false)) return "update_prompt";
        for (AccessibilityNodeInfo node : nodes()) {
            if (!inApp(node)) continue;
            String value = text(node);
            if (value.startsWith("Obteniendo servidores") || value.startsWith("Probando servidor")) return "connecting";
            if ("android:id/alertTitle".equals(node.getViewIdResourceName())) return "dialog";
        }
        if (editable().size() == 2) return "login_form";
        return "other";
    }

    private void search(String scope, String title) throws Exception {
        if (!label("Buscar en todas las carpetas", false)) click("⌕", false, 12);
        click(scope, false, 8);
        long deadline = SystemClock.elapsedRealtime() + 8000;
        List<AccessibilityNodeInfo> fields = editable();
        while (fields.size() != 1 && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(250);
            fields = editable();
        }
        if (fields.size() != 1) throw new ProbeFailure("search_field_not_found");
        fill(fields.get(0), title);
        deadline = SystemClock.elapsedRealtime() + 25000;
        while (!label(title, false) && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(250);
        for (AccessibilityNodeInfo node : nodes()) {
            if (inApp(node) && text(node).matches("(?:[2-9]|[1-9][0-9]+) resultados.*")) {
                throw new ProbeFailure("ambiguous_catalog_search");
            }
        }
        click(title, false, 3);
    }

    private void clickEpisode(String title, String indexValue, int pauseMs, JSONObject report) throws Exception {
        int index;
        try { index = Integer.parseInt(indexValue); }
        catch (NumberFormatException error) { throw new ProbeFailure("episode_position_not_verified"); }
        if (index < 0 || index > 10000) throw new ProbeFailure("episode_position_not_verified");
        long deadline = SystemClock.elapsedRealtime() + 25000;
        boolean positioned = false;
        int pages = 0;
        while (SystemClock.elapsedRealtime() < deadline) {
            List<AccessibilityNodeInfo> matches = new ArrayList<>();
            AccessibilityNodeInfo list = null;
            for (AccessibilityNodeInfo node : nodes()) {
                if (!inApp(node)) continue;
                if ("android.widget.ListView".contentEquals(node.getClassName())) list = node;
                if ("android.widget.CheckedTextView".contentEquals(node.getClassName())
                        && text(node).contains(title)) matches.add(node);
            }
            if (matches.size() > 1) throw new ProbeFailure("ambiguous_episode_selection");
            if (matches.size() == 1) {
                if (pauseMs > 0) {
                    long pausedAt = SystemClock.elapsedRealtime();
                    SystemClock.sleep(pauseMs);
                    report.put("pre_play_wait_ms", SystemClock.elapsedRealtime() - pausedAt);
                    report.put("episode_picker_observed", true);
                    deadline += pauseMs;
                    pauseMs = 0;
                    continue;
                }
                AccessibilityNodeInfo row = matches.get(0);
                if (row.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
                Rect bounds = new Rect(); row.getBoundsInScreen(bounds);
                if (!bounds.isEmpty()) {
                    long now = SystemClock.uptimeMillis();
                    MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN,
                            bounds.centerX(), bounds.centerY(), 0);
                    MotionEvent up = MotionEvent.obtain(now, now + 30, MotionEvent.ACTION_UP,
                            bounds.centerX(), bounds.centerY(), 0);
                    boolean sent = automation.injectInputEvent(down, true);
                    sent &= automation.injectInputEvent(up, true);
                    down.recycle(); up.recycle();
                    if (sent) return;
                }
            }
            if (list != null && !positioned) {
                Bundle position = new Bundle();
                position.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_ROW_INT, index);
                list.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.getId(), position);
                positioned = true;
            } else if (list != null && pages++ < 64) {
                if (!list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) break;
            }
            SystemClock.sleep(250);
        }
        throw new ProbeFailure("series_episode_not_observed");
    }

    private void revealControls() {
        Bitmap image = automation.takeScreenshot();
        if (image == null) return;
        float x = image.getWidth() * 0.5f;
        float y = image.getHeight() * 0.18f;
        image.recycle();
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 30, MotionEvent.ACTION_UP, x, y, 0);
        automation.injectInputEvent(down, true);
        automation.injectInputEvent(up, true);
        down.recycle();
        up.recycle();
    }

    private int[] picture() throws ProbeFailure {
        Bitmap image = automation.takeScreenshot();
        if (image == null) throw new ProbeFailure("native_image_unavailable");
        int[] samples = new int[40 * 24];
        for (int row = 0; row < 24; row++) {
            for (int column = 0; column < 40; column++) {
                int x = (int) (image.getWidth() * (0.15 + 0.7 * column / 40.0));
                int y = (int) (image.getHeight() * (0.3 + 0.4 * row / 24.0));
                int pixel = image.getPixel(x, y);
                samples[row * 40 + column] = ((pixel >> 16 & 255) * 3
                        + (pixel >> 8 & 255) * 6 + (pixel & 255)) / 10;
            }
        }
        image.recycle();
        return samples;
    }

    private boolean rich(int[] pixels) {
        int lit = 0;
        long total = 0;
        long squares = 0;
        for (int value : pixels) {
            if (value > 18) lit++;
            total += value;
            squares += value * value;
        }
        double mean = total / (double) pixels.length;
        return lit > pixels.length / 5 && squares / (double) pixels.length - mean * mean > 100;
    }

    private boolean changed(int[] before, int[] after) {
        long difference = 0;
        for (int index = 0; index < before.length; index++) difference += Math.abs(before[index] - after[index]);
        return difference > before.length * 2L;
    }

    private boolean audioTrackActive() {
        try {
            int uid = getContext().getPackageManager().getApplicationInfo(APP, 0).uid;
            ParcelFileDescriptor descriptor = automation.executeShellCommand("dumpsys audio");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (FileInputStream input = new FileInputStream(descriptor.getFileDescriptor())) {
                byte[] buffer = new byte[2048];
                int count;
                while ((count = input.read(buffer)) > 0 && output.size() < 262144) output.write(buffer, 0, count);
            } finally { descriptor.close(); }
            for (String line : output.toString(StandardCharsets.UTF_8.name()).split("\n")) {
                if (line.matches(".*u/pid:\\s*" + uid + "/.*") && line.contains("state:started")) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private boolean playerTitle(String expectedTitle) {
        return fgtPlayerTitle(expectedTitle);
    }

    private void observePlayback(String expectedTitle, JSONObject report) throws Exception {
        long started = System.currentTimeMillis();
        int[] previous = null;
        int consecutive = 0;
        boolean selected = false;
        boolean audio = false;
        long firstImage = 0;
        for (int sample = 0; sample < 10; sample++) {
            SystemClock.sleep(2000);
            audio |= audioTrackActive();
            if (label("Canal no disponible", false) || label("Contenido no disponible", false)) {
                report.put("reason", "app_resource_unavailable_observed");
                break;
            }
            if (!playerTitle(expectedTitle)) {
                revealControls();
                SystemClock.sleep(200);
            }
            if (playerTitle(expectedTitle)) selected = true;
            else if (selected) {
                report.put("reason","native_selected_resource_not_confirmed");
                report.put("status","not_verified");
                report.put("title_selection_checked",false);
                return;
            }
            for (AccessibilityNodeInfo node : nodes()) {
                String value = text(node);
                if (inApp(node) && value.startsWith("SNAPTVNOW  ·  ")
                        && !value.equals("SNAPTVNOW  ·  " + expectedTitle)) {
                    report.put("reason", "native_selected_resource_changed");
                    report.put("status", "not_verified");
                    report.put("title_selection_checked", false);
                    return;
                }
            }
            if (!selected) continue;
            int[] current = picture();
            boolean visible = rich(current);
            boolean moving = visible && previous != null && changed(previous, current);
            previous = current;
            if (moving) {
                if (firstImage == 0) firstImage = System.currentTimeMillis();
                consecutive++;
            } else consecutive = 0;
            audio |= audioTrackActive();
            if (consecutive >= 4) break;
        }
        report.put("status", consecutive >= 4 ? "native_image_observed" : "not_verified");
        report.put("title_selection_checked", selected);
        report.put("moving_image_samples", consecutive);
        report.put("audio_track_active", audio);
        report.put("first_image_epoch_ms", firstImage);
        report.put("observation_end_epoch_ms", System.currentTimeMillis());
        report.put("observation_duration_ms", System.currentTimeMillis() - started);
        report.put("origin_attempt_selection", "not_verified");
    }

    private boolean fgtClick(String... labels) {
        List<AccessibilityNodeInfo> targets = new ArrayList<>();
        for (AccessibilityNodeInfo node : nodes()) {
            if (!inApp(node) || node.isEditable()) continue;
            boolean match = false;
            for (String wanted : labels) if (text(node).equalsIgnoreCase(wanted)) match = true;
            if (!match) continue;
            AccessibilityNodeInfo target = node;
            while (target != null && !target.isClickable()) target = target.getParent();
            if (target != null && target.isEnabled() && !targets.contains(target)) targets.add(target);
        }
        return targets.size() == 1 && targets.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    private boolean fgtId(String part) {
        List<AccessibilityNodeInfo> targets = new ArrayList<>();
        for (AccessibilityNodeInfo node : nodes()) {
            String id = node.getViewIdResourceName();
            if (!inApp(node) || id == null || !id.toLowerCase().contains(part)) continue;
            AccessibilityNodeInfo target = node;
            while (target != null && !target.isClickable()) target = target.getParent();
            if (target != null && target.isEnabled() && !targets.contains(target)) targets.add(target);
        }
        return targets.size() == 1 && targets.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    private boolean fgtHome() {
        return editable().isEmpty() && (label("LIVE TV", false) || label("Live TV", false)
                || label("TV EN VIVO", false) || label("TV en vivo", false));
    }

    private void fgtLogin(String password, JSONObject report) throws Exception {
        if (password.isEmpty()) throw new ProbeFailure("secure_login_not_bound");
        launch();
        long deadline = SystemClock.elapsedRealtime() + 90000;
        boolean submitted = false, profileOpened = false, tvSelected = false;
        int setupActions = 0;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (fgtHome()) return;
            List<AccessibilityNodeInfo> fields = editable();
            AccessibilityNodeInfo secret = null;
            for (AccessibilityNodeInfo field : fields) {
                String id = field.getViewIdResourceName();
                if (field.isPassword() || (id != null && id.toLowerCase().contains("password"))) secret = field;
            }
            if (!submitted && secret != null && fields.size() >= 2 && fields.size() <= 4) {
                List<AccessibilityNodeInfo> ordinary = new ArrayList<>();
                for (AccessibilityNodeInfo field : fields) if (!field.equals(secret)) ordinary.add(field);
                ordinary.sort((a,b)->{Rect x=new Rect(),y=new Rect();a.getBoundsInScreen(x);b.getBoundsInScreen(y);return Integer.compare(x.top,y.top);});
                AccessibilityNodeInfo username = ordinary.get(ordinary.size()-1);
                for (AccessibilityNodeInfo field : ordinary) {
                    String id = String.valueOf(field.getViewIdResourceName()).toLowerCase();
                    if (id.contains("username") || id.contains("user_name")) username = field;
                }
                for (AccessibilityNodeInfo field : ordinary) {
                    String id = String.valueOf(field.getViewIdResourceName()).toLowerCase();
                    if (field.equals(username)) fill(field,"dieguito2026");
                    else if (id.contains("url") || id.contains("server") || id.contains("dns")) fill(field,"https://api.snaptvnow.com");
                    else fill(field,"SNAPTVNOW prueba");
                }
                fill(secret,password);
                if (!fgtClick("ADD USER","Add User","AÑADIR USUARIO","Añadir usuario","AGREGAR USUARIO","Agregar usuario","LOGIN","Login","LOG IN","INICIAR SESIÓN","Iniciar sesión","ACCEDER","Entrar")
                        && !fgtId("bt_login") && !fgtId("btn_login")) throw new ProbeFailure("fgt_login_control_not_identified");
                submitted = true;
                report.put("login_submission_actions",1);
            } else if (!submitted && setupActions < 6) {
                if (fgtClick("ACEPTAR","Aceptar","ACCEPT","Accept","I AGREE","I Agree")
                        || fgtClick("LOGIN WITH XTREAM CODES API","Login with Xtream Codes API")
                        || fgtClick("ADD USER","Add User","AÑADIR USUARIO","Añadir usuario","Agregar usuario")) setupActions++;
                else if (!tvSelected && fgtClick("TV")) {tvSelected=true;setupActions++;}
                else if (fgtClick("SAVE","Save","GUARDAR","Guardar")) setupActions++;
            } else if (submitted && !profileOpened && fgtClick("SNAPTVNOW prueba")) {
                profileOpened = true;
            }
            SystemClock.sleep(500);
        }
        throw new ProbeFailure(submitted ? "fgt_login_not_observed" : "fgt_login_form_not_identified");
    }

    private void fgtSearch(String title, JSONObject report) throws Exception {
        long deadline = SystemClock.elapsedRealtime()+30000;
        boolean entered = false, searched = false;
        while (SystemClock.elapsedRealtime()<deadline) {
            if (!entered && fgtClick("LIVE TV","Live TV","TV EN VIVO","TV en vivo")) {
                entered=true;SystemClock.sleep(1200);continue;
            }
            List<AccessibilityNodeInfo> fields=editable();
            if (fields.size()==1) {
                fields.get(0).performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                fill(fields.get(0),title);
                automation.injectInputEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER),true);
                automation.injectInputEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER),true);
                searched=true;
                break;
            }
            if (entered && (fgtClick("SEARCH","Search","BUSCAR","Buscar") || fgtId("search"))) {
                SystemClock.sleep(500);continue;
            }
            SystemClock.sleep(400);
        }
        if (!searched) throw new ProbeFailure("fgt_search_not_identified");
        deadline=SystemClock.elapsedRealtime()+18000;
        while(SystemClock.elapsedRealtime()<deadline) {
            if (fgtClick(title)) {
                report.put("start_action_sent",true);
                SystemClock.sleep(1500);
                // Expanding this one selected preview is not another channel start.
                if (!fgtComponent().toLowerCase().contains("player")) fgtId("full_screen");
                return;
            }
            SystemClock.sleep(400);
        }
        throw new ProbeFailure("fgt_exact_resource_not_selected");
    }

    private String fgtComponent() {
        try {
            ParcelFileDescriptor descriptor=automation.executeShellCommand("dumpsys activity activities");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(FileInputStream input=new FileInputStream(descriptor.getFileDescriptor())) {
                byte[] buffer=new byte[4096];int count;
                while((count=input.read(buffer))>0 && bytes.size()<524288)bytes.write(buffer,0,count);
            } finally {descriptor.close();}
            for(String line:bytes.toString(StandardCharsets.UTF_8.name()).split("\n")) {
                if(!line.contains("mResumedActivity") || !line.contains(APP+"/"))continue;
                String component=line.substring(line.indexOf(APP+"/")+APP.length()+1).split("[ }]")[0];
                if(component.matches("[A-Za-z0-9_.$]+"))return component;
            }
        }catch(Exception ignored){}
        return "not_verified";
    }

    private boolean fgtPlayerTitle(String expected) {
        if (!fgtComponent().toLowerCase().contains("player")) return false;
        return label(expected,false);
    }

    private JSONArray fgtControls() throws Exception {
        JSONArray result=new JSONArray();
        for(AccessibilityNodeInfo node:nodes()) {
            if(!inApp(node)||result.length()>=40)continue;
            String id=node.getViewIdResourceName();if(id==null)continue;
            JSONObject item=new JSONObject();item.put("id",id);item.put("editable",node.isEditable());
            item.put("clickable",node.isClickable());
            String value=text(node);
            for(String safe:new String[]{"TV","Mobile","LIVE TV","Live TV","TV EN VIVO","TV en vivo","ADD USER","Add User","Search","SEARCH","Buscar","LOGIN","Login","SAVE","Save","ACEPTAR","Aceptar","ACCEPT","Accept","I AGREE","I Agree"})
                if(value.equalsIgnoreCase(safe))item.put("label",safe);
            if(value.equals(arguments.getString("title",""))&&!value.isEmpty())item.put("exact_resource_label",true);
            result.put(item);
        }
        return result;
    }

    private static final class ProbeFailure extends Exception {
        final String code;
        ProbeFailure(String code) { this.code = code; }
    }
}
