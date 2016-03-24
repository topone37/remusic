package com.wm.remusic.activity;


import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.Scroller;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.wm.remusic.uitl.IConstants;
import com.wm.remusic.info.MusicInfo;
import com.wm.remusic.provider.PlaylistsManager;
import com.wm.remusic.R;
import com.wm.remusic.fragment.MoreFragment;
import com.wm.remusic.fragment.PlayQueueFragment;
import com.wm.remusic.fragment.RoundFragment;
import com.wm.remusic.service.MediaService;
import com.wm.remusic.service.MusicPlayer;
import com.wm.remusic.service.MusicTrack;
import com.wm.remusic.uitl.ImageUtils;
import com.wm.remusic.uitl.MusicUtils;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;


/**
 * Created by wm on 2016/2/21.
 */
public class PlayingActivity extends BaseActivity implements IConstants {
    ImageView backAlbum, playingmode, control, next, pre, playlist, cmt, fav, down, more, needle;
    TextView timePlayed, duration;
    SeekBar mProgress;
    ActionBar ab;
    ObjectAnimator needleAnim, animator;
    AnimatorSet animatorSet;
    ViewPager mViewPager;
    FragmentAdapter fAdapter;
    private  boolean isFav = false;
    private boolean isNextOrPreSetPage = false; //判断viewpager由手动滑动 还是setcruuentitem换页
    private boolean duetoplaypause = false; //判读是否是播放暂停的通知，不要切换专辑封面

    BitmapFactory.Options newOpts;
    View activeView;
    PlaylistsManager playlistsManager;
    WeakReference<ObjectAnimator> animatorWeakReference;



    public Runnable mUpdateProgress = new Runnable() {

        @Override
        public void run() {

            if (mProgress != null) {
                long position = MusicPlayer.position();
                mProgress.setProgress((int) position);
                timePlayed.setText(MusicUtils.makeShortTimeString(PlayingActivity.this.getApplication(), position / 1000));
            }

            if (MusicPlayer.isPlaying()) {
                mProgress.postDelayed(mUpdateProgress, 50);
            }

        }
    };


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playing);
        playlistsManager = PlaylistsManager.getInstance(this);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            ab = getSupportActionBar();
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setHomeAsUpIndicator(R.drawable.actionbar_back);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onBackPressed();
                }
            });
        }

        backAlbum = (ImageView) findViewById(R.id.albumArt);
        playingmode = (ImageView) findViewById(R.id.playing_mode);
        control = (ImageView) findViewById(R.id.playing_play);
        next = (ImageView) findViewById(R.id.playing_next);
        pre = (ImageView) findViewById(R.id.playing_pre);
        playlist = (ImageView) findViewById(R.id.playing_playlist);
        more = (ImageView) findViewById(R.id.playing_more);
        cmt = (ImageView) findViewById(R.id.playing_cmt);
        fav = (ImageView) findViewById(R.id.playing_fav);
        down = (ImageView) findViewById(R.id.playing_down);
        timePlayed = (TextView) findViewById(R.id.music_duration_played);
        duration = (TextView) findViewById(R.id.music_duration);
        mProgress = (SeekBar) findViewById(R.id.play_seek);
        needle = (ImageView) findViewById(R.id.needle);
        mViewPager = (ViewPager) findViewById(R.id.view_pager);

        needleAnim = ObjectAnimator.ofFloat(needle, "rotation", -30, 0);
        needleAnim.setDuration(60);
        needleAnim.setRepeatMode(0);
        needleAnim.setInterpolator(new LinearInterpolator());



        PlaybarPagerTransformer transformer = new PlaybarPagerTransformer();
        fAdapter = new FragmentAdapter(getSupportFragmentManager());
        mViewPager.setAdapter(fAdapter);
        mViewPager.setPageTransformer(true, transformer);

        try {
            Field mField = ViewPager.class.getDeclaredField("mScroller");
            mField.setAccessible(true);
            MyScroller mScroller = new MyScroller(mViewPager.getContext().getApplicationContext(), new AccelerateInterpolator());
            mField.set(mViewPager, mScroller);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }


        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            @Override
            public void onPageSelected(final int pPosition) {
                if (pPosition < 1) { //首位之前，跳转到末尾（N）
                    mViewPager.setCurrentItem(MusicPlayer.getQueue().length, false);
                    MusicPlayer.setQueuePosition(MusicPlayer.getQueue().length);
                    isNextOrPreSetPage = false;
                    return;

                } else if (pPosition > MusicPlayer.getQueue().length) { //末位之后，跳转到首位（1）
                    mViewPager.setCurrentItem(1, false); //false:不显示跳转过程的动画
                    MusicPlayer.setQueuePosition(0);
                    isNextOrPreSetPage = false;
                    return;
                } else {

                    if (isNextOrPreSetPage == false) {
                        if (pPosition < MusicPlayer.getQueuePosition() + 1) {
                            MusicPlayer.previous(PlayingActivity.this, true);
                        } else if (pPosition > MusicPlayer.getQueuePosition() + 1) {
                            MusicPlayer.next();
                        }
                    }

                }
                //MusicPlayer.setQueuePosition(pPosition - 1);
                isNextOrPreSetPage = false;

            }

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageScrollStateChanged(int pState) {
            }
        });

        setSeekBarListener();
        setTools();
    }

    private void setTools() {
        playingmode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MusicPlayer.cycleRepeat();
                updatePlaymode();
            }
        });

        pre.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MusicPlayer.previous(PlayingActivity.this.getApplication(), true);
            }
        });

        control.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                duetoplaypause = true;
                if (MusicPlayer.isPlaying()) {
                    control.setImageResource(R.drawable.play_rdi_btn_pause);
                } else {
                    control.setImageResource(R.drawable.play_rdi_btn_play);
                }
                MusicPlayer.playOrPause();
            }
        });

        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MusicPlayer.next();
            }
        });

        playlist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlayQueueFragment playQueueFragment = new PlayQueueFragment();
                playQueueFragment.show(getSupportFragmentManager(), "playlistframent");
            }
        });

        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                MoreFragment moreFragment = new MoreFragment().newInstance(String.valueOf(MusicPlayer.getCurrentAudioId()), IConstants.MUSICOVERFLOW);
                moreFragment.show(getSupportFragmentManager(), "music");
            }
        });

        fav.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                if (isFav == true) {
                    playlistsManager.removeItem(PlayingActivity.this, 10, MusicPlayer.getCurrentAudioId());
                    fav.setImageResource(R.drawable.play_rdi_icn_love);
                } else {
                    playlistsManager.Insert(PlayingActivity.this, 10, MusicPlayer.getCurrentAudioId(), 10);
                    fav.setImageResource(R.drawable.play_icn_loved);
                }
                Intent intent = new Intent(IConstants.PLAYLIST_COUNT_CHANGED);
                sendBroadcast(intent);
            }
        });


    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // handle item selection
        if (item.getItemId() == R.id.menu_share) {
            MusicInfo musicInfo = MusicUtils.getMusicInfo(PlayingActivity.this, MusicPlayer.getCurrentAudioId());
            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + musicInfo.data));
            shareIntent.setType("audio/*");
            this.startActivity(Intent.createChooser(shareIntent, "分享到"));

        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.playing_menu, menu);
        return true;

    }


    private void updatePlaymode() {
        if (MusicPlayer.getShuffleMode() == MediaService.SHUFFLE_NORMAL) {
            playingmode.setImageResource(R.drawable.play_icn_shuffle);
            Toast.makeText(PlayingActivity.this.getApplication(), "随机播放", Toast.LENGTH_SHORT).show();
            return;
        } else {
            switch (MusicPlayer.getRepeatMode()) {
                case MediaService.REPEAT_ALL:
                    playingmode.setImageResource(R.drawable.play_icn_loop);
                    Toast.makeText(PlayingActivity.this.getApplication(), "列表循环", Toast.LENGTH_SHORT).show();
                    break;
                case MediaService.REPEAT_CURRENT:
                    playingmode.setImageResource(R.drawable.play_icn_one);
                    Toast.makeText(PlayingActivity.this.getApplication(), "单曲播放", Toast.LENGTH_SHORT).show();
                    break;
            }
        }

    }


    static class MyHandler extends Handler {
        WeakReference<Activity> mActivityReference;

        MyHandler(Activity activity) {
            mActivityReference = new WeakReference<Activity>(activity);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
//        IntentFilter f = new IntentFilter();
//        f.addAction(MediaService.PLAYSTATE_CHANGED);
//        f.addAction(MediaService.META_CHANGED);
//        f.addAction(MediaService.QUEUE_CHANGED);
//        f.addAction(IConstants.MUSIC_COUNT_CHANGED);
//        registerReceiver(mStatusListener, new IntentFilter(f));

        //设置ViewPager的默认项
        mViewPager.setCurrentItem(MusicPlayer.getQueuePosition() + 1);
        new setBlurredAlbumArt().execute();

    }

    @Override
    public void onResume() {
        super.onResume();
    }


//    public BroadcastReceiver mStatusListener = new BroadcastReceiver() {
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            String action = intent.getAction();
//            if (action.equals(MediaService.META_CHANGED)) {
//
//                updateTrackInfo();
//                //setPauseButtonImage();
//                //queueNextRefresh(1);
//            } else if (action.equals(MediaService.PLAYSTATE_CHANGED)) {
//                //setPauseButtonImage();
//            } else if (action.equals(MediaService.QUEUE_CHANGED)) {
//                updateQueue();
//            } else if (action.equals(IConstants.MUSIC_COUNT_CHANGED)) {
//
//            }
//        }
//    };



    public void updateQueue() {
        fAdapter.notifyDataSetChanged();
        mViewPager.setCurrentItem(MusicPlayer.getQueuePosition() + 1, false);
        fAdapter.notifyDataSetChanged();
        updateTrackInfo();
    }

    private void updateFav(boolean b) {
        if (b == true) {
            fav.setImageResource(R.drawable.play_icn_loved);
        } else {
            fav.setImageResource(R.drawable.play_rdi_icn_love);
        }
    }

    WeakReference<View> viewWeakReference;

    public void updateTrackInfo() {

        if (!duetoplaypause) {
            isFav = false;
            ArrayList<MusicTrack> favlists = playlistsManager.getPlaylist(10);
            for (int i = 0; i < favlists.size(); i++) {
                if (MusicPlayer.getCurrentAudioId() == favlists.get(i).mId) {
                    isFav = true;
                    break;
                }
            }
            updateFav(isFav);
            new setBlurredAlbumArt().execute();

        }
        duetoplaypause = false;


        Fragment fragment = (RoundFragment) mViewPager.getAdapter().instantiateItem(mViewPager, mViewPager.getCurrentItem());
//        viewWeakReference = new WeakReference<View>(fragment.getView());
//        activeView = viewWeakReference.get();
        activeView = fragment.getView();

        if (activeView != null) {
//            animatorWeakReference = new WeakReference<>((ObjectAnimator) activeView.getTag(R.id.tag_animator));
//            animator = animatorWeakReference.get();
            animator = (ObjectAnimator) activeView.getTag(R.id.tag_animator);
        }

        ab.setTitle(MusicPlayer.getTrackName());
        ab.setSubtitle(MusicPlayer.getArtistName());


        duration.setText(MusicUtils.makeShortTimeString(PlayingActivity.this.getApplication(), MusicPlayer.duration() / 1000));
        mProgress.setMax((int) MusicPlayer.duration());

        mProgress.postDelayed(mUpdateProgress, 10);


        if (MusicPlayer.isPlaying()) {
            control.setImageResource(R.drawable.play_rdi_btn_pause);

        } else {
            control.setImageResource(R.drawable.play_rdi_btn_play);
        }

        animatorSet = new AnimatorSet();

        if (MusicPlayer.isPlaying()) {
            if (animator != null && !animator.isRunning()) {
                animatorSet.play(needleAnim).before(animator);
                animatorSet.start();
            }

        } else {
            if (needleAnim != null) {
                needleAnim.reverse();
                needleAnim.end();
            }

            if (animator != null && animator.isRunning()) {
                animator.cancel();
                float valueAvatar = (float) animator.getAnimatedValue();
                animator.setFloatValues(valueAvatar, 360f + valueAvatar);
            }
        }


        isNextOrPreSetPage = false;
        if (MusicPlayer.getQueuePosition() + 1 != mViewPager.getCurrentItem()) {
            mViewPager.setCurrentItem(MusicPlayer.getQueuePosition() + 1);
            isNextOrPreSetPage = true;
        }
    }


    public class PlaybarPagerTransformer implements ViewPager.PageTransformer {


        @Override
        public void transformPage(View view, float position) {

            if (position == 0) {
                if (MusicPlayer.isPlaying()) {
                    animator = (ObjectAnimator) view.getTag(R.id.tag_animator);
                    if (animator != null && !animator.isRunning()) {
                        animatorSet = new AnimatorSet();
                        animatorSet.play(needleAnim).before(animator);
                        animatorSet.start();
                    }
                }

            } else if (position == -1 || position == -2 || position == 1) {

                animator = (ObjectAnimator) view.getTag(R.id.tag_animator);
                if (animator != null) {
                    animator.setFloatValues(0);
                    animator.end();
                    animator = null;
                }
            } else {

                if (needleAnim != null) {
                    needleAnim.reverse();
                    needleAnim.end();
                }

                animator = (ObjectAnimator) view.getTag(R.id.tag_animator);
                if (animator != null) {
                    animator.cancel();
                    float valueAvatar = (float) animator.getAnimatedValue();
                    animator.setFloatValues(valueAvatar, 360f + valueAvatar);

                }
            }
        }

    }


    private void setSeekBarListener() {
        if (mProgress != null)
            mProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                    if (b) {
                        MusicPlayer.seek((long) i);
                        timePlayed.setText(MusicUtils.makeShortTimeString(PlayingActivity.this.getApplication(), i / 1000));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
    }


    private class setBlurredAlbumArt extends AsyncTask<Void, Void, Drawable> {

        long albumid = MusicPlayer.getCurrentAlbumId();

        @Override
        protected Drawable doInBackground(Void... loadedImage) {
            Drawable drawable = null;
            if (newOpts == null) {
                newOpts = new BitmapFactory.Options();
                newOpts.inSampleSize = 6;
                newOpts.inPreferredConfig = Bitmap.Config.RGB_565;
            }

            try {
                Bitmap bitmap;
                String art = MusicUtils.getAlbumInfo(PlayingActivity.this.getApplication(), albumid).album_art;

                if (art != null) {
                    bitmap = BitmapFactory.decodeFile(art, newOpts);
                } else {
                    bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.login_bg_night, newOpts);
                }
                if (bitmap != null) {
                    drawable = ImageUtils.createBlurredImageFromBitmap(bitmap, PlayingActivity.this.getApplication(), 3);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            return drawable;
        }

        @Override
        protected void onPostExecute(Drawable result) {
            if (albumid != MusicPlayer.getCurrentAlbumId()) {
                return;
            }
            if (result != null) {
                if (backAlbum.getDrawable() != null) {
                    TransitionDrawable td =
                            new TransitionDrawable(new Drawable[]{backAlbum.getDrawable(), result});

                    //去除过度绘制

                    backAlbum.setImageDrawable(td);
                    td.setCrossFadeEnabled(true);
                    td.startTransition(370);

                } else {
                    backAlbum.setImageDrawable(result);
                }
            }
        }

        @Override
        protected void onPreExecute() {
        }
    }


    class FragmentAdapter extends FragmentStatePagerAdapter {

        public FragmentAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {

            if (position == MusicPlayer.getQueue().length + 1 || position == 0) {
                return RoundFragment.newInstance(-1);
            }
            return RoundFragment.newInstance(MusicPlayer.getQueue()[position - 1]);
        }

        @Override
        public int getCount() {
            //左右各加一个
            return MusicPlayer.getQueue().length + 2;
        }
    }


    public class MyScroller extends Scroller {
        private int animTime = 600;

        public MyScroller(Context context) {
            super(context);
        }

        public MyScroller(Context context, Interpolator interpolator) {
            super(context, interpolator);
        }

        @Override
        public void startScroll(int startX, int startY, int dx, int dy, int duration) {
            super.startScroll(startX, startY, dx, dy, animTime);
        }

        @Override
        public void startScroll(int startX, int startY, int dx, int dy) {
            super.startScroll(startX, startY, dx, dy, animTime);
        }

        public void setmDuration(int animTime) {
            this.animTime = animTime;
        }
    }

    private void stopAnim() {
        activeView = null;

        if (animator != null) {
            animator.end();
            animator = null;
        }
        if (needleAnim != null) {
            needleAnim.end();
            needleAnim = null;
        }
        if (animatorSet != null) {
            animatorSet.end();
            animatorSet = null;
        }
    }


    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //unregisterReceiver(mStatusListener);
        mProgress.removeCallbacks(mUpdateProgress);
        stopAnim();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        stopAnim();
        mProgress.removeCallbacks(mUpdateProgress);
        finish();
    }


}
