/*
 *   Copyright 2012 Hai Bison
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.haibison.android.lockpattern;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.awei.android.lib.fingerprintidentify.FingerprintIdentify;
import com.awei.android.lib.fingerprintidentify.base.BaseFingerprint;
import com.haibison.android.lockpattern.util.IEncrypter;
import com.haibison.android.lockpattern.util.InvalidEncrypterException;
import com.haibison.android.lockpattern.util.Settings;
import com.haibison.android.lockpattern.util.Settings.Display;
import com.haibison.android.lockpattern.util.Settings.Security;
import com.haibison.android.lockpattern.widget.LockPatternUtils;
import com.haibison.android.lockpattern.widget.LockPatternView;
import com.haibison.android.lockpattern.widget.LockPatternView.Cell;
import com.haibison.android.lockpattern.widget.LockPatternView.DisplayMode;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static android.text.format.DateUtils.SECOND_IN_MILLIS;
import static com.haibison.android.lockpattern.BuildConfig.DEBUG;
import static com.haibison.android.lockpattern.util.Settings.Display.METADATA_CAPTCHA_WIRED_DOTS;
import static com.haibison.android.lockpattern.util.Settings.Display.METADATA_MAX_RETRIES;
import static com.haibison.android.lockpattern.util.Settings.Display.METADATA_MIN_WIRED_DOTS;
import static com.haibison.android.lockpattern.util.Settings.Display.METADATA_STEALTH_MODE;
import static com.haibison.android.lockpattern.util.Settings.Security.METADATA_AUTO_SAVE_PATTERN;
import static com.haibison.android.lockpattern.util.Settings.Security.METADATA_ENCRYPTER_CLASS;

/**
 * Main activity for this library.
 * <p>
 * You can deliver result to {@link PendingIntent}'s and/ or
 * {@link ResultReceiver} too. See {@link #EXTRA_PENDING_INTENT_OK},
 * {@link #EXTRA_PENDING_INTENT_CANCELLED} and {@link #EXTRA_RESULT_RECEIVER}
 * for more details.
 * </p>
 * <p/>
 * <h1>NOTES</h1>
 * <ul>
 * <li>
 * You must use one of built-in actions when calling this activity. They start
 * with {@code ACTION_*}. Otherwise the library might behave strangely (we don't
 * cover those cases).</li>
 * <li>You must use one of the themes that this library supports. They start
 * with {@code R.style.Alp_42447968_Theme_*}. The reason is the themes contain
 * resources that the library needs.</li>
 * <li>With {@link #ACTION_COMPARE_PATTERN}, there are <b><i>4 possible result
 * codes</i></b>: {@link Activity#RESULT_OK}, {@link Activity#RESULT_CANCELED},
 * {@link #RESULT_FAILED} and {@link #RESULT_FORGOT_PATTERN}.</li>
 * <li>With {@link #ACTION_VERIFY_CAPTCHA}, there are <b><i>3 possible result
 * codes</i></b>: {@link Activity#RESULT_OK}, {@link Activity#RESULT_CANCELED},
 * and {@link #RESULT_FAILED}.</li>
 * </ul>
 *
 * @author Hai Bison
 * @since v1.0
 */
@SuppressLint("LongLogTag")
public class LockPatternActivity extends Activity implements View.OnClickListener, LockPatternView.OnPatternListener {

    private static final String CLASSNAME = LockPatternActivity.class.getName();
    /**
     * Use this action to create new pattern. You can provide an
     * {@link IEncrypter} with
     * {@link Security#setEncrypterClass(Context, Class)} to
     * improve security.
     * <p/>
     * If the user created a pattern, {@link Activity#RESULT_OK} returns with
     * the pattern ({@link #EXTRA_PATTERN}). Otherwise
     * {@link Activity#RESULT_CANCELED} returns.
     *
     * @see #EXTRA_PENDING_INTENT_OK
     * @see #EXTRA_PENDING_INTENT_CANCELLED
     * @since v2.4 beta
     */
    public static final String ACTION_CREATE_PATTERN = CLASSNAME + ".create_pattern";
    public static final String ACTION_COMPARE_PATTERN = CLASSNAME + ".compare_pattern";
    public static final String ACTION_VERIFY_CAPTCHA = CLASSNAME + ".verify_captcha";

    /**
     * If you use {@link #ACTION_COMPARE_PATTERN} and the user fails to "login"
     * after a number of tries, this activity will finish with this result code.
     *
     * @see #ACTION_COMPARE_PATTERN
     * @see #EXTRA_RETRY_COUNT
     */
    public static final int RESULT_FAILED = RESULT_FIRST_USER + 1;
    /**
     * If you use {@link #ACTION_COMPARE_PATTERN} and the user forgot his/ her
     * pattern and decided to ask for your help with recovering the pattern (
     * {@link #EXTRA_PENDING_INTENT_FORGOT_PATTERN}), this activity will finish
     * with this result code.
     *
     * @see #ACTION_COMPARE_PATTERN
     * @see #EXTRA_RETRY_COUNT
     * @see #EXTRA_PENDING_INTENT_FORGOT_PATTERN
     * @since v2.8 beta
     */
    public static final int RESULT_FORGOT_PATTERN = RESULT_FIRST_USER + 2;
    /**
     * For actions {@link #ACTION_COMPARE_PATTERN} and
     * {@link #ACTION_VERIFY_CAPTCHA}, this key holds the number of tries that
     * the user attempted to verify the input pattern.
     */
    public static final String EXTRA_RETRY_COUNT = CLASSNAME + ".retry_count";
    /**
     * Sets value of this key to a theme in {@code R.style.Alp_42447968_Theme_*}
     * . Default is the one you set in your {@code AndroidManifest.xml}. Note
     * that theme {@link R.style#Alp_42447968_Theme_Light_DarkActionBar} is
     * available in API 4+, but it only works in API 14+.
     *
     * @since v1.5.3 beta
     */
    public static final String EXTRA_THEME = CLASSNAME + ".theme";
    /**
     * Key to hold the pattern. It must be a {@code char[]} array.
     * <p/>
     * <ul>
     * <li>If you use encrypter, it should be an encrypted array.</li>
     * <li>If you don't use encrypter, it should be the SHA-1 value of the
     * actual pattern. You can generate the value by
     * {@link LockPatternUtils#patternToSha1(List)}.</li>
     * </ul>
     *
     * @since v2 beta
     */
    public static final String EXTRA_PATTERN = CLASSNAME + ".pattern";
    /**
     * You can provide an {@link ResultReceiver} with this key. The activity
     * will notify your receiver the same result code and intent data as you
     * will receive them in {@link #onActivityResult(int, int, Intent)}.
     *
     * @since v2.4 beta
     */
    public static final String EXTRA_RESULT_RECEIVER = CLASSNAME + ".result_receiver";
    /**
     * Put a {@link PendingIntent} into this key. It will be sent before
     * {@link Activity#RESULT_OK} will be returning. If you were calling this
     * activity with {@link #ACTION_CREATE_PATTERN}, key {@link #EXTRA_PATTERN}
     * will be attached to the original intent which the pending intent holds.
     * <p/>
     * <h1>Notes</h1>
     * <ul>
     * <li>If you're going to use an activity, you don't need
     * {@link Intent#FLAG_ACTIVITY_NEW_TASK} for the intent, since the library
     * will call it inside {@link LockPatternActivity} .</li>
     * </ul>
     */
    public static final String EXTRA_PENDING_INTENT_OK = CLASSNAME + ".pending_intent_ok";
    /**
     * Put a {@link PendingIntent} into this key. It will be sent before
     * {@link Activity#RESULT_CANCELED} will be returning.
     * <p/>
     * <h1>Notes</h1>
     * <ul>
     * <li>If you're going to use an activity, you don't need
     * {@link Intent#FLAG_ACTIVITY_NEW_TASK} for the intent, since the library
     * will call it inside {@link LockPatternActivity} .</li>
     * </ul>
     */
    public static final String EXTRA_PENDING_INTENT_CANCELLED = CLASSNAME + ".pending_intent_cancelled";
    /**
     * You put a {@link PendingIntent} into this extra. The library will show a
     * button <i>"Forgot pattern?"</i> and call your intent later when the user
     * taps it.
     * <p/>
     * <h1>Notes</h1>
     * <ul>
     * <li>If you use an activity, you don't need
     * {@link Intent#FLAG_ACTIVITY_NEW_TASK} for the intent, since the library
     * will call it inside {@link LockPatternActivity} .</li>
     * <li>{@link LockPatternActivity} will finish with
     * {@link #RESULT_FORGOT_PATTERN} <i><b>after</b> making a call</i> to start
     * your pending intent.</li>
     * <li>It is your responsibility to make sure the Intent is good. The
     * library doesn't cover any errors when calling your intent.</li>
     * </ul>
     *
     * @author Thanks to Yan Cheng Cheok for his idea.
     * @see #ACTION_COMPARE_PATTERN
     * @since v2.8 beta
     */
    public static final String EXTRA_PENDING_INTENT_FORGOT_PATTERN = CLASSNAME + ".pending_intent_forgot_pattern";
    /**
     * 使用指纹
     **/
    public static final String EXTRA_USE_FINGERPRINT = "extra.use_fingerprint";
    /**
     * Delay time to reload the lock pattern view after a wrong pattern.
     */
    private static final long DELAY_TIME_TO_RELOAD_LOCK_PATTERN_VIEW = SECOND_IN_MILLIS;

    /*
     * FIELDS
     */
    private int mMaxRetries, mMinWiredDots, mRetryCount = 0, mCaptchaWiredDots;
    private boolean mAutoSave, mStealthMode;
    private IEncrypter mEncrypter;
    private ButtonOkCommand mBtnOkCmd;
    private Intent mIntentResult;


    /**
     * Helper enum for button OK commands. (Because we use only one "OK" button
     * for different commands).
     *
     * @author Hai Bison
     */
    private static enum ButtonOkCommand {
        CONTINUE, FORGOT_PATTERN, DONE
    }// ButtonOkCommand

    /*
     * CONTROLS
     */
    private TextView mTextInfo;
    private LockPatternView mLockPatternView;
    private Button mBtnConfirm;
    private ImageView mFingerPrintIcon;
    private static final String TAG = "test.LockPatternActivity";
    private FingerprintIdentify mFingerprintIdentify;
    private boolean isCanUseFingerPrint;

    public static Intent newIntentToCreatePattern(Context context) {
        Intent result = new Intent(ACTION_CREATE_PATTERN, null, context,
                LockPatternActivity.class);
        return result;
    }// newIntentToCreatePattern()

    public static boolean startToCreatePattern(Object caller, Context context, int requestCode) {
        return callStartActivityForResult(caller, newIntentToCreatePattern(context), requestCode);
    }// startToCreatePattern()

    public static boolean callStartActivityForResult(Object caller, Intent intent, int requestCode) {
        try {
            Method method = caller.getClass().getMethod(
                    "startActivityForResult", Intent.class, int.class);
            method.setAccessible(true);
            method.invoke(caller, intent, requestCode);

            return true;
        } catch (Exception e) {
            /*
             * Just log it. We don't need to go to details here, as it's
             * responsibility of user to take care of caller.
             */
            if (DEBUG)
                Log.d(CLASSNAME, e.getMessage(), e);
        }

        return false;
    }// callStartActivityForResult()

    public static Intent newIntentToComparePattern(Context context, char[] pattern, boolean usefingerprint) {
        Intent result = new Intent(ACTION_COMPARE_PATTERN, null, context,
                LockPatternActivity.class);
        if (pattern != null)
            result.putExtra(EXTRA_PATTERN, pattern);
        if (usefingerprint)
            result.putExtra(EXTRA_USE_FINGERPRINT, true);

        return result;
    }// newIntentToComparePattern()

    public static boolean startToComparePattern(Object caller, Context context, int requestCode, boolean usefingerPrint, char[] pattern) {
        return callStartActivityForResult(caller, newIntentToComparePattern(context, pattern, usefingerPrint), requestCode);
    }// startToComparePattern()

    public static Intent newIntentToVerifyCaptcha(Context context) {
        Intent result = new Intent(ACTION_VERIFY_CAPTCHA, null, context,
                LockPatternActivity.class);
        return result;
    }// newIntentToVerifyCaptcha()

    public static boolean startToVerifyCaptcha(Object caller, Context context, int requestCode) {
        return callStartActivityForResult(caller,
                newIntentToVerifyCaptcha(context), requestCode);
    }// startToVerifyCaptcha()

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (BuildConfig.DEBUG)
            Log.d(CLASSNAME, "ClassName = " + CLASSNAME);

        if (getIntent().hasExtra(EXTRA_THEME))
            setTheme(getIntent().getIntExtra(EXTRA_THEME, R.style.Alp_42447968_Theme_Dark));

        super.onCreate(savedInstanceState);

        loadSettings();

        mIntentResult = new Intent();
        setResult(RESULT_CANCELED, mIntentResult);

        initContentView();
        //fingerprint
        if (getIntent().hasExtra(EXTRA_USE_FINGERPRINT)) {
            Log.d(TAG, "onCreate: 使用指纹 ");
            mFingerprintIdentify = new FingerprintIdentify(this, new BaseFingerprint.FingerprintIdentifyExceptionListener() {
                @Override
                public void onCatchException(Throwable exception) {
                    exception.printStackTrace();
                    Log.d(TAG, "onCatchException: 指纹异常", exception);
                }
            });

            isCanUseFingerPrint = mFingerprintIdentify.isFingerprintEnable() && mFingerprintIdentify.isHardwareEnable() && mFingerprintIdentify.isRegisteredFinger();
            if (isCanUseFingerPrint) {
                findViewById(R.id.fingerprint_icon).setVisibility(View.VISIBLE);
                startFingerIdentify();
                Log.d(TAG, "onCreate: 开始读取指纹  ");
            }
        }
    }// onCreate()

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (BuildConfig.DEBUG)
            Log.d(CLASSNAME, "onConfigurationChanged()");
        super.onConfigurationChanged(newConfig);
        initContentView();
    }// onConfigurationChanged()

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        /*
         * Use this hook instead of onBackPressed(), because onBackPressed() is
         * not available in API 4.
         */
        if (keyCode == KeyEvent.KEYCODE_BACK
                && ACTION_COMPARE_PATTERN.equals(getIntent().getAction())) {
            finishWithNegativeResult(RESULT_CANCELED);

            return true;
        }// if

        return super.onKeyDown(keyCode, event);
    }// onKeyDown()

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        /*
         * Support canceling dialog on touching outside in APIs < 11.
         * 
         * This piece of code is copied from android.view.Window. You can find
         * it by searching for methods shouldCloseOnTouch() and isOutOfBounds().
         */
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB
                && event.getAction() == MotionEvent.ACTION_DOWN
                && getWindow().peekDecorView() != null) {
            final int x = (int) event.getX();
            final int y = (int) event.getY();
            final int slop = ViewConfiguration.get(this)
                    .getScaledWindowTouchSlop();
            final View decorView = getWindow().getDecorView();
            boolean isOutOfBounds = (x < -slop) || (y < -slop)
                    || (x > (decorView.getWidth() + slop))
                    || (y > (decorView.getHeight() + slop));
            if (isOutOfBounds) {
                finishWithNegativeResult(RESULT_CANCELED);
                return true;
            }
        }// if

        return super.onTouchEvent(event);
    }// onTouchEvent()

    @Override
    public void onClick(View view) {
        if (ACTION_CREATE_PATTERN.equals(getIntent().getAction())) {
            if (mBtnOkCmd == ButtonOkCommand.CONTINUE) {
                mBtnOkCmd = ButtonOkCommand.DONE;
                mLockPatternView.clearPattern();
                mTextInfo.setText(R.string.alp_42447968_msg_redraw_pattern_to_confirm);
                mBtnConfirm.setText(R.string.alp_42447968_cmd_confirm);
                mBtnConfirm.setEnabled(false);
            } else {
                final char[] pattern = getIntent().getCharArrayExtra(EXTRA_PATTERN);
                finishWithResultOk(pattern);
            }
        }// ACTION_CREATE_PATTERN
        else if (ACTION_COMPARE_PATTERN.equals(getIntent().getAction())) {
                /*
                 * We don't need to verify the extra. First, this button is only
                 * visible if there is this extra in the intent. Second, it is
                 * the responsibility of the caller to make sure the extra is
                 * good.
                 */
            PendingIntent pi = null;
            try {
                pi = getIntent().getParcelableExtra(
                        EXTRA_PENDING_INTENT_FORGOT_PATTERN);
                pi.send();
            } catch (Throwable t) {
                Log.e(CLASSNAME, "Error sending pending intent: " + pi, t);
            }
            finishWithNegativeResult(RESULT_FORGOT_PATTERN);
        }// ACTION_COMPARE_PATTERN
    }// onClick()

    @Override
    public void onPatternStart() {
        Log.d(TAG, "onPatternStart: ");
        mLockPatternView.removeCallbacks(mLockPatternViewReloader);
        mLockPatternView.setDisplayMode(DisplayMode.Correct);

        if (ACTION_CREATE_PATTERN.equals(getIntent().getAction())) {
            mTextInfo.setText(R.string.alp_42447968_msg_release_finger_when_done);
            mBtnConfirm.setEnabled(false);
            if (mBtnOkCmd == ButtonOkCommand.CONTINUE)
                getIntent().removeExtra(EXTRA_PATTERN);
        }// ACTION_CREATE_PATTERN
        else if (ACTION_COMPARE_PATTERN.equals(getIntent().getAction())) {
            mTextInfo
                    .setText(R.string.alp_42447968_msg_draw_pattern_to_unlock);
        }// ACTION_COMPARE_PATTERN
        else if (ACTION_VERIFY_CAPTCHA.equals(getIntent().getAction())) {
            mTextInfo
                    .setText(R.string.alp_42447968_msg_redraw_pattern_to_confirm);
        }// ACTION_VERIFY_CAPTCHA
    }// onPatternStart()

    @Override
    public void onPatternDetected(List<Cell> pattern) {
        Log.d(TAG, "onPatternDetected: " + getIntent().getAction());
        if (ACTION_CREATE_PATTERN.equals(getIntent().getAction())) {
            doCheckAndCreatePattern(pattern);
        }// ACTION_CREATE_PATTERN
        else if (ACTION_COMPARE_PATTERN.equals(getIntent().getAction())) {
            doComparePattern(pattern);
        }// ACTION_COMPARE_PATTERN
        else if (ACTION_VERIFY_CAPTCHA.equals(getIntent().getAction())) {
            if (!DisplayMode.Animate.equals(mLockPatternView
                    .getDisplayMode()))
                doComparePattern(pattern);
        }// ACTION_VERIFY_CAPTCHA
    }// onPatternDetected()

    @Override
    public void onPatternCleared() {
        Log.d(TAG, "onPatternCleared: ");
        mLockPatternView.removeCallbacks(mLockPatternViewReloader);

        if (ACTION_CREATE_PATTERN.equals(getIntent().getAction())) {
            mLockPatternView.setDisplayMode(DisplayMode.Correct);
            mBtnConfirm.setEnabled(false);
            if (mBtnOkCmd == ButtonOkCommand.CONTINUE) {
                getIntent().removeExtra(EXTRA_PATTERN);
                mTextInfo
                        .setText(R.string.alp_42447968_msg_draw_an_unlock_pattern);
            } else
                mTextInfo
                        .setText(R.string.alp_42447968_msg_redraw_pattern_to_confirm);
        }// ACTION_CREATE_PATTERN
        else if (ACTION_COMPARE_PATTERN.equals(getIntent().getAction())) {
            mLockPatternView.setDisplayMode(DisplayMode.Correct);
            mTextInfo
                    .setText(R.string.alp_42447968_msg_draw_pattern_to_unlock);
        }// ACTION_COMPARE_PATTERN
        else if (ACTION_VERIFY_CAPTCHA.equals(getIntent().getAction())) {
            mTextInfo
                    .setText(R.string.alp_42447968_msg_redraw_pattern_to_confirm);
            List<Cell> pattern = getIntent().getParcelableArrayListExtra(
                    EXTRA_PATTERN);
            mLockPatternView.setPattern(DisplayMode.Animate, pattern);
        }// ACTION_VERIFY_CAPTCHA
    }// onPatternCleared()

    @Override
    public void onPatternCellAdded(List<Cell> pattern) {
        Log.d(TAG, "onPatternCellAdded: ");
        // TODO Auto-generated method stub
    }// onPatternCellAdded()

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelFingerIdentify();
    }// onDestroy()

    /**
     * Loads settings, either from manifest or {@link Settings}.
     */
    private void loadSettings() {
        Bundle metaData = null;
        try {
            metaData = getPackageManager().getActivityInfo(getComponentName(),
                    PackageManager.GET_META_DATA).metaData;
        } catch (NameNotFoundException e) {
            /*
             * Never catch this.
             */
            e.printStackTrace();
        }

        if (metaData != null && metaData.containsKey(METADATA_MIN_WIRED_DOTS))
            mMinWiredDots = Display.validateMinWiredDots(this,
                    metaData.getInt(METADATA_MIN_WIRED_DOTS));
        else
            mMinWiredDots = Display.getMinWiredDots(this);

        if (metaData != null && metaData.containsKey(METADATA_MAX_RETRIES))
            mMaxRetries = Display.validateMaxRetries(this,
                    metaData.getInt(METADATA_MAX_RETRIES));
        else
            mMaxRetries = Display.getMaxRetries(this);

        if (metaData != null
                && metaData.containsKey(METADATA_AUTO_SAVE_PATTERN))
            mAutoSave = metaData.getBoolean(METADATA_AUTO_SAVE_PATTERN);
        else
            mAutoSave = Security.isAutoSavePattern(this);

        if (metaData != null
                && metaData.containsKey(METADATA_CAPTCHA_WIRED_DOTS))
            mCaptchaWiredDots = Display.validateCaptchaWiredDots(this,
                    metaData.getInt(METADATA_CAPTCHA_WIRED_DOTS));
        else
            mCaptchaWiredDots = Display.getCaptchaWiredDots(this);

        if (metaData != null && metaData.containsKey(METADATA_STEALTH_MODE))
            mStealthMode = metaData.getBoolean(METADATA_STEALTH_MODE);
        else
            mStealthMode = Display.isStealthMode(this);

        /*
         * Encrypter.
         */
        char[] encrypterClass;
        if (metaData != null && metaData.containsKey(METADATA_ENCRYPTER_CLASS))
            encrypterClass = metaData.getString(METADATA_ENCRYPTER_CLASS).toCharArray();
        else
            encrypterClass = Security.getEncrypterClass(this);

        if (encrypterClass != null) {
            try {
                mEncrypter = (IEncrypter) Class.forName(
                        new String(encrypterClass), false, getClassLoader())
                        .newInstance();
            } catch (Throwable t) {
                throw new InvalidEncrypterException();
            }
        }
    }// loadSettings()

    /**
     * Initializes UI...
     */
    private void initContentView() {
        /*
         * Save all controls' state to restore later.
         */
        CharSequence infoText = mTextInfo != null ? mTextInfo.getText() : null;
        Boolean btnOkEnabled = mBtnConfirm != null ? mBtnConfirm.isEnabled()
                : null;
        DisplayMode lastDisplayMode = mLockPatternView != null ? mLockPatternView
                .getDisplayMode() : null;
        List<Cell> lastPattern = mLockPatternView != null ? mLockPatternView
                .getPattern() : null;

        setContentView(R.layout.alp_42447968_lock_pattern_activity_land);

        /*
         * MAP CONTROLS
         */

        mTextInfo = (TextView) findViewById(R.id.alp_42447968_textview_info);
        mLockPatternView = (LockPatternView) findViewById(R.id.alp_42447968_view_lock_pattern);

        mBtnConfirm = (Button) findViewById(R.id.alp_42447968_button_confirm);
        mFingerPrintIcon = (ImageView) findViewById(R.id.fingerprint_icon);
        /*
         * LOCK PATTERN VIEW
         */

        switch (getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) {
            case Configuration.SCREENLAYOUT_SIZE_LARGE:
            case Configuration.SCREENLAYOUT_SIZE_XLARGE: {
                final int size = getResources().getDimensionPixelSize(
                        R.dimen.alp_42447968_lockpatternview_size);
                LayoutParams lp = mLockPatternView.getLayoutParams();
                lp.width = size;
                lp.height = size;
                mLockPatternView.setLayoutParams(lp);

                break;
            }// LARGE / XLARGE
        }

        /*
         * Haptic feedback.
         */
        boolean hapticFeedbackEnabled = false;
        try {
            hapticFeedbackEnabled = android.provider.Settings.System
                    .getInt(getContentResolver(),
                            android.provider.Settings.System.HAPTIC_FEEDBACK_ENABLED,
                            0) != 0;
        } catch (Throwable t) {
            /*
             * Ignore it.
             */
        }
        mLockPatternView.setTactileFeedbackEnabled(hapticFeedbackEnabled);

        mLockPatternView.setInStealthMode(mStealthMode
                && !ACTION_VERIFY_CAPTCHA.equals(getIntent().getAction()));
        mLockPatternView.setOnPatternListener(this);
        if (lastPattern != null && lastDisplayMode != null
                && !ACTION_VERIFY_CAPTCHA.equals(getIntent().getAction()))
            mLockPatternView.setPattern(lastDisplayMode, lastPattern);

        /*
         * COMMAND BUTTONS
         */

        if (ACTION_CREATE_PATTERN.equals(getIntent().getAction())) {
            mBtnConfirm.setOnClickListener(this);

            if (infoText != null)
                mTextInfo.setText(infoText);
            else
                mTextInfo
                        .setText(R.string.alp_42447968_msg_draw_an_unlock_pattern);

            /*
             * BUTTON OK
             */
            if (mBtnOkCmd == null)
                mBtnOkCmd = ButtonOkCommand.CONTINUE;
            switch (mBtnOkCmd) {
                case CONTINUE:
                    mBtnConfirm.setText(R.string.alp_42447968_cmd_continue);
                    break;
                case DONE:
                    mBtnConfirm.setText(R.string.alp_42447968_cmd_confirm);
                    break;
                default:
                /*
                 * Do nothing.
                 */
                    break;
            }
            if (btnOkEnabled != null)
                mBtnConfirm.setEnabled(btnOkEnabled);
        }// ACTION_CREATE_PATTERN
        else if (ACTION_COMPARE_PATTERN.equals(getIntent().getAction())) {
            if (TextUtils.isEmpty(infoText))
                mTextInfo.setText(R.string.alp_42447968_msg_draw_pattern_to_unlock);
            else
                mTextInfo.setText(infoText);
            if (getIntent().hasExtra(EXTRA_PENDING_INTENT_FORGOT_PATTERN)) {
                mBtnConfirm.setOnClickListener(this);
                mBtnConfirm.setText(R.string.alp_42447968_cmd_forgot_pattern);
                mBtnConfirm.setEnabled(true);
            }
        }// ACTION_COMPARE_PATTERN
        else if (ACTION_VERIFY_CAPTCHA.equals(getIntent().getAction())) {
            mTextInfo
                    .setText(R.string.alp_42447968_msg_redraw_pattern_to_confirm);

            /*
             * NOTE: EXTRA_PATTERN should hold a char[] array. In this case we
             * use it as a temporary variable to hold a list of Cell.
             */

            final ArrayList<Cell> pattern;
            if (getIntent().hasExtra(EXTRA_PATTERN))
                pattern = getIntent()
                        .getParcelableArrayListExtra(EXTRA_PATTERN);
            else
                getIntent().putParcelableArrayListExtra(
                        EXTRA_PATTERN,
                        pattern = LockPatternUtils
                                .genCaptchaPattern(mCaptchaWiredDots));

            mLockPatternView.setPattern(DisplayMode.Animate, pattern);
        }// ACTION_VERIFY_CAPTCHA
    }// initContentView()

    public void startFingerIdentify() {
        mFingerprintIdentify.startIdentify(3, new BaseFingerprint.FingerprintIdentifyListener() {
            @Override
            public void onSucceed() {
//                tag("验证成功");
                Log.d(TAG, "onSucceed: 验证指纹成功");
                finishWithResultOk(new char[]{});
            }

            @Override
            public void onNotMatch(int availableTimes) {
//                tag("指纹不匹配，可用次数剩余：" + availableTimes);
                Toast.makeText(LockPatternActivity.this, "指纹不匹配," + availableTimes, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailed() {
                Log.d(TAG, "onFailed: 验证错误 ");
            }
        });
    }

    public void cancelFingerIdentify() {
        if (mFingerprintIdentify != null) {
            Log.d(TAG, "cancelFingerIdentify: 指纹读取停止");
            mFingerprintIdentify.cancelIdentify();
        }
    }

    /**
     * Compares {@code pattern} to the given pattern (
     * {@link #ACTION_COMPARE_PATTERN}) or to the generated "CAPTCHA" pattern (
     * {@link #ACTION_VERIFY_CAPTCHA}). Then finishes the activity if they
     * match.
     *
     * @param pattern the pattern to be compared.
     */
    private void doComparePattern(final List<Cell> pattern) {
        if (pattern == null)
            return;

        boolean equels = false;
        if (ACTION_COMPARE_PATTERN.equals(getIntent().getAction())) {
            char[] currentPattern = getIntent().getCharArrayExtra(EXTRA_PATTERN);
            if (currentPattern != null) {
                if (mEncrypter != null)
                    equels = pattern.equals(mEncrypter.decrypt(LockPatternActivity.this, currentPattern));
                else
                    equels = Arrays.equals(currentPattern, LockPatternUtils.patternToSha1(pattern).toCharArray());
            }
        } else if (ACTION_VERIFY_CAPTCHA.equals(getIntent().getAction())) {
            equels = pattern.equals(getIntent().getParcelableArrayListExtra(EXTRA_PATTERN));
        }

        if (equels)
            finishWithResultOk(null);
        else {
            mRetryCount++;
            mIntentResult.putExtra(EXTRA_RETRY_COUNT, mRetryCount);
            if (mRetryCount >= mMaxRetries)
                finishWithNegativeResult(RESULT_FAILED);
            else {
                mLockPatternView.setDisplayMode(DisplayMode.Wrong);
                mTextInfo.setText(R.string.alp_42447968_msg_try_again);
                mLockPatternView.postDelayed(mLockPatternViewReloader, DELAY_TIME_TO_RELOAD_LOCK_PATTERN_VIEW);
            }
        }


    }// doComparePattern()

    /**
     * Checks and creates the pattern.
     *
     * @param pattern the current pattern of lock pattern view.
     */
    private void doCheckAndCreatePattern(final List<Cell> pattern) {
        if (pattern.size() < mMinWiredDots) {
            mLockPatternView.setDisplayMode(DisplayMode.Wrong);
            mTextInfo.setText(getResources().getQuantityString(R.plurals.alp_42447968_pmsg_connect_x_dots, mMinWiredDots, mMinWiredDots));
            mLockPatternView.postDelayed(mLockPatternViewReloader, DELAY_TIME_TO_RELOAD_LOCK_PATTERN_VIEW);
            return;
        }// if

        if (getIntent().hasExtra(EXTRA_PATTERN)) {

            boolean res = false;
            if (mEncrypter != null)
                res = pattern.equals(mEncrypter.decrypt(LockPatternActivity.this, getIntent().getCharArrayExtra(EXTRA_PATTERN)));
            else
                res = Arrays.equals(getIntent().getCharArrayExtra(EXTRA_PATTERN), LockPatternUtils.patternToSha1(pattern).toCharArray());

            if (res) {
                mTextInfo.setText(R.string.alp_42447968_msg_your_new_unlock_pattern);
                mBtnConfirm.setEnabled(true);
            } else {
                mTextInfo.setText(R.string.alp_42447968_msg_redraw_pattern_to_confirm);
                mBtnConfirm.setEnabled(false);
                mLockPatternView.setDisplayMode(DisplayMode.Wrong);
                mLockPatternView.postDelayed(mLockPatternViewReloader, DELAY_TIME_TO_RELOAD_LOCK_PATTERN_VIEW);
            }
        } else {
            /*
             * Use a LoadingView because encrypting pattern might take time...
             */
            char[] res = mEncrypter != null ? mEncrypter.encrypt(
                    LockPatternActivity.this, pattern)
                    : LockPatternUtils.patternToSha1(pattern)
                    .toCharArray();
            getIntent().putExtra(EXTRA_PATTERN, res);
            mBtnConfirm.setEnabled(true);
            mBtnConfirm.performClick();
        }

    }// doCheckAndCreatePattern()

    /**
     * Finishes activity with {@link Activity#RESULT_OK}.
     *
     * @param pattern the pattern, if this is in mode creating pattern. In any
     *                cases, it can be set to {@code null}.
     */
    private void finishWithResultOk(char[] pattern) {
        if (ACTION_CREATE_PATTERN.equals(getIntent().getAction()))
            mIntentResult.putExtra(EXTRA_PATTERN, pattern);
        else {
            /*
             * If the user was "logging in", minimum try count can not be zero.
             */
            mIntentResult.putExtra(EXTRA_RETRY_COUNT, mRetryCount + 1);
        }

        setResult(RESULT_OK, mIntentResult);

        /*
         * ResultReceiver
         */
        ResultReceiver receiver = getIntent().getParcelableExtra(
                EXTRA_RESULT_RECEIVER);
        if (receiver != null) {
            Bundle bundle = new Bundle();
            if (ACTION_CREATE_PATTERN.equals(getIntent().getAction()))
                bundle.putCharArray(EXTRA_PATTERN, pattern);
            else {
                /*
                 * If the user was "logging in", minimum try count can not be
                 * zero.
                 */
                bundle.putInt(EXTRA_RETRY_COUNT, mRetryCount + 1);
            }
            receiver.send(RESULT_OK, bundle);
        }

        /*
         * PendingIntent
         */
        PendingIntent pi = getIntent().getParcelableExtra(
                EXTRA_PENDING_INTENT_OK);
        if (pi != null) {
            try {
                pi.send(this, RESULT_OK, mIntentResult);
            } catch (Throwable t) {
                Log.e(CLASSNAME, "Error sending PendingIntent: " + pi, t);
            }
        }

        finish();
    }// finishWithResultOk()

    /**
     * Finishes the activity with negative result (
     * {@link Activity#RESULT_CANCELED}, {@link #RESULT_FAILED} or
     * {@link #RESULT_FORGOT_PATTERN}).
     */
    private void finishWithNegativeResult(int resultCode) {
        if (ACTION_COMPARE_PATTERN.equals(getIntent().getAction()))
            mIntentResult.putExtra(EXTRA_RETRY_COUNT, mRetryCount);

        setResult(resultCode, mIntentResult);

        /*
         * ResultReceiver
         */
        ResultReceiver receiver = getIntent().getParcelableExtra(
                EXTRA_RESULT_RECEIVER);
        if (receiver != null) {
            Bundle resultBundle = null;
            if (ACTION_COMPARE_PATTERN.equals(getIntent().getAction())) {
                resultBundle = new Bundle();
                resultBundle.putInt(EXTRA_RETRY_COUNT, mRetryCount);
            }
            receiver.send(resultCode, resultBundle);
        }

        /*
         * PendingIntent
         */
        PendingIntent pi = getIntent().getParcelableExtra(
                EXTRA_PENDING_INTENT_CANCELLED);
        if (pi != null) {
            try {
                pi.send(this, resultCode, mIntentResult);
            } catch (Throwable t) {
                Log.e(CLASSNAME, "Error sending PendingIntent: " + pi, t);
            }
        }

        finish();
    }// finishWithNegativeResult()

    /**
     * This reloads the {@link #mLockPatternView} after a wrong pattern.
     */
    private final Runnable mLockPatternViewReloader = new Runnable() {

        @Override
        public void run() {
            mLockPatternView.clearPattern();
            onPatternCleared();
        }// run()

    };// mLockPatternViewReloader

}