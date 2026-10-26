package com.clarotube.app;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Insets;
import android.os.Build;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Objects;

/** Native controls around the WebView owned by MainActivity. */
public final class BrowserChrome {
    public interface Listener {
        void onSubmit(String input);
        void onHome();
        void onBack();
        void onReload();
        void onToggleProtection();
        void onOpenSettings();
    }

    private final Activity activity;
    private final Listener listener;
    private final LinearLayout root;
    private final FrameLayout contentContainer;
    private final FrameLayout errorOverlay;
    private final TextView errorMessage;
    private final TextView retryButton;
    private final TextView pageTitle;
    private final TextView protectionTitle;
    private final TextView blockedCount;
    private final ImageView protectionIcon;
    private final ImageView protectionActionIcon;
    private final ImageView navProtectionIcon;
    private final TextView navProtectionLabel;
    private final LinearLayout protectionAction;
    private final LinearLayout navProtectionAction;
    private final LinearLayout backAction;
    private final LinearLayout navigation;
    private final ProgressBar loadingBar;
    private final EditText addressInput;
    private Runnable retryAction;
    private boolean protectionKnown;
    private boolean protectionEnabled;
    private boolean compactControls;
    private boolean compactUpdatePending;

    public BrowserChrome(Activity activity, Listener listener) {
        this.activity = Objects.requireNonNull(activity, "activity");
        this.listener = Objects.requireNonNull(listener, "listener");
        configureWindow();

        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(R.color.background));
        root.setFocusableInTouchMode(true);
        installInsets();

        LinearLayout controls = new LinearLayout(activity);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(16), dp(8), dp(16), dp(12));
        controls.setBackgroundColor(color(R.color.background));
        root.addView(controls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout toolbar = new LinearLayout(activity);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        controls.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        FrameLayout brand = new FrameLayout(activity);
        brand.setBackgroundResource(R.drawable.bg_brand);
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        brandParams.setMarginEnd(dp(12));
        toolbar.addView(brand, brandParams);
        ImageView playIcon = icon(R.drawable.ic_play, R.color.background, dp(22));
        FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER);
        brand.addView(playIcon, playParams);

        LinearLayout heading = new LinearLayout(activity);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(heading, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        TextView appName = text(R.string.app_name, 20, R.color.text_primary, true);
        heading.addView(appName);
        pageTitle = text(R.string.page_default, 12, R.color.text_secondary, false);
        pageTitle.setSingleLine(true);
        pageTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.addView(pageTitle);

        LinearLayout settingsAction = iconAction(R.drawable.ic_settings, R.color.text_primary,
                R.string.open_settings, listener::onOpenSettings);
        toolbar.addView(settingsAction, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout protectionCard = new LinearLayout(activity);
        protectionCard.setOrientation(LinearLayout.HORIZONTAL);
        protectionCard.setGravity(Gravity.CENTER_VERTICAL);
        protectionCard.setPadding(dp(12), dp(10), dp(6), dp(10));
        protectionCard.setBackgroundResource(R.drawable.bg_surface);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(10);
        controls.addView(protectionCard, cardParams);

        FrameLayout shieldBadge = new FrameLayout(activity);
        shieldBadge.setBackgroundResource(R.drawable.bg_mint_soft);
        LinearLayout.LayoutParams shieldParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        shieldParams.setMarginEnd(dp(12));
        protectionCard.addView(shieldBadge, shieldParams);
        protectionIcon = icon(R.drawable.ic_shield, R.color.text_secondary, dp(22));
        shieldBadge.addView(protectionIcon,
                new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));

        LinearLayout statusTexts = new LinearLayout(activity);
        statusTexts.setOrientation(LinearLayout.VERTICAL);
        statusTexts.setGravity(Gravity.CENTER_VERTICAL);
        protectionCard.addView(statusTexts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        protectionTitle = text(R.string.protection_status_unknown, 14, R.color.text_primary, true);
        statusTexts.addView(protectionTitle);
        blockedCount = text(R.string.blocked_count_unknown, 12, R.color.text_secondary, false);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countParams.topMargin = dp(2);
        statusTexts.addView(blockedCount, countParams);
        TextView scope = text(R.string.protection_scope, 11, R.color.text_tertiary, false);
        LinearLayout.LayoutParams scopeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scopeParams.topMargin = dp(3);
        statusTexts.addView(scope, scopeParams);

        protectionAction = new LinearLayout(activity);
        protectionAction.setGravity(Gravity.CENTER);
        protectionAction.setBackgroundResource(R.drawable.bg_icon_action);
        protectionAction.setContentDescription(activity.getString(R.string.enable_protection));
        protectionAction.setFocusable(true);
        protectionAction.setOnClickListener(v -> listener.onToggleProtection());
        protectionCard.addView(protectionAction,
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        protectionActionIcon = icon(R.drawable.ic_power, R.color.text_secondary, dp(22));
        protectionAction.addView(protectionActionIcon,
                new LinearLayout.LayoutParams(dp(22), dp(22)));

        LinearLayout searchBar = new LinearLayout(activity);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(dp(10), dp(4), dp(4), dp(4));
        searchBar.setBackgroundResource(R.drawable.bg_search);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        searchParams.topMargin = dp(12);
        controls.addView(searchBar, searchParams);
        ImageView searchIcon = icon(R.drawable.ic_search, R.color.text_secondary, dp(21));
        LinearLayout.LayoutParams searchIconParams = new LinearLayout.LayoutParams(dp(21), dp(21));
        searchIconParams.setMarginEnd(dp(7));
        searchBar.addView(searchIcon, searchIconParams);

        addressInput = new EditText(activity);
        addressInput.setSingleLine(true);
        addressInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        addressInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressInput.setTextSize(15);
        addressInput.setTextColor(color(R.color.text_primary));
        addressInput.setHintTextColor(color(R.color.text_tertiary));
        addressInput.setHint(R.string.search_hint);
        addressInput.setBackgroundColor(Color.TRANSPARENT);
        addressInput.setPadding(dp(4), 0, dp(6), 0);
        searchBar.addView(addressInput, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        addressInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN)) {
                submit();
                return true;
            }
            return false;
        });

        LinearLayout compactSettingsAction = iconAction(R.drawable.ic_settings,
                R.color.text_secondary, R.string.open_settings, listener::onOpenSettings);
        compactSettingsAction.setVisibility(View.GONE);
        searchBar.addView(compactSettingsAction,
                new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView goButton = text(R.string.go, 15, R.color.background, true);
        goButton.setGravity(Gravity.CENTER);
        goButton.setBackgroundResource(R.drawable.bg_primary_button);
        goButton.setContentDescription(activity.getString(R.string.go_to_search));
        goButton.setFocusable(true);
        goButton.setOnClickListener(v -> submit());
        searchBar.addView(goButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        loadingBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        loadingBar.setMax(100);
        loadingBar.setProgressTintList(ColorStateList.valueOf(color(R.color.mint)));
        loadingBar.setProgressBackgroundTintList(ColorStateList.valueOf(color(R.color.border)));
        loadingBar.setVisibility(View.GONE);
        root.addView(loadingBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));

        FrameLayout contentArea = new FrameLayout(activity);
        contentArea.setBackgroundColor(color(R.color.background));
        root.addView(contentArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        contentContainer = new FrameLayout(activity);
        contentContainer.setBackgroundColor(color(R.color.background));
        contentArea.addView(contentContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        errorOverlay = new FrameLayout(activity);
        errorOverlay.setBackgroundColor(color(R.color.error_scrim));
        errorOverlay.setVisibility(View.GONE);
        errorOverlay.setClickable(true);
        errorOverlay.setFocusable(true);
        contentArea.addView(errorOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ScrollView errorScroll = new ScrollView(activity);
        errorScroll.setFillViewport(true);
        errorScroll.setVerticalScrollBarEnabled(false);
        errorOverlay.addView(errorScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout errorContent = new FrameLayout(activity);
        errorScroll.addView(errorContent, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout errorCard = new LinearLayout(activity);
        errorCard.setOrientation(LinearLayout.VERTICAL);
        errorCard.setGravity(Gravity.CENTER_HORIZONTAL);
        errorCard.setPadding(dp(24), dp(24), dp(24), dp(24));
        errorCard.setBackgroundResource(R.drawable.bg_error_card);
        FrameLayout.LayoutParams errorCardParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        errorCardParams.leftMargin = dp(24);
        errorCardParams.rightMargin = dp(24);
        errorCardParams.topMargin = dp(16);
        errorCardParams.bottomMargin = dp(16);
        errorContent.addView(errorCard, errorCardParams);
        ImageView errorIcon = icon(R.drawable.ic_warning, R.color.mint, dp(30));
        errorCard.addView(errorIcon, new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView errorTitle = text(R.string.error_title, 18, R.color.text_primary, true);
        errorTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams errorTitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        errorTitleParams.topMargin = dp(14);
        errorCard.addView(errorTitle, errorTitleParams);
        errorMessage = text(R.string.error_default, 14, R.color.text_secondary, false);
        errorMessage.setGravity(Gravity.CENTER);
        errorMessage.setMaxWidth(dp(310));
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = dp(8);
        errorCard.addView(errorMessage, messageParams);
        retryButton = text(R.string.retry, 15, R.color.background, true);
        retryButton.setGravity(Gravity.CENTER);
        retryButton.setMinHeight(dp(48));
        retryButton.setPadding(dp(24), 0, dp(24), 0);
        retryButton.setBackgroundResource(R.drawable.bg_primary_button);
        retryButton.setFocusable(true);
        retryButton.setOnClickListener(v -> {
            if (retryAction != null) retryAction.run();
        });
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
        retryParams.topMargin = dp(20);
        errorCard.addView(retryButton, retryParams);

        View divider = new View(activity);
        divider.setBackgroundColor(color(R.color.border));
        root.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        navigation = new LinearLayout(activity);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setBackgroundColor(color(R.color.surface));
        root.addView(navigation, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
        navigation.addView(navAction(R.drawable.ic_home, R.string.home, R.string.go_home,
                listener::onHome), navParams());
        backAction = navAction(R.drawable.ic_back, R.string.back, R.string.go_back,
                listener::onBack);
        navigation.addView(backAction, navParams());
        navigation.addView(navAction(R.drawable.ic_refresh, R.string.reload, R.string.reload_page,
                listener::onReload), navParams());
        navProtectionAction = navAction(R.drawable.ic_shield, R.string.protection,
                R.string.enable_protection, listener::onToggleProtection);
        navProtectionIcon = (ImageView) navProtectionAction.getChildAt(0);
        navProtectionLabel = (TextView) navProtectionAction.getChildAt(1);
        navigation.addView(navProtectionAction, navParams());
        setNavigation(false);
        root.addOnLayoutChangeListener((v, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            int usableHeight = root.getHeight() - root.getPaddingTop() - root.getPaddingBottom();
            boolean hideCard = usableHeight < dp(540);
            boolean hideToolbar = usableHeight < dp(420);
            if (protectionCard.getVisibility() == (hideCard ? View.GONE : View.VISIBLE)
                    && toolbar.getVisibility() == (hideToolbar ? View.GONE : View.VISIBLE)) return;
            if (compactUpdatePending) return;
            compactUpdatePending = true;
            // Visibility changes during layout can leave the old measured height in place.
            root.post(() -> {
                compactUpdatePending = false;
                int currentHeight = root.getHeight() - root.getPaddingTop() - root.getPaddingBottom();
                boolean currentHideCard = currentHeight < dp(540);
                boolean currentHideToolbar = currentHeight < dp(420);
                boolean changed = false;
                int cardVisibility = currentHideCard ? View.GONE : View.VISIBLE;
                int toolbarVisibility = currentHideToolbar ? View.GONE : View.VISIBLE;
                if (protectionCard.getVisibility() != cardVisibility) {
                    protectionCard.setVisibility(cardVisibility);
                    changed = true;
                }
                if (toolbar.getVisibility() != toolbarVisibility) {
                    toolbar.setVisibility(toolbarVisibility);
                    compactSettingsAction.setVisibility(currentHideToolbar ? View.VISIBLE : View.GONE);
                    changed = true;
                }
                if (compactControls != currentHideCard) {
                    compactControls = currentHideCard;
                    updateProtectionLabel();
                }
                if (changed) controls.requestLayout();
            });
        });
    }

    public View getRoot() {
        return root;
    }

    public FrameLayout getContentContainer() {
        return contentContainer;
    }

    public void setProtection(boolean enabled, int blockedRequests) {
        protectionKnown = true;
        protectionEnabled = enabled;
        protectionTitle.setText(enabled ? R.string.protection_on : R.string.protection_off);
        protectionTitle.setTextColor(color(enabled ? R.color.mint : R.color.text_primary));
        blockedCount.setText(activity.getResources().getQuantityString(
                R.plurals.blocked_requests, Math.max(0, blockedRequests), Math.max(0, blockedRequests)));
        int tint = enabled ? R.color.mint : R.color.text_secondary;
        protectionIcon.setImageTintList(ColorStateList.valueOf(color(tint)));
        protectionActionIcon.setImageTintList(ColorStateList.valueOf(color(tint)));
        navProtectionIcon.setImageTintList(ColorStateList.valueOf(color(tint)));
        navProtectionLabel.setTextColor(color(enabled ? R.color.mint : R.color.text_secondary));
        updateProtectionLabel();
        String action = activity.getString(enabled ? R.string.disable_protection : R.string.enable_protection);
        protectionAction.setContentDescription(action);
        navProtectionAction.setContentDescription(action);
        if (Build.VERSION.SDK_INT >= 30) {
            protectionAction.setStateDescription(activity.getString(
                    enabled ? R.string.protection_on : R.string.protection_off));
            navProtectionAction.setStateDescription(activity.getString(
                    enabled ? R.string.protection_on : R.string.protection_off));
        }
    }

    public void setLoading(int progress) {
        int safeProgress = Math.max(0, Math.min(100, progress));
        loadingBar.setProgress(safeProgress);
        loadingBar.setVisibility(safeProgress == 100 ? View.GONE : View.VISIBLE);
    }

    public void setPageTitle(String title) {
        String value = title == null ? "" : title.trim();
        pageTitle.setText(value.isEmpty() ? activity.getString(R.string.page_default) : value);
    }

    public void setNavigation(boolean canGoBack) {
        backAction.setEnabled(canGoBack);
        backAction.setAlpha(canGoBack ? 1f : 0.38f);
    }

    public void showError(String message, Runnable retry) {
        retryAction = retry;
        String value = message == null ? "" : message.trim();
        errorMessage.setText(value.isEmpty() ? activity.getString(R.string.error_default) : value);
        retryButton.setVisibility(retry == null ? View.GONE : View.VISIBLE);
        contentContainer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        errorOverlay.setVisibility(View.VISIBLE);
        errorOverlay.requestFocus();
        errorOverlay.announceForAccessibility(activity.getString(R.string.error_title));
    }

    public void clearError() {
        retryAction = null;
        errorOverlay.setVisibility(View.GONE);
        contentContainer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }

    private void submit() {
        String input = addressInput.getText().toString().trim();
        listener.onSubmit(input);
        InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(addressInput.getWindowToken(), 0);
        addressInput.clearFocus();
    }

    private void updateProtectionLabel() {
        navProtectionLabel.setText(!compactControls || !protectionKnown
                ? R.string.protection
                : protectionEnabled ? R.string.protection_active_short : R.string.protection_off_short);
    }

    private void configureWindow() {
        Window window = activity.getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) window.setNavigationBarContrastEnforced(false);
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        } else {
            View decor = window.getDecorView();
            decor.setSystemUiVisibility(decor.getSystemUiVisibility()
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    private void installInsets() {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left;
            int top;
            int right;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
                Insets ime = insets.getInsets(WindowInsets.Type.ime());
                left = bars.left;
                top = bars.top;
                right = bars.right;
                bottom = Math.max(bars.bottom, ime.bottom);
                navigation.setVisibility(insets.isVisible(WindowInsets.Type.ime())
                        ? View.GONE : View.VISIBLE);
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
                navigation.setVisibility(bottom > dp(160) ? View.GONE : View.VISIBLE);
            }
            view.setPadding(left, top, right, bottom);
            return insets;
        });
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { view.requestApplyInsets(); }
            @Override public void onViewDetachedFromWindow(View view) { }
        });
    }

    private LinearLayout iconAction(int iconRes, int tintRes, int descriptionRes, Runnable action) {
        LinearLayout button = new LinearLayout(activity);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundResource(R.drawable.bg_icon_action);
        button.setContentDescription(activity.getString(descriptionRes));
        button.setFocusable(true);
        button.setOnClickListener(v -> action.run());
        ImageView image = icon(iconRes, tintRes, dp(22));
        button.addView(image, new LinearLayout.LayoutParams(dp(22), dp(22)));
        return button;
    }

    private LinearLayout navAction(int iconRes, int labelRes, int descriptionRes, Runnable action) {
        LinearLayout button = new LinearLayout(activity);
        button.setOrientation(LinearLayout.VERTICAL);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundResource(R.drawable.bg_nav_action);
        button.setContentDescription(activity.getString(descriptionRes));
        button.setFocusable(true);
        button.setOnClickListener(v -> action.run());
        ImageView image = icon(iconRes, R.color.text_secondary, dp(22));
        button.addView(image, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView label = text(labelRes, 11, R.color.text_secondary, true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(4);
        button.addView(label, labelParams);
        return button;
    }

    private LinearLayout.LayoutParams navParams() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    }

    private ImageView icon(int iconRes, int tintRes, int size) {
        ImageView image = new ImageView(activity);
        image.setImageResource(iconRes);
        image.setImageTintList(ColorStateList.valueOf(color(tintRes)));
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        image.setMinimumWidth(size);
        image.setMinimumHeight(size);
        return image;
    }

    private TextView text(int valueRes, int sp, int colorRes, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(valueRes);
        view.setTextSize(sp);
        view.setTextColor(color(colorRes));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setIncludeFontPadding(false);
        return view;
    }

    private int color(int colorRes) {
        return activity.getColor(colorRes);
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
