package net.jejer.hipda.async;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.text.TextUtils;
import android.widget.Toast;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;

import net.jejer.hipda.R;
import net.jejer.hipda.bean.SimpleListBean;
import net.jejer.hipda.utils.Constants;
import net.jejer.hipda.utils.HiParser;
import net.jejer.hipda.utils.HiUtils;
import net.jejer.hipda.utils.Logger;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class SimpleListLoader extends AsyncTaskLoader<SimpleListBean> {
    public static final int TYPE_MYREPLY = 0;
    public static final int TYPE_MYPOST = 1;
    public static final int TYPE_SEARCH = 2;
    public static final int TYPE_SMS = 3;
    public static final int TYPE_THREADNOTIFY = 4;
    public static final int TYPE_SMSDETAIL = 5;
    public static final int TYPE_FAVORITES = 6;
    public static final int TYPE_SEARCH_USER_THREADS = 7;

    private Context mCtx;
    private int mType;
    private int mPage = 1;
    private String mExtra = "";
    private Object mLocker;
    private String mRsp;

    public SimpleListLoader(Context context, int type, int page, String extra) {
        super(context);
        mCtx = context;
        mType = type;
        mPage = page;
        mExtra = extra;
        mLocker = this;
    }

    @Override
    public SimpleListBean loadInBackground() {

        int count = 0;
        boolean getOk = false;
        do {
            fetchSimpleList(mType);

            synchronized (mLocker) {
                try {
                    mLocker.wait();
                } catch (InterruptedException e) {
                }
            }

            if (mRsp != null) {
                if (!LoginHelper.checkLoggedin(mCtx, mRsp)) {
                    int status = new LoginHelper(mCtx, null).login();
                    if (status > Constants.STATUS_FAIL) {
                        break;
                    }
                } else {
                    getOk = true;
                }
            }
            count++;
        } while (!getOk && count < 3);

        if (!getOk) {
            return null;
        }

        Document doc = Jsoup.parse(mRsp);
        return HiParser.parseSimpleList(mCtx, mType, doc);
    }

    private void fetchSimpleList(int type) {
        String url = null;
        switch (type) {
            case TYPE_MYREPLY:
                url = HiUtils.MyReplyUrl + "&page=" + mPage;
                break;
            case TYPE_MYPOST:
                url = HiUtils.MyPostUrl + "&page=" + mPage;
                break;
            case TYPE_SMS:
                url = HiUtils.SMSUrl;
                break;
            case TYPE_THREADNOTIFY:
                url = HiUtils.ThreadNotifyUrl;
                break;
            case TYPE_SMSDETAIL:
                url = HiUtils.SMSDetailUrl + mExtra;
                break;
            case TYPE_SEARCH:
                try {
                    String prefixsft = mCtx.getResources().getString(R.string.prefix_search_fulltext);
                    if (mExtra.startsWith(prefixsft)) {
                        url = HiUtils.SearchFullText + URLEncoder.encode(mExtra.substring(prefixsft.length()), "GBK");
                        if (mPage > 1)
                            url += "&page=" + mPage;
                    } else {
                        url = HiUtils.SearchTitle + URLEncoder.encode(mExtra, "GBK");
                        if (mPage > 1)
                            url += "&page=" + mPage;
                    }
                } catch (UnsupportedEncodingException e) {
                    Logger.e("Encoding error", e);
                }
                break;
            case TYPE_SEARCH_USER_THREADS:
                if (TextUtils.isDigitsOnly(mExtra)) {
                    //first search, use uid
                    url = HiUtils.SearchUserThreads + mExtra + "&page=" + mPage;
                } else {
                    //after first seach, searchId is generated
                    url = HiUtils.BaseUrl + mExtra;
                    //replace page number in url
                    int pageIndex = url.indexOf("page=");
                    int pageEndIndex = url.indexOf("&", pageIndex + "page=".length());
                    if (pageIndex > 0 && pageEndIndex > pageIndex) {
                        url = url.substring(0, pageIndex) + "page=" + mPage + url.substring(pageEndIndex);
                    } else if (pageEndIndex == -1) {
                        url = url.substring(0, pageIndex) + "page=" + mPage;
                    }
                }
                break;
            case TYPE_FAVORITES:
                url = HiUtils.FavoritesUrl;
                if (mPage > 1)
                    url += "&page=" + mPage;
                break;
            default:
                break;
        }

        StringRequest sReq = new HiStringRequest(mCtx, url,
                new ThreadListListener(), new ThreadListErrorListener());
        VolleyHelper.getInstance().add(sReq);
    }

    private class ThreadListListener implements Response.Listener<String> {
        @Override
        public void onResponse(String response) {
            mRsp = response;
            synchronized (mLocker) {
                mLocker.notify();
            }
        }
    }

    private class ThreadListErrorListener implements Response.ErrorListener {
        @Override
        public void onErrorResponse(VolleyError error) {
            Logger.e(error);
            Toast.makeText(mCtx,
                    VolleyHelper.getErrorReason(error),
                    Toast.LENGTH_LONG).show();
            synchronized (mLocker) {
                mRsp = null;
                mLocker.notify();
            }
        }
    }

}
