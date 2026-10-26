package com.pocketai.app;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.pdf.PdfRenderer;
import android.graphics.pdf.PdfDocument;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.Editable;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.StyleSpan;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * To let other apps share screenshots, PDFs, text and code files straight into EDITH,
 * add this inside the <activity> tag of MainActivity in AndroidManifest.xml:
 *
 *   <intent-filter>
 *       <action android:name="android.intent.action.SEND" />
 *       <category android:name="android.intent.category.DEFAULT" />
 *       <data android:mimeType="image/*" />
 *       <data android:mimeType="application/pdf" />
 *       <data android:mimeType="text/*" />
 *   </intent-filter>
 *
 * (Also set android:launchMode="singleTop" on the activity.)
 */
public class MainActivity extends Activity {

    // ============================================================
    // Constants
    // ============================================================

    private static final String PREFS = "edith_preferences";
    private static final String CONVERSATIONS_KEY = "edith_conversations";
    private static final String PROJECTS_KEY = "edith_projects";
    private static final String MEMORY_KEY = "edith_memory_items";
    private static final String TASKS_KEY = "edith_tasks";
    private static final String MEMORY_ENABLED_KEY = "edith_memory_enabled";
    private static final String RECALL_ENABLED_KEY = "edith_recall_enabled";
    private static final String AUTOLEARN_KEY = "edith_auto_learn";
    private static final String SPEAK_KEY = "edith_speak_replies";
    private static final String NAME_KEY = "edith_user_name";
    private static final String PERSONA_KEY = "edith_persona";
    private static final String GREETING_KEY = "edith_greeting";
    private static final String TYPING_SOUND_KEY = "typing_sound_enabled";
    private static final String MODE_KEY = "ai_mode";
    private static final String FOCUS_KEY = "focus_mode";
    private static final String OLLAMA_KEY = "ollama_api_key";
    private static final String LEGACY_HISTORY_KEY = "chat_history";
    private static final String LEGACY_MEMORY_KEY = "edith_persistent_memories";
    private static final String OLLAMA_MODEL = "gpt-oss:120b";
    // Vision-capable cloud model used for screenshots and PDF pages. Change it if
    // your Ollama account exposes a different vision model.
    private static final String VISION_MODEL = "qwen3-vl:235b-instruct";
    private static final String OLLAMA_URL = "https://ollama.com/api/chat";
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private static final int PICK_MODEL = 701;
    private static final int VOICE_PERMISSION_REQUEST = 702;
    private static final int CAL_PERMISSION_REQUEST = 703;
    private static final int PICK_FILE = 704;
    private static final int PICK_IMAGE_EDIT = 705;
    private static final String POLLINATIONS_KEY = "pollinations_api_key";
    private static final int MAX_MESSAGES = 200;
    private static final int MAX_MEMORIES = 200;
    private static final int MAX_TASKS = 60;
    private static final int MAX_ATTACH_CHARS = 16000;
    private static final int MAX_PDF_PAGES = 4;

    private static final int STYLE_PRIMARY = 0;
    private static final int STYLE_SECONDARY = 1;
    private static final int STYLE_DANGER = 2;
    private static final int STYLE_GHOST = 3;

    private static final String[] STOP_WORDS = {
            "what", "that", "this", "with", "from", "have", "about", "your",
            "will", "just", "they", "them", "there", "would", "could",
            "should", "please", "know", "tell", "when", "where", "which"
    };

    private static final String[] SUGGESTIONS = {
            "Summarize a topic for me",
            "Help me prepare for tomorrow",
            "Explain this like I'm new to it",
            "Draft a short, friendly message"
    };

    private static final String[] REMEMBER_PREFIXES = {
            "please remember that ", "remember that ", "please remember: ",
            "remember this: ", "remember: ", "please remember "
    };

    private static final String[] FORGET_PREFIXES = {
            "please forget that ", "forget that ", "please forget about ",
            "forget about ", "forget my ", "please forget "
    };

    private static final String ACTION_HELP =
            "You control this Android phone. When the user asks for anything the phone can do, "
            + "you MUST reply with ONLY one JSON object. Never explain steps, never say you "
            + "cannot, and never add any text before or after the JSON. Use one of these forms:\n"
            + "{\"action\":\"open_app\",\"app\":\"app name\"}\n"
            + "{\"action\":\"whatsapp\",\"contact\":\"contact name or phone number\",\"message\":\"text to send\"}\n"
            + "{\"action\":\"open_url\",\"url\":\"website address\"}\n"
            + "{\"action\":\"web_search\",\"query\":\"search terms\"}\n"
            + "{\"action\":\"navigate\",\"destination\":\"place or address\"}\n"
            + "{\"action\":\"set_timer\",\"minutes\":5,\"label\":\"optional\"}\n"
            + "{\"action\":\"set_alarm\",\"hour\":7,\"minute\":30,\"label\":\"optional\"}\n"
            + "{\"action\":\"calendar_event\",\"title\":\"...\",\"start\":\"2026-10-25T15:00\","
            + "\"end\":\"2026-10-25T16:00\",\"location\":\"optional\",\"notes\":\"optional\"}\n"
            + "{\"action\":\"create_contact\",\"name\":\"...\",\"number\":\"...\",\"email\":\"optional\"}\n"
            + "{\"action\":\"call\",\"number\":\"phone number\"}\n"
            + "{\"action\":\"sms\",\"number\":\"phone number\",\"text\":\"message\"}\n"
            + "{\"action\":\"email\",\"to\":\"address\",\"subject\":\"...\",\"body\":\"...\"}\n"
            + "{\"action\":\"play_music\",\"query\":\"song or artist\"}\n"
            + "{\"action\":\"camera\"}\n"
            + "{\"action\":\"settings\",\"panel\":\"wifi|bluetooth|display|sound|battery|location|apps|date|general\"}\n"
            + "{\"action\":\"share\",\"text\":\"text to share\"}\n"
            + "For several steps in order, use:\n"
            + "{\"action\":\"multi\",\"steps\":[ ...any of the objects above... ]}\n"
            + "For WhatsApp, put the contact name exactly as the user said it and the message "
            + "exactly as it should be sent. Example: user says \"say hi to Mummy on WhatsApp\" -> "
            + "{\"action\":\"whatsapp\",\"contact\":\"Mummy\",\"message\":\"Hi\"}\n"
            + "Use the 24-hour clock for times. For normal questions, answer in plain text.";

    private static final String CODE_HELP =
            "This is a programming or technical question. Reply like a senior engineer.\n"
            + "- Always put code in fenced blocks with a language tag, e.g. ```java or ```python.\n"
            + "- Use one block per file. When there is more than one file, start each block with a "
            + "comment line giving the file name.\n"
            + "- New code: show the code first, then a short explanation.\n"
            + "- Bug reports: state the cause in one or two sentences, show the corrected code, "
            + "then list what changed.\n"
            + "- Keep prose brief. Do not repeat code outside the blocks. Never use markdown "
            + "headings, and never reply with phone action JSON.";

    // Applied to every normal reply, in every mode, so output always fits a phone screen.
    private static final String FORMAT_HELP =
            "Formatting (you are on a phone screen): use short paragraphs, **bold** for key terms "
            + "and '- ' bullets. Put every piece of code, SQL, formulas, commands and file contents "
            + "in a fenced block with a language tag. Do not use HTML tags, do not write horizontal "
            + "rules, and do not use markdown tables unless the user asks for a table.";

    private static final String PLAN_HELP =
            "You are in Task planner mode. Turn the user's request into a practical checklist. "
            + "Reply with ONLY this format and nothing else:\n"
            + "TITLE: <short plan title>\n"
            + "- <first step>\n"
            + "- <next step>\n"
            + "Use 4 to 10 short, concrete, ordered steps. Include times or deadlines when the "
            + "user mentioned them. Do not reply with phone action JSON.";

    private static final Pattern BULLET = Pattern.compile(
            "^(?:[-*\\u2022]|\\d{1,2}[.)])\\s+(?:\\[[ xX]\\]\\s*)?(.+)$");

    // ============================================================
    // Palette
    // ============================================================

    private final int bg = Color.rgb(11, 15, 18);
    private final int surface = Color.rgb(20, 27, 31);
    private final int surface2 = Color.rgb(27, 36, 41);
    private final int border = Color.rgb(38, 52, 58);
    private final int accent = Color.rgb(105, 199, 188);
    private final int accentSoft = Color.rgb(20, 48, 48);
    private final int userBubble = Color.rgb(31, 50, 56);
    private final int primaryText = Color.rgb(238, 244, 247);
    private final int secondaryText = Color.rgb(138, 154, 162);
    private final int readyGreen = Color.rgb(77, 201, 139);
    private final int dangerRed = Color.rgb(230, 120, 120);
    private final int thinkingAmber = Color.rgb(240, 190, 90);
    private final int speakingBlue = Color.rgb(120, 170, 255);

    // ============================================================
    // State
    // ============================================================

    private LinearLayout root;
    private LinearLayout content;
    private LinearLayout navBar;
    private LinearLayout chatList;
    private ScrollView chatScroll;
    private EditText messageInput;
    private TextView badge;
    private TextView voiceStatus;
    private TextView chatTitleView;
    private TextView presenceLabel;
    private TextView handsFreeButton;
    private LinearLayout attachRow;
    private OrbView homeOrb;
    private OrbView chatOrb;
    private ObjectAnimator thinkingAnim;

    private String screen = "HOME";
    private String focusMode = "GENERAL"; // GENERAL, ANDROID, CODE or PLAN
    private String currentProjectId;
    private String activeConversationId;
    private String pendingProjectId;
    private String busyConversationId;
    private String pendingDraft;

    private final ArrayList<JSONObject> conversations = new ArrayList<>();
    private final ArrayList<JSONObject> projects = new ArrayList<>();
    private final ArrayList<JSONObject> memories = new ArrayList<>();
    private final ArrayList<JSONObject> tasks = new ArrayList<>();
    private final ArrayList<JSONObject> pendingSteps = new ArrayList<>();
    private JSONObject pendingCalendarAction;
    private boolean awaitingReturn = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private boolean ttsReady = false;

    // Voice and presence
    private String presence = "IDLE"; // IDLE, LISTENING, THINKING, SPEAKING
    private boolean handsFree = false;
    private boolean replyBySpeech = false;
    private boolean permissionPending = false;
    private boolean pendingHandsFree = false;
    private int silentRounds = 0;
    private Runnable listenWatchdog;
    private Runnable speakWatchdog;

    // Attachments
    private String pendingAttachName = null;
    private String pendingAttachText = "";
    private ArrayList<String> pendingAttachImages = new ArrayList<>();
    private ArrayList<String> lastImages = new ArrayList<>();

    private SoundPool keySound;
    private int keySoundId = 0;
    private final Random keyRandom = new Random();
    private boolean suppressKeySound = false;

    private String aiMode = "LOCAL";
    private boolean modelReady = false;
    private boolean nativeAvailable = false;
    private File modelFile;

    private native String nativeGetBackendInfo();
    private native String nativeLoadModel(String path);
    private native String nativeGenerate(String prompt);
    private native void nativeUnloadModel();

    static {
        try {
            System.loadLibrary("c++_shared");
            System.loadLibrary("ggml-base");
            System.loadLibrary("ggml-cpu");
            System.loadLibrary("ggml");
            System.loadLibrary("llama");
            System.loadLibrary("edith_jni");
        } catch (UnsatisfiedLinkError error) {
            android.util.Log.e("EDITH", "Native libraries failed to load", error);
        }
    }

    // ============================================================
    // Animated assistant orb
    // ============================================================

    private final class OrbView extends View {
        private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private final long startNs = System.nanoTime();
        private String orbState = "IDLE";
        private float level = 0f;
        private float targetLevel = 0f;

        OrbView(Context context) {
            super(context);
            ring.setStyle(Paint.Style.STROKE);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeCap(Paint.Cap.ROUND);
        }

        void setOrbState(String state) {
            orbState = state;
            if (!"LISTENING".equals(state)) targetLevel = 0f;
            invalidate();
        }

        void setLevel(float value) {
            targetLevel = Math.max(0f, Math.min(1f, value));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float r = Math.min(getWidth(), getHeight()) / 2f;
            if (r <= 0f) return;

            float t = (System.nanoTime() - startNs) / 1000000000f;
            level += (targetLevel - level) * 0.18f;
            int color = orbColor(orbState);

            float pulse;
            if ("LISTENING".equals(orbState)) {
                pulse = 0.05f * (float) Math.sin(t * 4f) + 0.30f * level;
            } else if ("THINKING".equals(orbState)) {
                pulse = 0.04f * (float) Math.sin(t * 7f);
            } else if ("SPEAKING".equals(orbState)) {
                pulse = 0.10f * Math.abs((float) Math.sin(t * 5.5f))
                        + 0.04f * (float) Math.sin(t * 13f);
            } else {
                pulse = 0.035f * (float) Math.sin(t * 1.6f);
            }
            float cr = r * 0.5f * (1f + pulse);
            boolean idle = "IDLE".equals(orbState);

            for (int i = 0; i < 3; i++) {
                float phase = (t * (idle ? 0.25f : 0.7f) + i / 3f) % 1f;
                float rr = cr + (r - cr) * phase;
                ring.setColor(color);
                ring.setAlpha((int) ((1f - phase) * (idle ? 45 : 110)));
                ring.setStrokeWidth(Math.max(1f, r * 0.025f));
                canvas.drawCircle(cx, cy, rr, ring);
            }

            core.setShader(new RadialGradient(cx - cr * 0.3f, cy - cr * 0.3f, cr * 1.4f,
                    new int[]{blend(color, Color.WHITE, 0.55f), color, blend(color, Color.BLACK, 0.55f)},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, cr, core);

            if ("THINKING".equals(orbState)) {
                arc.setColor(color);
                arc.setStrokeWidth(r * 0.06f);
                float ar = cr * 1.22f;
                oval.set(cx - ar, cy - ar, cx + ar, cy + ar);
                canvas.drawArc(oval, (t * 260f) % 360f, 110f, false, arc);
            }
            postInvalidateOnAnimation();
        }
    }

    private int blend(int a, int b, float f) {
        return Color.rgb(
                (int) (Color.red(a) * (1f - f) + Color.red(b) * f),
                (int) (Color.green(a) * (1f - f) + Color.green(b) * f),
                (int) (Color.blue(a) * (1f - f) + Color.blue(b) * f));
    }

    private int orbColor(String state) {
        if ("LISTENING".equals(state)) return readyGreen;
        if ("THINKING".equals(state)) return thinkingAmber;
        if ("SPEAKING".equals(state)) return speakingBlue;
        return accent;
    }

    private String presenceText(String p) {
        if ("LISTENING".equals(p)) return "●  Listening…";
        if ("THINKING".equals(p)) return "●  Thinking…";
        if ("SPEAKING".equals(p)) return "●  Speaking  ·  tap the orb to interrupt";
        return handsFree ? "●  Hands-free on  ·  say \"stop\" to end" : "●  Ready";
    }

    private void setPresence(String p) {
        presence = p;
        if (homeOrb != null) homeOrb.setOrbState(p);
        if (chatOrb != null) chatOrb.setOrbState(p);
        if (presenceLabel != null) {
            presenceLabel.setText(presenceText(p));
            presenceLabel.setTextColor(orbColor(p));
        }
    }

    private void orbLevel(float v) {
        if (homeOrb != null) homeOrb.setLevel(v);
        if (chatOrb != null) chatOrb.setLevel(v);
    }

    private OrbView makeOrb() {
        OrbView orb = new OrbView(this);
        orb.setOnClickListener(v -> orbTapped());
        orb.setOnLongClickListener(v -> {
            setHandsFree(!handsFree);
            return true;
        });
        return orb;
    }

    private void orbTapped() {
        if ("SPEAKING".equals(presence)) {
            interruptSpeech();
        } else if ("LISTENING".equals(presence)) {
            if (handsFree) setHandsFree(false);
            else stopVoice();
        } else if ("THINKING".equals(presence)) {
            toast("EDITH is thinking…");
        } else {
            if (!"CHAT".equals(screen)) newChat(null);
            startVoice();
        }
    }

    // ============================================================
    // Lifecycle
    // ============================================================

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);

        Window window = getWindow();
        window.setStatusBarColor(bg);
        window.setNavigationBarColor(bg);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        nativeAvailable = checkNativeAvailable();
        modelFile = new File(getFilesDir(), "edith-model.gguf");
        aiMode = prefs().getString(MODE_KEY, "LOCAL");
        focusMode = prefs().getString(FOCUS_KEY, "GENERAL");

        loadConversations();
        loadProjects();
        loadMemories();
        loadTasks();
        initTextToSpeech();
        initKeySound();

        buildShell();

        if (saved != null) {
            screen = saved.getString("screen", "HOME");
            activeConversationId = saved.getString("activeConv");
            currentProjectId = saved.getString("projectId");
            pendingProjectId = saved.getString("pendingProject");
            pendingDraft = saved.getString("draft");
            refreshScreen();
        } else {
            showHome();
            handleSharedIntent(getIntent());
        }

        if (!modelReady && modelFile.exists() && nativeAvailable) {
            loadModel(modelFile);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleSharedIntent(intent);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("screen", screen);
        out.putString("activeConv", activeConversationId);
        out.putString("projectId", currentProjectId);
        out.putString("pendingProject", pendingProjectId);
        if (messageInput != null) {
            out.putString("draft", messageInput.getText().toString());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (permissionPending) return;
        if (handsFree) setHandsFree(false);
        else stopVoice();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Show the result of a background phone task (e.g. WhatsApp message sent).
        String result = EdithAccessibilityService.takeResult();
        if (result != null) toast(result);

        if (awaitingReturn) {
            awaitingReturn = false;
            confirmNextStep();
        }
    }

    @Override
    public void onBackPressed() {
        if ("DEVICE".equals(screen)) {
            showModels();
        } else if (!"HOME".equals(screen)) {
            showHome();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopVoice();
        if (thinkingAnim != null) thinkingAnim.cancel();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        if (keySound != null) {
            keySound.release();
            keySound = null;
        }
        executor.shutdown();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_FILE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                attachFromUri(data.getData());
            }
            return;
        }

        if (requestCode == PICK_IMAGE_EDIT) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri imageUri = data.getData();
                promptForImageEdit(imageUri);
            }
            return;
        }

        if (requestCode != PICK_MODEL || resultCode != RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();
        String name = displayName(uri);

        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".gguf")) {
            new AlertDialog.Builder(this)
                    .setTitle("Not a GGUF model")
                    .setMessage("Choose a file ending in .gguf.\n\nSelected: "
                            + (name == null ? "unknown file" : name))
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Import " + name + "?")
                .setMessage("EDITH will copy this file into private app storage. "
                        + "Large models can take a few minutes. Your original file stays where it is.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Import", (d, w) -> copyModel(uri))
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean granted = grantResults.length > 0;
        for (int r : grantResults) {
            if (r != PackageManager.PERMISSION_GRANTED) granted = false;
        }

        if (requestCode == VOICE_PERMISSION_REQUEST) {
            permissionPending = false;
            boolean wantedHandsFree = pendingHandsFree;
            pendingHandsFree = false;
            if (granted) {
                if (wantedHandsFree) {
                    setHandsFree(true);
                } else {
                    replyBySpeech = true;
                    launchSpeech();
                }
            } else {
                toast("Microphone permission is needed for voice input.");
            }
        } else if (requestCode == CAL_PERMISSION_REQUEST) {
            JSONObject waiting = pendingCalendarAction;
            pendingCalendarAction = null;
            if (granted && waiting != null) {
                addCalendarEvent(waiting);
            } else {
                toast("Calendar access is needed to add events.");
            }
            confirmNextStep();
        }
    }

    // ============================================================
    // Storage
    // ============================================================

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private ArrayList<JSONObject> readList(String key) {
        ArrayList<JSONObject> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs().getString(key, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) out.add(o);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void writeList(String key, ArrayList<JSONObject> list) {
        JSONArray arr = new JSONArray();
        for (JSONObject o : list) arr.put(o);
        prefs().edit().putString(key, arr.toString()).apply();
    }

    private static void put(JSONObject o, String key, Object value) {
        try {
            o.put(key, value);
        } catch (Exception ignored) {
        }
    }

    private static boolean removeById(ArrayList<JSONObject> list, String id) {
        for (int i = 0; i < list.size(); i++) {
            if (id.equals(list.get(i).optString("id"))) {
                list.remove(i);
                return true;
            }
        }
        return false;
    }

    private void loadConversations() {
        conversations.clear();
        conversations.addAll(readList(CONVERSATIONS_KEY));
        if (!prefs().contains(CONVERSATIONS_KEY)) migrateLegacyHistory();
        tidyAutoTitles();
    }

    private void saveConversations() {
        writeList(CONVERSATIONS_KEY, conversations);
    }

    private void tidyAutoTitles() {
        boolean changed = false;
        for (JSONObject c : conversations) {
            if (c.optBoolean("titled", false)) continue;
            String current = c.optString("title");
            if (!current.isEmpty() && !"New chat".equals(current) && !"Earlier chat".equals(current)) {
                continue;
            }
            JSONArray msgs = messagesOf(c);
            for (int i = 0; i < msgs.length(); i++) {
                JSONObject m = msgs.optJSONObject(i);
                if (m != null && "user".equals(m.optString("role"))) {
                    put(c, "title", makeTitle(m.optString("text")));
                    changed = true;
                    break;
                }
            }
        }
        if (changed) saveConversations();
    }

    private void migrateLegacyHistory() {
        String legacy = prefs().getString(LEGACY_HISTORY_KEY, "");
        if (legacy == null || legacy.trim().isEmpty()) return;

        JSONObject conv = newConversation(null);
        put(conv, "title", "Earlier chat");
        for (String line : legacy.split("\n")) {
            if (line.startsWith("You: ")) {
                appendMessageNoSave(conv, "user", line.substring(5), "");
            } else if (line.startsWith("EDITH: ")) {
                appendMessageNoSave(conv, "assistant", line.substring(7), "");
            }
        }
        conversations.add(conv);
        saveConversations();
    }

    private void loadProjects() {
        projects.clear();
        projects.addAll(readList(PROJECTS_KEY));
    }

    private void saveProjects() {
        writeList(PROJECTS_KEY, projects);
    }

    private void loadTasks() {
        tasks.clear();
        tasks.addAll(readList(TASKS_KEY));
    }

    private void saveTasks() {
        writeList(TASKS_KEY, tasks);
    }

    private void loadMemories() {
        memories.clear();
        memories.addAll(readList(MEMORY_KEY));
        if (!prefs().contains(MEMORY_KEY)) {
            try {
                JSONArray legacy = new JSONArray(prefs().getString(LEGACY_MEMORY_KEY, "[]"));
                for (int i = 0; i < legacy.length(); i++) {
                    String fact = legacy.optString(i, "").trim();
                    if (!fact.isEmpty()) {
                        memories.add(newMemory(fact, "Imported from earlier version"));
                    }
                }
            } catch (Exception ignored) {
            }
            if (!memories.isEmpty()) writeList(MEMORY_KEY, memories);
        }
    }

    private JSONObject newConversation(String projectId) {
        JSONObject conv = new JSONObject();
        put(conv, "id", UUID.randomUUID().toString());
        put(conv, "title", "New chat");
        put(conv, "project", projectId == null ? "" : projectId);
        put(conv, "recall", true);
        put(conv, "titled", false);
        put(conv, "updated", System.currentTimeMillis());
        put(conv, "messages", new JSONArray());
        return conv;
    }

    private JSONObject findConversation(String id) {
        if (id == null) return null;
        for (JSONObject c : conversations) {
            if (id.equals(c.optString("id"))) return c;
        }
        return null;
    }

    private JSONObject activeConversation() {
        return activeConversationId == null ? null : findConversation(activeConversationId);
    }

    private JSONArray messagesOf(JSONObject conv) {
        JSONArray msgs = conv.optJSONArray("messages");
        if (msgs == null) {
            msgs = new JSONArray();
            put(conv, "messages", msgs);
        }
        return msgs;
    }

    private void appendMessageNoSave(JSONObject conv, String role, String text, String provider) {
        JSONArray msgs = messagesOf(conv);
        JSONObject m = new JSONObject();
        put(m, "role", role);
        put(m, "text", text);
        put(m, "provider", provider == null ? "" : provider);
        msgs.put(m);
        while (msgs.length() > MAX_MESSAGES) msgs.remove(0);
        put(conv, "updated", System.currentTimeMillis());
    }

    private void appendMessage(JSONObject conv, String role, String text, String provider) {
        appendMessageNoSave(conv, role, text, provider);
        saveConversations();
    }

    private JSONObject findProject(String id) {
        if (id == null || id.isEmpty()) return null;
        for (JSONObject p : projects) {
            if (id.equals(p.optString("id"))) return p;
        }
        return null;
    }

    private String projectName(String id) {
        JSONObject p = findProject(id);
        return p == null ? null : p.optString("name");
    }

    private int countChats(String projectId) {
        int n = 0;
        for (JSONObject c : conversations) {
            if (projectId.equals(c.optString("project"))) n++;
        }
        return n;
    }

    private ArrayList<JSONObject> sortedConversations(String projectId) {
        ArrayList<JSONObject> out = new ArrayList<>();
        for (JSONObject c : conversations) {
            if (projectId == null || projectId.equals(c.optString("project"))) out.add(c);
        }
        Collections.sort(out, (a, b) -> Long.compare(b.optLong("updated"), a.optLong("updated")));
        return out;
    }

    private JSONObject newMemory(String text, String source) {
        JSONObject m = new JSONObject();
        put(m, "id", UUID.randomUUID().toString());
        put(m, "text", text);
        put(m, "source", source == null ? "" : source);
        put(m, "created", System.currentTimeMillis());
        return m;
    }

    private boolean saveMemory(String text, String source) {
        return saveMemory(text, source, false);
    }

    // "core" memories (preferences, name, key facts) are always given to EDITH,
    // not only when the current message happens to match them.
    private boolean saveMemory(String text, String source, boolean core) {
        String fact = text == null ? "" : text.trim();
        if (fact.isEmpty() || fact.length() > 500) return false;
        for (JSONObject m : memories) {
            if (fact.equalsIgnoreCase(m.optString("text").trim())) return true;
        }
        if (memories.size() >= MAX_MEMORIES) return false;
        JSONObject m = newMemory(fact, source);
        put(m, "core", core);
        memories.add(0, m);
        writeList(MEMORY_KEY, memories);
        return true;
    }

    private boolean isMemoryEnabled() {
        return prefs().getBoolean(MEMORY_ENABLED_KEY, true);
    }

    private boolean isRecallEnabled() {
        return prefs().getBoolean(RECALL_ENABLED_KEY, true);
    }

    private boolean isAutoLearnOn() {
        return prefs().getBoolean(AUTOLEARN_KEY, true);
    }

    private boolean isSpeakAlways() {
        return prefs().getBoolean(SPEAK_KEY, false);
    }

    private boolean isTypingSoundOn() {
        return prefs().getBoolean(TYPING_SOUND_KEY, true);
    }

    private void setFlag(String key, boolean value) {
        prefs().edit().putBoolean(key, value).apply();
    }

    private String getOllamaKey() {
        return prefs().getString(OLLAMA_KEY, "").trim();
    }

    private String getUserName() {
        return prefs().getString(NAME_KEY, "").trim();
    }

    // ============================================================
    // Design helpers
    // ============================================================

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private Typeface edithFont(int weight) {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            try {
                if (weight >= Typeface.BOLD) return getResources().getFont(R.font.space_grotesk_bold);
                if (weight >= Typeface.NORMAL) return getResources().getFont(R.font.space_grotesk_medium);
                return getResources().getFont(R.font.space_grotesk_regular);
            } catch (Exception error) {
                android.util.Log.w("EDITH", "Custom font unavailable", error);
            }
        }
        return Typeface.create("sans-serif", weight);
    }

    private GradientDrawable box(int color, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    private GradientDrawable gradientBox(int from, int to, int radiusDp) {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{from, to});
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(dp(1), border);
        return g;
    }

    private Drawable ripple(GradientDrawable shape) {
        return new RippleDrawable(
                ColorStateList.valueOf(Color.argb(30, 255, 255, 255)), shape, null);
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(edithFont(Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        return view;
    }

    private TextView heading(String value, float size) {
        TextView view = text(value, size, primaryText);
        view.setTypeface(edithFont(Typeface.BOLD));
        return view;
    }

    private TextView sectionLabel(String value) {
        TextView view = text(value, 10, secondaryText);
        view.setTypeface(edithFont(Typeface.BOLD));
        view.setLetterSpacing(0.12f);
        return view;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private LinearLayout.LayoutParams margins(int width, int height,
                                              int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = lp(width, height);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private LinearLayout.LayoutParams tileParams(int left, int right) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(dp(left), 0, dp(right), 0);
        return params;
    }

    private TextView button(String label, int style, View.OnClickListener action) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(14);
        b.setTypeface(edithFont(Typeface.BOLD));
        b.setPadding(dp(16), 0, dp(16), 0);
        b.setMinHeight(dp(46));

        GradientDrawable shape;
        if (style == STYLE_PRIMARY) {
            b.setTextColor(bg);
            shape = box(accent, 0, 14);
        } else if (style == STYLE_DANGER) {
            b.setTextColor(dangerRed);
            shape = box(surface, border, 14);
        } else if (style == STYLE_GHOST) {
            b.setTextColor(accent);
            shape = box(surface, border, 14);
        } else {
            b.setTextColor(primaryText);
            shape = box(surface2, border, 14);
        }
        b.setBackground(ripple(shape));
        b.setOnClickListener(action);
        return b;
    }

    private TextView textAction(String label, int color, View.OnClickListener action) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextColor(color);
        b.setTextSize(13);
        b.setTypeface(edithFont(Typeface.BOLD));
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(8), dp(6), dp(8), dp(6));
        b.setOnClickListener(action);
        return b;
    }

    private TextView iconCircle(String glyph, int fill, int glyphColor, View.OnClickListener action) {
        TextView c = new TextView(this);
        c.setText(glyph);
        c.setTextColor(glyphColor);
        c.setTextSize(18);
        c.setTypeface(edithFont(Typeface.BOLD));
        c.setGravity(Gravity.CENTER);
        c.setBackground(ripple(box(fill, 0, 20)));
        c.setOnClickListener(action);
        return c;
    }

    private EditText styledInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextColor(primaryText);
        input.setHintTextColor(secondaryText);
        input.setTextSize(15);
        input.setTypeface(edithFont(Typeface.NORMAL));
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(box(surface2, border, 14));
        return input;
    }

    private LinearLayout card(int padDp) {
        LinearLayout c = vertical();
        c.setPadding(dp(padDp), dp(padDp), dp(padDp), dp(padDp));
        c.setBackground(box(surface, border, 18));
        return c;
    }

    private LinearLayout addPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout page = vertical();
        page.setPadding(0, dp(4), 0, dp(16));
        scroll.addView(page);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        return page;
    }

    private void addTitle(LinearLayout page, String title, String subtitle) {
        page.addView(heading(title, 24), margins(-1, -2, 0, 4, 0, 4));
        page.addView(text(subtitle, 13, secondaryText), margins(-1, -2, 0, 0, 0, 18));
    }

    private void clearContent() {
        chatTitleView = null;
        if (thinkingAnim != null) {
            thinkingAnim.cancel();
            thinkingAnim = null;
        }
        content.removeAllViews();
        chatList = null;
        chatScroll = null;
        messageInput = null;
        voiceStatus = null;
        presenceLabel = null;
        handsFreeButton = null;
        attachRow = null;
        homeOrb = null;
        chatOrb = null;
        renderNav();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void toastOnUi(String message) {
        runOnUiThread(() -> toast(message));
    }

    private void scrollToBottom() {
        if (chatScroll != null) {
            chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private String formatDate(long ms) {
        if (ms <= 0) return "";
        return new SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(new Date(ms));
    }

    private String formatBytes(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        return mb >= 1024
                ? String.format(Locale.US, "%.2f GB", mb / 1024.0)
                : String.format(Locale.US, "%.0f MB", mb);
    }

    private String providerLabel(String provider) {
        if ("CLOUD".equals(provider)) return "Cloud";
        if ("LOCAL".equals(provider)) return "On-device";
        if ("FALLBACK".equals(provider)) return "Fallback";
        if ("ERROR".equals(provider)) return "Error";
        if ("MEMORY".equals(provider)) return "Memory";
        if ("PHONE".equals(provider)) return "Phone";
        return "Notice";
    }

    private String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h < 12) return "Good morning";
        if (h < 17) return "Good afternoon";
        return "Good evening";
    }

    private String homeGreeting() {
        String name = getUserName();
        String custom = prefs().getString(GREETING_KEY, "").trim();
        if (!custom.isEmpty()) {
            return custom.replace("{name}", name.isEmpty() ? "there" : name);
        }
        return greeting() + (name.isEmpty() ? "" : ", " + name);
    }

    // ============================================================
    // Shell
    // ============================================================

    private void buildShell() {
        root = vertical();
        root.setBackgroundColor(bg);
        root.setFitsSystemWindows(true);
        setContentView(root);

        LinearLayout top = horizontal();
        top.setPadding(dp(20), dp(14), dp(16), dp(10));

        LinearLayout brand = vertical();
        TextView name = heading("EDITH", 22);
        name.setLetterSpacing(0.18f);
        brand.addView(name);
        brand.addView(text("Personal AI assistant", 11, secondaryText));
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));

        badge = text("", 10, secondaryText);
        badge.setTypeface(edithFont(Typeface.BOLD));
        badge.setPadding(dp(12), dp(7), dp(12), dp(7));
        badge.setOnClickListener(v -> showModels());
        top.addView(badge);
        root.addView(top, lp(-1, -2));

        FrameLayout frame = new FrameLayout(this);
        root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));

        content = vertical();
        content.setPadding(dp(18), dp(4), dp(18), dp(8));
        frame.addView(content, new FrameLayout.LayoutParams(-1, -1));

        navBar = horizontal();
        navBar.setPadding(dp(6), dp(6), dp(6), dp(6));
        navBar.setBackground(box(surface, border, 22));
        root.addView(navBar, margins(-1, -2, 14, 0, 14, 12));
    }

    private String activeTab() {
        if ("CHATS".equals(screen) || "CHAT".equals(screen)) return "CHATS";
        if ("PROJECTS".equals(screen) || "PROJECT".equals(screen)) return "PROJECTS";
        if ("MEMORY".equals(screen)) return "MEMORY";
        if ("MODELS".equals(screen) || "DEVICE".equals(screen)) return "MODELS";
        return "HOME";
    }

    private void renderNav() {
        if (navBar == null) return;
        navBar.removeAllViews();
        String active = activeTab();
        addNavItem("🏠", "Home", "HOME", v -> showHome(), active);
        addNavItem("💬", "Chats", "CHATS", v -> showChats(), active);
        addNavItem("📁", "Projects", "PROJECTS", v -> showProjects(), active);
        addNavItem("🧠", "Memory", "MEMORY", v -> showMemory(), active);
        addNavItem("⚙️", "Models", "MODELS", v -> showModels(), active);
    }

    private void addNavItem(String icon, String label, String key,
                            View.OnClickListener action, String active) {
        boolean selected = key.equals(active);

        LinearLayout item = vertical();
        item.setGravity(Gravity.CENTER);
        item.setPadding(0, dp(4), 0, dp(4));

        TextView glyph = text(icon, 17, primaryText);
        glyph.setGravity(Gravity.CENTER);
        glyph.setPadding(dp(16), dp(4), dp(16), dp(4));
        glyph.setBackground(selected ? box(accentSoft, 0, 14) : null);
        item.addView(glyph, lp(-2, -2));

        TextView title = text(label, 10, selected ? accent : secondaryText);
        title.setGravity(Gravity.CENTER);
        if (selected) title.setTypeface(edithFont(Typeface.BOLD));
        item.addView(title, margins(-2, -2, 0, 2, 0, 0));

        item.setOnClickListener(action);
        navBar.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void refreshBadge() {
        if (badge == null) return;
        boolean ready = isReady();
        badge.setText("●  " + aiMode + (ready ? "  ·  READY" : "  ·  SETUP"));
        badge.setTextColor(ready ? readyGreen : secondaryText);
        badge.setBackground(box(ready ? Color.rgb(22, 44, 36) : surface2, 0, 16));
    }

    private boolean isReady() {
        if ("ONLINE".equals(aiMode)) return !getOllamaKey().isEmpty();
        if ("AUTO".equals(aiMode)) return modelReady || !getOllamaKey().isEmpty();
        return modelReady;
    }

    private String statusLine() {
        if ("ONLINE".equals(aiMode)) {
            return "Cloud mode  ·  " + (getOllamaKey().isEmpty() ? "add a key to start" : OLLAMA_MODEL);
        }
        if ("AUTO".equals(aiMode)) return "Auto mode  ·  cloud with on-device fallback";
        if (!nativeAvailable) return "On-device engine unavailable";
        return modelReady ? "On-device model ready" : "No on-device model loaded";
    }

    private void refreshScreen() {
        switch (screen) {
            case "CHATS": showChats(); break;
            case "PROJECTS": showProjects(); break;
            case "PROJECT": showProject(currentProjectId); break;
            case "MEMORY": showMemory(); break;
            case "MODELS": showModels(); break;
            case "DEVICE": showDeviceCheck(); break;
            case "TASKS": showTasks(); break;
            case "PERSONA": showPersona(); break;
            case "TOOLS": showCreativeTools(); break;
            case "SHOP": showShopping(); break;
            case "CABS": showCabs(); break;
            case "CAB_ROUTE":
                if (cabRouteId != null) showCabRoute(cabRouteId); else showCabs();
                break;
            case "CHAT": showChat(); break;
            default: showHome();
        }
    }

    // ============================================================
    // Focus modes
    // ============================================================

    private String focusLabel() {
        if ("ANDROID".equals(focusMode)) return "Android features";
        if ("CODE".equals(focusMode)) return "Code";
        if ("PLAN".equals(focusMode)) return "Task planner";
        return "General";
    }

    private void setFocus(String mode) {
        focusMode = mode.equals(focusMode) ? "GENERAL" : mode;
        prefs().edit().putString(FOCUS_KEY, focusMode).apply();
    }

    private String[] suggestionsFor() {
        if ("CODE".equals(focusMode)) {
            return new String[]{
                    "Write a function that sorts a list",
                    "Explain this error message",
                    "Review my code for bugs",
                    "Write unit tests for a login check"
            };
        }
        if ("ANDROID".equals(focusMode)) {
            return new String[]{
                    "Set a timer for 10 minutes",
                    "Add a meeting tomorrow at 3pm",
                    "Message Ravi on WhatsApp saying I'm on my way",
                    "Open the Wi-Fi settings"
            };
        }
        if ("PLAN".equals(focusMode)) {
            return new String[]{
                    "Help me prepare for tomorrow",
                    "Plan my study schedule for this week",
                    "Checklist for a weekend trip",
                    "Plan a small birthday party"
            };
        }
        return SUGGESTIONS;
    }

    private LinearLayout modeCard(String glyph, String title, String sub, String mode) {
        boolean on = mode.equals(focusMode);

        LinearLayout c = vertical();
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        c.setBackground(ripple(on
                ? gradientBox(Color.rgb(24, 74, 72), surface, 18)
                : box(surface, border, 18)));

        LinearLayout top = horizontal();
        TextView g = text(glyph, 15, on ? bg : accent);
        g.setGravity(Gravity.CENTER);
        g.setTypeface(edithFont(Typeface.BOLD));
        g.setPadding(dp(10), dp(6), dp(10), dp(6));
        g.setBackground(box(on ? accent : accentSoft, 0, 12));
        top.addView(g, lp(-2, -2));

        View spacer = new View(this);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));

        TextView pill = text(on ? "ACTIVE" : "", 9, accent);
        pill.setTypeface(edithFont(Typeface.BOLD));
        pill.setLetterSpacing(0.1f);
        top.addView(pill, lp(-2, -2));
        c.addView(top, lp(-1, -2));

        c.addView(heading(title, 15), margins(-1, -2, 0, 14, 0, 0));
        c.addView(text(sub, 11, secondaryText), margins(-1, -2, 0, 4, 0, 0));

        c.setOnClickListener(v -> {
            setFocus(mode);
            newChat(null);
        });
        return c;
    }

    // ============================================================
    // Home
    // ============================================================

    private void showHome() {
        screen = "HOME";
        clearContent();
        LinearLayout page = addPage();

        LinearLayout hero = vertical();
        hero.setPadding(dp(20), dp(20), dp(20), dp(20));
        hero.setBackground(gradientBox(Color.rgb(22, 60, 60), surface, 22));

        LinearLayout heroTop = horizontal();
        LinearLayout heroText = vertical();
        heroText.addView(heading(homeGreeting(), 20));
        heroText.addView(text("What should we work on?", 13, primaryText),
                margins(-1, -2, 0, 6, 0, 6));
        heroText.addView(text(statusLine(), 11, secondaryText));
        presenceLabel = text(presenceText(presence), 11, accent);
        heroText.addView(presenceLabel, margins(-1, -2, 0, 6, 0, 0));
        heroTop.addView(heroText, new LinearLayout.LayoutParams(0, -2, 1));

        homeOrb = makeOrb();
        heroTop.addView(homeOrb, margins(dp(96), dp(96), 8, 0, 0, 0));
        hero.addView(heroTop, lp(-1, -2));

        hero.addView(text("Tap the orb to talk  ·  hold it for hands-free", 10, secondaryText),
                margins(-1, -2, 0, 10, 0, 14));

        LinearLayout heroActions = horizontal();
        heroActions.addView(button("New chat", STYLE_PRIMARY, v -> newChat(null)), tileParams(0, 6));
        heroActions.addView(button("Chats", STYLE_SECONDARY, v -> showChats()), tileParams(6, 0));
        hero.addView(heroActions, lp(-1, -2));
        page.addView(hero, margins(-1, -2, 0, 0, 0, 18));

        page.addView(sectionLabel("CHOOSE A MODE"), margins(-1, -2, 0, 0, 0, 8));
        LinearLayout modes = horizontal();
        modes.addView(modeCard("📱", "Android features",
                "Calls, texts, WhatsApp, timers, calendar", "ANDROID"), tileParams(0, 6));
        modes.addView(modeCard("</>", "Code",
                "Write, fix and explain code", "CODE"), tileParams(6, 0));
        page.addView(modes, margins(-1, -2, 0, 0, 0, 10));

        LinearLayout modes2 = horizontal();
        modes2.addView(modeCard("☑", "Task planner",
                "Checklists with progress", "PLAN"), tileParams(0, 6));
        modes2.addView(tile("🎧", "Hands-free", "Talk, no tapping", v -> {
            newChat(null);
            setHandsFree(true);
        }), tileParams(6, 0));
        page.addView(modes2, margins(-1, -2, 0, 0, 0, 8));
        page.addView(text("Tap a mode to start a chat with it. Tap the active mode to go back to General.",
                11, secondaryText), margins(-1, -2, 4, 0, 4, 22));

        page.addView(sectionLabel("QUICK ACCESS"), margins(-1, -2, 0, 0, 0, 8));
        LinearLayout row1 = horizontal();
        row1.addView(tile("🧠", "Memory", "Facts EDITH knows", v -> showMemory()), tileParams(0, 6));
        row1.addView(tile("📁", "Projects", "Group your chats", v -> showProjects()), tileParams(6, 0));
        page.addView(row1, margins(-1, -2, 0, 0, 0, 10));

        LinearLayout row2 = horizontal();
        row2.addView(tile("☑", "Tasks", "Plans and progress", v -> showTasks()), tileParams(0, 6));
        row2.addView(tile("✨", "Persona", "Name, voice, greeting", v -> showPersona()), tileParams(6, 0));
        page.addView(row2, margins(-1, -2, 0, 0, 0, 10));

        LinearLayout row3 = horizontal();
        row3.addView(tile("⬆", "Import model", "Add a .gguf file", v -> importModel()), tileParams(0, 6));
        row3.addView(tile("✓", "Phone check", "See what works here", v -> showDeviceCheck()), tileParams(6, 0));
        page.addView(row3, margins(-1, -2, 0, 0, 0, 10));

        page.addView(tile("🎨", "Creative tools", "Generate or edit images · make PDFs",
                v -> showCreativeTools()), margins(-1, -2, 0, 0, 0, 10));
        page.addView(tile("🛒", "Smart shopping", "Compare stores · track prices",
                v -> showShopping()), margins(-1, -2, 0, 0, 0, 10));
        page.addView(tile("🚕", "Cab fares", "Compare fares you enter · track routes",
                v -> showCabs()), margins(-1, -2, 0, 0, 0, 24));

        ArrayList<JSONObject> active = new ArrayList<>();
        for (JSONObject t : tasks) {
            if (stepsDone(t) < stepsTotal(t)) active.add(t);
        }
        if (!active.isEmpty()) {
            LinearLayout planHeader = horizontal();
            planHeader.addView(sectionLabel("ACTIVE PLANS"), new LinearLayout.LayoutParams(0, -2, 1));
            planHeader.addView(textAction("See all", accent, v -> showTasks()), lp(-2, -2));
            page.addView(planHeader, margins(-1, -2, 0, 0, 0, 10));
            for (int i = 0; i < active.size() && i < 2; i++) {
                LinearLayout holder = card(16);
                fillPlanCard(holder, active.get(i), false);
                page.addView(holder, margins(-1, -2, 0, 0, 0, 8));
            }
            page.addView(new View(this), lp(-1, dp(14)));
        }

        LinearLayout header = horizontal();
        LinearLayout labels = vertical();
        labels.addView(sectionLabel("RECENT CHATS"));
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(textAction("See all", accent, v -> showChats()), lp(-2, -2));
        page.addView(header, margins(-1, -2, 0, 0, 0, 10));

        ArrayList<JSONObject> recent = sortedConversations(null);
        if (recent.isEmpty()) {
            LinearLayout empty = card(16);
            empty.addView(text("No conversations yet. Pick a mode above to start one.",
                    13, secondaryText));
            page.addView(empty, lp(-1, -2));
        }
        for (int i = 0; i < recent.size() && i < 3; i++) {
            page.addView(conversationCard(recent.get(i)), margins(-1, -2, 0, 0, 0, 8));
        }

        refreshBadge();
        setPresence(presence);
    }

    private LinearLayout tile(String glyph, String title, String sub, View.OnClickListener action) {
        LinearLayout t = vertical();
        t.setPadding(dp(14), dp(14), dp(14), dp(14));
        t.setBackground(ripple(box(surface, border, 18)));

        TextView g = text(glyph, 18, accent);
        g.setGravity(Gravity.CENTER);
        g.setPadding(dp(9), dp(5), dp(9), dp(5));
        g.setBackground(box(accentSoft, 0, 12));
        t.addView(g, lp(-2, -2));

        t.addView(heading(title, 14), margins(-1, -2, 0, 14, 0, 0));
        t.addView(text(sub, 11, secondaryText), margins(-1, -2, 0, 2, 0, 0));
        t.setOnClickListener(action);
        return t;
    }

    private LinearLayout conversationCard(JSONObject conv) {
        final String id = conv.optString("id");
        LinearLayout row = horizontal();
        row.setPadding(dp(14), dp(12), dp(10), dp(12));
        row.setBackground(ripple(box(surface, border, 16)));

        LinearLayout labels = vertical();
        labels.addView(heading(conv.optString("title", "Chat"), 14));

        String project = projectName(conv.optString("project"));
        int count = messagesOf(conv).length();
        String meta = (project == null ? "" : project + "  ·  ")
                + formatDate(conv.optLong("updated"))
                + "  ·  " + count + (count == 1 ? " message" : " messages");
        labels.addView(text(meta, 11, secondaryText), margins(-1, -2, 0, 4, 0, 0));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        row.addView(textAction("⋯", secondaryText, v -> showConversationMenu(id)), lp(-2, -2));

        row.setOnClickListener(v -> openConversation(id));
        row.setOnLongClickListener(v -> {
            showConversationMenu(id);
            return true;
        });
        return row;
    }

    // ============================================================
    // Chats list
    // ============================================================

    private void showChats() {
        screen = "CHATS";
        clearContent();
        LinearLayout page = addPage();
        addTitle(page, "Chats", "Every conversation stays saved until you delete it.");

        page.addView(button("+  New chat", STYLE_PRIMARY, v -> newChat(null)),
                margins(-1, -2, 0, 0, 0, 12));

        EditText search = styledInput("Search chats");
        search.setSingleLine(true);
        page.addView(search, margins(-1, -2, 0, 0, 0, 12));

        final LinearLayout list = vertical();
        page.addView(list, lp(-1, -2));

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                renderChatList(list, s.toString(), null);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        renderChatList(list, "", null);
    }

    private void renderChatList(LinearLayout list, String query, String projectId) {
        list.removeAllViews();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (JSONObject c : sortedConversations(projectId)) {
            if (!q.isEmpty() && !c.optString("title").toLowerCase(Locale.ROOT).contains(q)) continue;
            list.addView(conversationCard(c), margins(-1, -2, 0, 0, 0, 8));
            shown++;
        }
        if (shown == 0) {
            list.addView(text(q.isEmpty() ? "No chats yet."
                            : "No chats match \"" + query.trim() + "\".",
                    13, secondaryText), margins(-1, -2, 4, 8, 0, 0));
        }
    }

    private void showConversationMenu(String id) {
        JSONObject conv = findConversation(id);
        if (conv == null) return;

        String recallLabel = conv.optBoolean("recall", true)
                ? "Exclude from memory recall"
                : "Include in memory recall";

        new AlertDialog.Builder(this)
                .setTitle(conv.optString("title", "Chat"))
                .setItems(new String[]{"Open", "Rename", "Move to project", recallLabel, "Delete"},
                        (d, which) -> {
                            if (which == 0) {
                                openConversation(id);
                            } else if (which == 1) {
                                renameConversation(id);
                            } else if (which == 2) {
                                chooseProject(id);
                            } else if (which == 3) {
                                put(conv, "recall", !conv.optBoolean("recall", true));
                                saveConversations();
                                toast(conv.optBoolean("recall", true)
                                        ? "Included in memory recall"
                                        : "Excluded from memory recall");
                            } else {
                                confirmDeleteConversation(id);
                            }
                        })
                .show();
    }

    private void renameConversation(String id) {
        JSONObject conv = findConversation(id);
        if (conv == null) return;
        EditText input = styledInput("Chat name");
        input.setText(conv.optString("title"));
        new AlertDialog.Builder(this)
                .setTitle("Rename chat")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String title = input.getText().toString().trim();
                    if (!title.isEmpty()) {
                        put(conv, "title", title);
                        put(conv, "titled", true);
                        saveConversations();
                        refreshScreen();
                    }
                })
                .show();
    }

    private void chooseProject(String convId) {
        JSONObject conv = findConversation(convId);
        if (conv == null) return;

        ArrayList<String> names = new ArrayList<>();
        ArrayList<String> ids = new ArrayList<>();
        names.add("No project");
        ids.add("");
        for (JSONObject p : projects) {
            names.add(p.optString("name"));
            ids.add(p.optString("id"));
        }

        new AlertDialog.Builder(this)
                .setTitle("Move to project")
                .setItems(names.toArray(new String[0]), (d, which) -> {
                    put(conv, "project", ids.get(which));
                    saveConversations();
                    refreshScreen();
                })
                .show();
    }

    private void confirmDeleteConversation(String id) {
        new AlertDialog.Builder(this)
                .setTitle("Delete chat?")
                .setMessage("This removes the chat and all its messages.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    removeById(conversations, id);
                    if (id.equals(activeConversationId)) activeConversationId = null;
                    saveConversations();
                    if ("CHAT".equals(screen)) showChats();
                    else refreshScreen();
                })
                .show();
    }

    // ============================================================
    // Projects
    // ============================================================

    private void showProjects() {
        screen = "PROJECTS";
        clearContent();
        LinearLayout page = addPage();
        addTitle(page, "Projects", "Group chats together and give them shared instructions.");

        page.addView(button("+  New project", STYLE_PRIMARY, v -> editProject(null)),
                margins(-1, -2, 0, 0, 0, 14));

        if (projects.isEmpty()) {
            LinearLayout empty = card(16);
            empty.addView(text("No projects yet. Create one to group related chats.",
                    13, secondaryText));
            page.addView(empty, lp(-1, -2));
        }
        for (JSONObject p : projects) {
            page.addView(projectCard(p), margins(-1, -2, 0, 0, 0, 8));
        }
    }

    private LinearLayout projectCard(JSONObject p) {
        final String pid = p.optString("id");
        LinearLayout row = vertical();
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackground(ripple(box(surface, border, 18)));

        LinearLayout head = horizontal();
        head.addView(heading(p.optString("name"), 15), new LinearLayout.LayoutParams(0, -2, 1));
        int count = countChats(pid);
        TextView badgeCount = text(count + (count == 1 ? " chat" : " chats"), 11, accent);
        badgeCount.setPadding(dp(10), dp(4), dp(10), dp(4));
        badgeCount.setBackground(box(accentSoft, 0, 10));
        head.addView(badgeCount, lp(-2, -2));
        row.addView(head, lp(-1, -2));

        String instructions = p.optString("instructions").trim();
        row.addView(text(instructions.isEmpty() ? "No instructions set"
                        : truncate(instructions, 90),
                12, instructions.isEmpty() ? secondaryText : primaryText),
                margins(-1, -2, 0, 8, 0, 0));

        row.setOnClickListener(v -> showProject(pid));
        return row;
    }

    private void showProject(String projectId) {
        JSONObject project = findProject(projectId);
        if (project == null) {
            showProjects();
            return;
        }
        screen = "PROJECT";
        currentProjectId = projectId;
        clearContent();

        LinearLayout page = addPage();
        page.addView(textAction("← Projects", accent, v -> showProjects()), lp(-2, -2));
        addTitle(page, project.optString("name"),
                "Instructions apply to every chat in this project.");

        LinearLayout instructionsCard = card(16);
        instructionsCard.addView(sectionLabel("INSTRUCTIONS"), margins(-1, -2, 0, 0, 0, 8));
        String instructions = project.optString("instructions").trim();
        instructionsCard.addView(text(instructions.isEmpty() ? "None set." : instructions, 13,
                instructions.isEmpty() ? secondaryText : primaryText));
        page.addView(instructionsCard, margins(-1, -2, 0, 0, 0, 12));

        page.addView(button("+  New chat in project", STYLE_PRIMARY,
                v -> newChat(projectId)), margins(-1, -2, 0, 0, 0, 8));

        LinearLayout actions = horizontal();
        actions.addView(button("Edit", STYLE_SECONDARY, v -> editProject(project)),
                tileParams(0, 6));
        actions.addView(button("Delete", STYLE_DANGER, v -> confirmDeleteProject(projectId)),
                tileParams(6, 0));
        page.addView(actions, margins(-1, -2, 0, 0, 0, 20));

        page.addView(sectionLabel("CHATS IN THIS PROJECT"), margins(-1, -2, 0, 0, 0, 8));
        LinearLayout list = vertical();
        page.addView(list, lp(-1, -2));
        renderChatList(list, "", projectId);
    }

    private void editProject(JSONObject existing) {
        LinearLayout form = vertical();
        form.setPadding(dp(4), dp(8), dp(4), 0);

        EditText name = styledInput("Project name");
        name.setSingleLine(true);
        EditText instructions = styledInput("Instructions (optional)");
        instructions.setMinLines(3);

        if (existing != null) {
            name.setText(existing.optString("name"));
            instructions.setText(existing.optString("instructions"));
        }

        form.addView(name, margins(-1, -2, 0, 0, 0, 10));
        form.addView(instructions, margins(-1, -2, 0, 0, 0, 4));

        new AlertDialog.Builder(this)
                .setTitle(existing == null ? "New project" : "Edit project")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) {
                        toast("A project needs a name.");
                        return;
                    }
                    JSONObject target = existing;
                    if (target == null) {
                        target = new JSONObject();
                        put(target, "id", UUID.randomUUID().toString());
                        projects.add(target);
                    }
                    put(target, "name", n);
                    put(target, "instructions", instructions.getText().toString().trim());
                    saveProjects();
                    if (existing == null) showProjects();
                    else showProject(target.optString("id"));
                })
                .show();
    }

    private void confirmDeleteProject(String id) {
        new AlertDialog.Builder(this)
                .setTitle("Delete project?")
                .setMessage("Its chats are kept and moved out of the project.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    removeById(projects, id);
                    for (JSONObject c : conversations) {
                        if (id.equals(c.optString("project"))) put(c, "project", "");
                    }
                    saveProjects();
                    saveConversations();
                    showProjects();
                })
                .show();
    }

    // ============================================================
    // Chat screen
    // ============================================================

    private void newChat(String projectId) {
        activeConversationId = null;
        pendingProjectId = projectId;
        showChat();
    }

    private void openConversation(String id) {
        activeConversationId = id;
        pendingProjectId = null;
        showChat();
    }

    private void showChat() {
        screen = "CHAT";
        clearContent();

        JSONObject conv = activeConversation();
        String projectId = conv != null ? conv.optString("project")
                : (pendingProjectId == null ? "" : pendingProjectId);
        String project = projectName(projectId);

        LinearLayout top = horizontal();
        top.setPadding(0, 0, 0, dp(8));
        top.addView(textAction("←", accent, v -> showChats()), lp(-2, -2));

        chatOrb = makeOrb();
        top.addView(chatOrb, margins(dp(44), dp(44), 0, 0, 6, 0));

        LinearLayout titles = vertical();
        chatTitleView = heading(conv == null ? "New chat" : conv.optString("title"), 16);
        titles.addView(chatTitleView);
        String subtitle = (project == null ? "" : project + "  ·  ")
                + focusLabel() + "  ·  " + aiMode;
        titles.addView(text(subtitle, 11, secondaryText));
        presenceLabel = text(presenceText(presence), 10, accent);
        titles.addView(presenceLabel, margins(-1, -2, 0, 2, 0, 0));
        top.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));

        top.addView(textAction("⋯", secondaryText, v -> {
            if (activeConversationId != null && findConversation(activeConversationId) != null) {
                showConversationMenu(activeConversationId);
            } else {
                toast("Send a message to save this chat first.");
            }
        }), lp(-2, -2));
        content.addView(top, lp(-1, -2));

        chatScroll = new ScrollView(this);
        chatScroll.setVerticalScrollBarEnabled(false);
        chatList = vertical();
        chatList.setPadding(0, dp(6), 0, dp(12));
        chatScroll.addView(chatList);
        content.addView(chatScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        renderMessages();
        scrollToBottom();

        voiceStatus = text("", 11, accent);
        content.addView(voiceStatus, margins(-1, -2, 6, 0, 0, 2));

        attachRow = horizontal();
        attachRow.setPadding(dp(4), 0, 0, dp(4));
        content.addView(attachRow, lp(-1, -2));
        updateAttachChip();

        LinearLayout composer = horizontal();
        composer.setPadding(dp(6), dp(6), dp(6), dp(6));
        composer.setBackground(box(surface2, border, 24));

        composer.addView(iconCircle("📎", surface, accent, v -> pickFile()), lp(dp(42), dp(42)));

        messageInput = new EditText(this);
        messageInput.setMaxLines(5);
        messageInput.setTextSize(15);
        messageInput.setTextColor(primaryText);
        messageInput.setHintTextColor(secondaryText);
        messageInput.setHint("Message EDITH");
        messageInput.setTypeface(edithFont(Typeface.NORMAL));
        messageInput.setBackgroundColor(Color.TRANSPARENT);
        messageInput.setPadding(dp(8), dp(8), dp(6), dp(8));
        attachTypingSound(messageInput);
        if (pendingDraft != null) {
            messageInput.setText(pendingDraft);
            messageInput.setSelection(pendingDraft.length());
            pendingDraft = null;
        }
        composer.addView(messageInput, new LinearLayout.LayoutParams(0, -2, 1));

        handsFreeButton = iconCircle("🎧", surface, accent, v -> setHandsFree(!handsFree));
        composer.addView(handsFreeButton, lp(dp(42), dp(42)));
        updateHandsFreeButton();

        composer.addView(iconCircle("🎙", surface, accent, v -> startVoice()),
                margins(dp(42), dp(42), 4, 0, 0, 0));

        TextView send = iconCircle("➤", accent, bg, v -> sendMessage());
        send.setTextSize(20);
        composer.addView(send, margins(dp(48), dp(48), 4, 0, 0, 0));

        content.addView(composer, margins(-1, -2, 0, 4, 0, 0));
        setPresence(presence);
    }

    private void updateHandsFreeButton() {
        if (handsFreeButton == null) return;
        handsFreeButton.setTextColor(handsFree ? bg : accent);
        handsFreeButton.setBackground(ripple(box(handsFree ? accent : surface, 0, 20)));
    }

    private void refreshChatView() {
        if (chatList == null) return;
        renderMessages();
        scrollToBottom();
    }

    private void renderMessages() {
        chatList.removeAllViews();
        if (thinkingAnim != null) {
            thinkingAnim.cancel();
            thinkingAnim = null;
        }

        JSONObject conv = activeConversation();
        JSONArray msgs = conv == null ? new JSONArray() : messagesOf(conv);

        if (msgs.length() == 0) {
            addEmptyState();
        }

        for (int i = 0; i < msgs.length(); i++) {
            JSONObject m = msgs.optJSONObject(i);
            if (m == null) continue;
            boolean latest = i == msgs.length() - 1;
            if ("user".equals(m.optString("role"))) {
                addUserBubble(m.optString("text"), m.optString("attach_name"));
            } else {
                addAssistantMessage(m, latest);
            }
        }

        if (busyConversationId != null && busyConversationId.equals(activeConversationId)) {
            addThinking();
        }
    }

    private void addEmptyState() {
        LinearLayout empty = vertical();
        empty.setGravity(Gravity.CENTER_HORIZONTAL);
        empty.setPadding(0, dp(24), 0, dp(12));

        TextView mark = text("E", 22, bg);
        mark.setGravity(Gravity.CENTER);
        mark.setTypeface(edithFont(Typeface.BOLD));
        mark.setBackground(box(accent, 0, 20));
        empty.addView(mark, lp(dp(56), dp(56)));

        TextView title = heading(isReady() ? "What's the mission?"
                : "Let's get you set up", 20);
        title.setGravity(Gravity.CENTER);
        empty.addView(title, margins(-1, -2, 0, 16, 0, 6));

        TextView sub = text(isReady()
                        ? "Ask anything, attach a file or screenshot, or tap 🎙 to talk."
                        : "Load a model in Models, or add an Ollama key to start.",
                13, secondaryText);
        sub.setGravity(Gravity.CENTER);
        empty.addView(sub, margins(-1, -2, 12, 0, 12, 18));

        if (isReady() && ("ANDROID".equals(focusMode) || "CODE".equals(focusMode))) {
            addCapabilityCards(empty);
        } else if (isReady()) {
            for (String suggestion : suggestionsFor()) {
                TextView chip = text(suggestion, 13, primaryText);
                chip.setPadding(dp(14), dp(11), dp(14), dp(11));
                chip.setBackground(ripple(box(surface, border, 14)));
                chip.setOnClickListener(v -> {
                    if (messageInput != null) {
                        messageInput.setText(suggestion);
                        messageInput.setSelection(suggestion.length());
                    }
                });
                empty.addView(chip, margins(-1, -2, 0, 0, 0, 8));
            }
        }
        chatList.addView(empty, lp(-1, -2));
    }

    private int maxBubbleWidth() {
        return getResources().getDisplayMetrics().widthPixels * 78 / 100;
    }

    private void addUserBubble(String body, String attachName) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        String shown = (attachName == null || attachName.isEmpty())
                ? body : "📎 " + attachName + "\n" + body;
        TextView bubble = text(shown, 15, primaryText);
        bubble.setLineSpacing(dp(3), 1.0f);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.setBackground(ripple(box(userBubble, 0, 18)));
        bubble.setMaxWidth(maxBubbleWidth());
        bubble.setTextIsSelectable(true);
        bubble.setOnLongClickListener(v -> {
            copyText(body);
            return true;
        });
        row.addView(bubble, lp(-2, -2));
        chatList.addView(row, margins(-1, -2, 0, 6, 0, 6));
    }

    private void addAssistantMessage(JSONObject m, boolean latest) {
        String body = m.optString("text");
        String provider = m.optString("provider");
        String planId = m.optString("plan");
        JSONObject task = planId.isEmpty() ? null : findTask(planId);

        LinearLayout row = horizontal();
        row.setGravity(Gravity.TOP);

        TextView avatar = text("E", 13, bg);
        avatar.setGravity(Gravity.CENTER);
        avatar.setTypeface(edithFont(Typeface.BOLD));
        avatar.setBackground(box(accent, 0, 14));
        row.addView(avatar, lp(dp(28), dp(28)));

        LinearLayout col = vertical();
        col.setPadding(dp(10), 0, 0, 0);

        String label = "EDITH" + (provider.isEmpty() ? "" : "  ·  " + providerLabel(provider));
        TextView sender = text(label, 10, secondaryText);
        sender.setTypeface(edithFont(Typeface.BOLD));
        sender.setLetterSpacing(0.08f);
        col.addView(sender);

        JSONObject actionObj = null;
        String actionRaw = m.optString("action");
        if (!actionRaw.isEmpty()) {
            try {
                actionObj = new JSONObject(actionRaw);
            } catch (Exception ignored) {
            }
        }

        if (task != null) {
            LinearLayout holder = card(14);
            fillPlanCard(holder, task, false);
            col.addView(holder, margins(-1, -2, 0, 8, 0, 0));
        } else if (actionObj != null) {
            addActionCards(col, actionObj);
        } else {
            addRichText(col, body, latest);
        }

        LinearLayout actions = horizontal();
        actions.setPadding(0, dp(4), 0, 0);
        actions.addView(textAction("Copy", secondaryText, v -> copyText(body)), lp(-2, -2));
        actions.addView(textAction("Speak", secondaryText, v -> speak(body)), lp(-2, -2));
        actions.addView(textAction("Save memory", secondaryText, v -> {
            JSONObject conv = activeConversation();
            String source = conv == null ? "" : conv.optString("title");
            toast(saveMemory(body, source)
                    ? "Saved to memory"
                    : "Could not save: too long or memory is full.");
        }), lp(-2, -2));
        if (latest) {
            actions.addView(textAction("Regenerate", accent, v -> regenerate()), lp(-2, -2));
        }
        col.addView(actions, lp(-1, -2));

        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        chatList.addView(row, margins(-1, -2, 0, 8, 0, 10));
    }

    private void addThinking() {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView avatar = text("E", 13, bg);
        avatar.setGravity(Gravity.CENTER);
        avatar.setTypeface(edithFont(Typeface.BOLD));
        avatar.setBackground(box(accent, 0, 14));
        row.addView(avatar, lp(dp(28), dp(28)));

        TextView dots = text("●  ●  ●", 10, accent);
        dots.setPadding(dp(12), 0, 0, 0);
        row.addView(dots, lp(-2, -2));

        thinkingAnim = ObjectAnimator.ofFloat(dots, "alpha", 0.2f, 1f);
        thinkingAnim.setDuration(800);
        thinkingAnim.setRepeatCount(ObjectAnimator.INFINITE);
        thinkingAnim.setRepeatMode(ObjectAnimator.REVERSE);
        thinkingAnim.start();

        chatList.addView(row, margins(-1, -2, 0, 8, 0, 10));
    }

    private void showMessageMenu(String body, boolean latest) {
        ArrayList<String> items = new ArrayList<>();
        items.add("Copy");
        items.add("Read aloud");
        items.add("Save as memory");
        if (latest) items.add("Regenerate");

        new AlertDialog.Builder(this)
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    String choice = items.get(which);
                    if ("Copy".equals(choice)) {
                        copyText(body);
                    } else if ("Read aloud".equals(choice)) {
                        speak(body);
                    } else if ("Save as memory".equals(choice)) {
                        JSONObject conv = activeConversation();
                        String source = conv == null ? "" : conv.optString("title");
                        toast(saveMemory(body, source)
                                ? "Saved to memory"
                                : "Could not save: too long or memory is full.");
                    } else if ("Regenerate".equals(choice)) {
                        regenerate();
                    }
                })
                .show();
    }

    // ============================================================
    // Rich replies: prose + syntax-highlighted code cards
    // ============================================================

    private static final Pattern FENCE = Pattern.compile(
            "```([A-Za-z0-9_+#.\\-]*)[ \\t]*\\r?\\n([\\s\\S]*?)(?:```|$)");
    private static final Pattern INLINE = Pattern.compile(
            "\\*\\*(.+?)\\*\\*|`([^`\\n]+)`|<sub>(.+?)</sub>|<sup>(.+?)</sup>"
                    + "|(?<![*\\w])\\*([^*\\n]+)\\*(?![*\\w])", Pattern.CASE_INSENSITIVE);
    private static final Set<String> CODE_KEYWORDS = new HashSet<>(java.util.Arrays.asList((
            "public private protected static final class interface enum extends implements new "
                    + "return if else for while do switch case break continue try catch finally throw "
                    + "throws import package void int long double float boolean char byte short "
                    + "null true false this super def self None True False and or not in is lambda "
                    + "from as with yield pass raise except elif async await function const let var "
                    + "fun val when object override export default typeof instanceof struct "
                    + "namespace using sizeof unsigned signed template typename auto nullptr "
                    + "select insert update delete create table where join set values into "
                    + "echo fi then done").split(" ")));

    private static final String[][] ANDROID_CARDS = {
            {"⏱", "Timer", "Set a timer for 10 minutes", "Set a timer for 10 minutes"},
            {"⏰", "Alarm", "Wake me at 7:30", "Set an alarm for 7:30"},
            {"💬", "WhatsApp", "Message a contact", "Send a WhatsApp message to "},
            {"📞", "Call", "Dial a number or contact", "Call "},
            {"✉️", "Text", "Send an SMS", "Send a text message to "},
            {"📧", "Email", "Draft an email", "Send an email to "},
            {"📅", "Calendar", "Add an event", "Add to my calendar: "},
            {"🧭", "Navigate", "Directions in Maps", "Navigate to "},
            {"🎵", "Music", "Play a song or artist", "Play "},
            {"📲", "Open app", "Launch any app", "Open "},
            {"👤", "Contact", "Save a new contact", "Save a contact named "},
            {"⚙️", "Settings", "Wi-Fi, sound, battery…", "Open Wi-Fi settings"},
            {"📷", "Camera", "Open the camera", "Open the camera"},
            {"🔎", "Search", "Search the web", "Search the web for "}
    };

    private static final String[][] CODE_CARDS = {
            {"⌨", "Write code", "A function, class or script", "Write a function that "},
            {"💡", "Explain", "Walk through what it does", "Explain this code:\n"},
            {"🐞", "Debug", "Find and fix the bug", "Find and fix the bug in this code:\n"},
            {"🔍", "Review", "Spot issues and improvements",
                    "Review this code and suggest improvements:\n"},
            {"🧪", "Tests", "Unit tests for your code", "Write unit tests for this code:\n"},
            {"♻️", "Refactor", "A cleaner version", "Refactor this code to be cleaner:\n"},
            {"🔁", "Convert", "Move to another language", "Convert this code to Python:\n"},
            {"⚡", "Optimize", "Make it faster", "Optimize this code for speed:\n"},
            {"📝", "Document", "Comments and docstrings",
                    "Add comments and documentation to this code:\n"},
            {"🧩", "Regex / SQL", "Patterns and queries", "Write a regex that matches "}
    };

    private void addRichText(LinearLayout col, String body, boolean latest) {
        Matcher m = FENCE.matcher(body);
        int pos = 0;
        boolean first = true;
        while (m.find()) {
            String before = body.substring(pos, m.start()).trim();
            if (!before.isEmpty()) {
                first = addProse(col, before, body, latest, first);
            }
            String code = m.group(2).replaceAll("\\s+$", "");
            col.addView(codeCard(m.group(1), code),
                    margins(-1, -2, 0, first ? 8 : 6, 0, 4));
            first = false;
            pos = m.end();
        }
        String rest = body.substring(pos).trim();
        if (!rest.isEmpty()) {
            addProse(col, rest, body, latest, first);
        } else if (first) {
            col.addView(proseView("", body, latest, true), lp(-1, -2));
        }
    }

    // Splits prose into text and markdown tables. Tables become real table cards.
    // Returns the new value of "first" (false once something has been added).
    private boolean addProse(LinearLayout col, String text, String fullBody,
                             boolean latest, boolean first) {
        StringBuilder prose = new StringBuilder();
        ArrayList<String> table = new ArrayList<>();
        boolean added = false;

        for (String line : text.split("\n", -1)) {
            String t = line.trim();
            boolean isTableLine = t.startsWith("|") && t.indexOf('|', 1) > 0;
            if (isTableLine) {
                if (prose.toString().trim().length() > 0) {
                    col.addView(proseView(prose.toString().trim(), fullBody, latest,
                            first && !added), lp(-1, -2));
                    added = true;
                    prose.setLength(0);
                }
                table.add(t);
            } else {
                if (!table.isEmpty()) {
                    col.addView(tableCard(table), margins(-1, -2, 0, 8, 0, 4));
                    added = true;
                    table.clear();
                }
                prose.append(line).append('\n');
            }
        }
        if (!table.isEmpty()) {
            col.addView(tableCard(table), margins(-1, -2, 0, 8, 0, 4));
            added = true;
        }
        if (prose.toString().trim().length() > 0) {
            col.addView(proseView(prose.toString().trim(), fullBody, latest, first && !added),
                    lp(-1, -2));
            added = true;
        }
        return first && !added;
    }

    private View tableCard(ArrayList<String> lines) {
        ArrayList<String[]> rows = new ArrayList<>();
        int cols = 0;
        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("|")) t = t.substring(1);
            if (t.endsWith("|")) t = t.substring(0, t.length() - 1);
            String[] cells = t.split("\\|", -1);
            boolean separator = true;
            for (String c : cells) {
                if (!c.trim().matches(":?-{2,}:?")) {
                    separator = false;
                    break;
                }
            }
            if (separator) continue;
            for (int i = 0; i < cells.length; i++) cells[i] = cells[i].trim();
            rows.add(cells);
            cols = Math.max(cols, cells.length);
        }
        if (rows.isEmpty()) return new View(this);

        // One width per column so the columns line up.
        TextView probe = text("", 12, primaryText);
        int[] widths = new int[cols];
        for (String[] cells : rows) {
            for (int i = 0; i < cells.length; i++) {
                float w = probe.getPaint().measureText(styleProse(cells[i]).toString()) * 1.1f;
                widths[i] = Math.max(widths[i], (int) w + dp(22));
            }
        }
        for (int i = 0; i < cols; i++) {
            widths[i] = Math.max(dp(64), Math.min(dp(200), widths[i]));
        }

        LinearLayout table = vertical();
        for (int r = 0; r < rows.size(); r++) {
            LinearLayout row = horizontal();
            row.setGravity(Gravity.TOP);
            String[] cells = rows.get(r);
            for (int i = 0; i < cols; i++) {
                TextView tv = text("", 12, primaryText);
                tv.setText(i < cells.length ? styleProse(cells[i]) : "");
                if (r == 0) {
                    tv.setTypeface(edithFont(Typeface.BOLD));
                    tv.setTextColor(accent);
                    tv.setBackgroundColor(surface2);
                }
                tv.setPadding(dp(10), dp(8), dp(10), dp(8));
                row.addView(tv, new LinearLayout.LayoutParams(widths[i], -2));
            }
            table.addView(row, lp(-2, -2));
            if (r < rows.size() - 1) {
                View line = new View(this);
                line.setBackgroundColor(border);
                table.addView(line, lp(-1, dp(1)));
            }
        }

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setBackground(box(surface, border, 14));
        scroll.addView(table);
        return scroll;
    }

    private TextView proseView(String s, String fullBody, boolean latest, boolean first) {
        TextView tv = text("", 15, primaryText);
        tv.setText(styleProse(s));
        tv.setLineSpacing(dp(3), 1.0f);
        tv.setPadding(0, dp(first ? 6 : 8), 0, 0);
        tv.setTextIsSelectable(true);
        tv.setOnLongClickListener(v -> {
            showMessageMenu(fullBody, latest);
            return true;
        });
        return tv;
    }

    // Light markdown: **bold**, *italic*, `inline code`, <sub>/<sup>, headings, bullets, quotes.
    // Rules, line breaks and stray HTML tags are cleaned up so nothing shows up as raw markup.
    private CharSequence styleProse(String raw) {
        Matcher hm = Pattern.compile("(?m)^#{1,6}\\s+(.+)$").matcher(raw);
        StringBuffer sb = new StringBuffer();
        while (hm.find()) {
            hm.appendReplacement(sb, Matcher.quoteReplacement(
                    "**" + hm.group(1).replace("**", "").trim() + "**"));
        }
        hm.appendTail(sb);

        String s = sb.toString()
                .replaceAll("(?m)^\\s*([-*_])\\1{2,}\\s*$", "")
                .replaceAll("(?m)^[ \\t]*>[ \\t]?", "▎ ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</?(?:p|div|span|b|i|u|strong|em|ul|ol|li|table|thead|tbody|tr|td|th"
                        + "|h[1-6]|hr)\\b[^>]*>", "")
                .replaceAll("(?m)^(\\s*)[-*]\\s+", "$1•  ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();

        SpannableStringBuilder out = new SpannableStringBuilder();
        Matcher m = INLINE.matcher(s);
        int pos = 0;
        int flag = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE;
        while (m.find()) {
            out.append(s, pos, m.start());
            int start = out.length();
            if (m.group(1) != null) {
                out.append(m.group(1));
                out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), flag);
            } else if (m.group(2) != null) {
                out.append(m.group(2));
                out.setSpan(new android.text.style.TypefaceSpan("monospace"), start, out.length(), flag);
                out.setSpan(new BackgroundColorSpan(surface2), start, out.length(), flag);
                out.setSpan(new ForegroundColorSpan(accent), start, out.length(), flag);
            } else if (m.group(3) != null) {
                out.append(m.group(3));
                out.setSpan(new SubscriptSpan(), start, out.length(), flag);
                out.setSpan(new RelativeSizeSpan(0.75f), start, out.length(), flag);
            } else if (m.group(4) != null) {
                out.append(m.group(4));
                out.setSpan(new SuperscriptSpan(), start, out.length(), flag);
                out.setSpan(new RelativeSizeSpan(0.75f), start, out.length(), flag);
            } else {
                out.append(m.group(5));
                out.setSpan(new StyleSpan(Typeface.ITALIC), start, out.length(), flag);
            }
            pos = m.end();
        }
        out.append(s, pos, s.length());
        return out;
    }

    private LinearLayout codeCard(String langRaw, final String code) {
        final String lang = langRaw == null ? "" : langRaw.toLowerCase(Locale.ROOT);
        final int lines = code.split("\n", -1).length;

        LinearLayout card = vertical();
        card.setBackground(box(Color.rgb(8, 11, 13), border, 14));

        LinearLayout head = horizontal();
        head.setPadding(dp(12), dp(4), dp(4), dp(4));
        TextView label = text(lang.isEmpty() ? "CODE" : lang.toUpperCase(Locale.ROOT), 10, accent);
        label.setTypeface(edithFont(Typeface.BOLD));
        label.setLetterSpacing(0.1f);
        head.addView(label, lp(-2, -2));
        head.addView(text("   " + lines + (lines == 1 ? " line" : " lines"), 10, secondaryText),
                lp(-2, -2));
        head.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        head.addView(textAction("Copy", secondaryText, v -> copyText(code)), lp(-2, -2));
        head.addView(textAction("Share", secondaryText, v -> shareText(code)), lp(-2, -2));
        card.addView(head, lp(-1, -2));

        View divider = new View(this);
        divider.setBackgroundColor(border);
        card.addView(divider, lp(-1, dp(1)));

        StringBuilder nums = new StringBuilder();
        for (int i = 1; i <= lines; i++) {
            if (i > 1) nums.append('\n');
            nums.append(i);
        }
        TextView gutter = text(nums.toString(), 12, Color.rgb(84, 100, 108));
        gutter.setTypeface(Typeface.MONOSPACE);
        gutter.setGravity(Gravity.END);
        gutter.setLineSpacing(dp(2), 1.0f);
        gutter.setPadding(0, 0, dp(12), 0);

        TextView codeView = text("", 12, primaryText);
        codeView.setTypeface(Typeface.MONOSPACE);
        codeView.setLineSpacing(dp(2), 1.0f);
        codeView.setHorizontallyScrolling(true);
        codeView.setTextIsSelectable(true);
        codeView.setText(highlight(code, lang));

        LinearLayout row = horizontal();
        row.setGravity(Gravity.TOP);
        row.setPadding(dp(12), dp(10), dp(14), dp(10));
        row.addView(gutter, lp(-2, -2));
        row.addView(codeView, lp(-2, -2));

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(row);
        card.addView(scroll, lp(-1, -2));
        return card;
    }

    // Small regex highlighter: comments, strings, numbers, keywords, types and calls.
    private CharSequence highlight(String code, String lang) {
        SpannableStringBuilder out = new SpannableStringBuilder(code);
        if (code.length() > 20000) return out;

        boolean hash = lang.equals("python") || lang.equals("py") || lang.equals("bash")
                || lang.equals("sh") || lang.equals("shell") || lang.equals("yaml")
                || lang.equals("yml") || lang.equals("ruby") || lang.equals("rb")
                || lang.equals("toml");
        boolean dash = lang.equals("sql");
        String comment = hash ? "#[^\\n]*" : dash ? "--[^\\n]*" : "//[^\\n]*|/\\*[\\s\\S]*?\\*/";

        Pattern p = Pattern.compile("(" + comment + ")"
                + "|(\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*')"
                + "|(\\b\\d+(?:\\.\\d+)?\\b)"
                + "|([A-Za-z_][A-Za-z0-9_]*)");

        int kwColor = Color.rgb(199, 146, 234);
        int strColor = Color.rgb(152, 195, 121);
        Matcher m = p.matcher(code);
        while (m.find()) {
            int color = 0;
            boolean italic = false;
            if (m.group(1) != null) {
                color = secondaryText;
                italic = true;
            } else if (m.group(2) != null) {
                color = strColor;
            } else if (m.group(3) != null) {
                color = thinkingAmber;
            } else {
                String w = m.group(4);
                if (CODE_KEYWORDS.contains(w)) color = kwColor;
                else if (Character.isUpperCase(w.charAt(0))) color = speakingBlue;
                else if (m.end() < code.length() && code.charAt(m.end()) == '(') color = accent;
            }
            if (color != 0) {
                out.setSpan(new ForegroundColorSpan(color), m.start(), m.end(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (italic) {
                out.setSpan(new StyleSpan(Typeface.ITALIC), m.start(), m.end(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return out;
    }

    private void shareText(String text) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(send, "Share"));
        } catch (Exception error) {
            toast("Nothing to share with.");
        }
    }

    // ============================================================
    // Android action cards
    // ============================================================

    private void addActionCards(LinearLayout col, final JSONObject action) {
        ArrayList<JSONObject> steps = new ArrayList<>();
        boolean multi = "multi".equals(action.optString("action"));
        if (multi) {
            JSONArray arr = action.optJSONArray("steps");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject s = arr.optJSONObject(i);
                    if (s != null) steps.add(s);
                }
            }
        } else {
            steps.add(action);
        }

        LinearLayout header = horizontal();
        header.setPadding(0, dp(6), 0, dp(6));
        TextView chip = text(multi ? "●  " + steps.size() + " STEPS" : "●  PHONE ACTION", 10, readyGreen);
        chip.setTypeface(edithFont(Typeface.BOLD));
        chip.setLetterSpacing(0.1f);
        header.addView(chip, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(textAction(multi ? "Run all again" : "Run again", accent,
                v -> startActions(action)), lp(-2, -2));
        col.addView(header, lp(-1, -2));

        for (int i = 0; i < steps.size(); i++) {
            col.addView(actionCard(steps.get(i), multi ? i + 1 : 0), margins(-1, -2, 0, 0, 0, 6));
        }
    }

    private LinearLayout actionCard(final JSONObject a, int number) {
        String type = a.optString("action");

        LinearLayout c = horizontal();
        c.setGravity(Gravity.TOP);
        c.setPadding(dp(12), dp(12), dp(6), dp(12));
        c.setBackground(ripple(box(surface, border, 16)));

        TextView icon = text(actionIcon(type), 18, accent);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(box(accentSoft, 0, 12));
        c.addView(icon, lp(dp(44), dp(44)));

        LinearLayout info = vertical();
        info.setPadding(dp(12), 0, dp(4), 0);
        info.addView(heading((number > 0 ? number + ".  " : "") + actionTitle(type), 14));
        String detail = actionDetail(a);
        if (!detail.isEmpty()) {
            TextView d = text(detail, 12, secondaryText);
            d.setLineSpacing(dp(2), 1.0f);
            info.addView(d, margins(-1, -2, 0, 4, 0, 0));
        }
        c.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

        c.addView(textAction("▶", accent, v -> startActions(a)), lp(dp(40), dp(40)));
        return c;
    }

    private String actionIcon(String type) {
        switch (type) {
            case "open_app": return "📲";
            case "whatsapp": return "💬";
            case "open_url": return "🌐";
            case "web_search": return "🔎";
            case "navigate": return "🧭";
            case "set_timer": return "⏱";
            case "set_alarm": return "⏰";
            case "calendar_event": return "📅";
            case "create_contact": return "👤";
            case "call": return "📞";
            case "sms": return "✉️";
            case "email": return "📧";
            case "play_music": return "🎵";
            case "camera": return "📷";
            case "settings": return "⚙️";
            case "share": return "📤";
            default: return "❓";
        }
    }

    private String actionTitle(String type) {
        switch (type) {
            case "open_app": return "Open app";
            case "whatsapp": return "WhatsApp message";
            case "open_url": return "Open website";
            case "web_search": return "Web search";
            case "navigate": return "Navigate";
            case "set_timer": return "Timer";
            case "set_alarm": return "Alarm";
            case "calendar_event": return "Calendar event";
            case "create_contact": return "New contact";
            case "call": return "Call";
            case "sms": return "Text message";
            case "email": return "Email";
            case "play_music": return "Play music";
            case "camera": return "Camera";
            case "settings": return "Settings";
            case "share": return "Share";
            default: return "Unknown action";
        }
    }

    private String actionDetail(JSONObject a) {
        switch (a.optString("action")) {
            case "open_app":
                return a.optString("app");
            case "whatsapp": {
                String msg = a.optString("message").trim();
                return "To " + a.optString("contact")
                        + (msg.isEmpty() ? "" : "\n“" + truncate(msg, 160) + "”");
            }
            case "open_url":
                return a.optString("url");
            case "web_search":
                return a.optString("query");
            case "navigate":
                return a.optString("destination");
            case "set_timer": {
                int mins = a.optInt("minutes", 1);
                String label = a.optString("label");
                return mins + (mins == 1 ? " minute" : " minutes")
                        + (label.isEmpty() ? "" : "  ·  " + label);
            }
            case "set_alarm": {
                String label = a.optString("label");
                return String.format(Locale.US, "%02d:%02d", a.optInt("hour", 7), a.optInt("minute", 0))
                        + (label.isEmpty() ? "" : "  ·  " + label);
            }
            case "calendar_event": {
                StringBuilder sb = new StringBuilder(a.optString("title"));
                String start = a.optString("start").replace("T", "  ");
                String end = a.optString("end");
                if (!start.isEmpty()) sb.append('\n').append(start);
                if (!end.isEmpty()) sb.append("  →  ").append(end.substring(end.indexOf('T') + 1));
                String where = a.optString("location");
                if (!where.isEmpty()) sb.append('\n').append(where);
                return sb.toString();
            }
            case "create_contact": {
                StringBuilder sb = new StringBuilder(a.optString("name"));
                if (!a.optString("number").isEmpty()) sb.append('\n').append(a.optString("number"));
                if (!a.optString("email").isEmpty()) sb.append('\n').append(a.optString("email"));
                return sb.toString();
            }
            case "call":
                return a.optString("number");
            case "sms":
                return "To " + a.optString("number") + "\n“" + truncate(a.optString("text"), 160) + "”";
            case "email":
                return "To " + a.optString("to") + "\nSubject: " + a.optString("subject");
            case "play_music":
                return a.optString("query");
            case "camera":
                return "Open the camera";
            case "settings":
                return a.optString("panel", "general") + " settings";
            case "share":
                return truncate(a.optString("text"), 160);
            default:
                return "";
        }
    }

    // ============================================================
    // Starter cards for Android features and Code modes
    // ============================================================

    private void addCapabilityCards(LinearLayout parent) {
        boolean android = "ANDROID".equals(focusMode);
        String[][] cards = android ? ANDROID_CARDS : CODE_CARDS;
        parent.addView(sectionLabel(android ? "WHAT I CAN DO ON YOUR PHONE" : "WHAT I CAN DO WITH CODE"),
                margins(-1, -2, 0, 0, 0, 10));
        for (int i = 0; i < cards.length; i += 2) {
            LinearLayout row = horizontal();
            row.addView(capabilityCard(cards[i]), tileParams(0, 4));
            if (i + 1 < cards.length) {
                row.addView(capabilityCard(cards[i + 1]), tileParams(4, 0));
            } else {
                row.addView(new View(this), tileParams(4, 0));
            }
            parent.addView(row, margins(-1, -2, 0, 0, 0, 8));
        }
        if (!android) {
            parent.addView(text("Tip: tap 📎 to attach a code file and ask about it.", 11, secondaryText),
                    margins(-1, -2, 0, 2, 0, 0));
        }
    }

    private LinearLayout capabilityCard(final String[] c) {
        LinearLayout t = vertical();
        t.setPadding(dp(12), dp(12), dp(12), dp(12));
        t.setBackground(ripple(box(surface, border, 16)));

        TextView g = text(c[0], 16, accent);
        g.setGravity(Gravity.CENTER);
        g.setPadding(dp(8), dp(4), dp(8), dp(4));
        g.setBackground(box(accentSoft, 0, 10));
        t.addView(g, lp(-2, -2));

        t.addView(heading(c[1], 13), margins(-1, -2, 0, 10, 0, 0));
        t.addView(text(c[2], 10, secondaryText), margins(-1, -2, 0, 2, 0, 0));
        t.setOnClickListener(v -> fillInput(c[3]));
        return t;
    }

    private void fillInput(String template) {
        if (messageInput == null) return;
        messageInput.setText(template);
        messageInput.setSelection(template.length());
        messageInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(messageInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private void regenerate() {
        JSONObject conv = activeConversation();
        if (conv == null || busyConversationId != null) return;

        JSONArray msgs = messagesOf(conv);
        JSONObject last = msgs.optJSONObject(msgs.length() - 1);
        if (last != null && "assistant".equals(last.optString("role"))) {
            String planId = last.optString("plan");
            if (!planId.isEmpty()) {
                removeById(tasks, planId);
                saveTasks();
            }
            msgs.remove(msgs.length() - 1);
            saveConversations();
        }

        String lastUser = null;
        for (int i = msgs.length() - 1; i >= 0; i--) {
            JSONObject m = msgs.optJSONObject(i);
            if (m != null && "user".equals(m.optString("role"))) {
                lastUser = m.optString("text");
                break;
            }
        }

        if (lastUser == null || lastUser.isEmpty()) {
            toast("Nothing to regenerate yet.");
            return;
        }
        sendPrompt(lastUser, false);
    }

    private void copyText(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            toast("Clipboard unavailable");
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("EDITH", text == null ? "" : text));
        toast("Copied");
    }

    private void sendMessage() {
        if (messageInput == null) return;
        if (busyConversationId != null) {
            toast("Please wait for the current response.");
            return;
        }
        String prompt = messageInput.getText().toString().trim();
        if (prompt.isEmpty() && pendingAttachName != null) {
            prompt = "Explain this and summarize the key points.";
        }
        if (prompt.isEmpty()) return;

        messageInput.setText("");
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(messageInput.getWindowToken(), 0);

        stopVoice();
        replyBySpeech = handsFree || isSpeakAlways();
        sendPrompt(prompt, true);
    }

    private void sendPrompt(String rawPrompt, boolean addUserMessage) {
        if (busyConversationId != null) {
            toast("Please wait for the current response.");
            return;
        }
        String prompt = rawPrompt == null ? "" : rawPrompt.trim();
        if (prompt.isEmpty()) return;

        JSONObject conv = activeConversation();
        if (conv == null) {
            conv = newConversation(pendingProjectId);
            conversations.add(0, conv);
            activeConversationId = conv.optString("id");
            pendingProjectId = null;
        }
        final String convId = conv.optString("id");

        if (addUserMessage) {
            if (messagesOf(conv).length() == 0 && !conv.optBoolean("titled", false)) {
                put(conv, "title", makeTitle(prompt));
            }
            appendMessage(conv, "user", prompt, "");
            attachPendingToLastMessage(conv);

            // "remember ..." / "forget ..." / "what do you remember" are handled on the phone.
            if (handleMemoryCommand(conv, prompt)) return;
            autoLearn(prompt);

            // Clear phone commands (WhatsApp, timer, alarm, call, open app) run without the model.
            if (actionTurnFor(prompt)) {
                JSONObject quick = quickAction(prompt);
                if (quick != null) {
                    finishReply(convId, quick.toString(), "PHONE", false, true);
                    return;
                }
            }

            // "generate an image of ..." runs the image generator directly.
            String imageWanted = imagePromptOf(prompt);
            if (imageWanted != null) {
                deliverLocal(conv, "Generating an image of \"" + imageWanted
                        + "\"… it will be saved to Pictures/EDITH.", "NOTE");
                generateImage(imageWanted, convId);
                return;
            }

            // "make a pdf of this chat / save your last answer as a pdf" works from the chat itself.
            int chatPdfMode = pdfFromChatMode(prompt);
            if (chatPdfMode != 0) {
                startPdfFromChat(conv, convId, chatPdfMode);
                return;
            }

            // A bare "make a pdf" asks what the PDF should be about.
            if (isBarePdfRequest(prompt)) {
                deliverLocal(conv, "What should the PDF be about? Enter the topic and I'll write it.", "NOTE");
                showCustomPdfDialog();
                return;
            }

            // "create a pdf about ..." asks the AI to write it, then saves it as a PDF.
            String pdfWanted = pdfTopicOf(prompt);
            if (pdfWanted != null && isReady()) {
                startTopicPdf(convId, pdfWanted);
                return;
            }
        }

        if (!isReady()) {
            deliverLocal(conv,
                    "Load a local model in Models, or add an Ollama key and choose a cloud mode.",
                    "NOTE");
            return;
        }

        final boolean hasImages = !lastImages.isEmpty();
        if (hasImages && ("LOCAL".equals(aiMode) || getOllamaKey().isEmpty())) {
            deliverLocal(conv,
                    "Reading screenshots and PDFs needs the cloud. Add an Ollama key in Models "
                            + "and switch to ONLINE or AUTO.",
                    "NOTE");
            return;
        }

        final boolean planTurn = "PLAN".equals(focusMode)
                || ("GENERAL".equals(focusMode) && looksLikeTask(prompt));
        final boolean actionTurn = !planTurn && actionTurnFor(prompt);
        final String model = hasImages ? VISION_MODEL : OLLAMA_MODEL;
        final String system = buildSystemPrompt(conv, prompt, planTurn, actionTurn);
        final JSONArray msgs = ollamaMessages(conv);
        final String local = localPrompt(system, msgs);

        busyConversationId = convId;
        setPresence("THINKING");
        refreshChatView();

        executor.execute(() -> {
            String text;
            String provider;
            try {
                boolean cloud = "ONLINE".equals(aiMode)
                        || ("AUTO".equals(aiMode) && !getOllamaKey().isEmpty());
                if (cloud) {
                    try {
                        text = requestOllama(system, msgs, model);
                        provider = "CLOUD";
                    } catch (Exception onlineError) {
                        if (!"AUTO".equals(aiMode) || !modelReady) throw onlineError;
                        android.util.Log.w("EDITH", "Online failed; using local model", onlineError);
                        text = runLocal(local);
                        provider = "FALLBACK";
                    }
                } else {
                    text = runLocal(local);
                    provider = "LOCAL";
                }
            } catch (Throwable error) {
                android.util.Log.e("EDITH", "Generation failed", error);
                text = "Request failed: " + safeError(error);
                provider = "ERROR";
            }

            final String reply = text;
            final String tag = provider;
            runOnUiThread(() -> finishReply(convId, reply, tag, planTurn, actionTurn));
        });
    }

    // Shows a reply that EDITH produced on the phone (no model call) and keeps the voice loop going.
    private void deliverLocal(JSONObject conv, String text, String provider) {
        appendMessage(conv, "assistant", text, provider);
        boolean visible = conv.optString("id").equals(activeConversationId) && "CHAT".equals(screen);
        if (visible) refreshChatView();
        if (!(replyBySpeech && visible && speak(text))) {
            setPresence("IDLE");
            if (handsFree) listenSoon(400);
        }
    }

    private void finishReply(String convId, String text, String provider,
                             boolean planTurn, boolean actionTurn) {
        busyConversationId = null;

        // Phone actions run in Android features mode, or when a General chat clearly asks for one.
        JSONObject action = (actionTurn && !planTurn) ? parseAction(text) : null;
        String shown = action != null ? describeAction(action) : text;
        String spoken = null;
        String planId = null;

        if (planTurn && !"ERROR".equals(provider)) {
            JSONObject conv0 = findConversation(convId);
            JSONObject task = createPlanFromReply(text,
                    conv0 == null ? "My plan" : conv0.optString("title"));
            if (task != null) {
                planId = task.optString("id");
                shown = planAsText(task);
                JSONArray steps = stepsOf(task);
                JSONObject first = steps.optJSONObject(0);
                spoken = "I made a plan: " + task.optString("title") + ". It has "
                        + steps.length() + " steps. First, "
                        + (first == null ? "" : first.optString("text")) + ".";
            }
        }

        JSONObject conv = findConversation(convId);
        if (conv != null) {
            appendMessageNoSave(conv, "assistant", shown, provider);
            JSONArray msgs = messagesOf(conv);
            JSONObject lastMsg = msgs.optJSONObject(msgs.length() - 1);
            if (lastMsg != null) {
                if (planId != null) put(lastMsg, "plan", planId);
                if (action != null) put(lastMsg, "action", action.toString());
            }
            saveConversations();

            boolean firstExchange = msgs.length() == 2;
            if (firstExchange && "CLOUD".equals(provider) && !conv.optBoolean("titled", false)) {
                JSONObject first = msgs.optJSONObject(0);
                String question = first == null ? "" : first.optString("text");
                requestAiTitle(convId, question, shown);
            }
        }

        refreshBadge();
        boolean visible = convId.equals(activeConversationId) && "CHAT".equals(screen);
        if (visible) refreshChatView();

        boolean speaking = replyBySpeech && visible && speak(spoken != null ? spoken : shown);
        if (!speaking) {
            setPresence("IDLE");
            if (handsFree) listenSoon(400);
        }

        if (action != null) startActions(action);
    }

    private String runLocal(String prompt) {
        String out = clean(nativeGenerate(prompt));
        return out.isEmpty() ? "The local model returned an empty response." : out;
    }

    private String clean(String output) {
        if (output == null) return "";
        return output
                .replace("<|im_end|>", "")
                .replace("<|endoftext|>", "")
                .replace("<|im_start|>assistant", "")
                .replace("<|im_start|>user", "")
                .replace("<|im_start|>", "")
                .trim();
    }

    private String safeError(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Check your internet connection and AI settings.";
        }
        return truncate(message, 180);
    }

    // ============================================================
    // Chat naming
    // ============================================================

    private String makeTitle(String prompt) {
        String s = prompt.replaceAll("\\s+", " ").trim();
        String first = s.split("[.?!\\n]")[0].trim();
        if (first.isEmpty()) first = s;

        String[] fillers = {
                "please ", "can you ", "could you ", "would you ", "hey ", "hi ",
                "hello ", "help me ", "i want to ", "i need to ", "i'd like to ",
                "tell me ", "explain ", "what is ", "what's ", "how do i ",
                "how do you ", "how to ", "give me "
        };
        boolean changed = true;
        while (changed) {
            changed = false;
            String lower = first.toLowerCase(Locale.ROOT);
            for (String f : fillers) {
                if (lower.startsWith(f)) {
                    first = first.substring(f.length()).trim();
                    changed = true;
                    break;
                }
            }
        }

        String[] words = first.split(" ");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.length && i < 6; i++) {
            if (words[i].isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(words[i]);
        }
        String title = out.toString().replaceAll("[,:;\"'`]+$", "").trim();
        if (title.isEmpty()) return "New chat";
        title = Character.toUpperCase(title.charAt(0)) + title.substring(1);
        return truncate(title, 40);
    }

    private void requestAiTitle(String convId, String question, String answer) {
        String key = getOllamaKey();
        if (key.isEmpty()) return;

        executor.execute(() -> {
            try {
                JSONArray msgs = new JSONArray();
                JSONObject m = new JSONObject();
                put(m, "role", "user");
                put(m, "content",
                        "Write a short title of 2 to 5 words for this conversation. "
                                + "Reply with only the title, no quotes, no final punctuation.\n\n"
                                + "User: " + truncate(question, 500) + "\n"
                                + "EDITH: " + truncate(answer, 500));
                msgs.put(m);

                String raw = requestOllama("You write concise chat titles.", msgs);
                String title = raw.replace("\n", " ")
                        .replaceAll("^[\"'\\s]+|[\"'.\\s]+$", "")
                        .trim();
                if (title.isEmpty() || title.length() > 60) return;

                runOnUiThread(() -> applyAiTitle(convId, title));
            } catch (Exception error) {
                android.util.Log.w("EDITH", "Title generation skipped", error);
            }
        });
    }

    private void applyAiTitle(String convId, String title) {
        JSONObject conv = findConversation(convId);
        if (conv == null || conv.optBoolean("titled", false)) return;

        put(conv, "title", truncate(title, 40));
        saveConversations();

        if (convId.equals(activeConversationId) && chatTitleView != null) {
            chatTitleView.setText(conv.optString("title"));
        } else if ("HOME".equals(screen)) {
            refreshScreen();
        }
    }

    // ============================================================
    // Prompt building, memory recall
    // ============================================================

    private static final class Scored {
        final String text;
        final int score;

        Scored(String text, int score) {
            this.text = text;
            this.score = score;
        }
    }

    private Set<String> termsOf(String text) {
        Set<String> set = new HashSet<>();
        for (String word : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (word.length() < 4) continue;
            boolean stop = false;
            for (String s : STOP_WORDS) {
                if (s.equals(word)) {
                    stop = true;
                    break;
                }
            }
            if (!stop) set.add(word);
        }
        return set;
    }

    private int overlap(Set<String> wanted, String text) {
        Set<String> words = termsOf(text);
        int n = 0;
        for (String term : wanted) {
            if (words.contains(term)) n++;
        }
        return n;
    }

    private String buildRecallContext(String prompt, String currentId) {
        Set<String> wanted = termsOf(prompt);
        StringBuilder out = new StringBuilder();

        if (isMemoryEnabled()) {
            // Core memories (name, preferences) are always included.
            int coreCount = 0;
            Set<String> used = new HashSet<>();
            for (JSONObject m : memories) {
                if (!m.optBoolean("core", false)) continue;
                if (coreCount == 0) out.append("What you know about the user:\n");
                String t = m.optString("text");
                out.append("- ").append(t).append('\n');
                used.add(t);
                if (++coreCount >= 8) break;
            }
            if (coreCount > 0) out.append('\n');

            if (!wanted.isEmpty()) {
                ArrayList<Scored> hits = new ArrayList<>();
                for (JSONObject m : memories) {
                    String text = m.optString("text");
                    if (used.contains(text)) continue;
                    int score = overlap(wanted, text);
                    if (score > 0) hits.add(new Scored(text, score));
                }
                Collections.sort(hits, (a, b) -> b.score - a.score);
                if (!hits.isEmpty()) {
                    out.append("Approved memories relevant to this message:\n");
                    for (int i = 0; i < hits.size() && i < 8; i++) {
                        out.append("- ").append(hits.get(i).text).append('\n');
                    }
                    out.append('\n');
                }
            }
        }

        if (isRecallEnabled() && !wanted.isEmpty()) {
            ArrayList<Scored> snippets = new ArrayList<>();
            for (JSONObject conv : conversations) {
                if (currentId.equals(conv.optString("id"))) continue;
                if (!conv.optBoolean("recall", true)) continue;
                JSONArray msgs = conv.optJSONArray("messages");
                if (msgs == null) continue;
                for (int i = 0; i < msgs.length(); i++) {
                    JSONObject m = msgs.optJSONObject(i);
                    if (m == null) continue;
                    String body = m.optString("text");
                    int score = overlap(wanted, body);
                    if (score >= 2) {
                        snippets.add(new Scored(
                                conv.optString("title") + ": " + truncate(body, 240), score));
                    }
                }
            }
            Collections.sort(snippets, (a, b) -> b.score - a.score);
            if (!snippets.isEmpty()) {
                out.append("Relevant excerpts from earlier chats:\n");
                for (int i = 0; i < snippets.size() && i < 3; i++) {
                    out.append("- ").append(snippets.get(i).text).append('\n');
                }
                out.append('\n');
            }
        }
        return out.toString();
    }

    private String personaPrompt() {
        switch (prefs().getString(PERSONA_KEY, "FRIENDLY")) {
            case "PRO":
                return "Personality: polished, precise and efficient. No small talk.";
            case "WITTY":
                return "Personality: dry, quick wit with a light touch of humour, but useful first.";
            case "CALM":
                return "Personality: calm, patient and reassuring. Use short sentences.";
            default:
                return "Personality: warm, upbeat and encouraging. Use plain, conversational language.";
        }
    }

    // ----- Phone command detection and on-device parsing -----

    private static final Pattern[] WA_CONTACT_FIRST = {
            Pattern.compile("(?i)^(?:please\\s+)?(?:send\\s+)?(?:a\\s+)?(?:whatsapp\\s+)?"
                    + "(?:message|msg|text)\\s+(?:to\\s+)?(.+?)\\s+(?:on|via|in)\\s+whatsapp"
                    + "\\s*(?:saying|say|that says|:)?\\s*(.*)$"),
            Pattern.compile("(?i)^(?:please\\s+)?(?:send\\s+)?(?:a\\s+)?whatsapp(?:\\s+message)?"
                    + "\\s+to\\s+(.+?)\\s*(?:saying|say|that says|:)\\s*(.+)$"),
            Pattern.compile("(?i)^(?:please\\s+)?(?:open\\s+whatsapp\\s*(?:,|and)?\\s*)?"
                    + "(?:message|msg|text)\\s+(.+?)\\s*(?:saying|say|that says|:)\\s*(.+)$")
    };

    private static final Pattern WA_MESSAGE_FIRST = Pattern.compile(
            "(?i)^(?:please\\s+)?(?:open\\s+whatsapp\\s*(?:,|and)?\\s*)?(?:send|say|type)\\s+"
                    + "[\"“]?(.+?)[\"”]?\\s+to\\s+(.+?)(?:\\s+(?:on|via|in)\\s+whatsapp)?$");

    private boolean looksLikePhoneAction(String prompt) {
        String p = prompt.toLowerCase(Locale.ROOT).trim();
        if (p.contains("whatsapp")) return true;
        String[] starts = {
                "open ", "call ", "dial ", "play ", "navigate to", "directions to", "set a timer",
                "set an alarm", "set timer", "set alarm", "wake me", "add to my calendar",
                "send a text", "send an email", "send a message"
        };
        for (String s : starts) {
            if (p.startsWith(s)) return true;
        }
        return false;
    }

    private boolean actionTurnFor(String prompt) {
        if ("ANDROID".equals(focusMode)) return true;
        return "GENERAL".equals(focusMode) && !looksLikeTask(prompt) && looksLikePhoneAction(prompt);
    }

    private JSONObject whatsappAction(String contact, String message) {
        String c = contact == null ? "" : contact.trim().replaceAll("^[\"“']+|[\"”']+$", "");
        String m = message == null ? "" : message.trim().replaceAll("^[\"“]+|[\"”]+$", "");
        if (c.isEmpty()) return null;
        JSONObject o = new JSONObject();
        put(o, "action", "whatsapp");
        put(o, "contact", c);
        put(o, "message", m);
        return o;
    }

    // Understands the clearest phone commands without calling a model. Returns null otherwise.
    private JSONObject quickAction(String prompt) {
        String p = prompt.trim().replaceAll("[.!]+$", "");
        String lower = p.toLowerCase(Locale.ROOT);
        try {
            if (lower.contains("whatsapp")) {
                for (Pattern pat : WA_CONTACT_FIRST) {
                    Matcher m = pat.matcher(p);
                    if (m.matches()) return whatsappAction(m.group(1), m.group(2));
                }
                Matcher m = WA_MESSAGE_FIRST.matcher(p);
                if (m.matches()) return whatsappAction(m.group(2), m.group(1));
                return null;
            }

            if (lower.contains("timer")) {
                Matcher t = Pattern.compile("(\\d+)\\s*(hours?|hrs?|minutes?|mins?|seconds?|secs?)")
                        .matcher(lower);
                if (t.find()) {
                    int n = Integer.parseInt(t.group(1));
                    String unit = t.group(2);
                    int mins = unit.startsWith("h") ? n * 60
                            : unit.startsWith("s") ? Math.max(1, (n + 59) / 60) : n;
                    JSONObject o = new JSONObject();
                    put(o, "action", "set_timer");
                    put(o, "minutes", Math.max(1, mins));
                    return o;
                }
            }

            if (lower.contains("alarm") || lower.startsWith("wake me")) {
                int hour = -1;
                int minute = 0;
                Matcher ap = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)").matcher(lower);
                Matcher hm = Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(lower);
                if (ap.find()) {
                    hour = Integer.parseInt(ap.group(1)) % 12 + ("pm".equals(ap.group(3)) ? 12 : 0);
                    if (ap.group(2) != null) minute = Integer.parseInt(ap.group(2));
                } else if (hm.find()) {
                    hour = Integer.parseInt(hm.group(1));
                    minute = Integer.parseInt(hm.group(2));
                }
                if (hour >= 0 && hour < 24 && minute < 60) {
                    JSONObject o = new JSONObject();
                    put(o, "action", "set_alarm");
                    put(o, "hour", hour);
                    put(o, "minute", minute);
                    return o;
                }
            }

            Matcher call = Pattern.compile("(?i)^(?:please\\s+)?(?:call|dial)\\s+([+\\d][\\d\\s\\-()]{5,})$")
                    .matcher(p);
            if (call.matches()) {
                JSONObject o = new JSONObject();
                put(o, "action", "call");
                put(o, "number", call.group(1).replaceAll("[^+\\d]", ""));
                return o;
            }

            Matcher open = Pattern.compile("(?i)^(?:please\\s+)?open\\s+(?:the\\s+)?(.+?)(?:\\s+app)?$")
                    .matcher(p);
            if (open.matches() && !lower.contains("setting") && !lower.contains("camera")
                    && !lower.contains(".") && open.group(1).length() <= 30) {
                JSONObject o = new JSONObject();
                put(o, "action", "open_app");
                put(o, "app", open.group(1).trim());
                return o;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean looksLikeCode(String prompt) {
        String p = prompt.toLowerCase(Locale.ROOT);
        if (p.contains("```") || p.contains("c++") || p.contains("stack trace")
                || p.contains("relational algebra")) {
            return true;
        }
        return Pattern.compile("\\b(code|coding|function|python|java|javascript|kotlin|sql|regex|"
                + "bug|debug|compile|compiler|algorithm|database|api|json|html|css|script|program|"
                + "programming|exception|bash|git)\\b").matcher(p).find();
    }

    private boolean looksLikeTask(String prompt) {
        String p = prompt.toLowerCase(Locale.ROOT);
        String[] triggers = {
                "help me prepare", "prepare for", "make a plan", "make me a plan",
                "create a plan", "plan my", "checklist", "to-do list", "todo list",
                "to do list", "step by step plan"
        };
        for (String t : triggers) {
            if (p.contains(t)) return true;
        }
        return false;
    }

    private String buildSystemPrompt(JSONObject conv, String prompt,
                                     boolean planTurn, boolean actionTurn) {
        StringBuilder s = new StringBuilder(
                "You are EDITH, a helpful personal AI assistant. Be accurate and concise.\n");
        s.append(personaPrompt()).append('\n');
        String name = getUserName();
        if (!name.isEmpty()) s.append("The user's name is ").append(name).append(".\n");
        s.append("Current date and time: ")
                .append(new SimpleDateFormat("EEEE, d MMMM yyyy HH:mm", Locale.getDefault())
                        .format(new Date()))
                .append(".\n");
        s.append("If the user attaches a file or screenshot, use it directly: explain it, summarize it, "
                + "extract what they ask for, or help debug code in it.\n");

        JSONObject project = findProject(conv.optString("project"));
        if (project != null && !project.optString("instructions").trim().isEmpty()) {
            s.append("\nProject instructions (follow these):\n")
                    .append(project.optString("instructions").trim())
                    .append('\n');
        }

        String recall = buildRecallContext(prompt, conv.optString("id"));
        if (!recall.isEmpty()) s.append('\n').append(recall);

        if (planTurn) {
            s.append(PLAN_HELP).append('\n');
        } else if (actionTurn) {
            s.append(ACTION_HELP).append('\n');
        } else {
            s.append(FORMAT_HELP).append('\n');
            // Technical questions get code-style answers even if the Code mode isn't selected.
            if ("CODE".equals(focusMode) || looksLikeCode(prompt)) {
                s.append(CODE_HELP).append('\n');
            }
        }

        s.append("Use saved information only when relevant. "
                + "Treat the user's current message as the priority.");
        return s.toString();
    }

    private JSONArray ollamaMessages(JSONObject conv) {
        JSONArray out = new JSONArray();
        JSONArray msgs = messagesOf(conv);
        int start = Math.max(0, msgs.length() - 12);

        // Only the most recent attachment's contents are sent, to keep the prompt small.
        int lastAttach = -1;
        for (int i = msgs.length() - 1; i >= start; i--) {
            JSONObject m = msgs.optJSONObject(i);
            if (m != null && !m.optString("attach_name").isEmpty()) {
                lastAttach = i;
                break;
            }
        }

        for (int i = start; i < msgs.length(); i++) {
            JSONObject m = msgs.optJSONObject(i);
            if (m == null) continue;
            String body = m.optString("text");
            if (body.trim().isEmpty()) continue;
            boolean user = "user".equals(m.optString("role"));

            String contentText = truncate(body, 6000);
            if (user && i == lastAttach) {
                StringBuilder sb = new StringBuilder("[Attached: ")
                        .append(m.optString("attach_name")).append("]\n");
                String attached = m.optString("attach_text");
                if (!attached.isEmpty()) {
                    sb.append("```\n").append(truncate(attached, MAX_ATTACH_CHARS)).append("\n```\n");
                }
                sb.append('\n').append(contentText);
                contentText = sb.toString();
            }

            JSONObject item = new JSONObject();
            put(item, "role", user ? "user" : "assistant");
            put(item, "content", contentText);

            if (user && i == msgs.length() - 1 && !lastImages.isEmpty()) {
                JSONArray imgs = new JSONArray();
                for (String b64 : lastImages) imgs.put(b64);
                put(item, "images", imgs);
            }
            out.put(item);
        }
        return out;
    }

    private String localPrompt(String system, JSONArray msgs) {
        StringBuilder p = new StringBuilder(system).append("\n\nConversation:\n");
        for (int i = 0; i < msgs.length(); i++) {
            JSONObject m = msgs.optJSONObject(i);
            if (m == null) continue;
            p.append("user".equals(m.optString("role")) ? "User: " : "EDITH: ")
                    .append(m.optString("content"))
                    .append('\n');
        }
        p.append("EDITH:");
        return p.toString();
    }

    private String requestOllama(String system, JSONArray convMessages) throws Exception {
        return requestOllama(system, convMessages, OLLAMA_MODEL);
    }

    private String requestOllama(String system, JSONArray convMessages, String model) throws Exception {
        String key = getOllamaKey();
        if (key.isEmpty()) throw new IllegalStateException("Ollama API key is not configured.");

        JSONArray messages = new JSONArray();
        JSONObject sys = new JSONObject();
        put(sys, "role", "system");
        put(sys, "content", system);
        messages.put(sys);
        for (int i = 0; i < convMessages.length(); i++) {
            messages.put(convMessages.optJSONObject(i));
        }

        JSONObject payload = new JSONObject();
        put(payload, "model", model);
        put(payload, "messages", messages);
        put(payload, "stream", false);

        HttpURLConnection connection = (HttpURLConnection) new URL(OLLAMA_URL).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(120000);
            connection.setDoOutput(true);

            connection.setRequestProperty("Authorization", "Bearer " + key);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("User-Agent", "EDITH/1.0");

            byte[] bytes = payload.toString().getBytes("UTF-8");
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }

            int code = connection.getResponseCode();
            InputStream stream = (code >= 200 && code < 300)
                    ? connection.getInputStream() : connection.getErrorStream();
            String responseText = readAll(stream);

            if (code < 200 || code >= 300) {
                throw new IllegalStateException("Ollama HTTP " + code + ": "
                        + truncate(responseText.trim(), 400));
            }

            String answer = new JSONObject(responseText)
                    .getJSONObject("message")
                    .optString("content", "")
                    .trim();
            if (answer.isEmpty()) throw new IllegalStateException("Ollama returned an empty answer.");
            return answer;
        } finally {
            connection.disconnect();
        }
    }

    // One-off model call for helper tasks (e.g. breaking a plan step into smaller steps).
    private String askModel(String system, String prompt) throws Exception {
        JSONArray m = new JSONArray();
        JSONObject u = new JSONObject();
        put(u, "role", "user");
        put(u, "content", prompt);
        m.put(u);

        boolean cloud = !getOllamaKey().isEmpty() && !"LOCAL".equals(aiMode);
        if (cloud) return requestOllama(system, m);
        if (modelReady) return runLocal(system + "\n\nUser: " + prompt + "\nEDITH:");
        throw new IllegalStateException("No model is ready. Check Models.");
    }

    private String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        try (InputStream input = stream; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096];
            int n;
            while ((n = input.read(chunk)) != -1) buffer.write(chunk, 0, n);
            return buffer.toString("UTF-8");
        }
    }

    // ============================================================
    // Smarter memory: commands, learning, editing
    // ============================================================

    private boolean handleMemoryCommand(JSONObject conv, String prompt) {
        String p = prompt.trim();
        String lower = p.toLowerCase(Locale.ROOT);
        String reply = null;

        for (String prefix : REMEMBER_PREFIXES) {
            if (lower.startsWith(prefix)) {
                String fact = p.substring(prefix.length()).trim().replaceAll("[.!\\s]+$", "");
                if (fact.length() < 3) break;
                if (!isMemoryEnabled()) {
                    reply = "Memory is turned off. Turn it on in the Memory tab first.";
                } else if (saveMemory(fact, "You asked EDITH to remember", true)) {
                    reply = "Got it. I'll remember: " + fact;
                } else {
                    reply = "I couldn't save that. It's too long, or memory is full.";
                }
                break;
            }
        }

        if (reply == null) {
            for (String prefix : FORGET_PREFIXES) {
                if (lower.startsWith(prefix)) {
                    String what = p.substring(prefix.length()).trim().replaceAll("[.!\\s]+$", "");
                    reply = forgetMemories(what);
                    break;
                }
            }
        }

        if (reply == null && (lower.contains("what do you remember")
                || lower.contains("what do you know about me"))) {
            if (memories.isEmpty()) {
                reply = "I don't have any saved memories yet. Tell me to \"remember that ...\" "
                        + "or just state a preference.";
            } else {
                StringBuilder sb = new StringBuilder("Here's what I have saved:\n");
                for (int i = 0; i < memories.size() && i < 15; i++) {
                    sb.append("- ").append(memories.get(i).optString("text")).append('\n');
                }
                if (memories.size() > 15) {
                    sb.append("…and ").append(memories.size() - 15).append(" more in the Memory tab.\n");
                }
                sb.append("You can edit or delete any of them in the Memory tab.");
                reply = sb.toString();
            }
        }

        if (reply == null) return false;
        deliverLocal(conv, reply, "MEMORY");
        return true;
    }

    private String forgetMemories(String what) {
        String w = what.toLowerCase(Locale.ROOT);
        if (w.equals("everything") || w.equals("all") || w.equals("everything about me")) {
            return "To wipe everything, open the Memory tab and tap Clear all memories.";
        }
        Set<String> terms = termsOf(what);
        if (terms.isEmpty()) {
            return "Tell me what to forget, or delete it in the Memory tab.";
        }

        int best = 0;
        for (JSONObject m : memories) {
            best = Math.max(best, overlap(terms, m.optString("text")));
        }
        if (best == 0 || best < (terms.size() + 1) / 2) {
            return "I couldn't find a saved memory about that.";
        }

        ArrayList<String> removed = new ArrayList<>();
        for (int i = memories.size() - 1; i >= 0; i--) {
            String t = memories.get(i).optString("text");
            if (overlap(terms, t) == best) {
                removed.add(t);
                memories.remove(i);
            }
        }
        writeList(MEMORY_KEY, memories);

        StringBuilder sb = new StringBuilder("Forgotten:\n");
        for (String t : removed) sb.append("- ").append(truncate(t, 120)).append('\n');
        sb.append("If it was mentioned in older chats, delete or exclude those chats to remove it fully.");
        return sb.toString();
    }

    // Learns clearly stated personal facts and preferences ("my name is ...", "I prefer ...").
    private void autoLearn(String prompt) {
        if (!isMemoryEnabled() || !isAutoLearnOn()) return;
        String p = prompt.trim();
        if (p.length() < 8 || p.length() > 240 || p.endsWith("?")) return;

        String first = p.split("[.!?\\n]")[0].trim();
        String lower = first.toLowerCase(Locale.ROOT);

        String[][] rules = {
                {"my name is ", "The user's name is "},
                {"call me ", "The user likes to be called "},
                {"i prefer ", "The user prefers "},
                {"i like ", "The user likes "},
                {"i love ", "The user loves "},
                {"i don't like ", "The user doesn't like "},
                {"i hate ", "The user dislikes "},
                {"i live in ", "The user lives in "},
                {"i work as ", "The user works as "},
                {"i work at ", "The user works at "},
                {"my favorite ", "The user's favorite "},
                {"my favourite ", "The user's favourite "}
        };

        String fact = null;
        for (String[] rule : rules) {
            if (lower.startsWith(rule[0])) {
                String rest = first.substring(rule[0].length()).trim();
                if (rest.length() >= 2 && rest.length() <= 120) {
                    fact = rule[1] + rest;
                    if ((rule[0].equals("my name is ") || rule[0].equals("call me "))
                            && getUserName().isEmpty()) {
                        String n = rest.split("[ ,]")[0].trim();
                        if (!n.isEmpty()) prefs().edit().putString(NAME_KEY, n).apply();
                    }
                }
                break;
            }
        }
        if (fact == null) return;

        boolean isNew = true;
        for (JSONObject m : memories) {
            if (fact.equalsIgnoreCase(m.optString("text").trim())) {
                isNew = false;
                break;
            }
        }
        if (saveMemory(fact, "Learned from chat", true) && isNew) {
            toast("Remembered: " + truncate(fact, 60));
        }
    }

    private void editMemory(JSONObject m) {
        EditText input = styledInput("Memory");
        input.setMinLines(2);
        input.setText(m.optString("text"));
        final boolean core = m.optBoolean("core", false);

        LinearLayout form = vertical();
        form.setPadding(dp(4), dp(8), dp(4), 0);
        form.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("Edit memory")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setNeutralButton(core ? "Always use: ON" : "Always use: OFF", (d, w) -> {
                    put(m, "core", !core);
                    writeList(MEMORY_KEY, memories);
                    showMemory();
                })
                .setPositiveButton("Save", (d, w) -> {
                    String t = input.getText().toString().trim();
                    if (t.isEmpty() || t.length() > 500) {
                        toast("A memory must be 1 to 500 characters.");
                        return;
                    }
                    put(m, "text", t);
                    writeList(MEMORY_KEY, memories);
                    showMemory();
                })
                .show();
    }

    // ============================================================
    // Multi-step plans
    // ============================================================

    private JSONObject findTask(String id) {
        if (id == null || id.isEmpty()) return null;
        for (JSONObject t : tasks) {
            if (id.equals(t.optString("id"))) return t;
        }
        return null;
    }

    private JSONArray stepsOf(JSONObject task) {
        JSONArray steps = task.optJSONArray("steps");
        if (steps == null) {
            steps = new JSONArray();
            put(task, "steps", steps);
        }
        return steps;
    }

    private int stepsTotal(JSONObject task) {
        return stepsOf(task).length();
    }

    private int stepsDone(JSONObject task) {
        JSONArray steps = stepsOf(task);
        int n = 0;
        for (int i = 0; i < steps.length(); i++) {
            JSONObject s = steps.optJSONObject(i);
            if (s != null && s.optBoolean("done", false)) n++;
        }
        return n;
    }

    private String stripBullet(String line) {
        Matcher m = BULLET.matcher(line);
        if (!m.matches()) return null;
        String s = m.group(1).replace("**", "").trim();
        return s.isEmpty() ? null : truncate(s, 160);
    }

    private ArrayList<String> parseSteps(String text) {
        ArrayList<String> steps = new ArrayList<>();
        if (text == null) return steps;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            String step = stripBullet(line);
            if (step != null && steps.size() < 15) steps.add(step);
        }
        return steps;
    }

    private JSONObject createPlanFromReply(String text, String fallbackTitle) {
        ArrayList<String> steps = parseSteps(text);
        if (steps.size() < 2) return null;

        String title = null;
        for (String raw : text.split("\n")) {
            String cleaned = raw.replace("*", "").trim();
            if (cleaned.toLowerCase(Locale.ROOT).startsWith("title:")) {
                title = cleaned.substring(6).trim();
                break;
            }
        }
        if (title == null || title.isEmpty()) {
            title = (fallbackTitle == null || fallbackTitle.isEmpty() || "New chat".equals(fallbackTitle))
                    ? "My plan" : fallbackTitle;
        }

        JSONObject task = new JSONObject();
        put(task, "id", UUID.randomUUID().toString());
        put(task, "title", truncate(title, 60));
        put(task, "created", System.currentTimeMillis());
        JSONArray arr = new JSONArray();
        for (String s : steps) {
            JSONObject step = new JSONObject();
            put(step, "text", s);
            put(step, "done", false);
            arr.put(step);
        }
        put(task, "steps", arr);

        tasks.add(0, task);
        while (tasks.size() > MAX_TASKS) tasks.remove(tasks.size() - 1);
        saveTasks();
        return task;
    }

    private String planAsText(JSONObject task) {
        StringBuilder sb = new StringBuilder(task.optString("title")).append('\n');
        JSONArray steps = stepsOf(task);
        for (int i = 0; i < steps.length(); i++) {
            JSONObject s = steps.optJSONObject(i);
            if (s != null) sb.append("- ").append(s.optString("text")).append('\n');
        }
        return sb.toString().trim();
    }

    // Draws a checklist with a progress bar into holder. Re-run after every change.
    private void fillPlanCard(final LinearLayout holder, final JSONObject task, final boolean full) {
        holder.removeAllViews();
        final JSONArray steps = stepsOf(task);
        int total = steps.length();
        int done = stepsDone(task);
        boolean finished = total > 0 && done == total;

        LinearLayout head = horizontal();
        head.addView(heading(task.optString("title", "Plan"), 15),
                new LinearLayout.LayoutParams(0, -2, 1));
        TextView count = text(done + "/" + total, 11, finished ? readyGreen : accent);
        count.setPadding(dp(10), dp(4), dp(10), dp(4));
        count.setBackground(box(accentSoft, 0, 10));
        head.addView(count, lp(-2, -2));
        holder.addView(head, lp(-1, -2));

        LinearLayout bar = horizontal();
        bar.setBackground(box(surface2, 0, 4));
        View fill = new View(this);
        fill.setBackground(box(finished ? readyGreen : accent, 0, 4));
        bar.addView(fill, new LinearLayout.LayoutParams(0, dp(6), done));
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, dp(6), Math.max(0, total - done)));
        holder.addView(bar, margins(-1, dp(6), 0, 10, 0, 6));

        for (int i = 0; i < total; i++) {
            final int index = i;
            final JSONObject step = steps.optJSONObject(i);
            if (step == null) continue;
            boolean isDone = step.optBoolean("done", false);

            TextView row = text((isDone ? "☑  " : "☐  ") + step.optString("text"), 14,
                    isDone ? secondaryText : primaryText);
            row.setLineSpacing(dp(2), 1.0f);
            row.setPadding(0, dp(7), 0, dp(7));
            if (isDone) row.setPaintFlags(row.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            row.setOnClickListener(v -> {
                put(step, "done", !step.optBoolean("done", false));
                saveTasks();
                fillPlanCard(holder, task, full);
            });
            row.setOnLongClickListener(v -> {
                showStepMenu(holder, task, index, full);
                return true;
            });
            holder.addView(row, lp(-1, -2));
        }

        if (finished) {
            holder.addView(text("All done. Nice work.", 12, readyGreen),
                    margins(-1, -2, 0, 6, 0, 0));
        }

        LinearLayout foot = horizontal();
        foot.addView(textAction("+ Add step", accent, v -> addStepDialog(holder, task, full)),
                lp(-2, -2));
        if (full) {
            foot.addView(textAction("Reset", secondaryText, v -> {
                JSONArray all = stepsOf(task);
                for (int i = 0; i < all.length(); i++) {
                    JSONObject s = all.optJSONObject(i);
                    if (s != null) put(s, "done", false);
                }
                saveTasks();
                fillPlanCard(holder, task, full);
            }), lp(-2, -2));
            foot.addView(textAction("Delete plan", dangerRed,
                    v -> confirmDeletePlan(task.optString("id"))), lp(-2, -2));
        }
        holder.addView(foot, margins(-1, -2, -8, 4, 0, 0));
        holder.addView(text("Tap to tick off  ·  hold a step for more", 10, secondaryText));
    }

    private void showStepMenu(LinearLayout holder, JSONObject task, int index, boolean full) {
        new AlertDialog.Builder(this)
                .setItems(new String[]{"Break into smaller steps", "Edit step", "Delete step"},
                        (d, which) -> {
                            if (which == 0) breakDownStep(holder, task, index, full);
                            else if (which == 1) editStepDialog(holder, task, index, full);
                            else {
                                JSONArray steps = stepsOf(task);
                                JSONArray next = new JSONArray();
                                for (int i = 0; i < steps.length(); i++) {
                                    if (i != index) next.put(steps.opt(i));
                                }
                                put(task, "steps", next);
                                saveTasks();
                                fillPlanCard(holder, task, full);
                            }
                        })
                .show();
    }

    private void addStepDialog(LinearLayout holder, JSONObject task, boolean full) {
        EditText input = styledInput("New step");
        new AlertDialog.Builder(this)
                .setTitle("Add step")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", (d, w) -> {
                    String t = input.getText().toString().trim();
                    if (t.isEmpty()) return;
                    JSONObject step = new JSONObject();
                    put(step, "text", truncate(t, 160));
                    put(step, "done", false);
                    stepsOf(task).put(step);
                    saveTasks();
                    fillPlanCard(holder, task, full);
                })
                .show();
    }

    private void editStepDialog(LinearLayout holder, JSONObject task, int index, boolean full) {
        JSONObject step = stepsOf(task).optJSONObject(index);
        if (step == null) return;
        EditText input = styledInput("Step");
        input.setText(step.optString("text"));
        new AlertDialog.Builder(this)
                .setTitle("Edit step")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String t = input.getText().toString().trim();
                    if (t.isEmpty()) return;
                    put(step, "text", truncate(t, 160));
                    saveTasks();
                    fillPlanCard(holder, task, full);
                })
                .show();
    }

    private void breakDownStep(LinearLayout holder, JSONObject task, int index, boolean full) {
        JSONObject step = stepsOf(task).optJSONObject(index);
        if (step == null) return;
        final String stepText = step.optString("text");
        final String title = task.optString("title");
        toast("Breaking it down…");

        executor.execute(() -> {
            try {
                String out = askModel("You break tasks into smaller actions.",
                        "Break this step of the plan \"" + title + "\" into 2 to 4 smaller, concrete "
                                + "actions. Reply with ONLY lines starting with '- '.\nStep: " + stepText);
                ArrayList<String> subs = parseSteps(out);
                if (subs.size() < 2) {
                    toastOnUi("Couldn't break that step down.");
                    return;
                }
                runOnUiThread(() -> {
                    JSONArray steps = stepsOf(task);
                    JSONArray next = new JSONArray();
                    for (int i = 0; i < steps.length(); i++) {
                        if (i == index) {
                            for (String s : subs) {
                                JSONObject sub = new JSONObject();
                                put(sub, "text", s);
                                put(sub, "done", false);
                                next.put(sub);
                            }
                        } else {
                            next.put(steps.opt(i));
                        }
                    }
                    put(task, "steps", next);
                    saveTasks();
                    fillPlanCard(holder, task, full);
                });
            } catch (Throwable error) {
                toastOnUi(safeError(error));
            }
        });
    }

    private void confirmDeletePlan(String id) {
        new AlertDialog.Builder(this)
                .setTitle("Delete plan?")
                .setMessage("The plan and its progress will be removed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    removeById(tasks, id);
                    saveTasks();
                    refreshScreen();
                })
                .show();
    }

    private void showTasks() {
        screen = "TASKS";
        clearContent();
        LinearLayout page = addPage();
        page.addView(textAction("← Home", accent, v -> showHome()), lp(-2, -2));
        addTitle(page, "Tasks", "Plans EDITH made for you. Tick steps off as you go.");

        page.addView(button("+  New plan with EDITH", STYLE_PRIMARY, v -> {
            focusMode = "PLAN";
            prefs().edit().putString(FOCUS_KEY, focusMode).apply();
            newChat(null);
        }), margins(-1, -2, 0, 0, 0, 14));

        if (tasks.isEmpty()) {
            LinearLayout empty = card(16);
            empty.addView(text("No plans yet. Try \"Help me prepare for tomorrow\" in any chat.",
                    13, secondaryText));
            page.addView(empty, lp(-1, -2));
        }
        for (JSONObject t : tasks) {
            LinearLayout holder = card(16);
            fillPlanCard(holder, t, true);
            page.addView(holder, margins(-1, -2, 0, 0, 0, 10));
        }
    }

    // ============================================================
    // Persona
    // ============================================================

    private void showPersona() {
        screen = "PERSONA";
        clearContent();
        LinearLayout page = addPage();
        page.addView(textAction("← Home", accent, v -> showHome()), lp(-2, -2));
        addTitle(page, "Persona", "Shape how EDITH sounds, greets you and speaks.");

        page.addView(sectionLabel("YOUR NAME"), margins(-1, -2, 0, 0, 0, 8));
        final EditText nameIn = styledInput("What should EDITH call you?");
        nameIn.setSingleLine(true);
        nameIn.setText(getUserName());
        page.addView(nameIn, margins(-1, -2, 0, 0, 0, 16));

        page.addView(sectionLabel("CUSTOM GREETING"), margins(-1, -2, 0, 0, 0, 8));
        final EditText greetIn = styledInput("e.g. Welcome back, {name}");
        greetIn.setSingleLine(true);
        greetIn.setText(prefs().getString(GREETING_KEY, ""));
        page.addView(greetIn, margins(-1, -2, 0, 0, 0, 4));
        page.addView(text("Leave empty for the time-of-day greeting. {name} is replaced with your name.",
                11, secondaryText), margins(-1, -2, 4, 0, 4, 18));

        final Runnable commit = () -> prefs().edit()
                .putString(NAME_KEY, nameIn.getText().toString().trim())
                .putString(GREETING_KEY, greetIn.getText().toString().trim())
                .apply();

        page.addView(sectionLabel("PERSONALITY"), margins(-1, -2, 0, 0, 0, 8));
        String current = prefs().getString(PERSONA_KEY, "FRIENDLY");
        String[][] options = {
                {"FRIENDLY", "Friendly", "Warm and encouraging"},
                {"PRO", "Professional", "Precise and efficient"},
                {"WITTY", "Witty", "Dry humour, still useful"},
                {"CALM", "Calm", "Patient and reassuring"}
        };
        for (String[] option : options) {
            boolean on = option[0].equals(current);
            LinearLayout c = horizontal();
            c.setPadding(dp(16), dp(14), dp(16), dp(14));
            c.setBackground(ripple(on
                    ? gradientBox(Color.rgb(24, 74, 72), surface, 16)
                    : box(surface, border, 16)));
            LinearLayout labels = vertical();
            labels.addView(heading(option[1], 14));
            labels.addView(text(option[2], 11, secondaryText), margins(-1, -2, 0, 3, 0, 0));
            c.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            c.addView(text(on ? "●" : "○", 14, on ? accent : secondaryText), lp(-2, -2));
            c.setOnClickListener(v -> {
                commit.run();
                prefs().edit().putString(PERSONA_KEY, option[0]).apply();
                showPersona();
            });
            page.addView(c, margins(-1, -2, 0, 0, 0, 8));
        }

        page.addView(sectionLabel("VOICE"), margins(-1, -2, 0, 14, 0, 8));
        page.addView(settingRow("Speak every reply",
                        "Replies to voice and hands-free are always spoken",
                        isSpeakAlways(), v -> {
                            commit.run();
                            setFlag(SPEAK_KEY, !isSpeakAlways());
                            showPersona();
                        }),
                margins(-1, -2, 0, 0, 0, 16));

        LinearLayout actions = horizontal();
        actions.addView(button("Save", STYLE_PRIMARY, v -> {
            commit.run();
            toast("Persona saved");
            showHome();
        }), tileParams(0, 6));
        actions.addView(button("Hear greeting", STYLE_SECONDARY, v -> {
            commit.run();
            speak(homeGreeting());
        }), tileParams(6, 0));
        page.addView(actions, lp(-1, -2));
    }

    // ============================================================
    // Files, screenshots and shared content
    // ============================================================

    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, PICK_FILE);
        } catch (ActivityNotFoundException error) {
            toast("No file picker available on this phone.");
        }
    }

    private void clearPendingAttachment() {
        pendingAttachName = null;
        pendingAttachText = "";
        pendingAttachImages = new ArrayList<>();
    }

    private void attachPendingToLastMessage(JSONObject conv) {
        if (pendingAttachName == null) {
            lastImages = new ArrayList<>();
            return;
        }
        JSONArray msgs = messagesOf(conv);
        JSONObject last = msgs.optJSONObject(msgs.length() - 1);
        if (last != null) {
            put(last, "attach_name", pendingAttachName);
            if (!pendingAttachText.isEmpty()) put(last, "attach_text", pendingAttachText);
        }
        lastImages = new ArrayList<>(pendingAttachImages);
        clearPendingAttachment();
        saveConversations();
        updateAttachChip();
    }

    private void updateAttachChip() {
        if (attachRow == null) return;
        attachRow.removeAllViews();
        if (pendingAttachName == null) {
            attachRow.setVisibility(View.GONE);
            return;
        }
        attachRow.setVisibility(View.VISIBLE);
        TextView chip = text("📎  " + truncate(pendingAttachName, 34), 12, accent);
        chip.setPadding(dp(12), dp(7), dp(12), dp(7));
        chip.setBackground(box(accentSoft, 0, 12));
        attachRow.addView(chip, lp(-2, -2));
        attachRow.addView(textAction("✕", secondaryText, v -> {
            clearPendingAttachment();
            updateAttachChip();
        }), lp(-2, -2));
    }

    private void handleSharedIntent(Intent intent) {
        if (intent != null && intent.getStringExtra("edith_open_cabs") != null) {
            String rid = intent.getStringExtra("edith_open_cabs");
            intent.removeExtra("edith_open_cabs");
            if (EdithCabs.findRoute(this, rid) != null) showCabRoute(rid); else showCabs();
            return;
        }
        if (intent != null && intent.getBooleanExtra("edith_open_shop", false)) {
            intent.removeExtra("edith_open_shop");
            showShopping();
            return;
        }
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        Uri stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (stream == null && (sharedText == null || sharedText.trim().isEmpty())) return;

        // A product link shared from Amazon, Flipkart etc. goes straight to price tracking.
        if (stream == null) {
            String shopLink = EdithShopping.normalizeUrl(sharedText);
            if (shopLink != null && EdithShopping.isStoreUrl(shopLink)) {
                intent.setAction(null);
                showShopping();
                shopReadAndSave(null, "", shopLink, null);
                return;
            }
        }

        intent.setAction(null); // don't re-handle on rotation
        newChat(null);
        if (stream != null) {
            attachFromUri(stream);
        } else {
            pendingAttachName = "Shared text";
            pendingAttachText = truncate(sharedText, MAX_ATTACH_CHARS);
            pendingAttachImages = new ArrayList<>();
            updateAttachChip();
            toast("Shared text attached. Ask EDITH about it.");
        }
    }

    private void attachFromUri(final Uri uri) {
        String found = displayName(uri);
        final String name = found == null ? "file" : found;
        final String mime = getContentResolver().getType(uri);
        toast("Reading " + name + "…");

        executor.execute(() -> {
            try {
                String lower = name.toLowerCase(Locale.ROOT);
                ArrayList<String> images = new ArrayList<>();
                String fileText = "";

                boolean isImage = (mime != null && mime.startsWith("image/"))
                        || lower.matches(".*\\.(png|jpe?g|webp|gif|bmp)$");
                boolean isPdf = "application/pdf".equals(mime) || lower.endsWith(".pdf");

                if (isImage) {
                    images.add(encodeImage(uri));
                } else if (isPdf) {
                    String note = renderPdf(uri, images);
                    fileText = note;
                } else {
                    fileText = readTextFile(uri);
                }

                final String finalText = fileText;
                final ArrayList<String> finalImages = images;
                runOnUiThread(() -> {
                    pendingAttachName = name;
                    pendingAttachText = finalText;
                    pendingAttachImages = finalImages;
                    updateAttachChip();
                    toast("Attached " + truncate(name, 30));
                });
            } catch (Throwable error) {
                android.util.Log.e("EDITH", "Attach failed", error);
                toastOnUi("Could not read file: " + safeError(error));
            }
        });
    }

    private String encodeImage(Uri uri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        if (longest <= 0) throw new IllegalStateException("Couldn't read that image.");

        int sample = 1;
        while (longest / sample > 2400) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;

        Bitmap bmp;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            bmp = BitmapFactory.decodeStream(in, null, opts);
        }
        if (bmp == null) throw new IllegalStateException("Couldn't decode that image.");
        return bitmapToBase64(bmp, 1400);
    }

    private String bitmapToBase64(Bitmap bmp, int maxSide) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int longest = Math.max(w, h);
        Bitmap scaled = bmp;
        if (longest > maxSide) {
            float f = maxSide / (float) longest;
            scaled = Bitmap.createScaledBitmap(bmp,
                    Math.max(1, Math.round(w * f)), Math.max(1, Math.round(h * f)), true);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out);
        if (scaled != bmp) scaled.recycle();
        bmp.recycle();
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    // Renders the first pages of a PDF to images. Returns a short note about page count.
    private String renderPdf(Uri uri, ArrayList<String> images) throws Exception {
        try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r")) {
            if (pfd == null) throw new IllegalStateException("Could not open the PDF.");
            PdfRenderer renderer = new PdfRenderer(pfd);
            try {
                int total = renderer.getPageCount();
                int pages = Math.min(total, MAX_PDF_PAGES);
                for (int i = 0; i < pages; i++) {
                    PdfRenderer.Page page = renderer.openPage(i);
                    try {
                        int width = 1000;
                        int height = Math.max(1, width * page.getHeight() / Math.max(1, page.getWidth()));
                        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        bmp.eraseColor(Color.WHITE);
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        images.add(bitmapToBase64(bmp, 1200));
                    } finally {
                        page.close();
                    }
                }
                return total > pages
                        ? "(This PDF has " + total + " pages. The first " + pages + " are attached as images.)"
                        : "(This PDF has " + total + " page" + (total == 1 ? "" : "s")
                        + ", attached as images.)";
            } finally {
                renderer.close();
            }
        }
    }

    private String readTextFile(Uri uri) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("Could not open the file.");
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) != -1 && buf.size() < 200000) buf.write(chunk, 0, n);
        }
        String s = buf.toString("UTF-8");

        int sample = Math.min(s.length(), 4000);
        int bad = 0;
        for (int i = 0; i < sample; i++) {
            char c = s.charAt(i);
            if (c == 0 || c == '\uFFFD') bad++;
        }
        if (sample > 0 && bad * 20 > sample) {
            throw new IllegalStateException(
                    "This doesn't look like a text file. Try a screenshot, a PDF, or a text/code file.");
        }
        if (s.length() > MAX_ATTACH_CHARS) {
            s = s.substring(0, MAX_ATTACH_CHARS) + "\n…(truncated)";
        }
        return s;
    }

    // ============================================================
    // Phone actions (run directly, no confirmation prompt)
    // ============================================================

    private JSONObject parseAction(String reply) {
        if (reply == null) return null;
        String s = reply.trim();
        if (s.startsWith("```")) {
            s = s.replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
        }
        // Accept JSON even if the model wrapped it in text or code fences.
        int open = s.indexOf('{');
        int close = s.lastIndexOf('}');
        if (open < 0 || close <= open) return null;
        s = s.substring(open, close + 1);
        try {
            JSONObject o = new JSONObject(s);
            return o.has("action") ? o : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String describeAction(JSONObject a) {
        switch (a.optString("action")) {
            case "open_app":       return "Open the app \"" + a.optString("app") + "\"";
            case "whatsapp":       return a.optString("message").trim().isEmpty()
                    ? "Open WhatsApp chat with " + a.optString("contact")
                    : "WhatsApp " + a.optString("contact") + ": \""
                    + truncate(a.optString("message"), 120) + "\"";
            case "open_url":       return "Open " + a.optString("url");
            case "web_search":     return "Search the web for \"" + a.optString("query") + "\"";
            case "navigate":       return "Navigate to \"" + a.optString("destination") + "\"";
            case "set_timer":      return "Set a timer for " + a.optInt("minutes", 1) + " minute(s)";
            case "set_alarm":      return String.format(Locale.US, "Set an alarm for %02d:%02d",
                    a.optInt("hour", 7), a.optInt("minute", 0));
            case "calendar_event": return "Add calendar event \"" + a.optString("title")
                    + "\" at " + a.optString("start").replace("T", " ");
            case "create_contact": return "Create contact " + a.optString("name")
                    + " (" + a.optString("number") + ")";
            case "call":           return "Call " + a.optString("number");
            case "sms":            return "Text " + a.optString("number") + ": \""
                    + truncate(a.optString("text"), 120) + "\"";
            case "email":          return "Email " + a.optString("to") + " \""
                    + a.optString("subject") + "\"";
            case "play_music":     return "Play \"" + a.optString("query") + "\"";
            case "camera":         return "Open the camera";
            case "settings":       return "Open " + a.optString("panel", "general") + " settings";
            case "share":          return "Share this text: \""
                    + truncate(a.optString("text"), 120) + "\"";
            case "multi":          return "Run " + countSteps(a) + " steps in order";
            default:               return "Unknown action";
        }
    }

    private int countSteps(JSONObject a) {
        JSONArray steps = a.optJSONArray("steps");
        return steps == null ? 0 : steps.length();
    }

    private void startActions(JSONObject a) {
        pendingSteps.clear();
        awaitingReturn = false;
        if ("multi".equals(a.optString("action"))) {
            JSONArray steps = a.optJSONArray("steps");
            if (steps != null) {
                for (int i = 0; i < steps.length(); i++) {
                    JSONObject s = steps.optJSONObject(i);
                    if (s != null) pendingSteps.add(s);
                }
            }
        } else {
            pendingSteps.add(a);
        }
        confirmNextStep();
    }

    // Runs the queued steps in order. Waits when another app is in front
    // or when calendar permission is being requested.
    private void confirmNextStep() {
        while (!pendingSteps.isEmpty()) {
            if (pendingCalendarAction != null) return;
            JSONObject step = pendingSteps.remove(0);
            boolean launched = runAction(step);
            if (launched && !pendingSteps.isEmpty()) {
                awaitingReturn = true;
                return;
            }
        }
    }

    private boolean runAction(JSONObject a) {
        try {
            String type = a.optString("action");
            if ("calendar_event".equals(type)) {
                addCalendarEvent(a);
                return false;
            }
            if ("whatsapp".equals(type)) {
                return openWhatsApp(a.optString("contact").trim(), a.optString("message").trim());
            }
            if ("open_app".equals(type)) {
                return openApp(a.optString("app"));
            }
            Intent intent = buildIntent(a);
            if (intent == null) {
                toast("EDITH can't do that yet.");
                return false;
            }
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException error) {
            toast("No app on this phone can do that.");
        } catch (Exception error) {
            android.util.Log.e("EDITH", "Action failed", error);
            toast(safeError(error));
        }
        return false;
    }

    // Opens WhatsApp and, when a message is given, types and sends it through the
    // accessibility service. A phone number opens the chat directly.
    private boolean openWhatsApp(String contact, String message) {
        if (contact.isEmpty()) {
            toast("Tell EDITH who to message.");
            return false;
        }

        boolean needsControl = !message.isEmpty();
        if (needsControl && !EdithAccessibilityService.isEnabled()) {
            toast("Turn on EDITH phone control in Accessibility settings first.");
            openAccessibilitySettings();
            return false;
        }

        boolean isNumber = contact.replaceAll("[\\s\\-()+]", "").matches("\\d{7,15}");
        try {
            Intent intent;
            if (isNumber) {
                String url = "https://wa.me/" + contact.replaceAll("[^0-9]", "");
                if (!message.isEmpty()) url += "?text=" + Uri.encode(message);
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .setPackage(WHATSAPP_PACKAGE);
            } else {
                intent = getPackageManager().getLaunchIntentForPackage(WHATSAPP_PACKAGE);
                if (intent == null) {
                    toast("WhatsApp is not installed.");
                    return false;
                }
            }

            if (needsControl) {
                EdithAccessibilityService.begin(contact, message, isNumber);
            }
            startActivity(intent);
            return needsControl;
        } catch (ActivityNotFoundException error) {
            toast("WhatsApp is not installed.");
        }
        return false;
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception ignored) {
            toast("Open Settings > Accessibility to turn on EDITH.");
        }
    }

    private Intent buildIntent(JSONObject a) throws Exception {
        switch (a.optString("action")) {
            case "open_url": {
                String url = a.optString("url").trim();
                if (!url.startsWith("http")) url = "https://" + url;
                return new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            }
            case "web_search":
                return new Intent(Intent.ACTION_VIEW, Uri.parse(
                        "https://www.google.com/search?q=" + Uri.encode(a.optString("query"))));
            case "navigate":
                return new Intent(Intent.ACTION_VIEW, Uri.parse(
                        "geo:0,0?q=" + Uri.encode(a.optString("destination"))));
            case "set_timer": {
                Intent i = new Intent(AlarmClock.ACTION_SET_TIMER);
                i.putExtra(AlarmClock.EXTRA_LENGTH, a.optInt("minutes", 1) * 60);
                i.putExtra(AlarmClock.EXTRA_MESSAGE, a.optString("label", "EDITH timer"));
                i.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
                return i;
            }
            case "set_alarm": {
                Intent i = new Intent(AlarmClock.ACTION_SET_ALARM);
                i.putExtra(AlarmClock.EXTRA_HOUR, a.optInt("hour", 7));
                i.putExtra(AlarmClock.EXTRA_MINUTES, a.optInt("minute", 0));
                i.putExtra(AlarmClock.EXTRA_MESSAGE, a.optString("label", "EDITH alarm"));
                i.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
                return i;
            }
            case "create_contact": {
                Intent i = new Intent(ContactsContract.Intents.Insert.ACTION);
                i.setType(ContactsContract.RawContacts.CONTENT_TYPE);
                i.putExtra(ContactsContract.Intents.Insert.NAME, a.optString("name"));
                i.putExtra(ContactsContract.Intents.Insert.PHONE, a.optString("number"));
                i.putExtra(ContactsContract.Intents.Insert.EMAIL, a.optString("email"));
                return i;
            }
            case "call":
                return new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + a.optString("number")));
            case "sms": {
                Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + a.optString("number")));
                i.putExtra("sms_body", a.optString("text"));
                return i;
            }
            case "email": {
                Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + a.optString("to")));
                i.putExtra(Intent.EXTRA_SUBJECT, a.optString("subject"));
                i.putExtra(Intent.EXTRA_TEXT, a.optString("body"));
                return i;
            }
            case "play_music": {
                Intent i = new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH);
                i.putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*");
                i.putExtra("query", a.optString("query"));
                return i;
            }
            case "camera":
                return new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            case "settings":
                return new Intent(settingsAction(a.optString("panel", "general")));
            case "share": {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT, a.optString("text"));
                return Intent.createChooser(send, "Share with");
            }
            default:
                return null;
        }
    }

    private String settingsAction(String panel) {
        switch (panel.toLowerCase(Locale.ROOT)) {
            case "wifi": return Settings.ACTION_WIFI_SETTINGS;
            case "bluetooth": return Settings.ACTION_BLUETOOTH_SETTINGS;
            case "display": return Settings.ACTION_DISPLAY_SETTINGS;
            case "sound": return Settings.ACTION_SOUND_SETTINGS;
            case "battery": return Settings.ACTION_BATTERY_SAVER_SETTINGS;
            case "location": return Settings.ACTION_LOCATION_SOURCE_SETTINGS;
            case "apps": return Settings.ACTION_APPLICATION_SETTINGS;
            case "date": return Settings.ACTION_DATE_SETTINGS;
            default: return Settings.ACTION_SETTINGS;
        }
    }

    private void addCalendarEvent(JSONObject a) {
        if (checkSelfPermission(Manifest.permission.WRITE_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            pendingCalendarAction = a;
            requestPermissions(new String[]{
                    Manifest.permission.WRITE_CALENDAR,
                    Manifest.permission.READ_CALENDAR
            }, CAL_PERMISSION_REQUEST);
            return;
        }

        long calendarId = findWritableCalendarId();
        if (calendarId < 0) {
            toast("No calendar found. Add your Google account in phone settings.");
            return;
        }

        long start;
        long end;
        try {
            start = parseTime(a.optString("start"));
            end = a.has("end") ? parseTime(a.optString("end")) : start + 60 * 60 * 1000L;
        } catch (IllegalArgumentException error) {
            toast(error.getMessage());
            return;
        }

        ContentValues values = new ContentValues();
        values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
        values.put(CalendarContract.Events.TITLE, a.optString("title", "EDITH event"));
        values.put(CalendarContract.Events.DTSTART, start);
        values.put(CalendarContract.Events.DTEND, end);
        values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
        values.put(CalendarContract.Events.EVENT_LOCATION, a.optString("location"));
        values.put(CalendarContract.Events.DESCRIPTION, a.optString("notes"));

        try {
            Uri uri = getContentResolver().insert(CalendarContract.Events.CONTENT_URI, values);
            toast(uri != null ? "Added to Google Calendar" : "Could not save the event.");
        } catch (Exception error) {
            android.util.Log.e("EDITH", "Calendar insert failed", error);
            toast("Could not save the event.");
        }
    }

    private long findWritableCalendarId() {
        long fallback = -1;
        String[] projection = {
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.ACCOUNT_TYPE
        };
        try (Cursor c = getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI, projection,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + " >= ?",
                new String[]{"500"}, null)) {
            if (c == null) return -1;
            while (c.moveToNext()) {
                long id = c.getLong(0);
                if ("com.google".equals(c.getString(1))) return id;
                if (fallback < 0) fallback = id;
            }
        } catch (Exception error) {
            android.util.Log.e("EDITH", "Calendar lookup failed", error);
        }
        return fallback;
    }

    // Accepts "2026-10-25T15:00" and returns milliseconds.
    private long parseTime(String value) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
                    .parse(value.trim()).getTime();
        } catch (Exception error) {
            throw new IllegalArgumentException("Date must look like 2026-10-25T15:00");
        }
    }

    private boolean openApp(String name) {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        String wanted = name == null ? "" : name.toLowerCase(Locale.ROOT).trim();
        if (wanted.isEmpty()) {
            toast("No app name given.");
            return false;
        }
        for (ResolveInfo info : pm.queryIntentActivities(main, 0)) {
            String label = info.loadLabel(pm).toString().toLowerCase(Locale.ROOT);
            if (label.contains(wanted)) {
                Intent launch = pm.getLaunchIntentForPackage(info.activityInfo.packageName);
                if (launch != null) {
                    startActivity(launch);
                    return true;
                }
            }
        }
        toast("Couldn't find an app called " + name);
        return false;
    }

    // ============================================================
    // Phone feature check
    // ============================================================

    private void showDeviceCheck() {
        screen = "DEVICE";
        clearContent();
        LinearLayout page = addPage();

        LinearLayout top = horizontal();
        top.addView(textAction("← Models", accent, v -> showModels()), lp(-2, -2));
        page.addView(top, lp(-1, -2));
        addTitle(page, "Phone features",
                "What EDITH can use on this phone. A ✗ means that feature won't work here.");

        page.addView(sectionLabel("EDITH"), margins(-1, -2, 0, 0, 0, 8));
        addCheck(page, "Voice input", SpeechRecognizer.isRecognitionAvailable(this),
                "The phone has a speech recognizer");
        addCheck(page, "Microphone permission",
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
                "Needed to speak to EDITH");
        addCheck(page, "Read aloud", ttsReady, "Text-to-speech engine is ready");
        addCheck(page, "On-device engine", nativeAvailable, "The native llama library loaded");
        addCheck(page, "On-device model", modelReady, "A .gguf model is loaded");
        addCheck(page, "Cloud key", !getOllamaKey().isEmpty(),
                "An Ollama key is saved (needed for screenshots and PDFs)");

        page.addView(sectionLabel("WHATSAPP AND APP CONTROL"), margins(-1, -2, 0, 12, 0, 8));
        addCheck(page, "WhatsApp installed",
                getPackageManager().getLaunchIntentForPackage(WHATSAPP_PACKAGE) != null,
                "EDITH can message contacts on WhatsApp");
        addCheck(page, "Phone control (Accessibility)", EdithAccessibilityService.isEnabled(),
                "Lets EDITH type and tap inside apps. Turn on EDITH phone control in Accessibility.");
        page.addView(button("Open Accessibility settings", STYLE_GHOST,
                        v -> openAccessibilitySettings()),
                margins(-1, -2, 0, 0, 0, 8));

        page.addView(sectionLabel("PHONE ACTIONS"), margins(-1, -2, 0, 12, 0, 8));
        addIntentCheck(page, "Phone calls",
                new Intent(Intent.ACTION_DIAL, Uri.parse("tel:123")));
        addIntentCheck(page, "Text messages",
                new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:123")));
        addIntentCheck(page, "Email",
                new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:test@example.com")));
        addIntentCheck(page, "Web browser",
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")));
        addIntentCheck(page, "Maps navigation",
                new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=test")));
        addIntentCheck(page, "Calendar",
                new Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI));
        addIntentCheck(page, "Contacts",
                new Intent(ContactsContract.Intents.Insert.ACTION)
                        .setType(ContactsContract.RawContacts.CONTENT_TYPE));
        addIntentCheck(page, "Timer", new Intent(AlarmClock.ACTION_SET_TIMER));
        addIntentCheck(page, "Alarm", new Intent(AlarmClock.ACTION_SET_ALARM));
        addIntentCheck(page, "Music player",
                new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH));
        addIntentCheck(page, "Camera", new Intent(MediaStore.ACTION_IMAGE_CAPTURE));
        addIntentCheck(page, "Settings", new Intent(Settings.ACTION_SETTINGS));
        addIntentCheck(page, "Wi-Fi settings", new Intent(Settings.ACTION_WIFI_SETTINGS));

        int apps = getPackageManager()
                .queryIntentActivities(new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER), 0).size();
        addCheck(page, "Open apps by name", apps > 0,
                apps + " launchable apps are visible to EDITH");

        page.addView(button("Run check again", STYLE_SECONDARY, v -> showDeviceCheck()),
                margins(-1, -2, 0, 12, 0, 0));
    }

    private void addCheck(LinearLayout page, String title, boolean ok, String detail) {
        LinearLayout row = horizontal();
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(box(surface, border, 16));

        TextView mark = text(ok ? "✓" : "✗", 16, ok ? readyGreen : dangerRed);
        mark.setTypeface(edithFont(Typeface.BOLD));
        mark.setPadding(0, 0, dp(12), 0);
        row.addView(mark, lp(-2, -2));

        LinearLayout labels = vertical();
        labels.addView(heading(title, 14));
        labels.addView(text(detail, 11, secondaryText), margins(-1, -2, 0, 3, 0, 0));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        page.addView(row, margins(-1, -2, 0, 0, 0, 8));
    }

    private void addIntentCheck(LinearLayout page, String title, Intent intent) {
        boolean ok = intent.resolveActivity(getPackageManager()) != null;
        addCheck(page, title, ok, ok
                ? "An app on this phone can do this"
                : "No app on this phone can do this");
    }

    // ============================================================
    // Memory screen
    // ============================================================

    private LinearLayout settingRow(String title, String subtitle, boolean on,
                                    View.OnClickListener action) {
        LinearLayout row = horizontal();
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackground(ripple(box(surface, border, 16)));

        LinearLayout labels = vertical();
        labels.addView(heading(title, 14));
        labels.addView(text(subtitle, 11, secondaryText), margins(-1, -2, 0, 3, 0, 0));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        TextView pill = text(on ? "ON" : "OFF", 10, on ? bg : secondaryText);
        pill.setTypeface(edithFont(Typeface.BOLD));
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(14), dp(6), dp(14), dp(6));
        pill.setBackground(on ? box(accent, 0, 12) : box(surface2, border, 12));
        row.addView(pill, lp(-2, -2));

        row.setOnClickListener(action);
        return row;
    }

    private void showMemory() {
        screen = "MEMORY";
        clearContent();
        LinearLayout page = addPage();
        addTitle(page, "Memory",
                "EDITH remembers what you tell it. Tap a memory to edit it, ✕ to forget it.");

        page.addView(settingRow("Use memories",
                        "Relevant facts are added to EDITH's context",
                        isMemoryEnabled(), v -> {
                            setFlag(MEMORY_ENABLED_KEY, !isMemoryEnabled());
                            showMemory();
                        }),
                margins(-1, -2, 0, 0, 0, 8));

        page.addView(settingRow("Learn from what I say",
                        "Picks up clear preferences like \"I prefer short answers\"",
                        isAutoLearnOn(), v -> {
                            setFlag(AUTOLEARN_KEY, !isAutoLearnOn());
                            showMemory();
                        }),
                margins(-1, -2, 0, 0, 0, 8));

        page.addView(settingRow("Recall from older chats",
                        "Pull relevant excerpts from earlier conversations",
                        isRecallEnabled(), v -> {
                            setFlag(RECALL_ENABLED_KEY, !isRecallEnabled());
                            showMemory();
                        }),
                margins(-1, -2, 0, 0, 0, 10));

        page.addView(text("Tip: say \"remember that ...\" or \"forget about ...\" in any chat, "
                        + "or ask \"what do you remember about me?\".",
                11, secondaryText), margins(-1, -2, 4, 0, 4, 18));

        page.addView(sectionLabel("ADD A MEMORY"), margins(-1, -2, 0, 0, 0, 8));
        EditText input = styledInput("A fact you want EDITH to remember");
        input.setMinLines(2);
        attachTypingSound(input);
        page.addView(input, margins(-1, -2, 0, 0, 0, 8));
        page.addView(button("Save memory", STYLE_PRIMARY, v -> {
            String fact = input.getText().toString().trim();
            if (fact.isEmpty()) {
                toast("Enter a memory first.");
                return;
            }
            if (saveMemory(fact, "Added manually", true)) {
                toast("Memory saved");
                showMemory();
            } else {
                toast("Could not save. Check length or the memory limit.");
            }
        }), margins(-1, -2, 0, 0, 0, 20));

        page.addView(sectionLabel("SAVED  ·  " + memories.size()), margins(-1, -2, 0, 0, 0, 8));
        if (memories.isEmpty()) {
            LinearLayout empty = card(16);
            empty.addView(text("Nothing saved yet. Tell EDITH a preference, long-press a reply and "
                    + "choose \"Save as memory\", or add one above.", 13, secondaryText));
            page.addView(empty, lp(-1, -2));
        }

        for (JSONObject m : memories) {
            page.addView(memoryCard(m), margins(-1, -2, 0, 0, 0, 8));
        }

        if (!memories.isEmpty()) {
            page.addView(button("Clear all memories", STYLE_DANGER, v ->
                    new AlertDialog.Builder(this)
                            .setTitle("Clear all memories?")
                            .setMessage("This cannot be undone.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Clear", (d, w) -> {
                                memories.clear();
                                writeList(MEMORY_KEY, memories);
                                showMemory();
                            })
                            .show()), margins(-1, -2, 0, 12, 0, 0));
        }
    }

    private LinearLayout memoryCard(JSONObject m) {
        final String mid = m.optString("id");
        LinearLayout row = horizontal();
        row.setPadding(dp(14), dp(12), dp(6), dp(12));
        row.setBackground(ripple(box(surface, border, 16)));

        LinearLayout labels = vertical();
        labels.addView(text(m.optString("text"), 14, primaryText));

        LinearLayout chips = horizontal();
        String source = m.optString("source");
        TextView sourceChip = text(source.isEmpty() ? "Saved manually" : "From  " + source,
                10, accent);
        sourceChip.setPadding(dp(8), dp(3), dp(8), dp(3));
        sourceChip.setBackground(box(accentSoft, 0, 8));
        chips.addView(sourceChip, lp(-2, -2));
        if (m.optBoolean("core", false)) {
            TextView always = text("Always used", 10, secondaryText);
            always.setPadding(dp(8), dp(3), dp(8), dp(3));
            always.setBackground(box(surface2, 0, 8));
            chips.addView(always, margins(-2, -2, 6, 0, 0, 0));
        }
        labels.addView(chips, margins(-2, -2, 0, 8, 0, 0));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        row.addView(textAction("✕", secondaryText, v -> {
            removeById(memories, mid);
            writeList(MEMORY_KEY, memories);
            showMemory();
        }), lp(dp(40), dp(40)));

        row.setOnClickListener(v -> editMemory(m));
        return row;
    }


    // ============================================================
    // Creative tools: image generation/editing and PDF export
    // ============================================================

    // ---------- Chat commands: "create a pdf about X" / "generate an image of X" ----------

    private static String tidyTopic(String t) {
        if (t == null) return null;
        t = t.trim().replaceAll("[\\s.!?]+$", "").trim();
        return t.isEmpty() ? null : t;
    }

    private String pdfTopicOf(String msg) {
        String m = msg.trim();
        if (!m.toLowerCase(Locale.ROOT).contains("pdf")) return null;
        if (!Pattern.compile("(?i)\\b(create|make|generate|write|build|prepare|give|produce)\\b")
                .matcher(m).find()) return null;
        // Leave "save this chat / last answer as pdf" to the existing export buttons.
        if (Pattern.compile("(?i)\\b(this|our|my|the)\\s+(chat|conversation|answer|reply)\\b")
                .matcher(m).find()) return null;
        Matcher a = Pattern.compile(
                "(?i)\\bpdf\\b\\s*(?:document|file|report)?\\s*(?:about|on|of|for|regarding|covering)\\s*:?\\s*(.+)$")
                .matcher(m);
        if (a.find()) return tidyTopic(a.group(1));
        Matcher b = Pattern.compile(
                "(?i)\\b(?:about|on|regarding|covering)\\s+(.+?)(?:\\s+(?:as|in|into|to)\\s+(?:a\\s+)?pdf.*)?$")
                .matcher(m);
        if (b.find()) return tidyTopic(b.group(1));
        return null;
    }

    private String imagePromptOf(String msg) {
        String m = msg.trim();
        Matcher a = Pattern.compile(
                "(?i)^(?:please\\s+)?(?:can you\\s+)?(?:generate|create|make|draw|paint|show me)\\s+(?:me\\s+)?(?:an?\\s+)?"
                        + "(?:image|picture|photo|drawing|illustration|art)\\s*(?:of|about|showing|with)?\\s*(.*)$")
                .matcher(m);
        if (a.find()) return tidyTopic(a.group(1));
        Matcher d = Pattern.compile("(?i)^draw\\s+(.+)$").matcher(m);
        if (d.find()) return tidyTopic(d.group(1));
        return null;
    }

    private static final String PDF_WRITER_SYSTEM =
            "You are EDITH, an assistant that writes clear, accurate documents.";

    private boolean isBarePdfRequest(String msg) {
        return Pattern.compile("(?i)^\\s*(please\\s+)?(can you\\s+)?(create|make|generate|build|prepare|write)"
                + "\\s+(me\\s+)?(a|an|one)?\\s*pdf(\\s+(file|document))?\\s*[.!?]*\\s*$").matcher(msg).find();
    }

    /** 0 = not a "PDF from this chat" request, 1 = AI-written document, 2 = exact transcript, 3 = last answer. */
    private int pdfFromChatMode(String msg) {
        String m = msg.toLowerCase(Locale.ROOT);
        if (!m.contains("pdf")) return 0;
        if (!Pattern.compile("\\b(create|make|generate|save|export|turn|convert|give|prepare|write|build|produce|download|put)\\b")
                .matcher(m).find()) return 0;
        boolean chat = Pattern.compile("\\b(this|our|my|the|that|whole|entire|current|above)\\s+"
                + "(chat|conversation|discussion|thread)\\b(?!\\s+(feature|app|bot|history|gpt))"
                + "|\\b(from|of)\\s+(what we|everything we)\\b").matcher(m).find();
        boolean answer = Pattern.compile("\\b(this|that|your|the|last|latest|previous)\\s+"
                + "(answer|reply|response)\\b").matcher(m).find();
        if (!chat && !answer) return 0;
        if (chat && Pattern.compile("\\b(transcript|word for word|exactly as|as is|full chat)\\b")
                .matcher(m).find()) return 2;
        if (answer && !chat) return 3;
        return 1;
    }

    // Chat text as "You: ... / EDITH: ...", leaving out the PDF command that was just typed.
    private String chatTranscript(JSONObject conv) {
        JSONArray messages = messagesOf(conv);
        int end = messages.length();
        JSONObject last = end > 0 ? messages.optJSONObject(end - 1) : null;
        if (last != null && "user".equals(last.optString("role"))) end--;
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < end; i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) continue;
            String text = message.optString("text", "").trim();
            if (text.isEmpty()) continue;
            body.append("user".equals(message.optString("role")) ? "You" : "EDITH")
                    .append(":\n").append(text).append("\n\n");
        }
        return body.toString().trim();
    }

    private void startPdfFromChat(JSONObject conv, final String convId, int mode) {
        String chatTitle = conv.optString("title", "");
        final String title = (chatTitle.isEmpty() || "New chat".equals(chatTitle)) ? "EDITH chat" : chatTitle;
        final String transcript = chatTranscript(conv);
        if (transcript.isEmpty()) {
            deliverLocal(conv, "This chat is empty so far, so there's nothing to put in a PDF yet.", "NOTE");
            return;
        }

        if (mode == 3) {
            JSONArray messages = messagesOf(conv);
            for (int i = messages.length() - 1; i >= 0; i--) {
                JSONObject message = messages.optJSONObject(i);
                if (message != null && "assistant".equals(message.optString("role"))
                        && !message.optString("text", "").trim().isEmpty()) {
                    deliverLocal(conv, "Saving my last answer as a PDF in Downloads/EDITH…", "NOTE");
                    writePdf(title, message.optString("text").trim());
                    return;
                }
            }
            deliverLocal(conv, "I haven't given an answer in this chat yet.", "NOTE");
            return;
        }

        if (mode == 2) {
            deliverLocal(conv, "Saving this chat, word for word, as a PDF in Downloads/EDITH…", "NOTE");
            writePdf(title, transcript);
            return;
        }

        if (!isReady()) {
            deliverLocal(conv, "To write a PDF from this chat I need a model: load one in Models or add a cloud key. "
                    + "You can still say \"save the transcript as a PDF\" to export the chat exactly as it is.", "NOTE");
            return;
        }
        busyConversationId = convId;
        setPresence("THINKING");
        refreshChatView();
        final String source = transcript.length() > 12000
                ? "(earlier part of the chat left out)\n" + transcript.substring(transcript.length() - 12000)
                : transcript;
        final String ask = "Turn the conversation below into a clear, well-organized document. Keep its facts, "
                + "answers and conclusions, and do not add new facts. Use plain text only: short section headings "
                + "on their own lines, then paragraphs. Do not use markdown symbols, tables or code blocks.\n\n"
                + "Conversation:\n" + source;
        executor.execute(() -> {
            String body = null;
            String error = null;
            try {
                body = askModel(PDF_WRITER_SYSTEM, ask).replaceAll("[#*`]", "").trim();
                if (body.isEmpty()) error = "The AI returned no text.";
            } catch (Throwable t) {
                error = safeError(t);
            }
            final String finalBody = body;
            final String finalError = error;
            runOnUiThread(() -> {
                if (finalError != null) {
                    finishReply(convId, "I couldn't write the document: " + finalError, "ERROR", false, false);
                } else {
                    finishReply(convId, finalBody + "\n\nSaving this as a PDF in Downloads/EDITH…",
                            "NOTE", false, false);
                    writePdf(title, finalBody);
                }
            });
        });
    }

    private void startTopicPdf(final String convId, final String topic) {
        busyConversationId = convId;
        setPresence("THINKING");
        refreshChatView();
        final String ask = "Write a clear, well-organized document about: " + topic
                + ". Use plain text only: short section headings on their own lines, then paragraphs. "
                + "Do not use markdown symbols, tables or code blocks.";
        executor.execute(() -> {
            String body = null;
            String error = null;
            try {
                body = askModel("You are EDITH, an assistant that writes clear, accurate documents.", ask);
                body = body.replaceAll("[#*`]", "").trim();
                if (body.isEmpty()) error = "The AI returned no text.";
            } catch (Throwable t) {
                error = safeError(t);
            }
            final String finalBody = body;
            final String finalError = error;
            final String title = topic.substring(0, 1).toUpperCase(Locale.ROOT) + topic.substring(1);
            runOnUiThread(() -> {
                if (finalError != null) {
                    finishReply(convId, "I couldn't write the document: " + finalError, "ERROR", false, false);
                } else {
                    finishReply(convId, finalBody + "\n\nSaving this as a PDF in Downloads/EDITH…",
                            "NOTE", false, false);
                    writePdf(title, finalBody);
                }
            });
        });
    }

    // ============================================================
    // Smart shopping (stages 1-2: compare stores + price history)
    // Prices are only ever read from a page or typed by the user.
    // ============================================================

    private String shopQuery = "";

    private void openShopUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast("No app found to open this link.");
        }
    }

    private void showShopping() {
        screen = "SHOP";
        clearContent();
        LinearLayout page = addPage();
        addTitle(page, "Smart shopping",
                "Compare stores and track prices. EDITH only shows prices it read from a page or that you entered.");

        page.addView(sectionLabel("FIND A PRODUCT"), margins(-1, -2, 0, 0, 0, 8));
        LinearLayout searchCard = card(16);
        final EditText q = styledInput("e.g. boAt Airdopes 141");
        q.setSingleLine(true);
        q.setText(shopQuery);
        searchCard.addView(q, lp(-1, -2));
        searchCard.addView(button("Search stores", STYLE_PRIMARY, v -> {
            shopQuery = q.getText().toString().trim();
            if (!shopQuery.isEmpty()) {
                logCard(null, CARD_SHOP, "Search stores: " + shopQuery,
                        "Showed store search links for \"" + shopQuery + "\".");
            }
            showShopping();
        }), margins(-1, -2, 0, 10, 0, 0));
        if (!shopQuery.isEmpty()) {
            searchCard.addView(text("Open a store's results for \"" + shopQuery + "\":",
                    12, secondaryText), margins(-1, -2, 0, 14, 0, 8));
            for (String[] link : EdithShopping.searchLinks(shopQuery)) {
                final String url = link[1];
                searchCard.addView(button(link[0], STYLE_SECONDARY, v -> openShopUrl(url)),
                        margins(-1, -2, 0, 0, 0, 8));
            }
            searchCard.addView(text("Found one you like? Copy its link, then tap Track a price below.",
                    11, secondaryText));
        }
        page.addView(searchCard, margins(-1, -2, 0, 0, 0, 18));

        page.addView(sectionLabel("TRACKED PRODUCTS"), margins(-1, -2, 0, 0, 0, 8));
        page.addView(button("＋  Track a price", STYLE_PRIMARY, v -> trackPriceDialog(null)),
                margins(-1, -2, 0, 0, 0, 10));
        JSONArray products = EdithShopping.load(this);
        if (products.length() == 0) {
            LinearLayout empty = card(16);
            empty.addView(text("Nothing tracked yet. Paste a product link and EDITH will try to read its price, "
                    + "then keep a dated history each time you refresh.", 12, secondaryText));
            page.addView(empty, lp(-1, -2));
        } else {
            for (int i = 0; i < products.length(); i++) {
                JSONObject product = products.optJSONObject(i);
                if (product != null) page.addView(shopProductCard(product), margins(-1, -2, 0, 0, 0, 12));
            }
            page.addView(text("Prices are as last recorded, with their dates. EDITH also re-checks them "
                    + "about every 6 hours and notifies you of drops. "
                    + "Some stores block apps, so a price may need to be entered by hand.",
                    11, secondaryText), margins(-1, -2, 0, 4, 0, 0));
        }
    }

    private LinearLayout shopProductCard(final JSONObject product) {
        LinearLayout c = card(14);
        LinearLayout head = horizontal();
        head.addView(heading(product.optString("name", "Product"), 16), new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(textAction("⋯", secondaryText, v -> shopProductMenu(product)), lp(-2, -2));
        c.addView(head, lp(-1, -2));
        if (EdithShopping.targetOf(product) > 0) {
            c.addView(text("Alert at or below " + EdithShopping.money(EdithShopping.targetOf(product)),
                    12, secondaryText), margins(-1, -2, 0, 2, 0, 0));
        }

        JSONArray ls = product.optJSONArray("listings");
        int best = EdithShopping.cheapestIndex(product);
        for (int i = 0; ls != null && i < ls.length(); i++) {
            final JSONObject l = ls.optJSONObject(i);
            if (l == null) continue;
            boolean lowest = (i == best);
            LinearLayout row = vertical();
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackground(box(surface2, 0, 12));
            row.addView(text(l.optString("store") + (lowest ? "   ·   lowest recorded" : ""),
                    13, lowest ? readyGreen : primaryText));
            row.addView(text(EdithShopping.describeLatest(l), 12, secondaryText), margins(-1, -2, 0, 4, 0, 0));
            row.setOnClickListener(v -> shopListingMenu(product, l));
            c.addView(row, margins(-1, -2, 0, 10, 0, 0));
        }
        return c;
    }

    private void shopProductMenu(final JSONObject product) {
        final String[] items = {"Add another store link", "Set price alert", "Delete this product"};
        new AlertDialog.Builder(this)
                .setTitle(product.optString("name"))
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        trackPriceDialog(product);
                    } else if (which == 1) {
                        shopTargetDialog(product);
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle("Delete product?")
                                .setMessage("This removes its links and price history from this phone.")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Delete", (d2, w2) -> {
                                    EdithShopping.deleteProduct(this, product.optString("id"));
                                    showShopping();
                                })
                                .show();
                    }
                })
                .show();
    }

    private void shopListingMenu(final JSONObject product, final JSONObject listing) {
        final String url = listing.optString("url");
        final String[] items = {"Open the page", "Refresh price from the page",
                "Enter a price myself", "View price history", "Remove this link"};
        new AlertDialog.Builder(this)
                .setTitle(listing.optString("store"))
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        openShopUrl(url);
                    } else if (which == 1) {
                        shopReadAndSave(product, "", url, listing);
                    } else if (which == 2) {
                        shopAskManualPrice(product, "", url, listing, "",
                                "Type the price you can see on the store's page. It will be saved as \"entered by you\".");
                    } else if (which == 3) {
                        new AlertDialog.Builder(this)
                                .setTitle("Price history · " + listing.optString("store"))
                                .setMessage(EdithShopping.historyText(listing))
                                .setPositiveButton("OK", null)
                                .show();
                    } else {
                        EdithShopping.deleteListing(this, product.optString("id"), listing.optString("id"));
                        showShopping();
                    }
                })
                .show();
    }

    private void shopTargetDialog(final JSONObject product) {
        final EditText input = styledInput("Price in ₹, e.g. 1499 (empty to clear)");
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        double current = EdithShopping.targetOf(product);
        if (current > 0) input.setText(String.valueOf((long) current));
        new AlertDialog.Builder(this)
                .setTitle("Price alert")
                .setMessage("EDITH re-checks tracked pages about every 6 hours and notifies you when a "
                        + "price drops. Set a target to be told when it reaches that amount.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String raw = input.getText().toString().trim();
                    Double v = raw.isEmpty() ? Double.valueOf(0) : EdithShopping.parseNumber(raw);
                    if (v == null) {
                        toast("Enter a price greater than zero.");
                        return;
                    }
                    EdithShopping.setTarget(this, product.optString("id"), v);
                    logCard(null, CARD_SHOP, "Price alert: " + product.optString("name"),
                            v > 0 ? "Alert when a price is at or below " + EdithShopping.money(v) + "."
                                    : "Price alert cleared.");
                    EdithPriceJob.schedule(this);
                    askNotificationPermission();
                    showShopping();
                })
                .show();
    }

    // Android 13+ needs the user's OK before any notification can appear. Asked once.
    private void askNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < 33) return;
        final String perm = "android.permission.POST_NOTIFICATIONS";
        if (checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) return;
        SharedPreferences sp = getSharedPreferences("edith_shopping", MODE_PRIVATE);
        if (sp.getBoolean("notif_asked", false)) return;
        sp.edit().putBoolean("notif_asked", true).apply();
        requestPermissions(new String[]{perm}, 7301);
    }

    private void trackPriceDialog(final JSONObject existing) {
        LinearLayout form = vertical();
        final EditText nameIn = styledInput("Product name (optional)");
        final EditText linkIn = styledInput("Paste the product link (https://…)");
        if (existing == null) form.addView(nameIn, margins(-1, -2, 0, 0, 0, 8));
        form.addView(linkIn, lp(-1, -2));
        new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Track a price" : "Add another store")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Read price", (d, w) -> {
                    String link = EdithShopping.normalizeUrl(linkIn.getText().toString());
                    if (link == null) {
                        toast("Paste a valid product link.");
                        return;
                    }
                    shopReadAndSave(existing, nameIn.getText().toString().trim(), link, null);
                })
                .show();
    }

    // listing == null means a new link: ask the user to confirm the price read before saving it.
    private void shopReadAndSave(final JSONObject product, final String nameHint,
                                 final String url, final JSONObject listing) {
        toast("Reading the page… this can take a few seconds.");
        executor.execute(() -> {
            final EdithShopping.Read r = EdithShopping.readPrice(url);
            runOnUiThread(() -> {
                if (r.price == null) {
                    shopAskManualPrice(product, nameHint, url, listing, r.title,
                            "EDITH couldn't read a price: " + r.note
                                    + ".\n\nType the price you can see on the page. It will be saved as \"entered by you\".");
                    return;
                }
                if (listing != null) {
                    shopSave(product, nameHint, url, listing, r.title, r.price, EdithShopping.SRC_PAGE);
                    return;
                }
                new AlertDialog.Builder(this)
                        .setTitle("Price found")
                        .setMessage("EDITH read " + EdithShopping.money(r.price) + " from the page"
                                + (r.title.isEmpty() ? "" : " for \"" + r.title + "\"")
                                + ".\n\nCheck that it matches the price on the store's page before relying on it.")
                        .setNegativeButton("Cancel", null)
                        .setNeutralButton("Enter myself", (d, w) -> shopAskManualPrice(product, nameHint, url,
                                null, r.title, "Type the price you can see on the page."))
                        .setPositiveButton("Save", (d, w) -> shopSave(product, nameHint, url, null,
                                r.title, r.price, EdithShopping.SRC_PAGE))
                        .show();
            });
        });
    }

    private void shopAskManualPrice(final JSONObject product, final String nameHint, final String url,
                                    final JSONObject listing, final String title, String message) {
        final EditText input = styledInput("Price in ₹, e.g. 1299");
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        new AlertDialog.Builder(this)
                .setTitle("Enter the price")
                .setMessage(message)
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    Double v = EdithShopping.parseNumber(input.getText().toString());
                    if (v == null) {
                        toast("Enter a price greater than zero.");
                        return;
                    }
                    shopSave(product, nameHint, url, listing, title, v, EdithShopping.SRC_MANUAL);
                })
                .show();
    }

    private void shopSave(JSONObject product, String nameHint, String url, JSONObject listing,
                          String title, double price, String src) {
        try {
            EdithShopping.recordPrice(this,
                    product == null ? null : product.optString("id"),
                    nameHint, url,
                    listing == null ? null : listing.optString("id"),
                    title, price, src);
            toast("Saved " + EdithShopping.money(price)
                    + (EdithShopping.SRC_PAGE.equals(src) ? " (read from page)." : " (entered by you)."));
            String logName = product != null ? product.optString("name")
                    : (nameHint != null && !nameHint.isEmpty() ? nameHint
                    : (title != null && !title.isEmpty() ? title : EdithShopping.storeOf(url) + " item"));
            logCard(null, CARD_SHOP, "Track a price: " + logName + " (" + EdithShopping.storeOf(url) + ")",
                    "Saved " + EdithShopping.money(price)
                            + (EdithShopping.SRC_PAGE.equals(src) ? ", read from the page." : ", entered by you.")
                            + "\n" + url);
            EdithPriceJob.schedule(this);
            askNotificationPermission();
        } catch (Exception e) {
            toast("Couldn't save: " + (e.getMessage() == null ? "unknown error" : e.getMessage()));
        }
        showShopping();
    }

    // ============================================================
    // Cab fares: distance/time from OpenStreetMap, fares entered by the user
    // ============================================================
    private String cabPickup = "", cabDrop = "", cabWhen = "", cabCategory = "Mini", cabPassengers = "1";
    private String cabRouteId = null;
    private String cabLastProvider = "";
    private static final String[] CAB_CATEGORIES = {"Auto", "Bike", "Mini", "Sedan", "SUV", "Other"};
    private static final String[] CAB_PROVIDERS = {"Uber", "Ola", "Rapido"};

    private void cabReadFields(EditText pickup, EditText drop, EditText when, EditText pax) {
        cabPickup = pickup.getText().toString().trim();
        cabDrop = drop.getText().toString().trim();
        cabWhen = when.getText().toString().trim();
        String n = pax.getText().toString().trim();
        cabPassengers = n.isEmpty() ? "1" : n;
    }

    private void showCabs() {
        screen = "CABS";
        clearContent();
        LinearLayout page = addPage();
        addTitle(page, "Cab fares",
                "EDITH looks up distance and time. Fares are the ones you enter from each provider's app, "
                        + "and are always labelled as entered by you.");

        page.addView(sectionLabel("FIND A ROUTE"), margins(-1, -2, 0, 0, 0, 8));
        LinearLayout form = card(16);
        final EditText pickup = styledInput("Pickup, e.g. Law Garden, Ahmedabad");
        pickup.setSingleLine(true);
        pickup.setText(cabPickup);
        final EditText drop = styledInput("Destination, e.g. Ahmedabad airport");
        drop.setSingleLine(true);
        drop.setText(cabDrop);
        final EditText when = styledInput("Date and time (optional), e.g. Today 6:30 PM");
        when.setSingleLine(true);
        when.setText(cabWhen);
        final EditText pax = styledInput("Passengers");
        pax.setInputType(InputType.TYPE_CLASS_NUMBER);
        pax.setText(cabPassengers);
        form.addView(pickup, lp(-1, -2));
        form.addView(drop, margins(-1, -2, 0, 8, 0, 0));
        form.addView(when, margins(-1, -2, 0, 8, 0, 0));
        form.addView(pax, margins(-1, -2, 0, 8, 0, 0));
        form.addView(button("Category: " + cabCategory + "  ▾", STYLE_SECONDARY, v -> {
            cabReadFields(pickup, drop, when, pax);
            new AlertDialog.Builder(this)
                    .setTitle("Cab category")
                    .setItems(CAB_CATEGORIES, (d, which) -> {
                        cabCategory = CAB_CATEGORIES[which];
                        showCabs();
                    })
                    .show();
        }), margins(-1, -2, 0, 10, 0, 0));
        form.addView(button("Look up route", STYLE_PRIMARY, v -> {
            cabReadFields(pickup, drop, when, pax);
            cabLookupRoute();
        }), margins(-1, -2, 0, 10, 0, 0));
        form.addView(text("Uber, Ola and Rapido don't offer a free way for EDITH to fetch their fares. "
                + "After the lookup, type in the fares you see in each app and EDITH compares and tracks them.",
                11, secondaryText), margins(-1, -2, 0, 10, 0, 0));
        page.addView(form, margins(-1, -2, 0, 0, 0, 18));

        page.addView(sectionLabel("SAVED ROUTES"), margins(-1, -2, 0, 0, 0, 8));
        JSONArray routes = EdithCabs.load(this);
        if (routes.length() == 0) {
            LinearLayout empty = card(16);
            empty.addView(text("No routes yet. Look one up above, then add the fares you see.",
                    12, secondaryText));
            page.addView(empty, lp(-1, -2));
        }
        for (int i = routes.length() - 1; i >= 0; i--) {
            final JSONObject route = routes.optJSONObject(i);
            if (route == null) continue;
            LinearLayout row = card(14);
            row.addView(heading(route.optString("pickup") + " → " + route.optString("drop"), 15));
            java.util.List<EdithCabs.Option> opts = EdithCabs.options(route);
            String sub = route.has("km")
                    ? String.format(Locale.US, "%.1f km · about %d min", route.optDouble("km"),
                            Math.round(route.optDouble("min")))
                    : "Distance not available";
            sub += opts.isEmpty() ? "\nNo fares entered yet"
                    : "\nLowest entered: " + EdithShopping.money(opts.get(0).total) + " · "
                            + opts.get(0).provider + " " + opts.get(0).category;
            row.addView(text(sub, 12, secondaryText), margins(-1, -2, 0, 4, 0, 0));
            row.setOnClickListener(v -> showCabRoute(route.optString("id")));
            page.addView(row, margins(-1, -2, 0, 0, 0, 10));
        }
    }

    private void cabLookupRoute() {
        if (cabPickup.isEmpty() || cabDrop.isEmpty()) {
            toast("Enter a pickup and a destination.");
            return;
        }
        toast("Looking up the route…");
        final String pk = cabPickup, dr = cabDrop, wh = cabWhen, px = cabPassengers;
        executor.execute(() -> {
            final EdithCabs.Lookup res = EdithCabs.lookup(pk, dr);
            runOnUiThread(() -> {
                if (res.error != null) {
                    new AlertDialog.Builder(this)
                            .setTitle("Couldn't look up the route")
                            .setMessage(res.error + "\n\nYou can still save the route without distance "
                                    + "and time, and enter fares by hand.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Save anyway", (d, w) -> cabSaveAndOpen(pk, dr, null, wh, px))
                            .show();
                    return;
                }
                cabSaveAndOpen(pk, dr, res, wh, px);
            });
        });
    }

    private void cabSaveAndOpen(String pk, String dr, EdithCabs.Lookup res, String wh, String px) {
        try {
            String id = EdithCabs.saveRoute(this, pk, dr, res, wh, px);
            logCard(null, CARD_CAB, "Look up route: " + pk + " → " + dr,
                    res != null && res.trip != null
                            ? String.format(Locale.US, "%.1f km, about %d min (OpenStreetMap, no live traffic). "
                                    + "This is not a fare.", res.trip.km, Math.round(res.trip.minutes))
                            : "Saved without distance and time.");
            showCabRoute(id);
        } catch (Exception e) {
            toast("Couldn't save: " + (e.getMessage() == null ? "unknown error" : e.getMessage()));
        }
    }

    private void showCabRoute(final String routeId) {
        final JSONObject route = EdithCabs.findRoute(this, routeId);
        if (route == null) {
            showCabs();
            return;
        }
        screen = "CAB_ROUTE";
        cabRouteId = routeId;
        clearContent();
        LinearLayout page = addPage();
        page.addView(textAction("← Saved routes", accent, v -> showCabs()), margins(-2, -2, 0, 0, 0, 8));
        addTitle(page, route.optString("pickup") + " → " + route.optString("drop"), EdithCabs.tripLine(route));
        if (route.has("pickupMatch")) {
            page.addView(text("Matched on the map: " + route.optString("pickupMatch") + "  →  "
                    + route.optString("dropMatch") + ". If that's the wrong place, add more detail and look it up again.",
                    11, secondaryText), margins(-1, -2, 0, -8, 0, 14));
        }

        page.addView(button("＋  Add a fare I saw", STYLE_PRIMARY, v -> cabFareDialog(route, null, null)),
                margins(-1, -2, 0, 0, 0, 14));

        page.addView(sectionLabel("COMPARISON"), margins(-1, -2, 0, 0, 0, 8));
        final java.util.List<EdithCabs.Option> opts = EdithCabs.options(route);
        if (opts.isEmpty()) {
            LinearLayout empty = card(16);
            empty.addView(text("No fares yet. Open Uber, Ola or Rapido, search this route, and tap "
                    + "Add a fare I saw. EDITH can't read their fares for you.", 12, secondaryText));
            page.addView(empty, margins(-1, -2, 0, 0, 0, 18));
        } else {
            LinearLayout cmp = card(14);
            for (int i = 0; i < opts.size(); i++) {
                final EdithCabs.Option o = opts.get(i);
                boolean lowest = (i == 0 && opts.size() >= 2);
                LinearLayout row = vertical();
                row.setPadding(dp(12), dp(10), dp(12), dp(10));
                row.setBackground(box(surface2, 0, 12));
                row.addView(text(o.provider + " · " + o.category + (lowest ? "   ·   lowest recorded" : ""),
                        13, lowest ? readyGreen : primaryText));
                row.addView(text(EdithCabs.describe(o.latest), 12, secondaryText), margins(-1, -2, 0, 4, 0, 0));
                if (i > 0) {
                    row.addView(text(EdithShopping.money(o.total - opts.get(0).total)
                            + " more than the lowest", 12, secondaryText), margins(-1, -2, 0, 4, 0, 0));
                }
                row.setOnClickListener(v -> cabOptionDialog(route, o.provider, o.category));
                cmp.addView(row, margins(-1, -2, 0, i == 0 ? 0 : 10, 0, 0));
            }
            StringBuilder missing = new StringBuilder();
            for (String prov : CAB_PROVIDERS) {
                boolean found = false;
                for (EdithCabs.Option o : opts) if (o.provider.equalsIgnoreCase(prov)) found = true;
                if (!found) missing.append(missing.length() == 0 ? "" : ", ").append(prov);
            }
            String note = "Totals include any extra fees you entered. Tap a row for its history."
                    + (missing.length() > 0 ? "\nNo fare entered yet for: " + missing
                    + " (that means no data, not that they are unavailable)." : "");
            cmp.addView(text(note, 11, secondaryText), margins(-1, -2, 0, 12, 0, 0));
            page.addView(cmp, margins(-1, -2, 0, 0, 0, 18));
        }

        page.addView(sectionLabel("TARGET FARE"), margins(-1, -2, 0, 0, 0, 8));
        LinearLayout alertCard = card(14);
        double target = EdithCabs.targetOf(route);
        alertCard.addView(text(target > 0
                ? "Alert when a fare you enter is at or below " + EdithShopping.money(target)
                : "No target set.", 13, primaryText));
        alertCard.addView(text("You get an in-app alert and a notification when a fare you log reaches the target. "
                + "EDITH can't check provider prices on its own, so nothing is monitored in the background "
                + "unless an approved fare source is added later.", 11, secondaryText),
                margins(-1, -2, 0, 6, 0, 10));
        alertCard.addView(button(target > 0 ? "Change target fare" : "Set target fare", STYLE_SECONDARY,
                v -> cabTargetDialog(route)), lp(-1, -2));
        page.addView(alertCard, margins(-1, -2, 0, 0, 0, 18));

        page.addView(button("Delete this route", STYLE_SECONDARY, v ->
                new AlertDialog.Builder(this)
                        .setTitle("Delete route?")
                        .setMessage("This removes the route and all its fare history from this phone.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (d, w) -> {
                            EdithCabs.deleteRoute(this, routeId);
                            showCabs();
                        })
                        .show()), lp(-1, -2));
    }

    private void cabOptionDialog(final JSONObject route, final String provider, final String category) {
        java.util.List<JSONObject> h = EdithCabs.history(route, provider, category);
        if (h.isEmpty()) return;
        double[] values = new double[h.size()];
        long[] times = new long[h.size()];
        for (int i = 0; i < h.size(); i++) {
            values[i] = EdithCabs.totalOf(h.get(i));
            times[i] = h.get(i).optLong("t");
        }
        LinearLayout body = vertical();
        body.setPadding(dp(20), dp(8), dp(20), 0);
        body.addView(new EdithChartView(this, values, times, accent, secondaryText, border),
                new LinearLayout.LayoutParams(-1, dp(170)));
        body.addView(text(EdithCabs.statsText(h), 12, primaryText), margins(-1, -2, 0, 12, 0, 0));
        body.addView(text("\nAll entries\n" + EdithCabs.historyText(h), 12, secondaryText));
        ScrollView sv = new ScrollView(this);
        sv.addView(body);
        new AlertDialog.Builder(this)
                .setTitle(provider + " · " + category)
                .setView(sv)
                .setPositiveButton("Add fare", (d, w) -> cabFareDialog(route, provider, category))
                .setNeutralButton("Clear history", (d, w) -> {
                    EdithCabs.deleteOption(this, route.optString("id"), provider, category);
                    showCabRoute(route.optString("id"));
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void cabFareDialog(final JSONObject route, String provider, String category) {
        LinearLayout form = vertical();
        form.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText prov = styledInput("Provider, e.g. Uber, Ola, Rapido");
        prov.setSingleLine(true);
        prov.setText(provider != null ? provider : cabLastProvider);
        final EditText cat = styledInput("Category, e.g. Mini");
        cat.setSingleLine(true);
        cat.setText(category != null ? category : cabCategory);
        final EditText fare = styledInput("Fare in ₹ (the lower figure if it's a range)");
        fare.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText fareMax = styledInput("Upper figure, if the app shows a range (optional)");
        fareMax.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText fees = styledInput("Extra fees not in that price, ₹ (optional)");
        fees.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final android.widget.CheckBox est = new android.widget.CheckBox(this);
        est.setText("This is an estimate, not a confirmed price");
        est.setTextColor(primaryText);
        form.addView(prov, lp(-1, -2));
        form.addView(cat, margins(-1, -2, 0, 8, 0, 0));
        form.addView(fare, margins(-1, -2, 0, 8, 0, 0));
        form.addView(fareMax, margins(-1, -2, 0, 8, 0, 0));
        form.addView(fees, margins(-1, -2, 0, 8, 0, 0));
        form.addView(est, margins(-1, -2, 0, 8, 0, 0));
        ScrollView sv = new ScrollView(this);
        sv.addView(form);
        new AlertDialog.Builder(this)
                .setTitle("Add a fare")
                .setMessage("Type what the provider's app shows for this route. It is saved as entered by you.")
                .setView(sv)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String pv = prov.getText().toString().trim();
                    String ct = cat.getText().toString().trim();
                    Double f = EdithShopping.parseNumber(fare.getText().toString());
                    if (pv.isEmpty() || ct.isEmpty()) {
                        toast("Enter a provider and a category.");
                        return;
                    }
                    if (f == null) {
                        toast("Enter a fare greater than zero.");
                        return;
                    }
                    double max = 0;
                    String maxRaw = fareMax.getText().toString().trim();
                    if (!maxRaw.isEmpty()) {
                        Double m = EdithShopping.parseNumber(maxRaw);
                        if (m == null || m < f) {
                            toast("The upper figure must be at least the lower one.");
                            return;
                        }
                        max = m;
                    }
                    double fee = 0;
                    String feeRaw = fees.getText().toString().trim();
                    if (!feeRaw.isEmpty()) {
                        Double fv = EdithShopping.parseNumber(feeRaw);
                        if (fv == null && !feeRaw.matches("[0.]+")) {
                            toast("Enter a valid fee amount.");
                            return;
                        }
                        fee = fv == null ? 0 : fv;
                    }
                    try {
                        JSONObject saved = EdithCabs.recordFare(this, route.optString("id"), pv, ct, f, max, fee,
                                est.isChecked() ? EdithCabs.KIND_ESTIMATE : EdithCabs.KIND_QUOTE,
                                route.optString("when"));
                        cabLastProvider = pv;
                        double target = EdithCabs.targetOf(route);
                        boolean hit = target > 0 && EdithCabs.totalOf(saved) <= target;
                        logCard(null, CARD_CAB, "Add a fare: " + pv + " " + ct + ", "
                                        + route.optString("pickup") + " → " + route.optString("drop"),
                                EdithCabs.fareText(saved) + " (" + EdithCabs.kindLabel(saved.optString("kind"))
                                        + ", entered by you). Total " + EdithShopping.money(EdithCabs.totalOf(saved))
                                        + "." + (hit ? " Target fare reached." : ""));
                        showCabRoute(route.optString("id"));
                        if (hit) cabTargetReached(route, saved);
                    } catch (Exception e) {
                        toast("Couldn't save: " + (e.getMessage() == null ? "unknown error" : e.getMessage()));
                    }
                })
                .show();
    }

    private void cabTargetReached(JSONObject route, JSONObject obs) {
        new AlertDialog.Builder(this)
                .setTitle("Target fare reached")
                .setMessage(obs.optString("prov") + " " + obs.optString("cat") + ": "
                        + EdithShopping.money(EdithCabs.totalOf(obs)) + " (" + EdithCabs.kindLabel(obs.optString("kind"))
                        + " you entered), at or below your target of "
                        + EdithShopping.money(EdithCabs.targetOf(route))
                        + ".\n\nEDITH can't book or confirm rides. Check the price in the provider's app.")
                .setPositiveButton("OK", null)
                .show();
        EdithCabs.notifyTarget(this, route, obs);
        askNotificationPermission();
    }

    private void cabTargetDialog(final JSONObject route) {
        final EditText input = styledInput("Total fare in ₹, e.g. 150 (empty to clear)");
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        double current = EdithCabs.targetOf(route);
        if (current > 0) input.setText(String.valueOf((long) current));
        new AlertDialog.Builder(this)
                .setTitle("Target fare")
                .setMessage("EDITH alerts you when a fare you log for this route (fees included) is at or below this.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String raw = input.getText().toString().trim();
                    Double v = raw.isEmpty() ? Double.valueOf(0) : EdithShopping.parseNumber(raw);
                    if (v == null) {
                        toast("Enter a fare greater than zero.");
                        return;
                    }
                    EdithCabs.setTarget(this, route.optString("id"), v);
                    logCard(null, CARD_CAB, "Target fare: " + route.optString("pickup") + " → "
                                    + route.optString("drop"),
                            v > 0 ? "Alert when a fare you log is at or below " + EdithShopping.money(v) + "."
                                    : "Target fare cleared.");
                    askNotificationPermission();
                    showCabRoute(route.optString("id"));
                })
                .show();
    }

    private void showCreativeTools() {
        screen = "TOOLS";
        clearContent();
        LinearLayout page = addPage();
        addTitle(page, "Creative tools",
                "Create images, transform a picture, or save EDITH's answers as a PDF.");

        page.addView(sectionLabel("AI IMAGE STUDIO"), margins(-1, -2, 0, 0, 0, 8));
        LinearLayout imageCard = card(16);
        imageCard.addView(heading("Generate an image", 16));
        imageCard.addView(text("Describe the image you want EDITH to create.",
                12, secondaryText), margins(-1, -2, 0, 12, 0, 12));
        imageCard.addView(button("✨  Generate image", STYLE_PRIMARY, v -> promptForImageGeneration()),
                lp(-1, -2));
        imageCard.addView(button("🪄  Edit an existing image", STYLE_SECONDARY, v -> pickImageToEdit()),
                margins(-1, -2, 0, 10, 0, 0));
        page.addView(imageCard, margins(-1, -2, 0, 0, 0, 14));

        LinearLayout keyCard = card(14);
        keyCard.addView(heading("Image service connection", 14));
        keyCard.addView(text(getPollinationsKey().isEmpty()
                        ? "No key saved (generation still works)."
                        : "Pollinations API key saved on this device.",
                12, secondaryText), margins(-1, -2, 0, 8, 0, 10));
        keyCard.addView(text("Image generation works without a key (free, rate-limited). Image editing needs a key. Prompts are sent to Pollinations.",
                11, secondaryText), margins(-1, -2, 0, 0, 0, 10));
        LinearLayout keyButtons = horizontal();
        keyButtons.addView(button("Set image key", STYLE_SECONDARY, v -> editPollinationsKey()),
                tileParams(0, 6));
        keyButtons.addView(button("Remove key", STYLE_GHOST, v -> {
            prefs().edit().remove(POLLINATIONS_KEY).apply();
            showCreativeTools();
        }), tileParams(6, 0));
        keyCard.addView(keyButtons, lp(-1, -2));
        page.addView(keyCard, margins(-1, -2, 0, 0, 0, 20));

        page.addView(sectionLabel("PDF MAKER"), margins(-1, -2, 0, 0, 0, 8));
        LinearLayout pdfCard = card(16);
        pdfCard.addView(heading("Export to PDF", 16));
        pdfCard.addView(text("Save the latest EDITH answer, the current conversation, or text you enter as a PDF in Downloads/EDITH.",
                12, secondaryText), margins(-1, -2, 0, 12, 0, 12));
        pdfCard.addView(button("Save latest answer as PDF", STYLE_PRIMARY,
                v -> createPdfFromLatestReply()), lp(-1, -2));
        pdfCard.addView(button("Save current chat as PDF", STYLE_SECONDARY,
                v -> createPdfFromCurrentChat()), margins(-1, -2, 0, 8, 0, 8));
        pdfCard.addView(button("Make a PDF from a topic", STYLE_GHOST,
                v -> showCustomPdfDialog()), lp(-1, -2));
        page.addView(pdfCard, lp(-1, -2));
    }

    private String getPollinationsKey() {
        return prefs().getString(POLLINATIONS_KEY, "").trim();
    }

    private void editPollinationsKey() {
        EditText input = styledInput("Pollinations API key");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setText(getPollinationsKey());
        new AlertDialog.Builder(this)
                .setTitle("Image service key")
                .setMessage("This is separate from your Ollama key. Get a key from Pollinations' official site and check its current pricing/usage limits before generating.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String key = input.getText().toString().trim();
                    if (key.isEmpty()) {
                        toast("Enter a key, or use Remove key.");
                        return;
                    }
                    prefs().edit().putString(POLLINATIONS_KEY, key).apply();
                    showCreativeTools();
                })
                .show();
    }

    private void promptForImageGeneration() {
        EditText prompt = styledInput("Describe the image in detail…");
        prompt.setMinLines(3);
        prompt.setGravity(Gravity.TOP);
        new AlertDialog.Builder(this)
                .setTitle("Generate image")
                .setView(prompt)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Generate", (d, w) -> {
                    String value = prompt.getText().toString().trim();
                    if (value.isEmpty()) {
                        toast("Enter an image description first.");
                    } else {
                        generateImage(value);
                    }
                })
                .show();
    }

    // ============================================================
    // Card activity in Chats
    // ============================================================
    private static final String CARD_IMAGE = "🎨 Image generation";
    private static final String CARD_EDIT = "🎨 Image editing";
    private static final String CARD_PDF = "📄 PDF maker";
    private static final String CARD_SHOP = "🛒 Smart shopping";
    private static final String CARD_CAB = "🚕 Cab fares";

    /**
     * Records what a card did, so it shows up in Chats. If convId is a chat that still exists
     * (the card was started from that chat), the result is added to it. Otherwise the card gets
     * its own chat, created on first use and added to on every later use. UI thread only.
     */
    private void logCard(String convId, String card, String request, String result) {
        try {
            JSONObject conv = convId == null ? null : findConversation(convId);
            if (conv == null) {
                for (JSONObject c : conversations) {
                    if (card.equals(c.optString("card"))) {
                        conv = c;
                        break;
                    }
                }
                if (conv == null) {
                    conv = newConversation("");
                    put(conv, "title", card);
                    put(conv, "titled", true);
                    put(conv, "card", card);
                    conversations.add(0, conv);
                }
                appendMessageNoSave(conv, "user", request, "");
            }
            appendMessageNoSave(conv, "assistant", result, "NOTE");
            saveConversations();
            if (conv.optString("id").equals(activeConversationId) && "CHAT".equals(screen)) {
                refreshChatView();
            }
        } catch (Exception ignored) {
        }
    }

    private void generateImage(String prompt) {
        generateImage(prompt, null);
    }

    private void generateImage(final String prompt, final String originConvId) {
        toast("Generating image… this may take a little while.");
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String encoded = URLEncoder.encode(prompt, "UTF-8").replace("+", "%20");
                // No key saved: use the free keyless endpoint (tested working). Key saved: use the keyed one.
                String key = getPollinationsKey();
                URL url = key.isEmpty()
                        ? new URL("https://image.pollinations.ai/prompt/" + encoded)
                        : new URL("https://gen.pollinations.ai/image/" + encoded
                                + "?model=black-forest-labs/flux.1-schnell&width=1024&height=1024");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(120000);
                if (!key.isEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer " + key);
                }
                connection.setRequestProperty("Accept", "image/*, application/json");
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) {
                    String detail = readHttpError(connection);
                    throw new IllegalStateException("Image service returned HTTP " + status
                            + (detail.isEmpty() ? "" : ": " + detail));
                }
                byte[] bytes;
                String type = connection.getContentType();
                if (type != null && type.toLowerCase(Locale.ROOT).contains("json")) {
                    bytes = extractImageResponse(connection.getInputStream());
                } else {
                    bytes = readAll(connection.getInputStream(), 20 * 1024 * 1024);
                }
                if (bytes == null || bytes.length == 0) throw new IllegalStateException("The service returned an empty image.");
                Uri saved = saveImageToGallery(bytes, "edith_generated");
                runOnUiThread(() -> {
                    toast("Image saved to Pictures/EDITH.");
                    logCard(originConvId, CARD_IMAGE, "Generate an image: " + prompt,
                            "Image created and saved to Pictures/EDITH.");
                    new AlertDialog.Builder(this)
                            .setTitle("Image created")
                            .setMessage("EDITH saved the generated image to Pictures/EDITH.")
                            .setPositiveButton("OK", null)
                            .setNegativeButton("Open", (d2, w2) -> {
                                try {
                                    startActivity(new Intent(Intent.ACTION_VIEW)
                                            .setDataAndType(saved, "image/*")
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                                } catch (Exception ignored) {
                                    toast("No app found to open the image.");
                                }
                            })
                            .show();
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Unknown error" : e.getMessage();
                runOnUiThread(() -> logCard(originConvId, CARD_IMAGE, "Generate an image: " + prompt,
                        "Image generation failed: " + message));
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("Image generation failed")
                        .setMessage(message + "\n\nCheck your internet connection. HTTP 429 means wait a minute (free limit); 401 means the service now needs a key.")
                        .setPositiveButton("OK", null)
                        .show());
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void pickImageToEdit() {
        if (getPollinationsKey().isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Connect image editing")
                    .setMessage("Set a Pollinations API key first. Image editing sends the chosen image and your edit prompt to the service.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Set key", (d, w) -> editPollinationsKey())
                    .show();
            return;
        }
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.setType("image/*");
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(pick, PICK_IMAGE_EDIT);
    }

    private void promptForImageEdit(Uri imageUri) {
        EditText prompt = styledInput("Example: turn this into a watercolor illustration");
        prompt.setMinLines(3);
        prompt.setGravity(Gravity.TOP);
        new AlertDialog.Builder(this)
                .setTitle("How should the image change?")
                .setMessage("Choose a clear edit, such as changing the background, making it a painting, or adjusting the lighting.")
                .setView(prompt)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Edit image", (d, w) -> {
                    String instruction = prompt.getText().toString().trim();
                    if (instruction.isEmpty()) {
                        toast("Describe the change you want.");
                    } else {
                        editImage(imageUri, instruction);
                    }
                })
                .show();
    }

    private void editImage(Uri imageUri, String prompt) {
        toast("Editing image… this may take a little while.");
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                byte[] source = readUriBytes(imageUri, 15 * 1024 * 1024);
                String mime = getContentResolver().getType(imageUri);
                if (mime == null || !mime.startsWith("image/")) mime = "image/jpeg";
                String boundary = "----EDITH" + UUID.randomUUID().toString().replace("-", "");
                URL url = new URL("https://gen.pollinations.ai/v1/images/edits");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(120000);
                connection.setRequestProperty("Authorization", "Bearer " + getPollinationsKey());
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                try (DataOutputStream out = new DataOutputStream(connection.getOutputStream())) {
                    writeMultipartField(out, boundary, "model", "black-forest-labs/flux.1-kontext-pro");
                    writeMultipartField(out, boundary, "prompt", prompt);
                    out.writeBytes("--" + boundary + "\r\n");
                    out.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"edith-source\"\r\n");
                    out.writeBytes("Content-Type: " + mime + "\r\n\r\n");
                    out.write(source);
                    out.writeBytes("\r\n--" + boundary + "--\r\n");
                    out.flush();
                }
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) {
                    String detail = readHttpError(connection);
                    throw new IllegalStateException("Image edit returned HTTP " + status
                            + (detail.isEmpty() ? "" : ": " + detail));
                }
                String type = connection.getContentType();
                byte[] result;
                if (type != null && type.toLowerCase(Locale.ROOT).contains("json")) {
                    result = extractImageResponse(connection.getInputStream());
                } else {
                    result = readAll(connection.getInputStream(), 20 * 1024 * 1024);
                }
                if (result == null || result.length == 0) throw new IllegalStateException("The service returned no edited image.");
                saveImageToGallery(result, "edith_edited");
                runOnUiThread(() -> logCard(null, CARD_EDIT, "Edit an image: " + prompt,
                        "Edited image saved to Pictures/EDITH. The original was not changed."));
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("Image edited")
                        .setMessage("The edited image was saved to Pictures/EDITH. Your original image was not changed.")
                        .setPositiveButton("OK", null).show());
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Unknown error" : e.getMessage();
                runOnUiThread(() -> logCard(null, CARD_EDIT, "Edit an image: " + prompt,
                        "Image editing failed: " + message));
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("Image editing failed")
                        .setMessage(message + "\n\nCheck the service key, supported model, and current API limits.")
                        .setPositiveButton("OK", null).show());
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void writeMultipartField(DataOutputStream out, String boundary, String name, String value)
            throws Exception {
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        out.write(value.getBytes("UTF-8"));
        out.writeBytes("\r\n");
    }

    private byte[] readUriBytes(Uri uri, int limit) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("Couldn't open the selected image.");
            return readAll(in, limit);
        }
    }

    private byte[] readAll(InputStream in, int limit) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            if (out.size() + n > limit) throw new IllegalStateException("File is too large for this operation.");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private byte[] extractImageResponse(InputStream in) throws Exception {
        byte[] payload = readAll(in, 25 * 1024 * 1024);
        String jsonText = new String(payload, "UTF-8");
        JSONObject json = new JSONObject(jsonText);
        JSONArray data = json.optJSONArray("data");
        JSONObject first = data == null ? null : data.optJSONObject(0);
        if (first == null) throw new IllegalStateException("The image API returned an unexpected JSON response.");
        String b64 = first.optString("b64_json", "");
        if (!b64.isEmpty()) return android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
        String imageUrl = first.optString("url", "");
        if (imageUrl.isEmpty()) throw new IllegalStateException("The image API response did not contain an image.");
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(imageUrl).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(60000);
            return readAll(c.getInputStream(), 20 * 1024 * 1024);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private String readHttpError(HttpURLConnection connection) {
        try (InputStream in = connection.getErrorStream()) {
            if (in == null) return "";
            return new String(readAll(in, 4096), "UTF-8");
        } catch (Exception ignored) {
            return "";
        }
    }

    private Uri saveImageToGallery(byte[] bytes, String prefix) throws Exception {
        String extension = "jpg";
        String mime = "image/jpeg";
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47) {
            extension = "png";
            mime = "image/png";
        } else if (bytes.length >= 12 && bytes[0] == 0x52 && bytes[1] == 0x49
                && bytes[2] == 0x46 && bytes[3] == 0x46
                && bytes[8] == 0x57 && bytes[9] == 0x45
                && bytes[10] == 0x42 && bytes[11] == 0x50) {
            extension = "webp";
            mime = "image/webp";
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME,
                prefix + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())
                        + "." + extension);
        values.put(MediaStore.Images.Media.MIME_TYPE, mime);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/EDITH");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Uri uri = getContentResolver().insert(collection, values);
        if (uri == null) throw new IllegalStateException("Couldn't create an image in the gallery.");
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("Couldn't write the image.");
            out.write(bytes);
            out.flush();
        } catch (Exception e) {
            getContentResolver().delete(uri, null, null);
            throw e;
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            ContentValues done = new ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(uri, done, null, null);
        }
        return uri;
    }

    private void createPdfFromLatestReply() {
        JSONObject conv = activeConversation();
        if (conv == null) {
            toast("Open a chat with an EDITH answer first.");
            return;
        }
        JSONArray messages = messagesOf(conv);
        for (int i = messages.length() - 1; i >= 0; i--) {
            JSONObject message = messages.optJSONObject(i);
            if (message != null && "assistant".equals(message.optString("role"))) {
                String body = message.optString("text", "").trim();
                if (!body.isEmpty()) {
                    writePdfPromptDialog(conv.optString("title", "EDITH answer"), body);
                    return;
                }
            }
        }
        toast("There isn't an EDITH answer to export yet.");
    }

    private void createPdfFromCurrentChat() {
        JSONObject conv = activeConversation();
        if (conv == null) {
            toast("Open a conversation first.");
            return;
        }
        JSONArray messages = messagesOf(conv);
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) continue;
            String role = "user".equals(message.optString("role")) ? "You" : "EDITH";
            String text = message.optString("text", "").trim();
            if (!text.isEmpty()) {
                body.append(role).append(":\n").append(text).append("\n\n");
            }
        }
        if (body.length() == 0) {
            toast("This conversation is empty.");
            return;
        }
        writePdfPromptDialog(conv.optString("title", "EDITH chat"), body.toString());
    }

    private void showCustomPdfDialog() {
        LinearLayout form = vertical();
        final EditText topicInput = styledInput("Topic, e.g. How solar panels work");
        topicInput.setSingleLine(true);
        final EditText detailsInput = styledInput("Anything specific to include (optional)");
        detailsInput.setMinLines(2);
        detailsInput.setGravity(Gravity.TOP);
        form.addView(topicInput, margins(-1, -2, 0, 0, 0, 8));
        form.addView(detailsInput, lp(-1, -2));
        new AlertDialog.Builder(this)
                .setTitle("What is the PDF about?")
                .setMessage("EDITH will write the document with AI and save it to Downloads/EDITH.")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Use my own text", (d, w) -> showManualPdfDialog())
                .setPositiveButton("Write PDF", (d, w) -> {
                    String topic = topicInput.getText().toString().trim();
                    if (topic.isEmpty()) {
                        toast("Enter a topic first.");
                        return;
                    }
                    generateTopicPdf(topic, detailsInput.getText().toString().trim());
                })
                .show();
    }

    private void generateTopicPdf(final String topic, final String details) {
        if (!isReady()) {
            new AlertDialog.Builder(this)
                    .setTitle("No AI model ready")
                    .setMessage("EDITH needs a model to write the PDF. Load one in Models or add a cloud key, "
                            + "or choose \"Use my own text\".")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        toast("EDITH is writing about \"" + truncate(topic, 40) + "\"… the PDF is saved when it's done.");
        final String ask = "Write a clear, well-organized document about: " + topic
                + (details.isEmpty() ? "" : ". Make sure it covers: " + details)
                + ". Use plain text only: short section headings on their own lines, then paragraphs. "
                + "Do not use markdown symbols, tables or code blocks.";
        executor.execute(() -> {
            String body = null;
            String error = null;
            try {
                body = askModel(PDF_WRITER_SYSTEM, ask).replaceAll("[#*`]", "").trim();
                if (body.isEmpty()) error = "The AI returned no text.";
            } catch (Throwable t) {
                error = safeError(t);
            }
            final String finalBody = body;
            final String finalError = error;
            final String title = truncate(topic.substring(0, 1).toUpperCase(Locale.ROOT) + topic.substring(1), 60);
            runOnUiThread(() -> {
                if (finalError != null) {
                    logCard(null, CARD_PDF, "Create a PDF about: " + topic, "Couldn't write it: " + finalError);
                    new AlertDialog.Builder(this)
                            .setTitle("PDF not created")
                            .setMessage("EDITH couldn't write the document: " + finalError)
                            .setPositiveButton("OK", null)
                            .show();
                } else {
                    writePdf(title, finalBody);
                }
            });
        });
    }

    private void showManualPdfDialog() {
        LinearLayout form = vertical();
        EditText titleInput = styledInput("PDF title");
        EditText bodyInput = styledInput("Paste or type the content for the PDF…");
        bodyInput.setMinLines(7);
        bodyInput.setGravity(Gravity.TOP);
        form.addView(titleInput, margins(-1, -2, 0, 0, 0, 8));
        form.addView(bodyInput, lp(-1, -2));
        new AlertDialog.Builder(this)
                .setTitle("Create PDF from text/topic")
                .setMessage("Enter the title and the content. For AI-written notes on a topic, ask EDITH in chat first, then export the answer.")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create PDF", (d, w) -> {
                    String title = titleInput.getText().toString().trim();
                    String body = bodyInput.getText().toString().trim();
                    if (body.isEmpty()) toast("Enter the PDF content first.");
                    else writePdf(title.isEmpty() ? "EDITH notes" : title, body);
                })
                .show();
    }

    private void writePdfPromptDialog(String defaultTitle, String body) {
        EditText titleInput = styledInput("PDF title");
        titleInput.setText(defaultTitle);
        new AlertDialog.Builder(this)
                .setTitle("Save as PDF")
                .setMessage("Choose a title for the PDF file.")
                .setView(titleInput)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save PDF", (d, w) -> {
                    String title = titleInput.getText().toString().trim();
                    writePdf(title.isEmpty() ? "EDITH document" : title, body);
                })
                .show();
    }

    private void writePdf(String title, String body) {
        toast("Creating PDF…");
        executor.execute(() -> {
            PdfDocument document = new PdfDocument();
            try {
                Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                titlePaint.setColor(Color.rgb(20, 35, 40));
                titlePaint.setTextSize(22f);
                titlePaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                bodyPaint.setColor(Color.rgb(35, 45, 50));
                bodyPaint.setTextSize(11.5f);
                int pageWidth = 595;
                int pageHeight = 842;
                int margin = 48;
                int pageNo = 1;
                PdfDocument.Page page = null;
                Canvas canvas = null;
                float y = 0;
                try {
                    page = startPdfPage(document, pageNo, pageWidth, pageHeight);
                    canvas = page.getCanvas();
                    y = margin;
                    canvas.drawText(title, margin, y + 20, titlePaint);
                    y += 48;
                    String[] paragraphs = body.replace("\r", "").split("\n", -1);
                    for (String paragraph : paragraphs) {
                        if (paragraph.trim().isEmpty()) {
                            y += 10;
                        } else {
                            String remaining = paragraph;
                            while (!remaining.isEmpty()) {
                                int fit = bodyPaint.breakText(remaining, true,
                                        pageWidth - margin * 2, null);
                                if (fit <= 0) fit = Math.min(1, remaining.length());
                                String line = remaining.substring(0, fit);
                                if (y + 18 > pageHeight - margin) {
                                    drawPdfFooter(canvas, pageNo, pageWidth, pageHeight);
                                    document.finishPage(page);
                                    pageNo++;
                                    page = startPdfPage(document, pageNo, pageWidth, pageHeight);
                                    canvas = page.getCanvas();
                                    y = margin;
                                }
                                canvas.drawText(line, margin, y + 12, bodyPaint);
                                y += 17;
                                remaining = remaining.substring(fit).replaceFirst("^\\s+", "");
                            }
                        }
                    }
                    drawPdfFooter(canvas, pageNo, pageWidth, pageHeight);
                    document.finishPage(page);
                    page = null;
                } finally {
                    // If page generation throws, close the current page before cleanup.
                    if (page != null) {
                        try { document.finishPage(page); } catch (Exception ignored) {}
                    }
                }
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                document.writeTo(bytes);
                savePdfToDownloads(title, bytes.toByteArray());
                runOnUiThread(() -> logCard(null, CARD_PDF, "Create a PDF: " + title,
                        "PDF saved to Downloads/EDITH."));
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("PDF created")
                        .setMessage("Saved to Downloads/EDITH.")
                        .setPositiveButton("OK", null).show());
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Unknown error" : e.getMessage();
                runOnUiThread(() -> logCard(null, CARD_PDF, "Create a PDF: " + title,
                        "PDF creation failed: " + message));
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("PDF creation failed")
                        .setMessage(message)
                        .setPositiveButton("OK", null).show());
            } finally {
                document.close();
            }
        });
    }

    private PdfDocument.Page startPdfPage(PdfDocument document, int pageNumber,
                                           int width, int height) {
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(width, height, pageNumber).create();
        PdfDocument.Page page = document.startPage(info);
        page.getCanvas().drawColor(Color.WHITE);
        return page;
    }

    private void drawPdfFooter(Canvas canvas, int pageNumber, int width, int height) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.GRAY);
        p.setTextSize(9f);
        canvas.drawText("Created with EDITH  ·  " + pageNumber, 48, height - 24, p);
    }

    private void savePdfToDownloads(String title, byte[] bytes) throws Exception {
        String safeName = title.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (safeName.isEmpty()) safeName = "EDITH document";
        if (!safeName.toLowerCase(Locale.ROOT).endsWith(".pdf")) safeName += ".pdf";
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, safeName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/EDITH");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        }
        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("Couldn't create a PDF in Downloads.");
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("Couldn't write the PDF.");
            out.write(bytes);
            out.flush();
        } catch (Exception e) {
            getContentResolver().delete(uri, null, null);
            throw e;
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            getContentResolver().update(uri, done, null, null);
        }
    }

    // ============================================================
    // Models screen
    // ============================================================

    private TextView segment(String label, boolean selected, View.OnClickListener action) {
        TextView s = new TextView(this);
        s.setText(label);
        s.setGravity(Gravity.CENTER);
        s.setTextSize(12);
        s.setTypeface(edithFont(Typeface.BOLD));
        s.setTextColor(selected ? bg : secondaryText);
        s.setBackground(selected ? box(accent, 0, 12) : null);
        s.setOnClickListener(action);
        return s;
    }

    private void showModels() {
        screen = "MODELS";
        clearContent();
        LinearLayout page = addPage();
        addTitle(page, "Models", "Choose how EDITH answers and manage the on-device model.");

        page.addView(sectionLabel("RESPONSE MODE"), margins(-1, -2, 0, 0, 0, 8));
        LinearLayout seg = horizontal();
        seg.setPadding(dp(4), dp(4), dp(4), dp(4));
        seg.setBackground(box(surface, border, 16));
        seg.addView(segment("LOCAL", "LOCAL".equals(aiMode), v -> setMode("LOCAL")),
                new LinearLayout.LayoutParams(0, dp(40), 1));
        seg.addView(segment("ONLINE", "ONLINE".equals(aiMode), v -> setMode("ONLINE")),
                new LinearLayout.LayoutParams(0, dp(40), 1));
        seg.addView(segment("AUTO", "AUTO".equals(aiMode), v -> setMode("AUTO")),
                new LinearLayout.LayoutParams(0, dp(40), 1));
        page.addView(seg, lp(-1, -2));
        page.addView(text(
                "LOCAL uses only the on-device model. ONLINE uses Ollama Cloud, which may use "
                        + "paid credits. AUTO tries the cloud and falls back to the local model. "
                        + "Screenshots and PDFs always use the cloud.",
                11, secondaryText), margins(-1, -2, 4, 8, 4, 22));

        page.addView(sectionLabel("ON-DEVICE MODEL"), margins(-1, -2, 0, 0, 0, 8));
        boolean exists = modelFile.exists();
        LinearLayout local = card(16);
        local.addView(heading(exists ? modelFile.getName() : "No model imported", 15));
        local.addView(text(exists ? formatBytes(modelFile.length())
                        : "Import a GGUF file to run EDITH on this device.",
                11, secondaryText), margins(-1, -2, 0, 4, 0, 0));

        String state = !nativeAvailable ? "Engine unavailable"
                : modelReady ? "●  Loaded and ready" : "○  Not loaded";
        TextView stateView = text(state, 12, modelReady ? readyGreen : secondaryText);
        local.addView(stateView, margins(-1, -2, 0, 10, 0, 0));
        page.addView(local, lp(-1, -2));

        page.addView(button("⬆  Import model file", STYLE_PRIMARY, v -> importModel()),
                margins(-1, -2, 0, 10, 0, 8));

        LinearLayout ops = horizontal();
        ops.addView(button("Load", STYLE_SECONDARY, v -> {
            if (!nativeAvailable) toast("Native engine unavailable.");
            else if (!modelFile.exists()) toast("Import a model first.");
            else if (modelReady) toast("A model is already loaded.");
            else loadModel(modelFile);
        }), tileParams(0, 6));
        ops.addView(button("Unload", STYLE_GHOST, v -> {
            if (!modelReady) toast("No model is loaded.");
            else unloadModel();
        }), tileParams(6, 0));
        page.addView(ops, margins(-1, -2, 0, 0, 0, 24));

        page.addView(sectionLabel("OLLAMA CLOUD"), margins(-1, -2, 0, 0, 0, 8));
        LinearLayout cloud = card(16);
        cloud.addView(heading(getOllamaKey().isEmpty() ? "No API key set"
                : "API key saved on this device", 15));
        cloud.addView(text("Model  ·  " + OLLAMA_MODEL + "   Vision  ·  " + VISION_MODEL,
                        11, secondaryText),
                margins(-1, -2, 0, 4, 0, 0));
        page.addView(cloud, margins(-1, -2, 0, 0, 0, 10));

        LinearLayout keyOps = horizontal();
        keyOps.addView(button("Set key", STYLE_SECONDARY, v -> editOllamaKey()), tileParams(0, 6));
        keyOps.addView(button("Remove key", STYLE_DANGER, v -> {
            prefs().edit().remove(OLLAMA_KEY).apply();
            refreshBadge();
            showModels();
        }), tileParams(6, 0));
        page.addView(keyOps, lp(-1, -2));

        page.addView(button("Check phone features", STYLE_GHOST, v -> showDeviceCheck()),
                margins(-1, -2, 0, 10, 0, 0));

        page.addView(sectionLabel("FEEDBACK"), margins(-1, -2, 0, 22, 0, 8));
        page.addView(settingRow("Typing sound",
                        "A soft mechanical click as you type",
                        isTypingSoundOn(), v -> {
                            setFlag(TYPING_SOUND_KEY, !isTypingSoundOn());
                            showModels();
                        }),
                lp(-1, -2));
    }

    private void setMode(String mode) {
        aiMode = mode;
        prefs().edit().putString(MODE_KEY, aiMode).apply();
        if (("ONLINE".equals(mode) || "AUTO".equals(mode)) && getOllamaKey().isEmpty()) {
            editOllamaKey();
        }
        refreshBadge();
        showModels();
    }

    private void editOllamaKey() {
        LinearLayout form = vertical();
        form.setPadding(dp(4), dp(8), dp(4), 0);

        TextView note = text("Cloud requests may use paid credits. The key is stored only in "
                + "this app's private preferences on this device.", 12, secondaryText);
        form.addView(note, margins(-1, -2, 0, 0, 0, 10));

        EditText input = styledInput("Paste Ollama API key");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setText(getOllamaKey());
        form.addView(input, margins(-1, -2, 0, 0, 0, 4));

        new AlertDialog.Builder(this)
                .setTitle("Ollama Cloud key")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Remove", (d, w) -> {
                    prefs().edit().remove(OLLAMA_KEY).apply();
                    refreshBadge();
                    refreshScreen();
                })
                .setPositiveButton("Save", (d, w) -> {
                    prefs().edit().putString(OLLAMA_KEY, input.getText().toString().trim()).apply();
                    refreshBadge();
                    refreshScreen();
                })
                .show();
    }

    // ============================================================
    // Local model: import, copy, load, unload
    // ============================================================

    private void importModel() {
        if (!nativeAvailable) {
            toast("Native engine unavailable on this device.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*"); // GGUF has no standard MIME type, so the name is checked after picking
        startActivityForResult(intent, PICK_MODEL);
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private long contentSize(Uri uri) {
        try (Cursor cursor = getContentResolver().query(
                uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index);
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private void copyModel(Uri uri) {
        toast("Copying model…");
        executor.execute(() -> {
            File temp = new File(getFilesDir(), "edith-model.tmp");
            try {
                long free = new StatFs(getFilesDir().getAbsolutePath()).getAvailableBytes();
                long size = contentSize(uri);
                if (size > 0 && free < size + 150_000_000L) {
                    toastOnUi("Not enough storage. Free up space and try again.");
                    return;
                }

                try (InputStream input = getContentResolver().openInputStream(uri);
                     FileOutputStream output = new FileOutputStream(temp)) {
                    if (input == null) throw new IllegalStateException("Could not open selected file.");
                    byte[] buffer = new byte[1024 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    output.getFD().sync();
                }
            } catch (Exception error) {
                android.util.Log.e("EDITH", "Model copy failed", error);
                temp.delete();
                toastOnUi("Model copy failed: " + error.getMessage());
                return;
            }

            if (modelReady) {
                try {
                    nativeUnloadModel();
                } catch (Throwable ignored) {
                }
                runOnUiThread(() -> modelReady = false);
            }

            if (modelFile.exists()) modelFile.delete();
            if (!temp.renameTo(modelFile)) {
                temp.delete();
                toastOnUi("Could not save model file.");
                return;
            }
            loadModelNow(modelFile);
        });
    }

    private void loadModel(File file) {
        toast("Loading local model…");
        executor.execute(() -> loadModelNow(file));
    }

    private void loadModelNow(File file) {
        String result;
        try {
            result = nativeLoadModel(file.getAbsolutePath());
        } catch (Throwable error) {
            android.util.Log.e("EDITH", "Model load failed", error);
            result = "ERROR: " + error.getMessage();
        }

        final String message = result == null ? "ERROR: No response from native engine." : result;
        runOnUiThread(() -> {
            modelReady = "MODEL LOADED".equals(message);
            refreshBadge();
            toast(modelReady ? "Local model loaded" : message);
            if ("MODELS".equals(screen)) showModels();
        });
    }

    private void unloadModel() {
        executor.execute(() -> {
            try {
                if (nativeAvailable) nativeUnloadModel();
            } catch (Throwable error) {
                android.util.Log.w("EDITH", "Unload failed", error);
            }
            runOnUiThread(() -> {
                modelReady = false;
                refreshBadge();
                toast("Model unloaded");
                if ("MODELS".equals(screen)) showModels();
            });
        });
    }

    private boolean checkNativeAvailable() {
        try {
            String info = nativeGetBackendInfo();
            return info != null && !info.isEmpty();
        } catch (Throwable error) {
            android.util.Log.e("EDITH", "Native backend check failed", error);
            return false;
        }
    }

    // ============================================================
    // Typing sound
    // ============================================================

    private void initKeySound() {
        try {
            File file = new File(getCacheDir(), "edith_key_click.wav");
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(buildClickWav());
            }
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            keySound = new SoundPool.Builder()
                    .setMaxStreams(4)
                    .setAudioAttributes(attrs)
                    .build();
            keySoundId = keySound.load(file.getAbsolutePath(), 1);
        } catch (Exception error) {
            android.util.Log.w("EDITH", "Typing sound unavailable", error);
            keySound = null;
        }
    }

    private byte[] buildClickWav() {
        int rate = 22050;
        int samples = rate * 45 / 1000;
        int dataLen = samples * 2;

        ByteBuffer buf = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes()).putInt(36 + dataLen).put("WAVE".getBytes());
        buf.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16);
        buf.put("data".getBytes()).putInt(dataLen);

        Random r = new Random(42);
        for (int i = 0; i < samples; i++) {
            double t = i / (double) rate;
            double attack = i < 20 ? i / 20.0 : 1.0;
            double env = Math.exp(-t * 140) * attack;
            double noise = r.nextDouble() * 2 - 1;
            double ring = Math.sin(2 * Math.PI * 2600 * t);
            double s = (0.75 * noise + 0.25 * ring) * env * 0.55;
            buf.putShort((short) (s * 32767));
        }
        return buf.array();
    }

    private void attachTypingSound(EditText input) {
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (count > before) playKeyClick();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void playKeyClick() {
        if (suppressKeySound || keySound == null || keySoundId == 0 || !isTypingSoundOn()) return;
        float rate = 0.95f + keyRandom.nextFloat() * 0.1f;
        keySound.play(keySoundId, 0.5f, 0.5f, 1, 0, rate);
    }

    // ============================================================
    // Voice-first: listening, speaking, interrupting, hands-free
    // ============================================================

    private void initTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS && textToSpeech != null) {
                int lang = textToSpeech.setLanguage(Locale.getDefault());
                ttsReady = lang != TextToSpeech.LANG_MISSING_DATA
                        && lang != TextToSpeech.LANG_NOT_SUPPORTED;
                textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        runOnUiThread(() -> setPresence("SPEAKING"));
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        runOnUiThread(() -> speechFinished());
                    }

                    @Override
                    public void onError(String utteranceId) {
                        runOnUiThread(() -> speechFinished());
                    }
                });
            }
        });
    }

    // Strips code blocks, markdown and links so replies sound natural when spoken.
    private String speakable(String body) {
        if (body == null) return "";
        return body.replaceAll("(?s)```.*?```", " Code omitted. ")
                .replaceAll("https?://\\S+", "link")
                .replaceAll("[*#`_>]+", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // Returns true when speech actually started.
    private boolean speak(String raw) {
        if (!ttsReady || textToSpeech == null) {
            toast("Text-to-speech is not ready on this device.");
            return false;
        }
        String t = speakable(raw);
        if (t.isEmpty()) return false;

        stopVoice();
        int max = Math.max(200, Math.min(3500, TextToSpeech.getMaxSpeechInputLength() - 100));
        if (t.length() > max) t = t.substring(0, max);

        textToSpeech.speak(t, TextToSpeech.QUEUE_FLUSH, null, "edith_reply");
        setPresence("SPEAKING");

        // If the speech engine never reports "done", reset the orb after a sensible time.
        long maxMs = 5000L + t.length() * 90L;
        if (speakWatchdog != null) handler.removeCallbacks(speakWatchdog);
        speakWatchdog = () -> {
            if ("SPEAKING".equals(presence)) {
                if (textToSpeech != null) textToSpeech.stop();
                speechFinished();
            }
        };
        handler.postDelayed(speakWatchdog, maxMs);
        return true;
    }

    private void speechFinished() {
        if (speakWatchdog != null) handler.removeCallbacks(speakWatchdog);
        if ("SPEAKING".equals(presence)) {
            setPresence(busyConversationId != null ? "THINKING" : "IDLE");
        }
        if (handsFree) listenSoon(350);
    }

    // Stops EDITH mid-sentence. In hands-free mode it goes straight back to listening.
    private void interruptSpeech() {
        if (speakWatchdog != null) handler.removeCallbacks(speakWatchdog);
        if (textToSpeech != null) textToSpeech.stop();
        setPresence("IDLE");
        if (handsFree) listenSoon(200);
    }

    private boolean ensureMic(boolean forHandsFree) {
        if (android.os.Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            permissionPending = true;
            pendingHandsFree = forHandsFree;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},
                    VOICE_PERMISSION_REQUEST);
            return false;
        }
        return true;
    }

    // Mic button: tap to talk, tap again to cancel. Speaks the reply.
    private void startVoice() {
        if ("SPEAKING".equals(presence)) {
            interruptSpeech();
            if (handsFree) return;
        } else if ("LISTENING".equals(presence)) {
            stopVoice();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Speech recognition is unavailable on this device.");
            return;
        }
        if (!ensureMic(false)) return;
        replyBySpeech = true;
        launchSpeech();
    }

    private void setHandsFree(boolean on) {
        if (on) {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                toast("Speech recognition is unavailable on this device.");
                return;
            }
            if (!ensureMic(true)) return;
            handsFree = true;
            silentRounds = 0;
            replyBySpeech = true;
            if (!"CHAT".equals(screen)) newChat(null);
            updateHandsFreeButton();
            setPresence(presence);
            toast("Hands-free on. Say \"stop\" to end.");
            if (busyConversationId == null && !"SPEAKING".equals(presence)) listenSoon(250);
        } else {
            handsFree = false;
            handler.removeCallbacksAndMessages(null);
            stopVoice();
            if (textToSpeech != null) textToSpeech.stop();
            setPresence("IDLE");
            updateHandsFreeButton();
        }
    }

    private void listenSoon(long delayMs) {
        handler.postDelayed(() -> {
            if (handsFree && "CHAT".equals(screen) && busyConversationId == null
                    && !"SPEAKING".equals(presence) && !"LISTENING".equals(presence)) {
                launchSpeech();
            }
        }, delayMs);
    }

    private void onSilence() {
        silentRounds++;
        if (silentRounds >= 3) {
            setHandsFree(false);
            toast("Hands-free paused. Tap the orb to start again.");
        } else {
            listenSoon(300);
        }
    }

    private void launchSpeech() {
        if (textToSpeech != null) textToSpeech.stop();
        stopVoice();
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                setPresence("LISTENING");
            }

            @Override
            public void onBeginningOfSpeech() {
                armListenWatchdog(40000);
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                orbLevel((rmsdB + 2f) / 12f);
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
                setVoiceStatus("Processing…");
            }

            @Override
            public void onError(int error) {
                stopVoice();
                boolean quiet = error == SpeechRecognizer.ERROR_NO_MATCH
                        || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT;
                if (handsFree) {
                    if (quiet) {
                        onSilence();
                    } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                        listenSoon(600);
                    } else {
                        toast("Voice input stopped.");
                        setHandsFree(false);
                    }
                } else if (quiet) {
                    toast("I didn't catch that.");
                }
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results == null ? null
                        : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String said = (matches == null || matches.isEmpty()) ? "" : matches.get(0).trim();
                stopVoice();
                handleHeard(said);
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                applyRecognized(partialResults);
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500);
        speechRecognizer.startListening(intent);
        armListenWatchdog(12000);
    }

    // If the recognizer never reports back, stop listening instead of leaving the orb stuck.
    private void armListenWatchdog(long ms) {
        if (listenWatchdog != null) handler.removeCallbacks(listenWatchdog);
        listenWatchdog = () -> {
            if (speechRecognizer == null) return;
            stopVoice();
            setPresence("IDLE");
            if (handsFree) onSilence();
            else toast("I didn't hear anything.");
        };
        handler.postDelayed(listenWatchdog, ms);
    }

    // A finished voice request is sent straight away and answered out loud.
    private void handleHeard(String said) {
        if (messageInput != null) {
            suppressKeySound = true;
            messageInput.setText("");
            suppressKeySound = false;
        }
        if (said.isEmpty()) {
            if (handsFree) onSilence();
            return;
        }
        silentRounds = 0;

        String low = said.toLowerCase(Locale.ROOT).replaceAll("[.!?,]", "").trim();
        if (handsFree && (low.equals("stop") || low.equals("stop listening")
                || low.equals("exit") || low.equals("goodbye")
                || low.equals("end hands free") || low.equals("end hands-free"))) {
            setHandsFree(false);
            toast("Hands-free off");
            return;
        }

        if (!"CHAT".equals(screen) || busyConversationId != null) return;
        replyBySpeech = true;
        sendPrompt(said, true);
    }

    // Live transcript while the person is still talking.
    private void applyRecognized(Bundle bundle) {
        if (bundle == null || messageInput == null) return;
        ArrayList<String> matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            String recognized = matches.get(0).trim();
            suppressKeySound = true;
            messageInput.setText(recognized);
            messageInput.setSelection(recognized.length());
            suppressKeySound = false;
        }
    }

    private void setVoiceStatus(String status) {
        if (voiceStatus != null) voiceStatus.setText(status == null ? "" : status);
    }

    private void stopVoice() {
        if (speechRecognizer != null) {
            try {
                speechRecognizer.cancel();
            } catch (Exception ignored) {
            }
            try {
                speechRecognizer.destroy();
            } catch (Exception ignored) {
            }
            speechRecognizer = null;
        }
        if (listenWatchdog != null) handler.removeCallbacks(listenWatchdog);
        orbLevel(0f);
        setVoiceStatus(null);
        if ("LISTENING".equals(presence)) setPresence("IDLE");
    }
}