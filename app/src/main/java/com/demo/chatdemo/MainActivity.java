package com.demo.chatdemo;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    TextView mTvExtractedMsg;
    EditText mEdtMsg;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mEdtMsg = (EditText) findViewById(R.id.edt_msg);
        mEdtMsg.setText("@bob @john (success) such a cool feature; https://twitter.com/jdorfman/status/430511497475670016");
        mTvExtractedMsg = (TextView) findViewById(R.id.tv_deciphered_msg);
    }

    public void onClickSend(View view) {
        String msg = mEdtMsg.getText().toString();
        new Thread(new ExtractorRunnable(msg)).start();
    }

    private class ExtractorRunnable implements Runnable {
        private final String MENTIONS = "mentions";
        private final String EMOTICONS = "emoticons";
        private final String LINKS = "links";

        String mMsg;
        ExtractorRunnable(String msg) {
            mMsg = msg;
        }
        @Override
        public void run() {
            ExecutorService taskExecutor = Executors.newFixedThreadPool(3);
            List<Future<JSONArray>> futures = new ArrayList<>(3);
            futures.add(taskExecutor.submit(new MentionsParserTask(mMsg)));
            futures.add(taskExecutor.submit(new EmotionsParserTask(mMsg)));
            futures.add(taskExecutor.submit(new LinksParserTask(mMsg)));

            taskExecutor.shutdown();

            final JSONObject decipheredMsg = new JSONObject();
            try {
                decipheredMsg.put(MENTIONS, futures.get(0).get());
                decipheredMsg.put(EMOTICONS, futures.get(1).get());
                decipheredMsg.put(LINKS, futures.get(2).get());
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }

            //Post result
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    mTvExtractedMsg.setText(decipheredMsg.toString());
                }
            });
        }
    }

    private abstract class ParserTask implements Callable<JSONArray> {
        String mMsg;
        ParserTask(String msg) {
            mMsg = msg;
        }
    }
    private class MentionsParserTask extends ParserTask {

        MentionsParserTask(String msg) {
            super(msg);
        }

        @Override
        public JSONArray call() throws Exception {
            final String NON_WORD_PATTERN = "[^\\p{L}\\p{Nd}]+";
            JSONArray jArrResult = new JSONArray();
            String[] listMentions = mMsg.split("@");
            if (listMentions.length > 1) {
                for (int i = 1; i < listMentions.length; i++) {
                    Pattern pattern = Pattern.compile(NON_WORD_PATTERN);
                    Matcher matcher = pattern.matcher(listMentions[i]);
                    int lastCharIndex = listMentions[i].length();
                    if (matcher.find()) {
                        lastCharIndex = matcher.start();
                    }
                    jArrResult.put(listMentions[i].substring(0, lastCharIndex));
                }
            }
            return jArrResult;
        }
    }
    private class EmotionsParserTask extends ParserTask {

        EmotionsParserTask(String msg) {
            super(msg);
        }
        @Override
        public JSONArray call() {
            final String ALPHANUM_PATTERN = "\\([\\p{L}\\p{Nd}]{1,15}\\)";
            JSONArray jArrResult = new JSONArray();
            Pattern pattern = Pattern.compile(ALPHANUM_PATTERN);
            Matcher matcher = pattern.matcher(mMsg);
            while(matcher.find()) {
                jArrResult.put(matcher.group().replaceAll("[()]",""));
            }
            return jArrResult;
        }
    }
    private class LinksParserTask extends ParserTask {
        LinksParserTask(String msg) {
            super(msg);
        }
        @Override
        public JSONArray call() {
            JSONArray jArrResult = new JSONArray();
            final String NAME_URL = "url";
            final String NAME_TITLE = "title";
            Pattern pattern = Patterns.WEB_URL;
            Matcher matcher = pattern.matcher(mMsg);
            while(matcher.find()) {
                try {
                    String title = "";
                    //Solution 1: Using Jsoup
                    //Document document = Jsoup.connect(matcher.group()).get();
                    //title = document.title();

                    // Solution 2, Search in String
                    URL url = new URL(matcher.group());
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.setRequestProperty("User-Agent", ""); //Small trick to get browser version of the website instead of mobile version
                    try {
                        InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                        String strDoc = IOUtils.toString(in);
                        title = strDoc.substring(strDoc.indexOf("<title>") + "<title>".length(), strDoc.indexOf("</title>"));
                    } finally {
                        urlConnection.disconnect();
                    }

                    JSONObject jObjLinkInfo = new JSONObject();
                    jObjLinkInfo.put(NAME_URL, matcher.group());
                    jObjLinkInfo.put(NAME_TITLE, title);
                    jArrResult.put(jObjLinkInfo);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            return jArrResult;
        }
    }

}
