package net.jejer.hipda.ui;

import android.app.DownloadManager;
import android.app.FragmentManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Html;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import net.jejer.hipda.R;
import net.jejer.hipda.bean.HiSettingsHelper;
import net.jejer.hipda.ui.textstyle.HiHtmlTagHandler;
import net.jejer.hipda.utils.HiUtils;
import net.jejer.hipda.utils.HttpUtils;
import net.jejer.hipda.utils.Logger;

public class TextViewWithEmoticon extends TextView {
    private static Context mCtx;
    private static FragmentManager mFragmentManager;

    private static int TRIM_LENGTH = 80;

    private boolean mTrim;

    public TextViewWithEmoticon(Context context) {
        super(context);
        mCtx = context;
        init();
    }

    public TextViewWithEmoticon(Context context, AttributeSet attrs) {
        super(context, attrs);
        mCtx = context;
        init();
    }

    private void init() {
        setTextColor(mCtx.getResources().getColor(HiSettingsHelper.getInstance().getDefaultTextColor()));
        setLinkTextColor(mCtx.getResources().getColor(R.color.hipda));

        float lineSpacingExtra = 2;
        float lineSpacingMultiplier = 1.1f;
        if (HiSettingsHelper.getInstance().getPostLineSpacing() == 1) {
            lineSpacingExtra = 4;
            lineSpacingMultiplier = 1.2f;
        } else if (HiSettingsHelper.getInstance().getPostLineSpacing() == 2) {
            lineSpacingExtra = 6;
            lineSpacingMultiplier = 1.3f;
        } else if (HiSettingsHelper.getInstance().getPostLineSpacing() == 3) {
            lineSpacingExtra = 8;
            lineSpacingMultiplier = 1.4f;
        }
        setLineSpacing(lineSpacingExtra, lineSpacingMultiplier);
    }

    public void setFragmentManager(FragmentManager fm) {
        mFragmentManager = fm;
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        Spannable s = getTextWithImages(text);
        super.setText(s, BufferType.SPANNABLE);
    }

    public void setTrim(boolean trim) {
        mTrim = trim;
    }

    private Html.ImageGetter imageGetter = new Html.ImageGetter() {
        public Drawable getDrawable(String src) {
            Drawable icon = null;
            if (!TextUtils.isEmpty(src) && src.startsWith("images/smilies/") && src.contains(".")) {
                src = src.substring("images/smilies/".length(), src.lastIndexOf(".")).replace("/", "_");
                int id = mCtx.getResources().getIdentifier(src, "drawable", mCtx.getPackageName());
                if (id != 0) {
                    icon = mCtx.getResources().getDrawable(id);
                    if (icon != null)
                        icon.setBounds(0, 0, getLineHeight(), getLineHeight());
                }
            }
            return icon;
        }
    };

    private Spannable getTextWithImages(CharSequence text) {
        String t = text.toString().trim();
        //remove leading spaces
        while (t.startsWith("&nbsp;") || t.startsWith("<br>")) {
            if (t.startsWith("&nbsp;"))
                t = t.substring("&nbsp;".length()).trim();
            else
                t = t.substring("<br>".length()).trim();
        }
        if (mTrim)
            t = t.replace("<br>", "").trim();
        SpannableStringBuilder b = (SpannableStringBuilder) Html.fromHtml(t, imageGetter, new HiHtmlTagHandler());
        if (mTrim && b.length() > TRIM_LENGTH) {
            b = new SpannableStringBuilder(b.subSequence(0, TRIM_LENGTH));
            b.append(" ....");
        }
        for (URLSpan s : b.getSpans(0, b.length(), URLSpan.class)) {
            String s_url = s.getURL();
            if (s_url.startsWith("http://www.hi-pda.com/forum/attachment.php")) {
                URLSpan newSpan = getDownloadUrlSpan(s_url);
                b.setSpan(newSpan, b.getSpanStart(s), b.getSpanEnd(s), b.getSpanFlags(s));
                b.removeSpan(s);
            } else if (s_url.startsWith("http://www.hi-pda.com/forum/viewthread.php")) {
                String tid = HttpUtils.getMiddleString(s_url, "tid=", "&");
                if (tid != null) {
                    URLSpan newSpan = getThreadUrlSpan(s_url);
                    b.setSpan(newSpan, b.getSpanStart(s), b.getSpanEnd(s), b.getSpanFlags(s));
                    b.removeSpan(s);
                }
            } else if (s_url.startsWith("http://www.hi-pda.com/forum/space.php")) {
                String uid = HttpUtils.getMiddleString(s_url, "uid=", "&");
                if (uid != null) {
                    URLSpan newSpan = getUserInfoUrlSpan(s_url);
                    b.setSpan(newSpan, b.getSpanStart(s), b.getSpanEnd(s), b.getSpanFlags(s));
                    b.removeSpan(s);
                }
            }
        }
        return b;
    }

    private URLSpan getThreadUrlSpan(final String s_url) {
        return new URLSpan(s_url) {
            public void onClick(View view) {
                Bundle arguments = new Bundle();
                arguments.putString(ThreadDetailFragment.ARG_TID_KEY, HttpUtils.getMiddleString(getURL(), "tid=", "&"));
                arguments.putString(ThreadDetailFragment.ARG_TITLE_KEY, "");
                ThreadDetailFragment fragment = new ThreadDetailFragment();
                fragment.setArguments(arguments);

                mFragmentManager.findFragmentById(R.id.main_frame_container).setHasOptionsMenu(false);
                mFragmentManager.beginTransaction()
                        .setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right, R.anim.slide_in_left, R.anim.slide_out_right)
                        .add(R.id.main_frame_container, fragment, ThreadDetailFragment.class.getName())
                        .addToBackStack(ThreadDetailFragment.class.getName())
                        .commit();
            }
        };
    }

    private URLSpan getUserInfoUrlSpan(final String s_url) {
        return new URLSpan(s_url) {
            public void onClick(View view) {

                String uid = HttpUtils.getMiddleString(s_url, "uid=", "&");
                String username = "";

                Bundle arguments = new Bundle();
                arguments.putString(UserinfoFragment.ARG_UID, uid);
                arguments.putString(UserinfoFragment.ARG_USERNAME, username);
                UserinfoFragment fragment = new UserinfoFragment();
                fragment.setArguments(arguments);

                mFragmentManager.beginTransaction()
                        .setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right, R.anim.slide_in_left, R.anim.slide_out_right)
                        .add(R.id.main_frame_container, fragment, ThreadDetailFragment.class.getName())
                        .addToBackStack(ThreadDetailFragment.class.getName())
                        .commit();

            }
        };
    }

    private URLSpan getDownloadUrlSpan(final String s_url) {
        return new URLSpan(s_url) {
            public void onClick(View view) {
                try {
                    DownloadManager dm = (DownloadManager) mCtx.getSystemService(Context.DOWNLOAD_SERVICE);
                    DownloadManager.Request downloadReq = new DownloadManager.Request(Uri.parse(getURL()));
                    downloadReq.addRequestHeader("User-agent", HiUtils.UserAgent);

                    String fileName = "";

                    //clean way to get fileName
                    SpannableStringBuilder b = new SpannableStringBuilder(((TextView) view).getText());
                    URLSpan[] urls = b.getSpans(0, b.length(), URLSpan.class);
                    if (urls.length > 0) {
                        fileName = b.toString().substring(b.getSpanStart(urls[0]), b.getSpanEnd(urls[0]));
                    }
                    if (TextUtils.isEmpty(fileName)) {
                        //failsafe dirty way,  to get rid of ( xxx K ) file size string
                        fileName = ((TextView) view).getText().toString();
                        if (fileName.contains(" ("))
                            fileName = fileName.substring(0, fileName.lastIndexOf(" (")).trim();
                    }
                    downloadReq.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                    downloadReq.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    dm.enqueue(downloadReq);
                } catch (SecurityException e) {
                    Logger.e(e);
                    Toast.makeText(mCtx, "下载出现错误，请使用浏览器下载\n" + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        };
    }

    /**
     * http://stackoverflow.com/a/17246463/2299887
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean ret = false;
        CharSequence text = getText();
        Spannable stext = Spannable.Factory.getInstance().newSpannable(text);
        int action = event.getAction();

        if (action == MotionEvent.ACTION_UP ||
                action == MotionEvent.ACTION_DOWN) {
            int x = (int) event.getX();
            int y = (int) event.getY();

            x -= getTotalPaddingLeft();
            y -= getTotalPaddingTop();

            x += getScrollX();
            y += getScrollY();

            Layout layout = getLayout();
            int line = layout.getLineForVertical(y);
            int off = layout.getOffsetForHorizontal(line, x);

            ClickableSpan[] link = stext.getSpans(off, off, ClickableSpan.class);

            if (link.length != 0) {
                if (action == MotionEvent.ACTION_UP) {
                    link[0].onClick(this);
                }
                ret = true;
            }
        }
        return ret;
    }

}
