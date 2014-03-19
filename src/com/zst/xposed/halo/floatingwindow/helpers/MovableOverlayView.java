package com.zst.xposed.halo.floatingwindow.helpers;

import com.zst.xposed.halo.floatingwindow.Common;
import com.zst.xposed.halo.floatingwindow.R;
import com.zst.xposed.halo.floatingwindow.hooks.ActionBarColorHook;
import com.zst.xposed.halo.floatingwindow.hooks.MovableWindow;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Color;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

@SuppressLint("ViewConstructor")
// We only create this view programatically, so the default
// constructor used by XML inflating is not needed

public class MovableOverlayView extends RelativeLayout {
	
	public static final int ID_OVERLAY_VIEW = 1000000;
	
	/* Corner Button Actions Constants */
	private static final int ACTION_CLICK_TRIANGLE = 0x0;
	private static final int ACTION_LONGPRESS_TRIANGLE = 0x1;
	private static final int ACTION_CLICK_QUADRANT = 0x2;
	private static final int ACTION_LONGPRESS_QUADRANT = 0x3;
	
	// App Objects
	private Activity mActivity;
	private Resources mResource;
	private AeroSnap mAeroSnap;
	private SharedPreferences mPref;
	
	// Views
	private View mDragToMoveBar;
	private ImageView mQuadrant;
	private ImageView mTriangle;
	private View mBorderOutline;
	
	/* Title Bar */
	private static int mTitleBarHeight = Common.DEFAULT_WINDOW_TITLEBAR_SIZE;
	private static int mTitleBarDivider = Common.DEFAULT_WINDOW_TITLEBAR_SEPARATOR_SIZE;
	private static boolean mLiveResizing;
	
	/**
	 * Create the overlay view for Movable and Resizable feature
	 * @param activity - the current activity
	 * @param resources - resource from the module
	 * @param pref - preference of the module
	 * @param aerosnap - an aerosnap instance
	 */
	public MovableOverlayView(Activity activity, Resources resources, SharedPreferences pref,
			AeroSnap aerosnap) {
		super(activity);
		
		// Set the params
		mActivity = activity;
		mResource = resources;
		mPref = pref;
		mAeroSnap = aerosnap;
		
		/* get the layout from our module. we cannot just use the R reference
		 * since the layout is from the module, not the current app we are
		 * modifying. thus, we use a parser */
		XmlResourceParser parser = resources.getLayout(R.layout.movable_window);
		activity.getWindow().getLayoutInflater().inflate(parser, this);
		// Thanks to this post for some inspiration:
		// http://sriramramani.wordpress.com/2012/07/25/infamous-viewholder-pattern/
		
		setId(ID_OVERLAY_VIEW);
		
		mDragToMoveBar = findViewById(R.id.movable_action_bar);
		mTriangle = (ImageView) findViewById(R.id.movable_corner);
		mQuadrant = (ImageView) findViewById(R.id.movable_quadrant);
		mBorderOutline = findViewById(R.id.movable_background);
		mBorderOutline.bringToFront();
		
		// set preferences values
		boolean titlebar_enabled = mPref.getBoolean(Common.KEY_WINDOW_TITLEBAR_ENABLED,
				Common.DEFAULT_WINDOW_TITLEBAR_ENABLED);
		boolean titlebar_separator_enabled = mPref.getBoolean(Common.KEY_WINDOW_TITLEBAR_SEPARATOR_ENABLED,
				Common.DEFAULT_WINDOW_TITLEBAR_SEPARATOR_ENABLED);
		mTitleBarHeight = !titlebar_enabled ? 0 : Util.realDp(
				mPref.getInt(Common.KEY_WINDOW_TITLEBAR_SIZE, Common.DEFAULT_WINDOW_TITLEBAR_SIZE),
				activity);
		mTitleBarDivider = !titlebar_separator_enabled ? 0 : Util.realDp(
				mPref.getInt(Common.KEY_WINDOW_TITLEBAR_SEPARATOR_SIZE,
				Common.DEFAULT_WINDOW_TITLEBAR_SEPARATOR_SIZE), activity);
		mLiveResizing = mPref.getBoolean(Common.KEY_WINDOW_RESIZING_LIVE_UPDATE,
				Common.DEFAULT_WINDOW_RESIZING_LIVE_UPDATE);
		
		// init stuff
		initCornersViews();
	}
	
	/**
	 * Initializes the triangle and quadrant's transparency, color, size etc.
	 * @since When inflating, the system will find the drawables in the CURRENT
	 *        app, which will FAIL since the drawables are in the MODULE. So we
	 *        have no choice but to do this programatically
	 */
	private void initCornersViews() {
		Drawable triangle_background = mResource.getDrawable(R.drawable.movable_corner);
		Drawable quadrant_background = mResource.getDrawable(R.drawable.movable_quadrant);
		
		String color_triangle = mPref.getString(Common.KEY_WINDOW_TRIANGLE_COLOR,
				Common.DEFAULT_WINDOW_TRIANGLE_COLOR);
		if (!color_triangle.equals(Common.DEFAULT_WINDOW_TRIANGLE_COLOR)) { 
			triangle_background.setColorFilter(Color.parseColor("#" + color_triangle),
					Mode.MULTIPLY);
		}
		
		String color_quadrant = mPref.getString(Common.KEY_WINDOW_QUADRANT_COLOR,
				Common.DEFAULT_WINDOW_QUADRANT_COLOR);
		if (!color_quadrant.equals(Common.DEFAULT_WINDOW_QUADRANT_COLOR)) {
			quadrant_background.setColorFilter(Color.parseColor("#" + color_quadrant),
					Mode.MULTIPLY);
		}
		
		float triangle_alpha = mPref.getFloat(Common.KEY_WINDOW_TRIANGLE_ALPHA,
				Common.DEFAULT_WINDOW_TRIANGLE_ALPHA);
		triangle_background.setAlpha((int) (triangle_alpha * 255));
		
		float quadrant_alpha = mPref.getFloat(Common.KEY_WINDOW_QUADRANT_ALPHA,
				Common.DEFAULT_WINDOW_QUADRANT_ALPHA);
		quadrant_background.setAlpha((int) (quadrant_alpha * 255));
		
		Util.setBackgroundDrawable(mTriangle, triangle_background);
		Util.setBackgroundDrawable(mQuadrant, quadrant_background);
		
		int triangle_size = mPref.getInt(Common.KEY_WINDOW_TRIANGLE_SIZE,
				Common.DEFAULT_WINDOW_TRIANGLE_SIZE);
		mTriangle.getLayoutParams().width = triangle_size;
		mTriangle.getLayoutParams().height = triangle_size;
		
		int quadrant_size = mPref.getInt(Common.KEY_WINDOW_QUADRANT_SIZE,
				Common.DEFAULT_WINDOW_QUADRANT_SIZE);
		mQuadrant.getLayoutParams().width = quadrant_size;
		mQuadrant.getLayoutParams().height = quadrant_size;
		
		final boolean triangle_enabled = mPref.getBoolean(Common.KEY_WINDOW_TRIANGLE_ENABLE,
				Common.DEFAULT_WINDOW_TRIANGLE_ENABLE);
		if (triangle_enabled) {
			if (mPref.getBoolean(Common.KEY_WINDOW_TRIANGLE_RESIZE_ENABLED,
					Common.DEFAULT_WINDOW_TRIANGLE_RESIZE_ENABLED)) {
				if (mLiveResizing) {
					mTriangle.setOnTouchListener(new Resizable(mActivity, mActivity.getWindow()));
				} else {
					mTriangle.setOnTouchListener(new OutlineLeftResizable(mActivity, mActivity
							.getWindow()));
				}
			}
			
			if (mPref.getBoolean(Common.KEY_WINDOW_TRIANGLE_DRAGGING_ENABLED,
					Common.DEFAULT_WINDOW_TRIANGLE_DRAGGING_ENABLED)) {
				mTriangle.setOnTouchListener(new Movable(mActivity.getWindow(), mTriangle,
						mAeroSnap));
			}
			
			mTriangle.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					cornerButtonClickAction(ACTION_CLICK_TRIANGLE);
				}
			});
			
			mTriangle.setOnLongClickListener(new View.OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					cornerButtonClickAction(ACTION_LONGPRESS_TRIANGLE);
					return true;
				}
			});
		} else {
			mTriangle.getLayoutParams().width = 0;
			mTriangle.getLayoutParams().height = 0;
		}
		
		boolean quadrant_enabled = mPref.getBoolean(Common.KEY_WINDOW_QUADRANT_ENABLE,
				Common.DEFAULT_WINDOW_QUADRANT_ENABLE);
		if (quadrant_enabled) {
			if (mPref.getBoolean(Common.KEY_WINDOW_QUADRANT_RESIZE_ENABLED,
					Common.DEFAULT_WINDOW_QUADRANT_RESIZE_ENABLED)) {
				if (mLiveResizing) {
					mQuadrant.setOnTouchListener(new RightResizable(mActivity.getWindow()));
				} else {
					mQuadrant.setOnTouchListener(new OutlineRightResizable(mActivity.getWindow()));
				}
			}
			
			if (mPref.getBoolean(Common.KEY_WINDOW_QUADRANT_DRAGGING_ENABLED,
					Common.DEFAULT_WINDOW_QUADRANT_DRAGGING_ENABLED)) {
				mQuadrant.setOnTouchListener(new Movable(mActivity.getWindow(), mQuadrant,
						mAeroSnap));
			}
			
			mQuadrant.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					cornerButtonClickAction(ACTION_CLICK_QUADRANT);
				}
			});
			
			mQuadrant.setOnLongClickListener(new View.OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					cornerButtonClickAction(ACTION_LONGPRESS_QUADRANT);
					return true;
				}
			});
		} else {
			mQuadrant.getLayoutParams().width = 0;
			mQuadrant.getLayoutParams().height = 0;
		}
		
		setDragActionBarVisibility(false, true);
		initDragToMoveBar();
		
		if (mPref.getBoolean(Common.KEY_WINDOW_BORDER_ENABLED,
				Common.DEFAULT_WINDOW_BORDER_ENABLED)) {
			final int color = Color.parseColor("#" + mPref.getString(Common.KEY_WINDOW_BORDER_COLOR,
							Common.DEFAULT_WINDOW_BORDER_COLOR));
			final int thickness = mPref.getInt(Common.KEY_WINDOW_BORDER_THICKNESS,
					Common.DEFAULT_WINDOW_BORDER_THICKNESS);
			setWindowBorder(color, thickness);
			ActionBarColorHook.setBorderThickness(thickness);
		}
		
		initTitleBar();
	}
	
	// Corner Buttons (Triangle, Quadrant) Actions.
	private void cornerButtonClickAction(int type_of_action) {
		String index = "0";
		switch (type_of_action) {
		case ACTION_CLICK_TRIANGLE:
			index = mPref.getString(Common.KEY_WINDOW_TRIANGLE_CLICK_ACTION,
					Common.DEFAULT_WINDOW_TRIANGLE_CLICK_ACTION);
			break;
		case ACTION_LONGPRESS_TRIANGLE:
			index = mPref.getString(Common.KEY_WINDOW_TRIANGLE_LONGPRESS_ACTION,
					Common.DEFAULT_WINDOW_TRIANGLE_LONGPRESS_ACTION);
			break;
		case ACTION_CLICK_QUADRANT:
			index = mPref.getString(Common.KEY_WINDOW_QUADRANT_CLICK_ACTION,
					Common.DEFAULT_WINDOW_QUADRANT_CLICK_ACTION);
			break;
		case ACTION_LONGPRESS_QUADRANT:
			index = mPref.getString(Common.KEY_WINDOW_QUADRANT_LONGPRESS_ACTION,
					Common.DEFAULT_WINDOW_QUADRANT_LONGPRESS_ACTION);
			break;
		}
		switch (Integer.parseInt(index)) {
		case 0: // Do Nothing
			break;
		case 1: // Drag & Move Bar
			setDragActionBarVisibility(true, true);
			break;
		case 2:
			if (Build.VERSION.SDK_INT >= 16) {
				mActivity.finishAffinity();
			} else {
				mActivity.finish();
			}
			break;
		case 3: // Transparency Dialog
			showTransparencyDialogVisibility();
			break;
		case 4: // Minimize / Hide Entire App
			MovableWindow.minimizeAndShowNotification(mActivity);
			break;
		case 5: // Drag & Move Bar w/o hiding corner
			setDragActionBarVisibility(true, false);
			break;
		case 6: // Maximize App
			MovableWindow.maximizeApp(mActivity);
			break;
		}
	}
	
	// Create the Titlebar
	private void initTitleBar() {
		if (mTitleBarHeight == 0) return;
		
		final FrameLayout decorView = (FrameLayout) mActivity.getWindow().peekDecorView()
				.getRootView();
		
		View child = decorView.getChildAt(0);
		FrameLayout.LayoutParams parammm = (FrameLayout.LayoutParams) child.getLayoutParams();
		parammm.setMargins(0, mTitleBarHeight, 0, 0);
		child.setLayoutParams(parammm);
		
		final View divider = findViewById(R.id.movable_titlebar_line);
		final TextView app_title = (TextView) findViewById(R.id.movable_titlebar_appname);
		final ImageButton max_button = (ImageButton) findViewById(R.id.movable_titlebar_max);
		final ImageButton min_button = (ImageButton) findViewById(R.id.movable_titlebar_min);
		final ImageButton more_button = (ImageButton) findViewById(R.id.movable_titlebar_more);
		final ImageButton close_button = (ImageButton) findViewById(R.id.movable_titlebar_close);
		final RelativeLayout header = (RelativeLayout) findViewById(R.id.movable_titlebar);

		
		app_title.setText(mActivity.getApplicationInfo().loadLabel(mActivity.getPackageManager()));
		close_button.setImageDrawable(mResource.getDrawable(R.drawable.movable_title_close));
		max_button.setImageDrawable(mResource.getDrawable(R.drawable.movable_title_max));
		min_button.setImageDrawable(mResource.getDrawable(R.drawable.movable_title_min));
		more_button.setImageDrawable(mResource.getDrawable(R.drawable.movable_title_more));
		
		RelativeLayout.LayoutParams header_param = (LayoutParams) header.getLayoutParams();
		header_param.height = mTitleBarHeight;
		header.setLayoutParams(header_param);
		
		ViewGroup.LayoutParams divider_param = divider.getLayoutParams();
		divider_param.height = mTitleBarDivider;
		divider.setLayoutParams(divider_param);
		
		String color_str = mPref.getString(Common.KEY_WINDOW_TITLEBAR_SEPARATOR_COLOR,
				Common.DEFAULT_WINDOW_TITLEBAR_SEPARATOR_COLOR);
		divider.setBackgroundColor(Color.parseColor("#" + color_str));
		
		final String item1 = mResource.getString(R.string.dnm_transparency);
		final PopupMenu popupMenu = new PopupMenu(mActivity, more_button);
		final Menu menu = popupMenu.getMenu();
		menu.add(item1);
		popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem item) {
				if (item.getTitle().equals(item1)) {
					showTransparencyDialogVisibility();
				}
				return false;
			}
		});
		
		final View.OnClickListener click = new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				switch (v.getId()) {
				case R.id.movable_titlebar_close:
					if (Build.VERSION.SDK_INT >= 16) {
						mActivity.finishAffinity();
					} else {
						mActivity.finish();
					}
					break;
				case R.id.movable_titlebar_max:
					MovableWindow.maximizeApp(mActivity);
					break;
				case R.id.movable_titlebar_min:
					MovableWindow.minimizeAndShowNotification(mActivity);
					break;
				case R.id.movable_titlebar_more:
					popupMenu.show();
					break;
				}
			}
		};
		close_button.setOnClickListener(click);
		max_button.setOnClickListener(click);
		min_button.setOnClickListener(click);
		more_button.setOnClickListener(click);
		header.setOnTouchListener(new Movable(mActivity.getWindow(), mAeroSnap));
	}
	
	// Create the drag-to-move bar
	private void initDragToMoveBar() {
		mDragToMoveBar.setOnTouchListener(new Movable(mActivity.getWindow(), mAeroSnap));
		
		final TextView dtm_title = (TextView) mDragToMoveBar.findViewById(R.id.textView1);
		dtm_title.setText(mResource.getString(R.string.dnm_title));
		
		final ImageButton done = (ImageButton) mDragToMoveBar.findViewById(R.id.movable_done);
		done.setImageDrawable(mResource.getDrawable(R.drawable.movable_done));
		done.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setDragActionBarVisibility(false, true);
			}
		});
		
		final String menu_item1 = mResource.getString(R.string.dnm_transparency);
		final String menu_item3 = mResource.getString(R.string.dnm_minimize);
		final String menu_item2 = mResource.getString(R.string.dnm_close_app);
		
		final ImageButton overflow = (ImageButton) mDragToMoveBar.findViewById(R.id.movable_overflow);
		overflow.setImageDrawable(mResource.getDrawable(R.drawable.movable_overflow));
		overflow.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				PopupMenu popupMenu = new PopupMenu(overflow.getContext(), overflow);
				Menu menu = popupMenu.getMenu();
				menu.add(menu_item1);
				menu.add(menu_item3);
				menu.add(menu_item2);
				popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
					@Override
					public boolean onMenuItemClick(MenuItem item) {
						if (item.getTitle().equals(menu_item1)) {
							showTransparencyDialogVisibility();
						} else if (item.getTitle().equals(menu_item2)) {
							mActivity.finish();
						} else if (item.getTitle().equals(menu_item3)) {
							MovableWindow.minimizeAndShowNotification(mActivity);
						}
						return false;
					}
				});
				popupMenu.show();
			}
		});
	}
	
	private void showTransparencyDialogVisibility() {
		final View bg = findViewById(R.id.movable_bg);
		final TextView number = (TextView) findViewById(R.id.movable_textView8);
		final SeekBar t = (SeekBar) findViewById(R.id.movable_seekBar1);
		
		float oldValue = mActivity.getWindow().getAttributes().alpha;
		number.setText((int) (oldValue * 100) + "%");
		t.setProgress((int) (oldValue * 100) - 10);
		t.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}
			
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				int newProgress = (progress + 10);
				number.setText(newProgress + "%");
				
				WindowManager.LayoutParams params = mActivity.getWindow().getAttributes();
				params.alpha = newProgress * 0.01f;
				mActivity.getWindow().setAttributes(params);
			}
		});
		
		bg.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View paramView, MotionEvent paramMotionEvent) {
				bg.setVisibility(View.INVISIBLE);
				return true;
			}
		});
		
		bg.setVisibility(View.VISIBLE);
	}
	
	private void setDragActionBarVisibility(boolean visible, boolean with_corner) {
		mDragToMoveBar.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
		if (with_corner) {
			mTriangle.setVisibility(visible ? View.INVISIBLE : View.VISIBLE);
			mQuadrant.setVisibility(visible ? View.INVISIBLE : View.VISIBLE);
		}
	}
	
	public void setWindowBorder(int color, int thickness) {
		if (thickness == 0) {
			mBorderOutline.setBackgroundResource(0);
		} else {
			mBorderOutline.setBackgroundDrawable(Util.makeOutline(color, thickness));
		}
	}
	
	public static final RelativeLayout.LayoutParams getParams() {
		final RelativeLayout.LayoutParams paramz = new RelativeLayout.LayoutParams(
				ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT);
		paramz.setMargins(0, 0, 0, 0);
		return paramz;
	}
}
