package com.snaptvnow.nativeprobe;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Controls the installed official app through Android accessibility only. */
public final class NativeProbe extends Instrumentation {
    private static final String APP = "com.snaptvnow.tv";
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
            AccessibilityServiceInfo service = automation.getServiceInfo();
            if (service != null) {
                service.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
                automation.setServiceInfo(service);
            }
            String mode = arguments.getString("mode", "");
            report.put("mode", mode);
            if (mode.equals("audio_probe")) {
                ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
                try { tone.startTone(ToneGenerator.TONE_DTMF_5, 1800); SystemClock.sleep(2200); }
                finally { tone.release(); }
                report.put("status", "test_tone_completed");
            } else if (mode.equals("login")) {
                login(arguments.getString("password", ""));
                report.put("status", "login_observed");
            } else if (mode.equals("movie")) {
                String title = arguments.getString("title", "");
                if (title.isEmpty()) throw new ProbeFailure("missing_exact_title");
                boolean longWatch = "true".equals(arguments.getString("long_watch", "false"));
                report.put("start_action_sent", false);
                report.put("phase", "movie_launch");
                launch();
                search("Películas", title, report);
                playbackOpened = true;
                report.put("phase", "movie_resume");
                report.put("movie_resume_observed", resumeMovie(title, report, longWatch));
                report.put("start_action_sent", true);
                report.put("phase", "movie_playback_observation");
                if (longWatch) {
                    startFromBeginning(title, report);
                    observeContinuity(title, report);
                } else observePlayback(title, report);
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
            if (playbackOpened && automation != null) {
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
        AccessibilityNodeInfo root = automation.getRootInActiveWindow();
        if (root != null) collect(root, result);
        return result;
    }

    private void collect(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> result) {
        if (result.size() >= 3000) return;
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

    private List<AccessibilityNodeInfo> matchingControls(String label, boolean navigation) {
        List<AccessibilityNodeInfo> candidates = new ArrayList<>();
        for (AccessibilityNodeInfo node : nodes()) {
            String value = text(node);
            if (!inApp(node) || node.isEditable() || !matchesLabel(value, label, navigation)) continue;
            AccessibilityNodeInfo clickable = node;
            while (clickable != null && !clickable.isClickable()) clickable = clickable.getParent();
            if (clickable == null || !clickable.isEnabled()) continue;
            boolean duplicate = false;
            for (AccessibilityNodeInfo existing : candidates) {
                if (existing.equals(clickable)) duplicate = true;
            }
            if (!duplicate) candidates.add(clickable);
        }
        return candidates;
    }

    private void click(String label, boolean navigation, int timeoutSeconds) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + timeoutSeconds * 1000L;
        while (SystemClock.elapsedRealtime() < deadline) {
            List<AccessibilityNodeInfo> candidates = matchingControls(label, navigation);
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

    private void search(String scope, String title, JSONObject report) throws Exception {
        report.put("phase", "movie_search_navigation");
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
        report.put("phase", "movie_search_result_selection");
        deadline = SystemClock.elapsedRealtime() + 25000;
        while (SystemClock.elapsedRealtime() < deadline) {
            List<AccessibilityNodeInfo> candidates = matchingControls(title, false);
            int textControls = 0, buttonControls = 0, containerControls = 0, otherControls = 0;
            for (AccessibilityNodeInfo candidate : candidates) {
                String type = String.valueOf(candidate.getClassName());
                if (type.equals("android.widget.TextView")) textControls++;
                else if (type.equals("android.widget.Button")) buttonControls++;
                else if (type.equals("android.widget.LinearLayout")
                        || type.equals("android.widget.FrameLayout")) containerControls++;
                else otherControls++;
            }
            report.put("exact_candidate_count", candidates.size());
            report.put("candidate_text_controls", textControls);
            report.put("candidate_button_controls", buttonControls);
            report.put("candidate_container_controls", containerControls);
            report.put("candidate_other_controls", otherControls);
            if (candidates.size() > 1) throw new ProbeFailure("ambiguous_native_selection");
            if (candidates.size() == 1
                    && candidates.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
            SystemClock.sleep(250);
        }
        throw new ProbeFailure("native_control_not_found");
    }

    private boolean resumeMovie(String title, JSONObject report, boolean fromBeginning) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 8000;
        while (SystemClock.elapsedRealtime() < deadline) {
            int titleCount = 0;
            boolean dialogControls = false;
            String resumeLabel = null;
            String beginningLabel = null;
            int choices = 0;
            for (AccessibilityNodeInfo node : nodes()) {
                if (!inApp(node)) continue;
                String value = text(node);
                if (!node.isEditable() && value.equalsIgnoreCase(title)) titleCount++;
                if ("android:id/alertTitle".equals(node.getViewIdResourceName())) dialogControls = true;
                boolean button = "android.widget.Button".contentEquals(node.getClassName()) && node.isEnabled();
                String lower = value.toLowerCase(Locale.ROOT);
                if (button && lower.equals("desde inicio")) beginningLabel = value;
                if (button && (lower.equals("desde inicio") || lower.equals("cancelar"))) dialogControls = true;
                if (button && lower.startsWith("continuar desde ")) {
                    resumeLabel = value;
                    choices++;
                }
            }
            report.put("resume_title_count", titleCount);
            report.put("resume_button_count", choices);
            if (choices > 1 || titleCount > 1) throw new ProbeFailure("ambiguous_movie_resume");
            if (choices == 1) {
                if (titleCount != 1 || !dialogControls) throw new ProbeFailure("movie_resume_title_not_verified");
                if (fromBeginning) {
                    if (beginningLabel == null) throw new ProbeFailure("movie_beginning_control_not_verified");
                    click(beginningLabel, false, 3);
                    report.put("start_from_beginning_clicked", true);
                } else click(resumeLabel, false, 3);
                return true;
            }
            if (playerTitle(title)) return false;
            if (label("Contenido no disponible", false)) throw new ProbeFailure("app_resource_unavailable_observed");
            SystemClock.sleep(250);
        }
        throw new ProbeFailure("movie_start_not_observed");
    }

    private void startFromBeginning(String title, JSONObject report) throws Exception {
        if (report.optBoolean("start_from_beginning_clicked")) return;
        long deadline = SystemClock.elapsedRealtime() + 12000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (playerTitle(title)) {
                click("Desde inicio", false, 3);
                report.put("start_from_beginning_clicked", true);
                return;
            }
            revealControls();
            SystemClock.sleep(300);
        }
        throw new ProbeFailure("movie_beginning_control_not_verified");
    }

    private long clockSeconds(String value) {
        if (value == null || !value.matches("[0-9]{1,3}:[0-9]{2}(?::[0-9]{2})?")) return -1;
        String[] parts = value.split(":");
        long seconds = 0;
        for (int index = 0; index < parts.length; index++) {
            int part = Integer.parseInt(parts[index]);
            if (index > 0 && part >= 60) return -1;
            seconds = seconds * 60 + part;
        }
        return seconds <= 172800 ? seconds : -1;
    }

    private long idClock(AccessibilityNodeInfo root, String id) {
        List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByViewId(APP + ":id/" + id);
        if (found.size() != 1 || !found.get(0).isVisibleToUser()) return -1;
        return clockSeconds(text(found.get(0)));
    }

    private long[] position() {
        AccessibilityNodeInfo root = automation.getRootInActiveWindow();
        if (root == null) return new long[]{-1, -1, 0};
        long current = idClock(root, "exo_position"), duration = idClock(root, "exo_duration");
        if (current >= 0) return new long[]{current, duration, 1};
        long fallback = -1;
        int choices = 0;
        for (AccessibilityNodeInfo node : nodes()) {
            String type = String.valueOf(node.getClassName());
            if (!inApp(node) || !(type.contains("SeekBar") || type.contains("TimeBar"))) continue;
            long value = clockSeconds(String.valueOf(node.getContentDescription()));
            if (value >= 0) { fallback = value; choices++; }
        }
        return choices == 1 ? new long[]{fallback, duration, 2} : new long[]{-1, duration, 0};
    }

    private void observeContinuity(String expectedTitle, JSONObject report) throws Exception {
        final long limit = 360000;
        long started = SystemClock.elapsedRealtime(), firstAt = -1, lastAt = -1;
        long first = -1, last = -1, firstDuration = -1, lastDuration = -1;
        long largestRegression = 0, firstImage = 0;
        int regressions = 0, valid = 0, consecutive = 0, longest = 0, reveals = 0;
        boolean selected = playerTitle(expectedTitle), audio = false, unavailable = false, atEnd = false;
        int[] previousImage = null;
        JSONArray timeline = new JSONArray();
        while (SystemClock.elapsedRealtime() - started <= limit + 1000) {
            long[] point = position();
            if (point[0] < 0) {
                revealControls(); reveals++;
                SystemClock.sleep(150);
                point = position();
            }
            long elapsed = SystemClock.elapsedRealtime() - started;
            selected |= playerTitle(expectedTitle);
            if (label("Contenido no disponible", false)) {
                unavailable = true;
                report.put("reason", "app_resource_unavailable_observed");
            }
            for (AccessibilityNodeInfo node : nodes()) {
                String value = text(node);
                if (inApp(node) && value.startsWith("SNAPTVNOW  ·  ")
                        && !value.equals("SNAPTVNOW  ·  " + expectedTitle)) {
                    unavailable = true;
                    report.put("reason", "native_selected_resource_changed");
                }
            }
            boolean activeAudio = audioTrackActive(); audio |= activeAudio;
            int[] currentImage = picture();
            boolean moving = rich(currentImage) && previousImage != null && changed(previousImage, currentImage);
            previousImage = currentImage;
            if (moving) {
                if (firstImage == 0) firstImage = System.currentTimeMillis();
                longest = Math.max(longest, ++consecutive);
            } else consecutive = 0;
            JSONObject sample = new JSONObject().put("elapsed_ms", elapsed)
                .put("position_seconds", point[0] >= 0 ? point[0] : JSONObject.NULL)
                .put("duration_seconds", point[1] >= 0 ? point[1] : JSONObject.NULL)
                .put("source", point[2] == 1 ? "media3_ids" : point[2] == 2 ? "timebar_description" : "not_verified")
                .put("moving_image", moving).put("audio_active", activeAudio);
            AccessibilityNodeInfo root = automation.getRootInActiveWindow();
            if (root != null) {
                List<AccessibilityNodeInfo> bars = root.findAccessibilityNodeInfosByViewId(APP + ":id/exo_progress");
                if (bars.size() == 1) {
                    sample.put("timebar_enabled", bars.get(0).isEnabled());
                    AccessibilityNodeInfo.RangeInfo range = bars.get(0).getRangeInfo();
                    if (range != null) sample.put("timebar_range_min", range.getMin()).put("timebar_range_max", range.getMax());
                }
            }
            timeline.put(sample);
            if (point[0] >= 0 && (first >= 0 || point[0] <= 5)) {
                if (first < 0) { first = point[0]; firstAt = elapsed; firstDuration = point[1]; }
                if (last >= 0 && last - point[0] > 2) {
                    regressions++; largestRegression = Math.max(largestRegression, last - point[0]);
                }
                last = point[0]; lastAt = elapsed; lastDuration = point[1]; valid++;
            }
            if (point[1] > 0 && point[0] >= point[1] - 1) atEnd = true;
            if (unavailable || elapsed >= limit) break;
            SystemClock.sleep(Math.min(5000, limit - elapsed));
        }
        long elapsed = SystemClock.elapsedRealtime() - started;
        String state = unavailable ? "resource_unavailable" : regressions > 0 ? "position_regression_observed"
            : first < 0 || valid < 12 ? "position_not_verified" : elapsed < limit ? "incomplete_observation"
            : last - first >= 300 ? "advanced_without_regression" : "insufficient_progress";
        report.put("phase", "movie_continuity_complete");
        report.put("continuity_state", state);
        report.put("position_samples", timeline);
        report.put("position_sample_count", valid);
        report.put("position_regressions", regressions);
        report.put("largest_regression_seconds", largestRegression);
        report.put("first_position_seconds", first);
        report.put("last_position_seconds", last);
        report.put("first_duration_seconds", firstDuration);
        report.put("last_duration_seconds", lastDuration);
        report.put("tracking_span_ms", firstAt >= 0 ? lastAt - firstAt : 0);
        report.put("end_state_observed", atEnd);
        report.put("reveal_input_count", reveals);
        report.put("reveal_input_keycode", KeyEvent.KEYCODE_DPAD_UP);
        report.put("reveal_input_purpose", "show_controls_for_timeline");
        report.put("status", longest >= 4 ? "native_image_observed" : "not_verified");
        report.put("title_selection_checked", selected);
        report.put("moving_image_samples", longest);
        report.put("audio_track_active", audio);
        report.put("first_image_epoch_ms", firstImage);
        report.put("observation_end_epoch_ms", System.currentTimeMillis());
        report.put("observation_duration_ms", elapsed);
    }

    private void revealControls() {
        long now = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP, 0);
        KeyEvent up = new KeyEvent(now, now + 30, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP, 0);
        automation.injectInputEvent(down, true);
        automation.injectInputEvent(up, true);
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
        return label("SNAPTVNOW  ·  " + expectedTitle, false);
    }

    private void observePlayback(String expectedTitle, JSONObject report) throws Exception {
        long started = SystemClock.elapsedRealtime();
        int[] previous = null;
        int consecutive = 0;
        int longestConsecutive = 0;
        boolean selected = false;
        boolean audio = false;
        boolean unavailable = false;
        long firstImage = 0;
        for (int sample = 0; sample < 18; sample++) {
            SystemClock.sleep(2000);
            audio |= audioTrackActive();
            if (label("Canal no disponible", false) || label("Contenido no disponible", false)) {
                report.put("reason", "app_resource_unavailable_observed");
                unavailable = true;
                break;
            }
            if (!playerTitle(expectedTitle)) {
                revealControls();
                SystemClock.sleep(200);
            }
            if (playerTitle(expectedTitle)) selected = true;
            for (AccessibilityNodeInfo node : nodes()) {
                String value = text(node);
                if (inApp(node) && value.startsWith("SNAPTVNOW  ·  ")
                        && !value.equals("SNAPTVNOW  ·  " + expectedTitle)) {
                    report.put("reason", "native_selected_resource_changed");
                    report.put("status", "not_verified");
                    report.put("title_selection_checked", false);
                    report.put("observation_end_epoch_ms", System.currentTimeMillis());
                    report.put("observation_duration_ms", SystemClock.elapsedRealtime() - started);
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
                longestConsecutive = Math.max(longestConsecutive, consecutive);
            } else consecutive = 0;
            audio |= audioTrackActive();
        }
        long duration = SystemClock.elapsedRealtime() - started;
        report.put("status", !unavailable && duration >= 30000 && longestConsecutive >= 4
                ? "native_image_observed" : "not_verified");
        report.put("title_selection_checked", selected);
        report.put("moving_image_samples", longestConsecutive);
        report.put("audio_track_active", audio);
        report.put("first_image_epoch_ms", firstImage);
        report.put("observation_end_epoch_ms", System.currentTimeMillis());
        report.put("observation_duration_ms", duration);
        report.put("origin_attempt_selection", "not_verified");
    }

    private static final class ProbeFailure extends Exception {
        final String code;
        ProbeFailure(String code) { this.code = code; }
    }
}
