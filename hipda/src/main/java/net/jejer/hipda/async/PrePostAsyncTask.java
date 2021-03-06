package net.jejer.hipda.async;

import android.content.Context;
import android.os.AsyncTask;
import android.text.TextUtils;

import net.jejer.hipda.bean.HiSettingsHelper;
import net.jejer.hipda.bean.PostBean;
import net.jejer.hipda.bean.PrePostInfoBean;
import net.jejer.hipda.utils.ACRAUtils;
import net.jejer.hipda.utils.Constants;
import net.jejer.hipda.utils.HiUtils;
import net.jejer.hipda.utils.HttpUtils;
import net.jejer.hipda.utils.Utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class PrePostAsyncTask extends AsyncTask<PostBean, Void, PrePostInfoBean> {

    private PrePostListener mListener;
    private Context mCtx;
    private int mMode;

    private String mUrl;

    public PrePostAsyncTask(Context ctx, PrePostListener listener, int mode) {
        mCtx = ctx;
        mListener = listener;
        mMode = mode;
    }

    @Override
    protected PrePostInfoBean doInBackground(PostBean... postBeans) {

        PostBean postBean = postBeans[0];
        String tid = postBean.getTid();
        String pid = postBean.getPid();
        String fid = postBean.getFid();

        mUrl = HiUtils.ReplyUrl + tid;
        switch (mMode) {
            case PostAsyncTask.MODE_REPLY_THREAD:
            case PostAsyncTask.MODE_QUICK_REPLY:
                break;
            case PostAsyncTask.MODE_REPLY_POST:
                mUrl += "&reppost=" + pid;
                break;
            case PostAsyncTask.MODE_QUOTE_POST:
                mUrl += "&repquote=" + pid;
                break;
            case PostAsyncTask.MODE_NEW_THREAD:
                mUrl = HiUtils.NewThreadUrl + fid;
                break;
            case PostAsyncTask.MODE_EDIT_POST:
                //fid is not really needed, just put a value here
                mUrl = HiUtils.EditUrl + "&fid=" + fid + "&tid=" + tid + "&pid=" + pid + "&page=1";
                break;
        }

        String rsp_str;
        Boolean rspOk = false;
        int retry = 0;
        VolleyHelper.MyErrorListener errorListener;
        do {
            errorListener = VolleyHelper.getInstance().getErrorListener();
            rsp_str = VolleyHelper.getInstance().synchronousGet(mUrl, errorListener);
            if (rsp_str != null) {
                if (!LoginHelper.checkLoggedin(mCtx, rsp_str)) {
                    int status = new LoginHelper(mCtx, null).login();
                    if (status > Constants.STATUS_FAIL) {
                        break;
                    }
                } else {
                    rspOk = true;
                }
            }
            retry++;
        } while (!rspOk && retry < 3);

        if (!rspOk) {
            if (HiSettingsHelper.getInstance().isErrorReportMode()) {
                if (errorListener != null && errorListener.getError() != null)
                    ACRAUtils.acraReport(errorListener.getError(), "url=" + mUrl + "\nresponse=" + rsp_str);
                else
                    ACRAUtils.acraReport("Error when pre posting", "url=" + mUrl + "\nresponse=" + rsp_str);
            }
            return null;
        }

        Document doc = Jsoup.parse(rsp_str);
        return parseRsp(doc);
    }

    private PrePostInfoBean parseRsp(Document doc) {
        PrePostInfoBean result = new PrePostInfoBean();

        Elements formhashES = doc.select("input[name=formhash]");
        if (formhashES.size() < 1) {
            return result;
        } else {
            result.setFormhash(formhashES.first().attr("value"));
        }

        Elements addtextES = doc.select("textarea");
        if (addtextES.size() < 1) {
            return result;
        } else {
            result.setText(addtextES.first().text());
        }

        Elements scriptES = doc.select("script");
        if (scriptES.size() < 1) {
            return result;
        } else {
            result.setUid(HttpUtils.getMiddleString(scriptES.first().data(), "discuz_uid = ", ","));
        }

        Elements hashES = doc.select("input[name=hash]");
        if (hashES.size() < 1) {
            return result;
        } else {
            result.setHash(hashES.first().attr("value"));
        }

        //for edit post
        Elements subjectES = doc.select("input[name=subject]");
        if (subjectES.size() > 0) {
            result.setSubject(subjectES.first().attr("value"));
        }

        Elements unusedImagesES = doc.select("div#unusedimgattachlist table.imglist img");
        for (int i = 0; i < unusedImagesES.size(); i++) {
            Element imgE = unusedImagesES.get(i);
            String href = Utils.nullToText(imgE.attr("src"));
            String imgId = Utils.nullToText(imgE.attr("id"));
            if (href.startsWith("attachments/") && imgId.contains("_")) {
                imgId = imgId.substring(imgId.lastIndexOf("_") + 1);
                if (imgId.length() > 0 && TextUtils.isDigitsOnly(imgId)) {
                    result.addUnusedImage(imgId);
                }
            }
        }

        Elements typeidES = doc.select("#typeid > option");
        for (int i = 0; i < typeidES.size(); i++) {
            Element typeidEl = typeidES.get(i);
            result.addTypeidValues(typeidEl.val());
            result.addTypeidNames(typeidEl.text());
            if ("selected".equals(typeidEl.attr("selected")))
                result.setTypeid(typeidEl.val());
        }
        return result;
    }

    @Override
    protected void onPostExecute(PrePostInfoBean result) {
        if (result == null) {
            mListener.PrePostComplete(mMode, false, null);
            return;
        }
        mListener.PrePostComplete(mMode, !TextUtils.isEmpty(result.getFormhash()), result);
    }

    public interface PrePostListener {
        void PrePostComplete(int mode, boolean result, PrePostInfoBean info);
    }

}
