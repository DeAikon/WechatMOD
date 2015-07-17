package dg.shenm233.wechatmod.hooks.ui;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v4.widget.DrawerLayout;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import chrisrenke.drawerarrowdrawable.DrawerArrowDrawable;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import dg.shenm233.wechatmod.Common;
import dg.shenm233.wechatmod.ObfuscationHelper;
import dg.shenm233.wechatmod.R;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.getStaticObjectField;
import static dg.shenm233.wechatmod.BuildConfig.DEBUG;
import static dg.shenm233.wechatmod.ObfuscationHelper.MM_Classes;
import static dg.shenm233.wechatmod.ObfuscationHelper.MM_Fields;
import static dg.shenm233.wechatmod.ObfuscationHelper.MM_Methods;
import static dg.shenm233.wechatmod.ObfuscationHelper.MM_Res;


public class LauncherUI {
    //save tabview instance for getting unread message
    private Object tabView;
    private static Activity LauncherUI_INSTANCE;
    private boolean isMainTabCreated;

    private String navMode;


    public void init(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        findAndHookMethod(MM_Classes.LauncherUI, MM_Methods.startMainUI, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                isMainTabCreated = (boolean) getObjectField(param.thisObject, MM_Fields.isMainTabCreated);
                LauncherUI_INSTANCE = (Activity) param.thisObject;
                Common.XMOD_PREFS.reload();
                if (Common.XMOD_PREFS.getAll().size() > 0) {
                    navMode = Common.XMOD_PREFS.getString(Common.KEY_SETNAV, "default");
                } else {
                    navMode = "default";
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!isMainTabCreated) {
                    if (DEBUG) XposedBridge.log("on maintab create");
                    if ((boolean) callStaticMethod(MM_Classes.AccountStorage, MM_Methods.isMMcoreReady)) {
                        if ("navidrawer".equals(navMode)) {
                            removeMMtabs((Activity) param.thisObject, false);
                            addNavigationDrawer((Activity) param.thisObject);
                        } else if ("notabs".equals(navMode)) {
                            removeMMtabs((Activity) param.thisObject, true);
                        }
                    } else {
                        if (DEBUG) XposedBridge.log("mmcore has not ready, finish LauncherUI hook");
                    }
                }
            }
        });

        findAndHookMethod(MM_Classes.LauncherUI, "dispatchKeyEvent", KeyEvent.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if ("navidrawer".equals(navMode)) {
                    if (mDrawer != null && ((KeyEvent) param.args[0]).getKeyCode() == KeyEvent.KEYCODE_BACK) {
                        if (drawerLayout.isDrawerOpen(Gravity.LEFT)) {
                            drawerLayout.closeDrawers();
                            param.setResult(true);
                        }
                    }
                }
            }
        });

        findAndHookMethod(MM_Classes.LauncherUI, MM_Methods.initActionBar, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if ("navidrawer".equals(navMode)) {
                    initNewActionBar((Activity) param.thisObject);
                }
            }
        });

        findAndHookMethod(MM_Classes.LauncherUI, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    if ("navidrawer".equals(navMode) && drawerLayout != null) {
                        refreshDrawerInfo();
                    }
                } catch (Throwable l) {

                }
            }
        });

        findAndHookMethod(MM_Classes.LauncherUI, "onDestroy", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if ("navidrawer".equals(navMode)) {
                    onDestroyDrawer();
                    onDestroyCustomActionBar((Activity) param.thisObject);
                    XposedBridge.log("onDestroy,remove custom view");
                }
                LauncherUI_INSTANCE = null;
                tabView = null;
            }
        });
    }

    private void removeMMtabs(Activity activity, boolean keepCanSlide) {
        ViewGroup customViewPager = (ViewGroup) getObjectField(activity, MM_Fields.customViewPager);
        tabView = getObjectField(activity, MM_Fields.tabView);
        ((ViewGroup) customViewPager.getParent()).removeView((View) tabView);
        if (DEBUG) ObfuscationHelper.getRawXml(MM_Res.main_tab, Common.MM_Context);
        callMethod(customViewPager, "setCanSlide", keepCanSlide);
    }

    private static DrawerLayout drawerLayout;
    private View mDrawer;
    private ListView mDrawerList;
    private DrawerListAdapter drawerListAdapter;
    private DrawerArrowDrawable drawerArrowDrawable;
    private Bitmap mDrawerBgBitmap;
    private ImageView bg_image;
    public ImageView user_avatar;
    private TextView username;

    private void initNewActionBar(Activity activity) throws Throwable {
        Object actionBar = getObjectField(activity, MM_Fields.actionBar);
        View actionBarView = (View) callMethod(actionBar, "getCustomView");

        //add DrawerArrowDrawable to ActionBar
        ViewGroup newActionBarView = (ViewGroup) View.inflate(Common.MOD_Context, R.layout.actionbar_container, null);
        ImageView iv = (ImageView) newActionBarView.findViewById(R.id.drawer_indicator);

        //create DrawerArrowDrawable
        drawerArrowDrawable = new DrawerArrowDrawable((Resources) callMethod(activity, "getResources"));
        drawerArrowDrawable.setStrokeColor(Common.MOD_RES.getColor(R.color.drawer_indicator_color));
        iv.setImageDrawable(drawerArrowDrawable);

        //remove original view,and then add again
        ((ViewGroup) actionBarView.getParent()).removeView(actionBarView);
        newActionBarView.addView(actionBarView);
        callMethod(actionBar, "setCustomView", newActionBarView);

        iv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (drawerLayout.isDrawerOpen(Gravity.LEFT)) {
                    drawerLayout.closeDrawers();
                } else {
                    drawerLayout.openDrawer(Gravity.LEFT);
                }
            }
        });
    }

    private void onDestroyCustomActionBar(Activity activity) {
        Object actionBar = getObjectField(activity, MM_Fields.actionBar);
        ViewGroup actionBarView = (ViewGroup) callMethod(actionBar, "getCustomView");
        ImageView iv = (ImageView) actionBarView.findViewById(R.id.drawer_indicator);
        iv.setImageDrawable(null);
        iv.setOnKeyListener(null);
        actionBarView.removeAllViews();
    }

    private void addNavigationDrawer(Activity activity) throws Throwable {
        drawerLayout = new DrawerLayout(activity);
        drawerLayout.setFitsSystemWindows(true);
        drawerLayout.setFocusable(true);
        drawerLayout.setFocusableInTouchMode(true);

        //Create Drawer
        mDrawer = View.inflate(Common.MOD_Context, R.layout.drawer, null);
        DrawerLayout.LayoutParams lp =
                new DrawerLayout.LayoutParams(DrawerLayout.LayoutParams.MATCH_PARENT, DrawerLayout.LayoutParams.MATCH_PARENT);
        lp.gravity = Gravity.START;
        mDrawer.setLayoutParams(lp);

        //Drawer List
        mDrawerList = (ListView) mDrawer.findViewById(R.id.drawer_list);
        drawerListAdapter = new DrawerListAdapter(Common.MOD_Context);
        mDrawerList.setAdapter(drawerListAdapter);
        mDrawerList.setOnItemClickListener(drawerListAdapter);
        initDrawerList(drawerListAdapter);
        mDrawerList.setItemsCanFocus(true);

        //remove orginal frameLayout that including customViewPager,tabView...
        View main_tab = (View) getObjectField(activity, MM_Fields.main_tab);
        ((ViewGroup) main_tab.getParent()).removeView(main_tab);
        drawerLayout.addView(main_tab);
        drawerLayout.addView(mDrawer);

        //go go go
        activity.addContentView(drawerLayout,
                new DrawerLayout.LayoutParams(DrawerLayout.LayoutParams.MATCH_PARENT, DrawerLayout.LayoutParams.MATCH_PARENT));
        //don't use activity.addContentView(drawerLayout);  because it causes activity exit.

        bg_image = (ImageView) mDrawer.findViewById(R.id.bg_image);
        username = (TextView) mDrawer.findViewById(R.id.username);
        user_avatar = (ImageView) mDrawer.findViewById(R.id.user_avatar);
        user_avatar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainFragments.callMMFragmentFeature(LauncherUI_INSTANCE, 3, "more_tab_setting_personal_info");
            }
        });

        drawerLayout.setDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                // Sometimes slideOffset ends up so close to but not quite 1 or 0.
                if (slideOffset >= .995) {
                    drawerArrowDrawable.setFlip(true);
                } else if (slideOffset <= .005) {
                    drawerArrowDrawable.setFlip(false);
                }

                drawerArrowDrawable.setParameter(slideOffset);
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                refreshDrawerInfo();
            }

            @Override
            public void onDrawerClosed(View drawerView) {

            }

            @Override
            public void onDrawerStateChanged(int newState) {

            }
        });
    }

    private void onDestroyDrawer() {
        bg_image.setImageBitmap(null);
        if (mDrawerBgBitmap != null) {
            mDrawerBgBitmap.recycle();
            mDrawerBgBitmap = null;
        }
        user_avatar.setImageBitmap(null);
        user_avatar.setOnClickListener(null);
        mDrawerList.setAdapter(null);
        mDrawerList.setOnItemClickListener(null);
        drawerListAdapter = null;
        drawerLayout.removeAllViews();
        drawerLayout.setDrawerListener(null);
    }

    private boolean item_sns_moments_enabled;
    private boolean item_sns_shake_enabled;
    private boolean item_sns_people_nearby_enabled;
    private boolean item_sns_drift_bottle_enabled;
    private boolean item_sns_shopping_enabled;
    private boolean item_sns_games_enabled;

    private void initDrawerList(DrawerListAdapter drawerListAdapter) {
        item_sns_moments_enabled = true;
        item_sns_shake_enabled = true;
        item_sns_people_nearby_enabled = true;
        item_sns_drift_bottle_enabled = true;
        item_sns_shopping_enabled = true;
        item_sns_games_enabled = true;
        Set<String> defstrs = new HashSet<String>();
        Set<String> strs = Common.XMOD_PREFS.getStringSet(Common.KEY_DISABLED_ITEMS, defstrs);
        if (strs != null) {
            for (String str : strs) {
                if ("item_sns_moments".equals(str)) {
                    item_sns_moments_enabled = false;
                } else if ("item_sns_shake".equals(str)) {
                    item_sns_shake_enabled = false;
                } else if ("item_sns_people_nearby".equals(str)) {
                    item_sns_people_nearby_enabled = false;
                } else if ("item_sns_drift_bottle".equals(str)) {
                    item_sns_drift_bottle_enabled = false;
                } else if ("item_sns_shopping".equals(str)) {
                    item_sns_shopping_enabled = false;
                } else if ("item_sns_games".equals(str)) {
                    item_sns_games_enabled = false;
                }
            }
        }

        //chatting
        drawerListAdapter.addItem(Common.item_main_chat, R.drawable.main_chat, R.string.main_chat);
        //contact
        drawerListAdapter.addItem(Common.item_main_contact, R.drawable.main_contact, R.string.main_contact);
        //Discovery
        drawerListAdapter.addSectionHeaderItem(Common.item_main_addcontact, R.string.main_addcontact);

        //
        if (item_sns_moments_enabled) {
            drawerListAdapter.addItem(Common.item_sns_moments, R.drawable.sns_moments, R.string.sns_moments);
        }
        drawerListAdapter.addItem(Common.item_sns_scan, R.drawable.sns_scan, R.string.sns_scan);
        if (item_sns_shake_enabled) {
            drawerListAdapter.addItem(Common.item_sns_shake, R.drawable.sns_shake, R.string.sns_shake);
        }
        if (item_sns_people_nearby_enabled) {
            drawerListAdapter.addItem(Common.item_sns_people_nearby, R.drawable.sns_people_nearby, R.string.sns_people_nearby);
        }
        if (item_sns_drift_bottle_enabled) {
            drawerListAdapter.addItem(Common.item_sns_drift_bottle, R.drawable.sns_drift_bottle, R.string.sns_drift_bottle);
        }
        if (item_sns_shopping_enabled) {
            drawerListAdapter.addItem(Common.item_sns_shopping, R.drawable.sns_shopping, R.string.sns_shopping);
        }
        if (item_sns_games_enabled) {
            drawerListAdapter.addItem(Common.item_sns_games, R.drawable.sns_games, R.string.sns_games);
        }

        //Me
        drawerListAdapter.addSectionHeaderItem(Common.item_main_more, R.string.main_more);

        //
        drawerListAdapter.addItem(Common.item_me_posts, R.drawable.me_posts, R.string.me_posts);
        drawerListAdapter.addItem(Common.item_me_favorites, R.drawable.me_favorites, R.string.me_favorites);
        drawerListAdapter.addItem(Common.item_me_wallet, R.drawable.me_wallet, R.string.me_wallet);
        drawerListAdapter.addItem(Common.item_me_settings, R.drawable.me_settings, R.string.me_settings);
    }

    private void refreshDrawerInfo() {
        //background image
        if (mDrawerBgBitmap != null) {
            mDrawerBgBitmap.recycle();
        }
        try {
            InputStream inputStream = Common.MOD_Context.openFileInput(Common.DRAWER_BG_PNG);
            mDrawerBgBitmap = BitmapFactory.decodeStream(inputStream);
            bg_image.setImageBitmap(mDrawerBgBitmap);
            inputStream.close();
        } catch (IOException e) {
            bg_image.setImageDrawable(Common.MOD_RES.getDrawable(R.drawable.bg_test));
        }

        //avatar image
//        user_avatar.setImageDrawable(Common.MOD_RES.getDrawable(R.drawable.avatar_test));
        setAvatar(user_avatar);

        //username,wechat name
        CharSequence str = getNickname();
        if (str != null)
            username.setText(str);
        username.append("\n" + Common.MOD_RES.getText(R.string.username) + getUsername());

        try {
            //set Unread message
            int i;
            i = (int) callMethod(tabView, "getMainTabUnread");
            drawerListAdapter.setMainChattingUnread(i);
            i = (int) callMethod(tabView, "getContactTabUnread");
            drawerListAdapter.setContactUnread(i);
            if (item_sns_moments_enabled) {
                Object object = getStaticObjectField(MM_Classes.WTFClazz, MM_Fields.moments_jj);
                i = object != null ? (int) callMethod(object, MM_Methods.getMomentsUnreadCount) : 0;
                drawerListAdapter.setMomentsUnread(i);
                if (i == 0) {
                    boolean showPoint = (boolean) callMethod(tabView, "getShowFriendPoint");
                    drawerListAdapter.setMomentsPoint(showPoint);
                }
            }
            if (item_sns_shake_enabled) {
                i = (int) callMethod(callStaticMethod(MM_Classes.NewFriendMessage, MM_Methods.getShakeVerifyMessage), MM_Methods.getVerifyMessageCount);
                drawerListAdapter.setShakeUnread(i);
            }
            if (item_sns_people_nearby_enabled) {
                i = (int) callMethod(callStaticMethod(MM_Classes.NewFriendMessage, MM_Methods.getLBSVerifyMessage), MM_Methods.getVerifyMessageCount);
                drawerListAdapter.setNearbyPeopleUnread(i);
            }
            if (item_sns_drift_bottle_enabled) {
                i = (int) callStaticMethod(MM_Classes.Bottle, MM_Methods.getBottleUnreadCount);
                drawerListAdapter.setDriftBottleUnread(i);
            }
        } catch (Throwable l) {
            if (DEBUG) XposedBridge.log(l);
        }
    }

    private CharSequence getNickname() {
        Object object = callStaticMethod(MM_Classes.AccountStorage, MM_Methods.getAccStg);
        Object obj = callMethod(callMethod(object, MM_Methods.getUserInfoFromDB), "get", 4, null);
        if (obj != null && ((String) obj).length() > 0)
            return (CharSequence) callStaticMethod(MM_Classes.UserNickName, MM_Methods.getNickname, Common.MM_Context, (CharSequence) obj);
        else
            return null;
    }

    private String getUsername() {
        String str = (String) callStaticMethod(MM_Classes.UserInfo, MM_Methods.getUsername);
        if (str == null)
            str = (String) callStaticMethod(MM_Classes.UserInfo, MM_Methods.getOrigUsername);
        return str;
    }

    private void setAvatar(ImageView imageView) {
        String str = (String) callStaticMethod(MM_Classes.UserInfo, MM_Methods.getOrigUsername);
        callStaticMethod(MM_Classes.Avatar, MM_Methods.setAvatarByOrigUsername, imageView, str);
    }

    protected static void callMMFeature(int key) {
        if (drawerLayout == null || LauncherUI_INSTANCE == null) return;
        switch (key) {
            case Common.item_main_chat:
                drawerLayout.closeDrawers();
                MainFragments.switchMMFragment(LauncherUI_INSTANCE, 0);
                break;
            case Common.item_main_contact:
                drawerLayout.closeDrawers();
                MainFragments.switchMMFragment(LauncherUI_INSTANCE, 1);
                break;
            case Common.item_main_addcontact:
                drawerLayout.closeDrawers();
                MainFragments.switchMMFragment(LauncherUI_INSTANCE, 2);
                break;
            case Common.item_main_more:
                drawerLayout.closeDrawers();
                MainFragments.switchMMFragment(LauncherUI_INSTANCE, 3);
                break;
            case Common.item_sns_moments:
                MainFragments.callMMFragmentFeature(LauncherUI_INSTANCE, 2, "album_dyna_photo_ui_title");
                break;
            case Common.item_sns_scan:
                MainFragments.callMMFragmentFeature(LauncherUI_INSTANCE, 2, "find_friends_by_qrcode");
                break;
            case Common.item_sns_shake:
                MainFragments.callMMFragmentFeature(LauncherUI_INSTANCE, 2, "find_friends_by_shake");
                break;
            case Common.item_sns_people_nearby:
                MainFragments.callMMFragmentFeature(LauncherUI_INSTANCE, 2, "find_friends_by_near");
                break;
            case Common.item_sns_drift_bottle:
                MainFragments.callMMFragmentFeature(LauncherUI_INSTANCE, 2, "voice_bottle");
                break;
            case Common.item_sns_shopping:
                MainFragments.callMMFragmentFeature(LauncherUI_INSTANCE, 2, "jd_market_entrance");
                break;
            case Common.item_sns_games:
                MainFragments.callMMFragmentFeature(LauncherUI_INSTANCE, 2, "more_tab_game_recommend");
                break;
            case Common.item_me_posts:
                MainFragments.callMMFragmentFeature(LauncherUI_INSTANCE, 3, "settings_my_album");
                break;
            case Common.item_me_favorites:
                MainFragments.callMMFragmentFeature(LauncherUI_INSTANCE, 3, "settings_mm_favorite");
                break;
            case Common.item_me_wallet:
                MainFragments.callMMFragmentFeature(LauncherUI_INSTANCE, 3, "settings_mm_wallet");
                break;
            case Common.item_me_settings:
                MainFragments.callMMFragmentFeature(LauncherUI_INSTANCE, 3, "more_setting");
                break;
        }
    }
}