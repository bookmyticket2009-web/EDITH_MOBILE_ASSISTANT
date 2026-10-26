package com.pocketai.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

// Drives WhatsApp for EDITH: search a contact, open the chat, type, and send.
// The user turns this on once in Settings > Accessibility.
public class EdithAccessibilityService extends AccessibilityService {

    static final String WHATSAPP = "com.whatsapp";
    private static final long TIMEOUT_MS = 30000;

    private static final int IDLE = 0;
    private static final int OPEN_SEARCH = 1;
    private static final int TYPE_NAME = 2;
    private static final int PICK_CHAT = 3;
    private static final int TYPE_MESSAGE = 4;
    private static final int SEND = 5;

    static volatile EdithAccessibilityService instance;
    private static volatile int stage = IDLE;
    private static volatile String taskContact = "";
    private static volatile String taskMessage = "";
    private static volatile long taskStarted = 0;
    private static volatile String lastResult = null;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable advanceRunnable = this::advance;

    private interface NodeMatch {
        boolean matches(AccessibilityNodeInfo node);
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
    }

    @Override
    public void onInterrupt() {
        // Nothing to clean up.
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    static boolean isEnabled() {
        return instance != null;
    }

    // Returns the last result once, so MainActivity can show it.
    static String takeResult() {
        String r = lastResult;
        lastResult = null;
        return r;
    }

    // Called by MainActivity before it opens WhatsApp.
    static void begin(String contact, String message, boolean isNumber) {
        taskContact = contact;
        taskMessage = message;
        taskStarted = System.currentTimeMillis();
        lastResult = null;
        // A phone number opens the chat with the text prefilled, so only Send is left.
        stage = isNumber ? SEND : OPEN_SEARCH;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (stage == IDLE || event == null || event.getPackageName() == null) return;
        if (!WHATSAPP.equals(event.getPackageName().toString())) return;
        handler.removeCallbacks(advanceRunnable);
        handler.postDelayed(advanceRunnable, 350); // let the screen settle first
    }

    private void advance() {
        if (stage == IDLE) return;

        if (System.currentTimeMillis() - taskStarted > TIMEOUT_MS) {
            finish("Couldn't finish in WhatsApp. Please try again.");
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        switch (stage) {
            case OPEN_SEARCH: {
                AccessibilityNodeInfo search = findByDescOrId(root, "Search",
                        "com.whatsapp:id/menuitem_search");
                if (search != null && clickNode(search)) stage = TYPE_NAME;
                break;
            }
            case TYPE_NAME: {
                AccessibilityNodeInfo field = findEditable(root);
                if (field != null && setText(field, taskContact)) stage = PICK_CHAT;
                break;
            }
            case PICK_CHAT: {
                AccessibilityNodeInfo hit = findExactText(root, taskContact);
                if (hit != null && clickNode(hit)) stage = TYPE_MESSAGE;
                break;
            }
            case TYPE_MESSAGE: {
                AccessibilityNodeInfo field = findEditable(root);
                if (field != null && setText(field, taskMessage)) stage = SEND;
                break;
            }
            case SEND: {
                AccessibilityNodeInfo send = findByDescOrId(root, "Send",
                        "com.whatsapp:id/send");
                if (send != null && clickNode(send)) {
                    finish("Message sent to " + taskContact);
                }
                break;
            }
            default:
                break;
        }
    }

    // Ends the task and brings EDITH back to the front.
    private void finish(String message) {
        stage = IDLE;
        lastResult = message;
        Intent back = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(back);
    }

    // ---------- Node helpers ----------

    private AccessibilityNodeInfo findNode(AccessibilityNodeInfo node, NodeMatch match) {
        if (node == null) return null;
        if (match.matches(node)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findNode(node.getChild(i), match);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo findByDescOrId(AccessibilityNodeInfo root,
                                                 String desc, String viewId) {
        List<AccessibilityNodeInfo> byId = root.findAccessibilityNodeInfosByViewId(viewId);
        if (byId != null && !byId.isEmpty()) return byId.get(0);
        return findNode(root, n -> n.getContentDescription() != null
                && desc.equalsIgnoreCase(n.getContentDescription().toString()));
    }

    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focus != null && focus.isEditable()) return focus;
        return findNode(root, AccessibilityNodeInfo::isEditable);
    }

    private AccessibilityNodeInfo findExactText(AccessibilityNodeInfo root, String text) {
        List<AccessibilityNodeInfo> hits = root.findAccessibilityNodeInfosByText(text);
        if (hits == null) return null;
        for (AccessibilityNodeInfo n : hits) {
            if (n.isEditable()) continue;
            if (clickableAncestor(n) != null) return n;
        }
        return null;
    }

    private AccessibilityNodeInfo clickableAncestor(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo p = node;
        while (p != null) {
            if (p.isClickable()) return p;
            p = p.getParent();
        }
        return null;
    }

    private boolean clickNode(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo target = clickableAncestor(node);
        return target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    private boolean setText(AccessibilityNodeInfo node, String text) {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        Bundle args = new Bundle();
        args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }
}