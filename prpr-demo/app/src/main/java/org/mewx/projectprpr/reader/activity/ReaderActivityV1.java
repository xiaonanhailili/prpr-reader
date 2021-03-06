package org.mewx.projectprpr.reader.activity;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.afollestad.materialdialogs.GravityEnum;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.nononsenseapps.filepicker.FilePickerActivity;

import org.adw.library.widgets.discreteseekbar.DiscreteSeekBar;
import org.mewx.projectprpr.R;
import org.mewx.projectprpr.activity.DataSourceItemInitialActivity;
import org.mewx.projectprpr.global.FormatPluginManager;
import org.mewx.projectprpr.global.G;
import org.mewx.projectprpr.plugin.NovelDataSourceBasic;
import org.mewx.projectprpr.plugin.component.ChapterInfo;
import org.mewx.projectprpr.plugin.component.NetRequest;
import org.mewx.projectprpr.plugin.component.NovelContent;
import org.mewx.projectprpr.plugin.component.VolumeInfo;
import org.mewx.projectprpr.reader.loader.ReaderFormatLoader;
import org.mewx.projectprpr.reader.loader.ReaderFormatLoaderBasic;
import org.mewx.projectprpr.reader.setting.ReaderSaveBasic;
import org.mewx.projectprpr.reader.setting.ReaderSettingBasic;
import org.mewx.projectprpr.reader.slider.SlidingAdapter;
import org.mewx.projectprpr.reader.slider.SlidingLayout;
import org.mewx.projectprpr.reader.slider.base.OverlappedSlider;
import org.mewx.projectprpr.reader.view.ReaderPageViewBasic;
import org.mewx.projectprpr.toolkit.CryptoTool;
import org.mewx.projectprpr.toolkit.FileTool;
import org.mewx.projectprpr.toolkit.thirdparty.SystemBarTintManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * Created by MewX on 2015/7/10.
 * Novel Reader Engine V1.
 */
public class ReaderActivityV1 extends AppCompatActivity {
    // constant
    private static final String TAG = ReaderActivityV1.class.getSimpleName();
    public static final String TAG_NOVEL = "novel_tag";
    public static final String TAG_VOLUME = "volume_info";
    public static final String TAG_CHAPTER = "current_chapter";
    public static final String TAG_LOCAL_BOOK = "local_book"; // todo: need further optimize

    // vars
    private boolean readLocalBook;
    private String novelTag; // MUST when in local
    private String currentChapterTag;
    private VolumeInfo volumeInfo = null;
    private NovelContent nc;
    private RelativeLayout mSliderHolder;
    private SlidingLayout sl;

    // components
    private SystemBarTintManager tintManager;
    private SlidingPageAdapter mSlidingPageAdapter;
    private ReaderFormatLoader loader;
    private ReaderSettingBasic setting;
    private NovelDataSourceBasic dataSourceBasic;

    // views
    private View readerTop, readerBot;
    private View botSetting, botSeeker;
    private View btnDayNight, btnJump, btnFind, btnConfig;
    private View btnCustomFont, btnCustomBG;
    private View btnNextChapter, btnPreviewChapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.layout_reader_swipe_temp);

        // fetch values
        readLocalBook = getIntent().getBooleanExtra(TAG_LOCAL_BOOK, false);
        novelTag = getIntent().getStringExtra(TAG_NOVEL);
        if(!readLocalBook) {
            volumeInfo = (VolumeInfo) getIntent().getSerializableExtra(TAG_VOLUME);
            currentChapterTag = getIntent().getStringExtra(TAG_CHAPTER);
        } else {
            volumeInfo = new VolumeInfo(novelTag);
            volumeInfo.addToChapterList(new ChapterInfo(novelTag));
            currentChapterTag = novelTag;
        }

        // set indicator enable
        Toolbar mToolbar = (Toolbar) findViewById(R.id.toolbar_actionbar);
        setSupportActionBar(mToolbar);
        if(getSupportActionBar() != null) {
            getSupportActionBar().setTitle(readLocalBook ? novelTag: volumeInfo.getTitle());
            final Drawable upArrow = getResources().getDrawable(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
            if (upArrow != null)
                upArrow.setColorFilter(getResources().getColor(android.R.color.white), PorterDuff.Mode.SRC_ATOP);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(upArrow);
        }

        if (Build.VERSION.SDK_INT >= 16 ) {
            // Android API 22 has more effects on status bar, so ignore

            // create our manager instance after the content view is set
            tintManager = new SystemBarTintManager(this);
            // enable all tint
            tintManager.setStatusBarTintEnabled(true);
            tintManager.setNavigationBarTintEnabled(true);
            tintManager.setTintAlpha(0.0f);
            // set all color
            tintManager.setTintColor(getResources().getColor(android.R.color.black));
        }

        // find views
        mSliderHolder = (RelativeLayout) findViewById(R.id.slider_holder);
        readerTop = findViewById(R.id.reader_top);
        readerBot = findViewById(R.id.reader_bot);
        botSetting = findViewById(R.id.reader_bot_settings);
        botSeeker = findViewById(R.id.reader_bot_seeker);
        btnDayNight = findViewById(R.id.btn_daylight);
        btnJump = findViewById(R.id.btn_jump);
        btnFind = findViewById(R.id.btn_find);
        btnConfig = findViewById(R.id.btn_config);
        btnCustomFont = findViewById(R.id.btn_custom_font);
        btnCustomBG = findViewById(R.id.btn_custom_background);
        btnNextChapter = findViewById(R.id.text_next);
        btnPreviewChapter = findViewById(R.id.text_previous);

        if (readLocalBook) {
            // todo fetch local book
            requestNovelContent("SYSTEM_1_SUCCEEDED");
        } else {
            // fetch net novel content
            dataSourceBasic = DataSourceItemInitialActivity.dataSourceBasic; // get a reference
            fetchNetNovel();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(readerBot.getVisibility() != View.VISIBLE)
            hideNavigationBar();
        else
            showNavigationBar();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_reader_v1, menu);

        Drawable drawable = menu.getItem(0).getIcon();
        if (drawable != null) {
            drawable.mutate();
            drawable.setColorFilter(getResources().getColor(android.R.color.white), PorterDuff.Mode.SRC_ATOP);
        }

        return true;
    }

    private void hideNavigationBar() {
        // set navigation bar status, remember to disable "setNavigationBarTintEnabled"
        final int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        // This work only for android 4.4+
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(flags);

            // Code below is to handle presses of Volume up or Volume down.
            // Without this, after pressing volume buttons, the navigation bar will
            // show up and won't hide
            final View decorView = getWindow().getDecorView();
            decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
                @Override
                public void onSystemUiVisibilityChange(int visibility) {
                    if((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                        decorView.setSystemUiVisibility(flags);
                    }
                }
            });
        }
    }

    private void showNavigationBar() {
        // set navigation bar status, remember to disable "setNavigationBarTintEnabled"
        final int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        // This work only for android 4.4+
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(flags);

            // Code below is to handle presses of Volume up or Volume down.
            // Without this, after pressing volume buttons, the navigation bar will
            // show up and won't hide
            final View decorView = getWindow().getDecorView();
            decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
                @Override
                public void onSystemUiVisibilityChange(int visibility) {
                    if((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                        decorView.setSystemUiVisibility(flags);
                    }
                }
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // save record
        if(mSlidingPageAdapter != null && loader != null) {
            loader.setCurrentIndex(mSlidingPageAdapter.getCurrentLastLineIndex());
            if (volumeInfo.getChapterListSize() > 1 && volumeInfo.getChapterByListIndex(volumeInfo.getChapterListSize() - 1).getChapterTag().equals(currentChapterTag) && mSlidingPageAdapter.getCurrentLastWordIndex() == loader.getCurrentAsString().length() - 1)
                G.removeReadSavesRecordV1((readLocalBook ? "" : dataSourceBasic.getTag()) + novelTag);
            else
                G.addReadSavesRecordV1((readLocalBook ? "" : dataSourceBasic.getTag()) + novelTag, volumeInfo.getVolumeTag(), currentChapterTag, mSlidingPageAdapter.getCurrentFirstLineIndex(), mSlidingPageAdapter.getCurrentFirstWordIndex());
        }
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        switch(event.getKeyCode()) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                gotoNextPage();
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
                gotoPreviousPage();
                return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void fetchNetNovel() {
        if (dataSourceBasic != null) {
            try {
                String filePath = G.getProjectFolderNetNovel(dataSourceBasic.getTag(), novelTag) + File.separator
                        + CryptoTool.hashMessageDigest(volumeInfo.getVolumeTag() + currentChapterTag);
                if (FileTool.existFile(filePath)) {
                    // load from local storage
                    Log.e(TAG, "downloaded: " + filePath);
                    nc = new NovelContent(filePath);
                    requestNovelContent("SYSTEM_1_SUCCEEDED");
                } else {
                    // load from Internet
                    G.globalOkHttpClient3.newCall(dataSourceBasic.getNovelContentRequest(currentChapterTag).getOkHttpRequest(G.STANDARD_CHARSET))
                            .enqueue(new Callback() {
                                @Override
                                public void onFailure(Call call, IOException e) {
                                    e.printStackTrace();
                                    Toast.makeText(ReaderActivityV1.this, e.toString(), Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onResponse(Call call, Response response) throws IOException {
                                    String content = response.body().string();
                                    NetRequest[] netRequests = dataSourceBasic.getUltraRequests(currentChapterTag, content);
                                    byte[][] returnBytes = new byte[netRequests.length][];
                                    for (int i = 0; i < netRequests.length; i++) {
                                        Log.e(TAG, netRequests[i].getFullGetUrl());
                                        returnBytes[i] = G.globalOkHttpClient3.newCall(netRequests[i].getOkHttpRequest(G.STANDARD_CHARSET)).execute().body().bytes();
                                    }

                                    Log.e(TAG, "code: " + response.code());
                                    if (response.isSuccessful()) {
                                        dataSourceBasic.ultraReturn(currentChapterTag, returnBytes);
                                        nc = dataSourceBasic.parseNovelContent(content);
                                        requestNovelContent("SYSTEM_1_SUCCEEDED");
                                    } else {
                                        requestNovelContent("FAILED");
                                    }
                                }
                            });
                }
            } catch (Exception ok) {
                ok.printStackTrace();
                Toast.makeText(ReaderActivityV1.this, ok.toString(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Error, data source is null!", Toast.LENGTH_SHORT).show();
        }
    }

    class SlidingPageAdapter extends SlidingAdapter<ReaderPageViewBasic> {
        int firstLineIndex = 0; // line index of first index of this page
        int firstWordIndex = 0; // first index of this page
        int lastLineIndex = 0; // line index of last index of this page
        int lastWordIndex = 0; // last index of this page

        ReaderPageViewBasic nextPage;
        ReaderPageViewBasic previousPage;
        boolean isLoadingNext = false;
        boolean isLoadingPrevious = false;

        public SlidingPageAdapter(int begLineIndex, int begWordIndex) {
            super();

            // init values
            firstLineIndex = begLineIndex;
            firstWordIndex = begWordIndex;

            // check valid first
            if(firstLineIndex + 1 >= loader.getElementCount()) firstLineIndex = loader.getElementCount() - 1; // to last one
            loader.setCurrentIndex(firstLineIndex);
            if(firstWordIndex + 1 >= loader.getCurrentAsString().length()) {
                firstLineIndex --;
                firstWordIndex = 0;
                if(firstLineIndex < 0) firstLineIndex = 0;
            }
        }

        @Override
        public View getView(View contentView, ReaderPageViewBasic pageView) {
            Log.e("MewX", "-- slider getView");
            if (contentView == null)
                contentView = getLayoutInflater().inflate(R.layout.layout_reader_swipe_page, null);

            // prevent memory leak
            final RelativeLayout rl = (RelativeLayout) contentView.findViewById(R.id.page_holder);
            rl.removeAllViews();
            ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            rl.addView(pageView, lp);

            return contentView;
        }

        public int getCurrentFirstLineIndex() {
            return firstLineIndex;
        }

        public int getCurrentFirstWordIndex() {
            return firstWordIndex;
        }

        public int getCurrentLastLineIndex() {
            return lastLineIndex;
        }

        public int getCurrentLastWordIndex() {
            return lastWordIndex;
        }

        public void setCurrentIndex(int lineIndex, int wordIndex) {
            firstLineIndex = lineIndex + 1 >= loader.getElementCount() ? loader.getElementCount() - 1 : lineIndex;
            loader.setCurrentIndex(firstLineIndex);
            firstWordIndex = wordIndex + 1 >= loader.getCurrentAsString().length() ? loader.getCurrentAsString().length() - 1 : wordIndex;

            ReaderPageViewBasic temp = new ReaderPageViewBasic(ReaderActivityV1.this, firstLineIndex, firstWordIndex, ReaderPageViewBasic.LOADING_DIRECTION.CURRENT);
            firstLineIndex = temp.getFirstLineIndex();
            firstWordIndex = temp.getFirstWordIndex();
            lastLineIndex = temp.getLastLineIndex();
            lastWordIndex = temp.getLastWordIndex();
        }

        @Override
        public boolean hasNext() {
            Log.e("MewX", "-- slider hasNext");
            loader.setCurrentIndex(lastLineIndex);
            return !isLoadingNext && loader.hasNext(lastWordIndex);
        }

        @Override
        protected void computeNext() {
            Log.e("MewX", "-- slider computeNext");
            // vars change to next
            //if(nextPage == null) return;

            nextPage = new ReaderPageViewBasic(ReaderActivityV1.this, lastLineIndex, lastWordIndex, ReaderPageViewBasic.LOADING_DIRECTION.FORWARDS);
            firstLineIndex = nextPage.getFirstLineIndex();
            firstWordIndex = nextPage.getFirstWordIndex();
            lastLineIndex = nextPage.getLastLineIndex();
            lastWordIndex = nextPage.getLastWordIndex();
            printLog();
        }

        @Override
        protected void computePrevious() {
            Log.e("MewX", "-- slider computePrevious");
            // vars change to previous
//            if(previousPage == null) return;
//            loader.setCurrentIndex(firstLineIndex);

            ReaderPageViewBasic previousPage = new ReaderPageViewBasic(ReaderActivityV1.this, firstLineIndex, firstWordIndex, ReaderPageViewBasic.LOADING_DIRECTION.BACKWARDS);
            firstLineIndex = previousPage.getFirstLineIndex();
            firstWordIndex = previousPage.getFirstWordIndex();
            lastLineIndex = previousPage.getLastLineIndex();
            lastWordIndex = previousPage.getLastWordIndex();

            // reset first page
//            if(firstLineIndex == 0 && firstWordIndex == 0)
//                notifyDataSetChanged();
            printLog();
        }

        @Override
        public ReaderPageViewBasic getNext() {
            Log.e("MewX", "-- slider getNext");
//            isLoadingNext = true;
            nextPage = new ReaderPageViewBasic(ReaderActivityV1.this, lastLineIndex, lastWordIndex, ReaderPageViewBasic.LOADING_DIRECTION.FORWARDS);
//            isLoadingNext = false;
            return nextPage;
        }

        @Override
        public boolean hasPrevious() {
            Log.e("MewX", "-- slider hasPrevious");
            loader.setCurrentIndex(firstLineIndex);
            return !isLoadingPrevious && loader.hasPrevious(firstWordIndex);
        }

        @Override
        public ReaderPageViewBasic getPrevious() {
            Log.e("MewX", "-- slider getPrevious");
//            isLoadingPrevious = true;
            previousPage = new ReaderPageViewBasic(ReaderActivityV1.this, firstLineIndex, firstWordIndex, ReaderPageViewBasic.LOADING_DIRECTION.BACKWARDS);
//            isLoadingPrevious = false;
            return previousPage;
        }

        @Override
        public ReaderPageViewBasic getCurrent() {
            Log.e("MewX", "-- slider getCurrent");
            ReaderPageViewBasic temp = new ReaderPageViewBasic(ReaderActivityV1.this, firstLineIndex, firstWordIndex, ReaderPageViewBasic.LOADING_DIRECTION.CURRENT);
            firstLineIndex = temp.getFirstLineIndex();
            firstWordIndex = temp.getFirstWordIndex();
            lastLineIndex = temp.getLastLineIndex();
            lastWordIndex = temp.getLastWordIndex();
            printLog();
            return temp;
        }

        private void printLog() {
            Log.e("MewX", "saved index: " + firstLineIndex + "(" + firstWordIndex + ") -> " + lastLineIndex + "(" + lastWordIndex + ") | Total: " + loader.getCurrentIndex() + " of " + (loader.getElementCount()-1) );
        }
    }

    protected void requestNovelContent(String result) {
        if (!result.equals("SYSTEM_1_SUCCEEDED")) {
            Toast.makeText(ReaderActivityV1.this, result, Toast.LENGTH_LONG).show();
            ReaderActivityV1.this.finish(); // return friendly
            return;
        }
        Log.e("MewX", "-- 小说获取完成");

        // init components
        Log.e(TAG, "NovelTag: " + novelTag);
        loader = readLocalBook ? FormatPluginManager.detectFileAndLoadFormatLoader(novelTag) : new ReaderFormatLoaderBasic(nc);
        if (loader == null) {
            Toast.makeText(this, "error loading loader class!", Toast.LENGTH_SHORT).show();
            finish();
        }
        setting = new ReaderSettingBasic();
        loader.setCurrentIndex(0);

        if(!readLocalBook) {
            for (int i = 0; i < volumeInfo.getChapterListSize(); i++) {
                // get chapter name
                if (volumeInfo.getChapterByListIndex(i).getChapterTag().equals(currentChapterTag)) {
                    loader.setChapterName(volumeInfo.getChapterByListIndex(i).getTitle());
                    break;
                }
            }
        }

        // config sliding layout
        mSlidingPageAdapter = new SlidingPageAdapter(0, 0);
        ReaderPageViewBasic.setViewComponents(loader, setting, false);
        Log.e("MewX", "-- loader, setting 初始化完成");
        sl = new SlidingLayout(ReaderActivityV1.this);
        final ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        sl.setAdapter(mSlidingPageAdapter);
        sl.setSlider(new OverlappedSlider());
        sl.setOnTapListener(new SlidingLayout.OnTapListener() {
            boolean barStatus = false;
            boolean isSet = false;

            @Override
            public void onSingleTap(MotionEvent event) {
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                int x = (int) event.getX();
                int y = (int) event.getY();

                if (x > screenWidth / 3 && x < screenWidth * 2 / 3 && y > screenHeight / 3 && y < screenHeight * 2 / 3) {
                    // first init
                    if (!barStatus) {
                        showNavigationBar();
                        readerTop.setVisibility(View.VISIBLE);
                        readerBot.setVisibility(View.VISIBLE);

                        if (Build.VERSION.SDK_INT >= 16) {
                            tintManager.setStatusBarAlpha(0.90f);
                            tintManager.setNavigationBarAlpha(0.80f); // TODO: fix bug
                        }
                        barStatus = true;

                        if (!isSet) {
                            // add action to each
                            btnDayNight.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    // switch day/night mode
                                    ReaderPageViewBasic.switchDayMode();
                                    ReaderPageViewBasic.resetTextColor();
                                    mSlidingPageAdapter.restoreState(null, null);
                                    mSlidingPageAdapter.notifyDataSetChanged();
                                }
                            });
                            btnDayNight.setOnLongClickListener(new View.OnLongClickListener() {
                                @Override
                                public boolean onLongClick(View v) {
                                    Toast.makeText(ReaderActivityV1.this, getResources().getString(R.string.reader_daynight), Toast.LENGTH_SHORT).show();
                                    return true;
                                }
                            });

                            btnJump.setOnClickListener(new View.OnClickListener() {
                                boolean isOpen = false;

                                @Override
                                public void onClick(View v) {
                                    // show jump dialog
                                    if (botSetting.getVisibility() == View.VISIBLE || botSeeker.getVisibility() == View.INVISIBLE) {
                                        isOpen = false;
                                        botSetting.setVisibility(View.INVISIBLE);
                                    }
                                    if (!isOpen)
                                        botSeeker.setVisibility(View.VISIBLE);
                                    else
                                        botSeeker.setVisibility(View.INVISIBLE);
                                    isOpen = !isOpen;

                                    DiscreteSeekBar seeker = (DiscreteSeekBar) findViewById(R.id.reader_seekbar);
                                    seeker.setMin(1);
                                    seeker.setProgress(mSlidingPageAdapter.getCurrentFirstLineIndex() + 1); // bug here
                                    seeker.setMax(loader.getElementCount());
                                    seeker.setOnProgressChangeListener(new DiscreteSeekBar.OnProgressChangeListener() {
                                        @Override
                                        public void onProgressChanged(DiscreteSeekBar discreteSeekBar, int i, boolean b) {
                                        }

                                        @Override
                                        public void onStartTrackingTouch(DiscreteSeekBar discreteSeekBar) {
                                        }

                                        @Override
                                        public void onStopTrackingTouch(DiscreteSeekBar discreteSeekBar) {
                                            mSlidingPageAdapter.setCurrentIndex(discreteSeekBar.getProgress() - 1, 0);
                                            mSlidingPageAdapter.restoreState(null, null);
                                            mSlidingPageAdapter.notifyDataSetChanged();
                                        }
                                    });
                                }
                            });
                            btnJump.setOnLongClickListener(new View.OnLongClickListener() {
                                @Override
                                public boolean onLongClick(View v) {
                                    Toast.makeText(ReaderActivityV1.this, getResources().getString(R.string.reader_jump), Toast.LENGTH_SHORT).show();
                                    return true;
                                }
                            });

                            btnFind.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    // show label page
                                    Toast.makeText(ReaderActivityV1.this, "查找功能尚未就绪", Toast.LENGTH_SHORT).show();
                                }
                            });
                            btnFind.setOnLongClickListener(new View.OnLongClickListener() {
                                @Override
                                public boolean onLongClick(View v) {
                                    Toast.makeText(ReaderActivityV1.this, getResources().getString(R.string.reader_find), Toast.LENGTH_SHORT).show();
                                    return true;
                                }
                            });

                            btnConfig.setOnClickListener(new View.OnClickListener() {
                                private boolean isOpen = false;

                                @Override
                                public void onClick(View v) {
                                    // show jump dialog
                                    if (botSeeker.getVisibility() == View.VISIBLE || botSetting.getVisibility() == View.INVISIBLE) {
                                        isOpen = false;
                                        botSeeker.setVisibility(View.INVISIBLE);
                                    }
                                    if (!isOpen)
                                        botSetting.setVisibility(View.VISIBLE);
                                    else
                                        botSetting.setVisibility(View.INVISIBLE);
                                    isOpen = !isOpen;

                                    // set all listeners
                                    DiscreteSeekBar seekerFontSize = (DiscreteSeekBar) findViewById(R.id.reader_font_size_seeker),
                                            seekerLineDistance = (DiscreteSeekBar) findViewById(R.id.reader_line_distance_seeker),
                                            seekerParagraphDistance = (DiscreteSeekBar) findViewById(R.id.reader_paragraph_distance_seeker),
                                            seekerParagraphEdgeDistance = (DiscreteSeekBar) findViewById(R.id.reader_paragraph_edge_distance_seeker);

                                    seekerFontSize.setProgress(setting.getFontSize());
                                    seekerFontSize.setOnProgressChangeListener(new DiscreteSeekBar.OnProgressChangeListener() {
                                        @Override
                                        public void onProgressChanged(DiscreteSeekBar discreteSeekBar, int i, boolean b) {
                                        }

                                        @Override
                                        public void onStartTrackingTouch(DiscreteSeekBar discreteSeekBar) {
                                        }

                                        @Override
                                        public void onStopTrackingTouch(DiscreteSeekBar discreteSeekBar) {
                                            setting.setFontSize(discreteSeekBar.getProgress());
                                            ReaderPageViewBasic.setViewComponents(loader, setting, false);
                                            mSlidingPageAdapter.restoreState(null, null);
                                            mSlidingPageAdapter.notifyDataSetChanged();
                                        }
                                    });

                                    seekerLineDistance.setProgress(setting.getLineDistance());
                                    seekerLineDistance.setOnProgressChangeListener(new DiscreteSeekBar.OnProgressChangeListener() {
                                        @Override
                                        public void onProgressChanged(DiscreteSeekBar discreteSeekBar, int i, boolean b) {
                                        }

                                        @Override
                                        public void onStartTrackingTouch(DiscreteSeekBar discreteSeekBar) {
                                        }

                                        @Override
                                        public void onStopTrackingTouch(DiscreteSeekBar discreteSeekBar) {
                                            setting.setLineDistance(discreteSeekBar.getProgress());
                                            ReaderPageViewBasic.setViewComponents(loader, setting, false);
                                            mSlidingPageAdapter.restoreState(null, null);
                                            mSlidingPageAdapter.notifyDataSetChanged();
                                        }
                                    });

                                    seekerParagraphDistance.setProgress(setting.getParagraphDistance());
                                    seekerParagraphDistance.setOnProgressChangeListener(new DiscreteSeekBar.OnProgressChangeListener() {
                                        @Override
                                        public void onProgressChanged(DiscreteSeekBar discreteSeekBar, int i, boolean b) {
                                        }

                                        @Override
                                        public void onStartTrackingTouch(DiscreteSeekBar discreteSeekBar) {
                                        }

                                        @Override
                                        public void onStopTrackingTouch(DiscreteSeekBar discreteSeekBar) {
                                            setting.setParagraphDistance(discreteSeekBar.getProgress());
                                            ReaderPageViewBasic.setViewComponents(loader, setting, false);
                                            mSlidingPageAdapter.restoreState(null, null);
                                            mSlidingPageAdapter.notifyDataSetChanged();
                                        }
                                    });

                                    seekerParagraphEdgeDistance.setProgress(setting.getParagraphEdgeDistance());
                                    seekerParagraphEdgeDistance.setOnProgressChangeListener(new DiscreteSeekBar.OnProgressChangeListener() {
                                        @Override
                                        public void onProgressChanged(DiscreteSeekBar discreteSeekBar, int i, boolean b) {
                                        }

                                        @Override
                                        public void onStartTrackingTouch(DiscreteSeekBar discreteSeekBar) {
                                        }

                                        @Override
                                        public void onStopTrackingTouch(DiscreteSeekBar discreteSeekBar) {
                                            setting.setParagraphEdgeDistance(discreteSeekBar.getProgress());
                                            ReaderPageViewBasic.setViewComponents(loader, setting, false);
                                            mSlidingPageAdapter.restoreState(null, null);
                                            mSlidingPageAdapter.notifyDataSetChanged();
                                        }
                                    });

                                    btnCustomFont.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            new MaterialDialog.Builder(ReaderActivityV1.this)
                                                    .theme(ReaderPageViewBasic.getInDayMode() ? Theme.LIGHT : Theme.DARK)
                                                    .title(R.string.reader_custom_font)
                                                    .items(R.array.reader_font_option)
                                                    .itemsCallback(new MaterialDialog.ListCallback() {
                                                        @Override
                                                        public void onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
                                                            switch (which) {
                                                                case 0:
                                                                    // system default
                                                                    setting.setCustomFontPath(ReaderSettingBasic.NO_EXTRA_FONT);
                                                                    ReaderPageViewBasic.setViewComponents(loader, setting, false);
                                                                    mSlidingPageAdapter.restoreState(null, null);
                                                                    mSlidingPageAdapter.notifyDataSetChanged();
                                                                    break;
                                                                case 1:
                                                                    // choose a ttf file
                                                                    Intent i = new Intent(ReaderActivityV1.this, FilePickerActivity.class);
                                                                    i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false);
                                                                    i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true);
                                                                    i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_FILE);
                                                                    i.putExtra(FilePickerActivity.EXTRA_START_PATH,
                                                                            TextUtils.isEmpty(G.pathPickedSave) ?
                                                                                    Environment.getExternalStorageDirectory().getPath() : G.pathPickedSave);
                                                                    startActivityForResult(i, 0); // choose font is 0
                                                                    break;
                                                            }
                                                        }
                                                    })
                                                    .show();
                                        }
                                    });

                                    btnCustomBG.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            new MaterialDialog.Builder(ReaderActivityV1.this)
                                                    .theme(ReaderPageViewBasic.getInDayMode() ? Theme.LIGHT : Theme.DARK)
                                                    .title(R.string.reader_custom_background)
                                                    .items(R.array.reader_background_option)
                                                    .itemsCallback(new MaterialDialog.ListCallback() {
                                                        @Override
                                                        public void onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
                                                            switch (which) {
                                                                case 0:
                                                                    // system default
                                                                    setting.setPageBackgroundType(ReaderSettingBasic.PAGE_BACKGROUND_TYPE.SYSTEM_DEFAULT);
                                                                    ReaderPageViewBasic.setViewComponents(loader, setting, true);
                                                                    mSlidingPageAdapter.restoreState(null, null);
                                                                    mSlidingPageAdapter.notifyDataSetChanged();
                                                                    break;
                                                                case 1:
                                                                    // choose a image file
                                                                    Intent i = new Intent(ReaderActivityV1.this, FilePickerActivity.class);
                                                                    i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false);
                                                                    i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true);
                                                                    i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_FILE);
                                                                    i.putExtra(FilePickerActivity.EXTRA_START_PATH,
                                                                            TextUtils.isEmpty(G.pathPickedSave) ?
                                                                                    Environment.getExternalStorageDirectory().getPath() : G.pathPickedSave);
                                                                    startActivityForResult(i, 1); // choose image is 1
                                                                    break;
                                                            }
                                                        }
                                                    })
                                                    .show();
                                        }
                                    });
                                }
                            });
                            btnConfig.setOnLongClickListener(new View.OnLongClickListener() {
                                @Override
                                public boolean onLongClick(View v) {
                                    Toast.makeText(ReaderActivityV1.this, getResources().getString(R.string.reader_config), Toast.LENGTH_SHORT).show();
                                    return true;
                                }
                            });

                            btnPreviewChapter.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    // goto previous chapter
                                    for (int i = 0; i < volumeInfo.getChapterListSize(); i++) {
                                        if (currentChapterTag.equals(volumeInfo.getChapterByListIndex(i).getChapterTag())) {
                                            // found self
                                            if (i == 0) {
                                                // no more previous
                                                Toast.makeText(ReaderActivityV1.this, getResources().getString(R.string.reader_already_first_chapter), Toast.LENGTH_SHORT).show();
                                            } else {
                                                // jump to previous
                                                final int i_bak = i;
                                                new MaterialDialog.Builder(ReaderActivityV1.this)
                                                        .callback(new MaterialDialog.ButtonCallback() {
                                                            @Override
                                                            public void onPositive(MaterialDialog dialog) {
                                                                super.onPositive(dialog);
                                                                Intent intent = new Intent(ReaderActivityV1.this, ReaderActivityV1.class); //VerticalReaderActivity.class);
                                                                intent.putExtra(TAG_NOVEL, novelTag);
                                                                intent.putExtra(TAG_VOLUME, volumeInfo);
                                                                intent.putExtra(TAG_CHAPTER, volumeInfo.getChapterByListIndex(i_bak - 1).getChapterTag());
                                                                startActivity(intent);
                                                                overridePendingTransition(R.anim.fade_in, R.anim.hold); // fade in animation
                                                                ReaderActivityV1.this.finish();
                                                            }
                                                        })
                                                        .theme(ReaderPageViewBasic.getInDayMode() ? Theme.LIGHT : Theme.DARK)
                                                        .title(R.string.reader_jump)
                                                        .positiveText(R.string.dialog_positive_yes)
                                                        .negativeText(R.string.dialog_negative_no)
                                                        .content(volumeInfo.getChapterByListIndex(i_bak - 1).getTitle())
                                                        .contentGravity(GravityEnum.CENTER)
                                                        .show();
                                            }
                                            break;
                                        }
                                    }
                                }
                            });

                            btnNextChapter.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    // goto next chapter
                                    for (int i = 0; i < volumeInfo.getChapterListSize(); i++) {
                                        if (currentChapterTag.equals(volumeInfo.getChapterByListIndex(i).getChapterTag())) {
                                            // found self
                                            if (i + 1 >= volumeInfo.getChapterListSize()) {
                                                // no more previous
                                                Toast.makeText(ReaderActivityV1.this, getResources().getString(R.string.reader_already_last_chapter), Toast.LENGTH_SHORT).show();
                                            } else {
                                                // jump to previous
                                                final int i_bak = i;
                                                new MaterialDialog.Builder(ReaderActivityV1.this)
                                                        .callback(new MaterialDialog.ButtonCallback() {
                                                            @Override
                                                            public void onPositive(MaterialDialog dialog) {
                                                                super.onPositive(dialog);
                                                                Intent intent = new Intent(ReaderActivityV1.this, ReaderActivityV1.class); //VerticalReaderActivity.class);
                                                                intent.putExtra(TAG_NOVEL, novelTag);
                                                                intent.putExtra(TAG_VOLUME, volumeInfo);
                                                                intent.putExtra(TAG_CHAPTER, volumeInfo.getChapterByListIndex(i_bak + 1).getChapterTag());
                                                                startActivity(intent);
                                                                overridePendingTransition(R.anim.fade_in, R.anim.hold); // fade in animation
                                                                ReaderActivityV1.this.finish();
                                                            }
                                                        })
                                                        .theme(ReaderPageViewBasic.getInDayMode() ? Theme.LIGHT : Theme.DARK)
                                                        .title(R.string.reader_jump)
                                                        .positiveText(R.string.dialog_positive_yes)
                                                        .negativeText(R.string.dialog_negative_no)
                                                        .content(volumeInfo.getChapterByListIndex(i_bak + 1).getTitle())
                                                        .contentGravity(GravityEnum.CENTER)
                                                        .show();
                                            }
                                            break;
                                        }
                                    }
                                }
                            });
                        }
                    } else {
                        // show menu
                        hideNavigationBar();
                        readerTop.setVisibility(View.INVISIBLE);
                        readerBot.setVisibility(View.INVISIBLE);
                        botSeeker.setVisibility(View.INVISIBLE);
                        botSetting.setVisibility(View.INVISIBLE);
                        if (Build.VERSION.SDK_INT >= 16) {
                            tintManager.setStatusBarAlpha(0.0f);
                            tintManager.setNavigationBarAlpha(0.0f);
                        }
                        barStatus = false;
                    }
                    return;
                }

                if (x > screenWidth / 2) {
                    gotoNextPage();
                } else if (x <= screenWidth / 2) {
                    gotoPreviousPage();
                }
            }
        });
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mSliderHolder.addView(sl, 0, lp);
                Log.e("MewX", "-- slider创建完毕");

                // todo: end loading dialog

                // show dialog, jump to last read position
                ReaderSaveBasic rs = G.getReadSavesRecordV1((readLocalBook ? "" : dataSourceBasic.getTag()) + novelTag);
                if (rs != null && rs.vid.equals(volumeInfo.getVolumeTag()) && rs.cid.equals(currentChapterTag)) {
                    mSlidingPageAdapter.setCurrentIndex(rs.lineId, rs.wordId);
                    mSlidingPageAdapter.restoreState(null, null);
                    mSlidingPageAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    private void gotoNextPage() {
        if(mSlidingPageAdapter != null && !mSlidingPageAdapter.hasNext()) {
            // goto next chapter
            for (int i = 0; i < volumeInfo.getChapterListSize(); i++) {
                if (currentChapterTag.equals(volumeInfo.getChapterByListIndex(i).getChapterTag())) {
                    // found self
                    if (i + 1 >= volumeInfo.getChapterListSize()) {
                        // no more previous
                        Toast.makeText(ReaderActivityV1.this, getResources().getString(R.string.reader_already_last_chapter), Toast.LENGTH_SHORT).show();
                    } else {
                        // jump to previous
                        final int i_bak = i;
                        new MaterialDialog.Builder(ReaderActivityV1.this)
                                .callback(new MaterialDialog.ButtonCallback() {
                                    @Override
                                    public void onPositive(MaterialDialog dialog) {
                                        super.onPositive(dialog);
                                        Intent intent = new Intent(ReaderActivityV1.this, ReaderActivityV1.class); //VerticalReaderActivity.class);
                                        intent.putExtra(TAG_NOVEL, novelTag);
                                        intent.putExtra(TAG_VOLUME, volumeInfo);
                                        intent.putExtra(TAG_CHAPTER, volumeInfo.getChapterByListIndex(i_bak + 1).getChapterTag());
                                        startActivity(intent);
                                        overridePendingTransition(R.anim.fade_in, R.anim.hold); // fade in animation
                                        ReaderActivityV1.this.finish();
                                    }
                                })
                                .theme(ReaderPageViewBasic.getInDayMode() ? Theme.LIGHT : Theme.DARK)
                                .title(R.string.reader_jump_last)
                                .positiveText(R.string.dialog_positive_yes)
                                .negativeText(R.string.dialog_negative_no)
                                .content(volumeInfo.getChapterByListIndex(i_bak + 1).getTitle())
                                .contentGravity(GravityEnum.CENTER)
                                .show();
                    }
                    break;
                }
            }
        }
        else {
            if(sl != null)
                sl.slideNext();
        }
    }

    private void gotoPreviousPage() {
        if(mSlidingPageAdapter != null && !mSlidingPageAdapter.hasPrevious()) {
            // goto previous chapter
            for (int i = 0; i < volumeInfo.getChapterListSize(); i++) {
                if (currentChapterTag.equals(volumeInfo.getChapterByListIndex(i).getChapterTag())) {
                    // found self
                    if (i == 0) {
                        // no more previous
                        Toast.makeText(ReaderActivityV1.this, getResources().getString(R.string.reader_already_first_chapter), Toast.LENGTH_SHORT).show();
                    } else {
                        // jump to previous
                        final int i_bak = i;
                        new MaterialDialog.Builder(ReaderActivityV1.this)
                                .callback(new MaterialDialog.ButtonCallback() {
                                    @Override
                                    public void onPositive(MaterialDialog dialog) {
                                        super.onPositive(dialog);
                                        Intent intent = new Intent(ReaderActivityV1.this, ReaderActivityV1.class); //VerticalReaderActivity.class);
                                        intent.putExtra(TAG_NOVEL, novelTag);
                                        intent.putExtra(TAG_VOLUME, volumeInfo);
                                        intent.putExtra(TAG_CHAPTER, volumeInfo.getChapterByListIndex(i_bak - 1).getChapterTag());
                                        startActivity(intent);
                                        overridePendingTransition(R.anim.fade_in, R.anim.hold); // fade in animation
                                        ReaderActivityV1.this.finish();
                                    }
                                })
                                .theme(ReaderPageViewBasic.getInDayMode() ? Theme.LIGHT : Theme.DARK)
                                .title(R.string.reader_jump_last)
                                .positiveText(R.string.dialog_positive_yes)
                                .negativeText(R.string.dialog_negative_no)
                                .content(volumeInfo.getChapterByListIndex(i_bak - 1).getTitle())
                                .contentGravity(GravityEnum.CENTER)
                                .show();
                    }
                    break;
                }
            }
        }
        else {
            if(sl != null)
                sl.slidePrevious();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 0 && resultCode == Activity.RESULT_OK) {
            // get ttf path
            if (data.getBooleanExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false)) {
                // For JellyBean and above
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    ClipData clip = data.getClipData();
                    if (clip != null) {
                        for (int i = 0; i < clip.getItemCount(); i++) {
                            Uri uri = clip.getItemAt(i).getUri();
                            // Do something with the URI
                            runSaveCustomFontPath(uri.getPath().replaceAll("file://", ""));
                        }
                    }
                    // For Ice Cream Sandwich
                } else {
                    ArrayList<String> paths = data.getStringArrayListExtra(FilePickerActivity.EXTRA_PATHS);
                    if (paths != null) {
                        for (String path: paths) {
                            Uri uri = Uri.parse(path);
                            // Do something with the URI
                            runSaveCustomFontPath(uri.getPath().replaceAll("file://", ""));
                        }
                    }
                }
            } else {
                Uri uri = data.getData();
                // Do something with the URI
                runSaveCustomFontPath(uri.getPath().replaceAll("file://", ""));
            }
        }
        else if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            // get image path
            if (data.getBooleanExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false)) {
                // For JellyBean and above
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    ClipData clip = data.getClipData();
                    if (clip != null) {
                        for (int i = 0; i < clip.getItemCount(); i++) {
                            Uri uri = clip.getItemAt(i).getUri();
                            // Do something with the URI
                            runSaveCustomBackgroundPath(uri.getPath().replaceAll("file://", ""));
                        }
                    }
                    // For Ice Cream Sandwich
                } else {
                    ArrayList<String> paths = data.getStringArrayListExtra(FilePickerActivity.EXTRA_PATHS);
                    if (paths != null) {
                        for (String path: paths) {
                            Uri uri = Uri.parse(path);
                            // Do something with the URI
                            runSaveCustomBackgroundPath(uri.getPath().replaceAll("file://", ""));
                        }
                    }
                }
            } else {
                Uri uri = data.getData();
                // Do something with the URI
                runSaveCustomBackgroundPath(uri.getPath().replaceAll("file://", ""));
            }

        }
    }

    private void runSaveCustomFontPath(String path) {
        setting.setCustomFontPath(path);
        ReaderPageViewBasic.setViewComponents(loader, setting, false);
        mSlidingPageAdapter.restoreState(null, null);
        mSlidingPageAdapter.notifyDataSetChanged();
    }

    private void runSaveCustomBackgroundPath(String path) {
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(path);
        } catch (OutOfMemoryError oome) {
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 2;
                Bitmap bitmap = BitmapFactory.decodeFile(path, options);
                if(bitmap == null) throw new Exception("PictureDecodeFailedException");
            } catch(Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Exception: " + e.toString() + "\n可能的原因有：图片不在内置SD卡；图片格式不正确；图片像素尺寸太大，请使用小一点的图，谢谢，此功能为试验性功能；", Toast.LENGTH_LONG).show();
                return;
            }
        }
        setting.setPageBackgroundCustomPath(path);
        ReaderPageViewBasic.setViewComponents(loader, setting, true);
        mSlidingPageAdapter.restoreState(null, null);
        mSlidingPageAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
            case R.id.action_watch_image:
                if(sl != null && sl.getAdapter().getCurrentView() != null && ((RelativeLayout) sl.getAdapter().getCurrentView()).getChildAt(0) instanceof ReaderPageViewBasic)
                    ((ReaderPageViewBasic) ((RelativeLayout) sl.getAdapter().getCurrentView()).getChildAt(0)).watchImageDetailed(this);
                break;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(0, R.anim.fade_out);
    }
}
