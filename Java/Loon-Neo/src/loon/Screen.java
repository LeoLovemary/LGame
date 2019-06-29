/**
 * Copyright 2008 - 2015 The Loon Game Engine Authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 * 
 * @project loon
 * @author cping
 * @email：javachenpeng@yahoo.com
 * @version 0.5
 */
package loon;

import java.io.OutputStream;

import loon.action.ActionBind;
import loon.action.ActionControl;
import loon.action.ActionTween;
import loon.action.camera.BaseCamera;
import loon.action.camera.EmptyCamera;
import loon.action.collision.CollisionHelper;
import loon.action.collision.CollisionManager;
import loon.action.collision.CollisionObject;
import loon.action.collision.Gravity;
import loon.action.collision.GravityHandler;
import loon.action.collision.GravityResult;
import loon.action.map.Config;
import loon.action.map.Field2D;
import loon.action.page.ScreenSwitch;
import loon.action.sprite.ISprite;
import loon.action.sprite.Sprite;
import loon.action.sprite.SpriteControls;
import loon.action.sprite.SpriteLabel;
import loon.action.sprite.Sprites;
import loon.action.sprite.Sprites.SpriteListener;
import loon.canvas.Image;
import loon.canvas.LColor;
import loon.canvas.Pixmap;
import loon.component.DefUI;
import loon.component.Desktop;
import loon.component.LClickButton;
import loon.component.LComponent;
import loon.component.LLabel;
import loon.component.LLayer;
import loon.component.LMenuSelect;
import loon.component.LPaper;
import loon.component.UIControls;
import loon.component.layout.LayoutConstraints;
import loon.component.layout.LayoutManager;
import loon.component.layout.LayoutPort;
import loon.component.skin.SkinManager;
import loon.event.ActionKey;
import loon.event.ClickListener;
import loon.event.DrawListener;
import loon.event.FrameLoopEvent;
import loon.event.GameKey;
import loon.event.GameTouch;
import loon.event.LTouchArea;
import loon.event.LTouchLocation;
import loon.event.QueryEvent;
import loon.event.SysInput;
import loon.event.SysTouch;
import loon.event.Touched;
import loon.event.TouchedClick;
import loon.event.Updateable;
import loon.event.LTouchArea.Event;
import loon.font.Font.Style;
import loon.font.IFont;
import loon.geom.BoxSize;
import loon.geom.Circle;
import loon.geom.Line;
import loon.geom.PointI;
import loon.geom.RectBox;
import loon.geom.Triangle2f;
import loon.geom.Vector2f;
import loon.geom.XY;
import loon.opengl.GLEx;
import loon.opengl.LTextureImage;
import loon.utils.ArrayByte;
import loon.utils.ArrayMap;
import loon.utils.Calculator;
import loon.utils.CollectionUtils;
import loon.utils.ConfigReader;
import loon.utils.Easing.EasingMode;
import loon.utils.GLUtils;
import loon.utils.IntMap;
import loon.utils.MathUtils;
import loon.utils.ObjectBundle;
import loon.utils.Resolution;
import loon.utils.StringKeyValue;
import loon.utils.TArray;
import loon.utils.processes.GameProcess;
import loon.utils.processes.RealtimeProcess;
import loon.utils.processes.RealtimeProcessManager;
import loon.utils.reply.Closeable;
import loon.utils.reply.Port;
import loon.utils.res.ResourceLocal;
import loon.utils.timer.LTimer;
import loon.utils.timer.LTimerContext;

/**
 * LGame游戏的运行与显示主体，用来显示与操作游戏基础画布，精灵，UI以及其他组件
 */
public abstract class Screen extends PlayerUtils implements SysInput, LRelease, XY {

	/**
	 * Screen切换方式(单纯移动)
	 *
	 */
	public static enum MoveMethod {
		FROM_LEFT, FROM_UP, FROM_DOWN, FROM_RIGHT, FROM_UPPER_LEFT, FROM_UPPER_RIGHT, FROM_LOWER_LEFT, FROM_LOWER_RIGHT, OUT_LEFT, OUT_UP, OUT_DOWN, OUT_RIGHT, OUT_UPPER_LEFT, OUT_UPPER_RIGHT, OUT_LOWER_LEFT, OUT_LOWER_RIGHT;
	}

	/**
	 * Screen切换方式(渐变效果)
	 *
	 */
	public static enum PageMethod {
		Unkown, Accordion, BackToFore, CubeIn, Depth, Fade, Rotate, RotateDown, RotateUp, Stack, ZoomIn, ZoomOut;
	}

	/**
	 * Screen中组件替换选择器,用switch之类分支结构选择指定Screen进行替换
	 */
	public static interface ReplaceEvent {

		public Screen getScreen(int idx);

	}

	/**
	 * Screen中组件渲染顺序选择用枚举类型,Loon的Screen允许桌面组件(UI),精灵,以及用户渲染(Draw接口中的实现)<br>
	 * 自行设置渲染顺序.默认条件下精灵最下,桌面在后,用户渲染最上.
	 */
	public static enum DrawOrder {
		SPRITE, DESKTOP, USER
	}

	public final static int SCREEN_NOT_REPAINT = 0;

	public final static int SCREEN_TEXTURE_REPAINT = 1;

	public final static int SCREEN_COLOR_REPAINT = 2;

	public final static byte DRAW_EMPTY = -1;

	public final static byte DRAW_USER = 0;

	public final static byte DRAW_SPRITE = 1;

	public final static byte DRAW_DESKTOP = 2;

	/**
	 * 通用碰撞管理器(需要用户自行初始化(getCollisionManager或initializeCollision),不实例化默认不存在)
	 */
	private CollisionManager _collisionManager;

	private boolean _collisionClosed;

	private ArrayMap _keyActions = new ArrayMap(CollectionUtils.INITIAL_CAPACITY);

	private Updateable _closeUpdate;

	private ClickListener _clickListener;

	private TouchedClick _touchListener;

	private String _screenName;

	private boolean _isExistCamera = false;

	private BaseCamera _baseCamera;

	private ScreenAction _screenAction = null;

	private final TArray<LTouchArea> _touchAreas = new TArray<LTouchArea>();

	protected final Closeable.Set _conns = new Closeable.Set();

	private LTransition _transition;

	private DrawListener<Screen> _drawListener;

	private boolean spriteRun, desktopRun, stageRun;

	private boolean fristPaintFlag;

	private boolean secondPaintFlag;

	private boolean lastPaintFlag;

	// 0.3.2版新增的简易重力控制接口
	private GravityHandler gravityHandler;

	private LColor _backgroundColor;

	private RectBox _rectBox;

	private LColor _baseColor;

	private float _alpha = 1f;

	private float _rotation = 0;

	private float _pivotX = -1f, _pivotY = -1f;

	private float _scaleX = 1f, _scaleY = 1f;

	private boolean _flipX = false, _flipY = false;

	private boolean _visible = true;

	private TArray<RectBox> _limits = new TArray<RectBox>(10);

	private Accelerometer.SensorDirection direction = Accelerometer.SensorDirection.EMPTY;

	private int mode, frame;

	private boolean processing = true;

	private LTexture currentScreenBackground;

	protected LProcess handler;

	private TArray<LRelease> releases;

	private int width, height, halfWidth, halfHeight;
	// 精灵集合
	private Sprites sprites;

	// 桌面集合
	private Desktop desktop;

	private PointI touch = new PointI(0, 0);

	private boolean isLoad, isLock, isClose, isTranslate, isGravity;

	private float tx, ty;

	private float lastTouchX, lastTouchY, touchDX, touchDY;

	public long elapsedTime;

	private final IntMap<Boolean> keyType = new IntMap<Boolean>();

	private final IntMap<Boolean> touchType = new IntMap<Boolean>();

	private int touchButtonPressed = SysInput.NO_BUTTON, touchButtonReleased = SysInput.NO_BUTTON;

	private int keyButtonPressed = SysInput.NO_KEY, keyButtonReleased = SysInput.NO_KEY;

	protected boolean isNext;

	// 首先绘制的对象
	private PaintOrder fristOrder;

	// 其次绘制的对象
	private PaintOrder secondOrder;

	// 最后绘制的对象
	private PaintOrder lastOrder;

	private PaintOrder userOrder, spriteOrder, desktopOrder;

	private boolean replaceLoading;

	private int replaceScreenSpeed = 8;

	private LTimer replaceDelay = new LTimer(0);

	private Screen replaceDstScreen;

	private ScreenSwitch screenSwitch;

	private EmptyObject dstPos = new EmptyObject();

	private MoveMethod replaceMethod = MoveMethod.FROM_LEFT;

	private boolean isScreenFrom = false;

	// 每次screen处理事件循环的额外间隔时间
	private LTimer delayTimer = new LTimer(0);
	// 希望Screen中所有组件的update暂停的时间
	private LTimer pauseTimer = new LTimer(LSystem.SECOND);
	// 是否已经暂停
	private boolean isTimerPaused = false;

	public int index = 0;

	public static final class PaintOrder {

		private byte type;

		private Screen screen;

		public PaintOrder(byte t, Screen s) {
			this.type = t;
			this.screen = s;
		}

		void paint(GLEx g) {
			switch (type) {
			case DRAW_USER:
				DrawListener<Screen> drawing = screen._drawListener;
				if (drawing != null) {
					drawing.draw(g, screen.getX(), screen.getY());
				}
				screen.draw(g);
				break;
			case DRAW_SPRITE:
				if (screen.spriteRun) {
					screen.sprites.createUI(g);
				} else if (screen.spriteRun = (screen.sprites != null && screen.sprites.size() > 0)) {
					screen.sprites.createUI(g);
				}
				break;
			case DRAW_DESKTOP:
				if (screen.desktopRun) {
					screen.desktop.createUI(g);
				} else if (screen.desktopRun = (screen.desktop != null && screen.desktop.size() > 0)) {
					screen.desktop.createUI(g);
				}
				break;
			case DRAW_EMPTY:
			default:
				break;
			}
		}

		void update(LTimerContext c) {
			try {
				switch (type) {
				case DRAW_USER:
					DrawListener<Screen> drawing = screen._drawListener;
					if (drawing != null) {
						drawing.update(c.timeSinceLastUpdate);
					}
					screen.alter(c);
					break;
				case DRAW_SPRITE:
					screen.spriteRun = (screen.sprites != null && screen.sprites.size() > 0);
					if (screen.spriteRun) {
						screen.sprites.update(c.timeSinceLastUpdate);
					}
					break;
				case DRAW_DESKTOP:
					screen.desktopRun = (screen.desktop != null && screen.desktop.size() > 0);
					if (screen.desktopRun) {
						screen.desktop.update(c.timeSinceLastUpdate);
					}
					break;
				case DRAW_EMPTY:
				default:
					break;
				}
			} catch (Throwable cause) {
				LSystem.error("Screen update() dispatch failure", cause);
			}
		}
	}

	public Screen(String name, int w, int h) {
		init(name, w, h);
	}

	public Screen(String name) {
		this(name, 0, 0);
	}

	public Screen(int w, int h) {
		this(LSystem.UNKOWN, w, h);
	}

	public Screen() {
		this(LSystem.UNKOWN, 0, 0);
	}

	protected void init(String name, int w, int h) {
		this._screenName = name;
		resetSize(w, h);
	}

	public Screen setID(int id) {
		this.index = id;
		return this;
	}

	public int getID() {
		return this.index;
	}

	/**
	 * 转化DrawOrder为PaintOrder
	 * 
	 * @param tree
	 * @return
	 */
	public final PaintOrder toPaintOrder(DrawOrder tree) {
		PaintOrder order = null;
		switch (tree) {
		case SPRITE:
			order = DRAW_SPRITE_PAINT();
			break;
		case DESKTOP:
			order = DRAW_DESKTOP_PAINT();
			break;
		case USER:
		default:
			order = DRAW_USER_PAINT();
			break;
		}
		return order;
	}

	/**
	 * 设置Screen中组件渲染顺序
	 * 
	 * @param one
	 * @param two
	 * @param three
	 */
	public void setDrawOrder(DrawOrder one, DrawOrder two, DrawOrder three) {
		this.setFristOrder(toPaintOrder(one));
		this.setSecondOrder(toPaintOrder(two));
		this.setLastOrder(toPaintOrder(three));
	}

	/**
	 * 设置为默认渲染顺序
	 */
	public void defaultDraw() {
		setDrawOrder(DrawOrder.SPRITE, DrawOrder.DESKTOP, DrawOrder.USER);
	}

	/**
	 * 把精灵渲染置于桌面与桌面之间
	 */
	public void centerSpriteDraw() {
		setFristOrder(DRAW_DESKTOP_PAINT());
		setSecondOrder(DRAW_SPRITE_PAINT());
		setLastOrder(DRAW_USER_PAINT());
	}

	/**
	 * 最后绘制精灵
	 */
	public void lastSpriteDraw() {
		setFristOrder(DRAW_USER_PAINT());
		setSecondOrder(DRAW_DESKTOP_PAINT());
		setLastOrder(DRAW_SPRITE_PAINT());
	}

	/**
	 * 只绘制精灵
	 */
	public void onlySpriteDraw() {
		setFristOrder(null);
		setSecondOrder(null);
		setLastOrder(DRAW_SPRITE_PAINT());
	}

	/**
	 * 把桌面渲染置于精灵与桌面之间
	 */
	public void centerDesktopDraw() {
		setFristOrder(DRAW_SPRITE_PAINT());
		setSecondOrder(DRAW_DESKTOP_PAINT());
		setLastOrder(DRAW_USER_PAINT());
	}

	/**
	 * 最后绘制组件
	 */
	public void lastDesktopDraw() {
		setFristOrder(DRAW_USER_PAINT());
		setSecondOrder(DRAW_SPRITE_PAINT());
		setLastOrder(DRAW_DESKTOP_PAINT());
	}

	/**
	 * 只绘制组件
	 */
	public void onlyDesktopDraw() {
		setFristOrder(null);
		setSecondOrder(null);
		setLastOrder(DRAW_DESKTOP_PAINT());
	}

	/**
	 * 最后绘制用户界面
	 */
	public void lastUserDraw() {
		setFristOrder(DRAW_SPRITE_PAINT());
		setSecondOrder(DRAW_DESKTOP_PAINT());
		setLastOrder(DRAW_USER_PAINT());
	}

	/**
	 * 优先绘制用户界面
	 */
	public void fristUserDraw() {
		setFristOrder(DRAW_USER_PAINT());
		setSecondOrder(DRAW_SPRITE_PAINT());
		setLastOrder(DRAW_DESKTOP_PAINT());
	}

	/**
	 * 把用户渲染置于精灵与桌面之间
	 */
	public void centerUserDraw() {
		setFristOrder(DRAW_SPRITE_PAINT());
		setSecondOrder(DRAW_USER_PAINT());
		setLastOrder(DRAW_DESKTOP_PAINT());
	}

	/**
	 * 只保留一个用户渲染接口（即无组件会被渲染出来）
	 */
	public void onlyUserDraw() {
		setFristOrder(null);
		setSecondOrder(null);
		setLastOrder(DRAW_USER_PAINT());
	}

	public boolean containsActionKey(Integer keyCode) {
		return _keyActions.containsKey(keyCode);
	}

	public void addActionKey(Integer keyCode, ActionKey e) {
		_keyActions.put(keyCode, e);
	}

	public void removeActionKey(Integer keyCode) {
		_keyActions.remove(keyCode);
	}

	public void pressActionKey(Integer keyCode) {
		ActionKey key = (ActionKey) _keyActions.getValue(keyCode);
		if (key != null) {
			key.press();
		}
	}

	public void releaseActionKey(Integer keyCode) {
		ActionKey key = (ActionKey) _keyActions.getValue(keyCode);
		if (key != null) {
			key.release();
		}
	}

	public void clearActionKey() {
		_keyActions.clear();
	}

	public void releaseActionKeys() {
		int keySize = _keyActions.size();
		if (keySize > 0) {
			for (int i = 0; i < keySize; i++) {
				ActionKey act = (ActionKey) _keyActions.get(i);
				act.release();
			}
		}
	}

	public SkinManager skin() {
		return SkinManager.get();
	}

	private final TouchedClick makeTouched() {
		if (_touchListener == null) {
			_touchListener = new TouchedClick();
		}
		if (_clickListener != null) {
			_touchListener.addClickListener(_clickListener);
		}
		this._clickListener = _touchListener;
		return _touchListener;
	}

	public Screen addClickListener(ClickListener c) {
		makeTouched().addClickListener(c);
		return this;
	}

	public Screen clearTouched() {
		if (_touchListener != null) {
			_touchListener.clear();
		}
		_touchListener = null;
		return this;
	}

	public boolean isTouchedEnabled() {
		if (_touchListener != null) {
			return _touchListener.isEnabled();
		}
		return false;
	}

	public void setTouchedEnabled(boolean e) {
		if (_touchListener != null) {
			_touchListener.setEnabled(e);
		}
	}

	/**
	 * 监听所有触屏事件(可以添加多个)
	 * 
	 * @param t
	 * @return
	 */
	public Screen all(Touched t) {
		makeTouched().setAllTouch(t);
		return this;
	}

	/**
	 * 添加所有点击事件(可以添加多个)
	 * 
	 * @param t
	 * @return
	 */
	public Screen down(Touched t) {
		makeTouched().setDownTouch(t);
		return this;
	}

	/**
	 * 监听所有触屏离开事件(可以添加多个)
	 * 
	 * @param t
	 * @return
	 */
	public Screen up(Touched t) {
		makeTouched().setUpTouch(t);
		return this;
	}

	/**
	 * 监听所有触屏拖拽事件(可以添加多个)
	 * 
	 * @param t
	 * @return
	 */
	public Screen onDrag(Touched t) {
		makeTouched().setDragTouch(t);
		return this;
	}

	/** 受限函数,关系到线程的同步与异步，使用此部分函数实现的功能，将无法在GWT编译的HTML5环境运行，所以默认注释掉. **/

	/**
	 * 但是，TeaVM之类的Bytecode to JS转码器是支持的.因此视情况有恢复可能性，但千万注意，恢复此部分函数的话。[不保证完整的跨平台性]
	 **/
	/*
	 * private boolean isDrawing;
	 * 
	 * @Deprecated public void yieldDraw() { notifyDraw(); waitUpdate(); }
	 * 
	 * @Deprecated public void yieldUpdate() { notifyUpdate(); waitDraw(); }
	 * 
	 * @Deprecated public synchronized void notifyDraw() { this.isDrawing =
	 * true; this.notifyAll(); }
	 * 
	 * @Deprecated public synchronized void notifyUpdate() { this.isDrawing =
	 * false; this.notifyAll(); }
	 * 
	 * @Deprecated public synchronized void waitDraw() { for (; !isDrawing;) {
	 * try { this.wait(); } catch (InterruptedException ex) { } } }
	 * 
	 * @Deprecated public synchronized void waitUpdate() { for (; isDrawing;) {
	 * try { this.wait(); } catch (InterruptedException ex) { } } }
	 * 
	 * @Deprecated public synchronized void waitFrame(int i) { for (int wait =
	 * frame + i; frame < wait;) { try { super.wait(); } catch (Throwable ex) {
	 * } } }
	 * 
	 * @Deprecated public synchronized void waitTime(long i) { for (long time =
	 * System.currentTimeMillis() + i; System .currentTimeMillis() < time;) try
	 * { super.wait(time - System.currentTimeMillis()); } catch (Throwable ex) {
	 * } }
	 */
	/** 受限函数结束 **/

	public LayoutConstraints getRootConstraints() {
		if (desktop != null) {
			return desktop.getRootConstraints();
		}
		return null;
	}

	public LayoutPort getLayoutPort() {
		if (desktop != null) {
			return desktop.getLayoutPort();
		}
		return null;
	}

	public LayoutPort getLayoutPort(final RectBox newBox, final LayoutConstraints newBoxConstraints) {
		if (desktop != null) {
			return desktop.getLayoutPort(newBox, newBoxConstraints);
		}
		return null;
	}

	public LayoutPort getLayoutPort(final LayoutPort src) {
		if (desktop != null) {
			return desktop.getLayoutPort(src);
		}
		return null;
	}

	public void layoutElements(final LayoutManager manager, final LComponent... comps) {
		if (desktop != null) {
			desktop.layoutElements(manager, comps);
		}
	}

	public void layoutElements(final LayoutManager manager, final LayoutPort... ports) {
		if (desktop != null) {
			desktop.layoutElements(manager, ports);
		}
	}

	public void packLayout(final LayoutManager manager) {
		if (desktop != null) {
			desktop.packLayout(manager);
		}
	}

	public void packLayout(final LayoutManager manager, final float spacex, final float spacey, final float spaceWidth,
			final float spaceHeight) {
		if (desktop != null) {
			desktop.packLayout(manager, spacex, spacey, spaceHeight, spaceHeight);
		}
	}

	public Screen setCamera(BaseCamera came) {
		_isExistCamera = (came != null);
		if (_isExistCamera) {
			_baseCamera = came;
			_baseCamera.setup();
		}
		return this;
	}

	public BaseCamera getCamera() {
		if (_baseCamera == null) {
			_baseCamera = new EmptyCamera();
		}
		return _baseCamera;
	}

	public void stopRepaint() {
		LSystem.stopRepaint();
	}

	public void startRepaint() {
		LSystem.startRepaint();
	}

	public void stopProcess() {
		this.processing = false;
	}

	public void startProcess() {
		this.processing = true;
	}

	public void registerTouchArea(final LTouchArea touchArea) {
		this._touchAreas.add(touchArea);
	}

	public boolean unregisterTouchArea(final LTouchArea touchArea) {
		return this._touchAreas.remove(touchArea);
	}

	public void clearTouchAreas() {
		this._touchAreas.clear();
	}

	public TArray<LTouchArea> getTouchAreas() {
		return this._touchAreas;
	}

	private final void updateTouchArea(final LTouchArea.Event e, final float touchX, final float touchY) {
		if (this._touchAreas.size == 0) {
			return;
		}
		final TArray<LTouchArea> touchAreas = this._touchAreas;
		final int touchAreaCount = touchAreas.size;
		if (touchAreaCount > 0) {
			for (int i = 0; i < touchAreaCount; i++) {
				final LTouchArea touchArea = touchAreas.get(i);
				if (touchArea.contains(touchX, touchY)) {
					touchArea.onAreaTouched(e, touchX, touchY);
				}
			}
		}
	}

	/**
	 * 作用近似于js的同名函数,以指定的延迟执行Updateable
	 * 
	 * @param update
	 * @param delay
	 * @return
	 */
	public Screen setTimeout(final Updateable update, final long delay) {
		return add(new Port<LTimerContext>() {

			final LTimer timer = new LTimer(0);

			@Override
			public void onEmit(LTimerContext event) {
				if (timer.action(delay) && update != null) {
					update.action(event);
				}
			}
		}, true);
	}

	/**
	 * 添加一个监听LTimerContext的Port
	 * 
	 * @param timer
	 * @return
	 */
	public Screen add(Port<LTimerContext> timer) {
		return add(timer, false);
	}

	/**
	 * 添加一个监听LTimerContext的Port(若paint为true,则监听同步画布刷新,否则异步监听,默认为false)
	 * 
	 * @param timer
	 * @param paint
	 * @return
	 */
	public Screen add(Port<LTimerContext> timer, boolean paint) {
		LGame game = LSystem.base();
		if (game != null && game.display() != null) {
			if (paint) {
				_conns.add(game.display().paint.connect(timer));
			} else {
				_conns.add(game.display().update.connect(timer));
			}
		}
		return this;
	}

	/**
	 * 删除一个Port的LTimerContext监听
	 * 
	 * @param timer
	 * @return
	 */
	public Screen remove(Port<LTimerContext> timer) {
		return remove(timer, false);
	}

	/**
	 * 删除一个Port的LTimerContext监听(为true时,删除绑定到画布刷新中的,为false删除绑定到异步的update监听上的,默认为false)
	 * 
	 * @param timer
	 * @param paint
	 * @return
	 */
	public Screen remove(Port<LTimerContext> timer, boolean paint) {
		LGame game = LSystem.base();
		if (game != null && game.display() != null) {
			if (paint) {
				_conns.remove(game.display().paint.connect(timer));
			} else {
				_conns.remove(game.display().update.connect(timer));
			}
		}
		return this;
	}

	/**
	 * 要求缓动动画计算与游戏画布刷新同步或异步(默认异步)
	 * 
	 * @param sync
	 * @return
	 */
	public Screen syncTween(boolean sync) {
		LGame game = LSystem.base();
		if (game != null && game.display() != null) {
			game.display().updateSyncTween(sync);
		}
		return this;
	}

	/**
	 * 设定缓动动画延迟时间
	 * 
	 * @param delay
	 * @return
	 */
	public Screen delayTween(long delay) {
		ActionControl.setDelay(delay);
		return this;
	}

	public Screen putRelease(LRelease r) {
		if (releases == null) {
			releases = new TArray<LRelease>(10);
		}
		releases.add(r);
		return this;
	}

	public boolean containsRelease(LRelease r) {
		if (releases == null) {
			return false;
		}
		return releases.contains(r);
	}

	public Screen removeRelease(LRelease r) {
		if (releases == null) {
			releases = new TArray<LRelease>(10);
		}
		releases.remove(r);
		return this;
	}

	public Screen putReleases(LRelease... rs) {
		if (releases == null) {
			releases = new TArray<LRelease>(10);
		}
		final int size = rs.length;
		for (int i = 0; i < size; i++) {
			releases.add(rs[i]);
		}
		return this;
	}

	public float getDeltaTime() {
		return MathUtils.max(elapsedTime / 1000f, LSystem.MIN_SECONE_SPEED_FIXED);
	}

	public long getElapsedTime() {
		return elapsedTime;
	}

	public LGame getGame() {
		return LSystem.base();
	}

	public final boolean isSpriteRunning() {
		return spriteRun;
	}

	public final boolean isDesktopRunning() {
		return desktopRun;
	}

	public final boolean isStageRunning() {
		return stageRun;
	}

	public abstract void draw(GLEx g);

	/**
	 * 替换当前Screen为其它Screen(替换效果随机)
	 * 
	 * @param screen
	 * @return
	 */
	public Screen replaceScreen(final Screen screen) {
		Screen tmp = null;
		int rnd = MathUtils.random(0, 11);
		switch (rnd) {
		default:
		case 0:
			tmp = replaceScreen(screen, PageMethod.ZoomOut);
			break;
		case 1:
			tmp = replaceScreen(screen, PageMethod.ZoomIn);
			break;
		case 2:
			tmp = replaceScreen(screen, PageMethod.Accordion);
			break;
		case 3:
			tmp = replaceScreen(screen, PageMethod.BackToFore);
			break;
		case 4:
			tmp = replaceScreen(screen, PageMethod.CubeIn);
			break;
		case 5:
			tmp = replaceScreen(screen, PageMethod.Depth);
			break;
		case 6:
			tmp = replaceScreen(screen, PageMethod.Fade);
			break;
		case 7:
			tmp = replaceScreen(screen, PageMethod.RotateDown);
			break;
		case 8:
			tmp = replaceScreen(screen, PageMethod.RotateUp);
			break;
		case 9:
			tmp = replaceScreen(screen, PageMethod.Stack);
			break;
		case 10:
			tmp = replaceScreen(screen, PageMethod.Rotate);
			break;
		case 11:
			int random = MathUtils.random(0, 15);
			if (random == 0) {
				tmp = replaceScreen(screen, MoveMethod.FROM_LEFT);
			} else if (random == 1) {
				tmp = replaceScreen(screen, MoveMethod.FROM_UP);
			} else if (random == 2) {
				tmp = replaceScreen(screen, MoveMethod.FROM_DOWN);
			} else if (random == 3) {
				tmp = replaceScreen(screen, MoveMethod.FROM_RIGHT);
			} else if (random == 4) {
				tmp = replaceScreen(screen, MoveMethod.FROM_UPPER_LEFT);
			} else if (random == 5) {
				tmp = replaceScreen(screen, MoveMethod.FROM_UPPER_RIGHT);
			} else if (random == 6) {
				tmp = replaceScreen(screen, MoveMethod.FROM_LOWER_LEFT);
			} else if (random == 7) {
				tmp = replaceScreen(screen, MoveMethod.FROM_LOWER_RIGHT);
			} else if (random == 8) {
				tmp = replaceScreen(screen, MoveMethod.OUT_LEFT);
			} else if (random == 9) {
				tmp = replaceScreen(screen, MoveMethod.OUT_UP);
			} else if (random == 10) {
				tmp = replaceScreen(screen, MoveMethod.OUT_DOWN);
			} else if (random == 11) {
				tmp = replaceScreen(screen, MoveMethod.OUT_RIGHT);
			} else if (random == 12) {
				tmp = replaceScreen(screen, MoveMethod.OUT_UPPER_LEFT);
			} else if (random == 13) {
				tmp = replaceScreen(screen, MoveMethod.OUT_UPPER_RIGHT);
			} else if (random == 14) {
				tmp = replaceScreen(screen, MoveMethod.OUT_LOWER_LEFT);
			} else if (random == 15) {
				tmp = replaceScreen(screen, MoveMethod.OUT_LOWER_RIGHT);
			} else {
				tmp = replaceScreen(screen, MoveMethod.FROM_LEFT);
			}
			break;
		}
		return tmp;
	}

	/**
	 * 替换当前Screen为其它Screen,替换效果指定
	 * 
	 * @param screen
	 * @param screenSwitch
	 * @return
	 */
	public Screen replaceScreen(final Screen screen, ScreenSwitch screenSwitch) {
		if (screen != null && screen != this) {
			screen.setOnLoadState(false);
			setLock(true);
			screen.setLock(true);
			this.replaceDstScreen = screen;
			this.screenSwitch = screenSwitch;
			screen.setRepaintMode(SCREEN_NOT_REPAINT);

			RealtimeProcessManager.get().addProcess(new RealtimeProcess() {

				@Override
				public void run(LTimerContext time) {
					try {
						screen.onCreate(LSystem.viewSize.getWidth(), LSystem.viewSize.getHeight());
						screen.setClose(false);
						screen.onLoad();
						screen.setRepaintMode(SCREEN_NOT_REPAINT);
						screen.onLoaded();
						screen.setOnLoadState(true);
					} catch (Throwable cause) {
						LSystem.error("Replace screen dispatch failure", cause);
					} finally {
						kill();
					}
				}
			});

			replaceLoading = true;
		}
		return this;
	}

	public Screen replaceScreen(final Screen screen, PageMethod m) {
		return replaceScreen(screen, new ScreenSwitch(m, this, screen));
	}

	/**
	 * 替换当前Screen为其它Screen,并指定页面交替的移动效果
	 * 
	 * @param screen
	 * @param m
	 * @return
	 */
	public Screen replaceScreen(final Screen screen, MoveMethod m) {
		if (screen != null && screen != this) {
			screen.setOnLoadState(false);
			setLock(true);
			screen.setLock(true);
			this.replaceMethod = m;
			this.replaceDstScreen = screen;
			screen.setRepaintMode(SCREEN_NOT_REPAINT);
			switch (m) {
			case FROM_LEFT:
				dstPos.setLocation(-getWidth(), 0);
				isScreenFrom = true;
				break;
			case FROM_RIGHT:
				dstPos.setLocation(getWidth(), 0);
				isScreenFrom = true;
				break;
			case FROM_UP:
				dstPos.setLocation(0, -getHeight());
				isScreenFrom = true;
				break;
			case FROM_DOWN:
				dstPos.setLocation(0, getHeight());
				isScreenFrom = true;
				break;
			case FROM_UPPER_LEFT:
				dstPos.setLocation(-getWidth(), -getHeight());
				isScreenFrom = true;
				break;
			case FROM_UPPER_RIGHT:
				dstPos.setLocation(getWidth(), -getHeight());
				isScreenFrom = true;
				break;
			case FROM_LOWER_LEFT:
				dstPos.setLocation(-getWidth(), getHeight());
				isScreenFrom = true;
				break;
			case FROM_LOWER_RIGHT:
				dstPos.setLocation(getWidth(), getHeight());
				isScreenFrom = true;
				break;
			default:
				dstPos.setLocation(0, 0);
				isScreenFrom = false;
				break;
			}

			RealtimeProcessManager.get().addProcess(new RealtimeProcess() {

				@Override
				public void run(LTimerContext time) {
					try {
						screen.onCreate(LSystem.viewSize.getWidth(), LSystem.viewSize.getHeight());
						screen.setClose(false);
						screen.onLoad();
						screen.setRepaintMode(SCREEN_NOT_REPAINT);
						screen.onLoaded();
						screen.setOnLoadState(true);
					} catch (Throwable cause) {
						LSystem.error("Replace screen dispatch failure", cause);
					} finally {
						kill();
					}
				}
			});

			replaceLoading = true;
		}

		return this;
	}

	public int getReplaceScreenSpeed() {
		return replaceScreenSpeed;
	}

	public Screen setReplaceScreenSpeed(int s) {
		this.replaceScreenSpeed = s;
		return this;
	}

	public Screen setReplaceScreenDelay(long d) {
		replaceDelay.setDelay(d);
		return this;
	}

	public long getReplaceScreenDelay() {
		return replaceDelay.getDelay();
	}

	private void submitReplaceScreen() {
		if (handler != null) {
			handler.setCurrentScreen(replaceDstScreen, false);
			replaceDstScreen._closeUpdate = new Updateable() {

				@Override
				public void action(Object a) {
					destroy();
				}
			};
		}
		replaceLoading = false;
	}

	public Screen removeTouchLimit() {
		_limits.clear();
		return this;
	}

	public Screen removeTouchLimit(RectBox r) {
		if (r != null) {
			_limits.remove(r);
		}
		return this;
	}

	public Screen removeTouchLimit(LObject<?> c) {
		if (c != null) {
			_limits.remove(c.getCollisionArea());
		}
		return this;
	}

	public Screen addTouchLimit(LObject<?> c) {
		if (c != null) {
			_limits.add(c.getCollisionArea());
		}
		return this;
	}

	public Screen addTouchLimit(RectBox r) {
		if (r != null) {
			_limits.add(r);
		}
		return this;
	}

	public boolean isClickLimit(GameTouch e) {
		return isClickLimit(e.x(), e.y());
	}

	public boolean isClickLimit(int x, int y) {
		if (_limits.size == 0) {
			return false;
		}
		for (RectBox rect : _limits) {
			if (rect.contains(x, y)) {
				return true;
			}
		}
		return false;
	}

	protected final PaintOrder DRAW_USER_PAINT() {
		if (userOrder == null) {
			userOrder = new PaintOrder(DRAW_USER, this);
		}
		return userOrder;
	}

	protected final PaintOrder DRAW_SPRITE_PAINT() {
		if (spriteOrder == null) {
			spriteOrder = new PaintOrder(DRAW_SPRITE, this);
		}
		return spriteOrder;
	}

	protected final PaintOrder DRAW_DESKTOP_PAINT() {
		if (desktopOrder == null) {
			desktopOrder = new PaintOrder(DRAW_DESKTOP, this);
		}
		return desktopOrder;
	}

	final public void resetSize() {
		resetSize(0, 0);
	}

	final public void resetSize(int w, int h) {
		this.handler = LSystem.getProcess();
		this.width = (w <= 0 ? LSystem.viewSize.getWidth() : w);
		this.height = (h <= 0 ? LSystem.viewSize.getHeight() : h);
		this.halfWidth = width / 2;
		this.halfHeight = height / 2;
		this.setSize(width, height);
		if (spriteRun && sprites != null) {
			sprites.setSize(width, height);
		}
		if (desktopRun && desktop != null) {
			desktop.setSize(width, height);
			desktop.resize();
		}
		if (isGravity && gravityHandler != null) {
			gravityHandler.setLimit(width, height);
		}
		this.resize(width, height);
	}

	final public void resetOrder() {
		// 最先精灵
		this.fristOrder = DRAW_SPRITE_PAINT();
		// 其次桌面
		this.secondOrder = DRAW_DESKTOP_PAINT();
		// 最后用户
		this.lastOrder = DRAW_USER_PAINT();
		this.fristPaintFlag = true;
		this.secondPaintFlag = true;
		this.lastPaintFlag = true;
	}

	public boolean contains(float x, float y) {
		return getRectBox().contains(x, y);
	}

	public boolean contains(float x, float y, float w, float h) {
		return getRectBox().contains(x, y, w, h);
	}

	public boolean intersects(float x, float y) {
		return getRectBox().intersects(x, y);
	}

	public boolean intersects(float x, float y, float w, float h) {
		return getRectBox().intersects(x, y, w, h);
	}

	/**
	 * 当Screen被创建(或再次加载)时将调用此函数
	 * 
	 * @param width
	 * @param height
	 */
	public void onCreate(int width, int height) {
		this.mode = SCREEN_NOT_REPAINT;
		this.width = width;
		this.height = height;
		this.halfWidth = width / 2;
		this.halfHeight = height / 2;
		this.lastTouchX = lastTouchY = touchDX = touchDY = 0;
		this.isLoad = isLock = isClose = isTranslate = isGravity = false;
		if (sprites != null) {
			sprites.close();
			sprites.removeAll();
			sprites = null;
		}
		this.sprites = new Sprites("ScreenSprites", this, width, height);
		if (desktop != null) {
			desktop.close();
			desktop.clear();
			desktop = null;
		}
		this.desktop = new Desktop("ScreenDesktop", this, width, height);
		this._keyActions.clear();
		this.isNext = true;
		this.index = 0;
		this.tx = ty = 0;
		this.isTranslate = false;
		this._visible = true;
		this._rotation = 0;
		this._scaleX = _scaleY = _alpha = 1f;
		this._baseColor = null;
		this._isExistCamera = false;
	}

	public Screen invokeAsync(Runnable runnable) {
		LSystem.invokeAsync(runnable);
		return this;
	}

	public Screen invokeLater(Runnable runnable) {
		LSystem.invokeLater(runnable);
		return this;
	}

	public Screen addResume(Updateable u) {
		if (handler != null) {
			handler.addResume(u);
		}
		return this;
	}

	public Screen removeResume(Updateable u) {
		if (handler != null) {
			handler.removeResume(u);
		}
		return this;
	}

	public Screen addLoad(Updateable u) {
		if (handler != null) {
			handler.addLoad(u);
		}
		return this;
	}

	public boolean containsLoad(Updateable u) {
		if (handler != null) {
			return handler.containsLoad(u);
		}
		return false;
	}

	public Screen removeLoad(Updateable u) {
		if (handler != null) {
			handler.removeLoad(u);
		}
		return this;
	}

	public Screen removeAllLoad() {
		if (handler != null) {
			handler.removeAllLoad();
		}
		return this;
	}

	public Screen addUnLoad(Updateable u) {
		if (handler != null) {
			handler.addUnLoad(u);
		}
		return this;
	}

	public boolean containsUnLoad(Updateable u) {
		if (handler != null) {
			return handler.containsUnLoad(u);
		}
		return false;
	}

	public Screen removeUnLoad(Updateable u) {
		if (handler != null) {
			handler.removeUnLoad(u);
		}
		return this;
	}

	public Screen removeAllUnLoad() {
		if (handler != null) {
			handler.removeAllUnLoad();
		}
		return this;
	}

	/**
	 * 设置游戏中使用的默认IFont
	 * 
	 * @param font
	 * @return
	 */
	public Screen setSystemGameFont(IFont font) {
		LSystem.setSystemGameFont(font);
		return this;
	}

	/**
	 * 获得游戏中使用的IFont
	 * 
	 * @return
	 */
	public IFont getSystemGameFont() {
		return LSystem.getSystemGameFont();
	}

	/**
	 * 设置系统log使用的默认IFont
	 * 
	 * @param font
	 * @return
	 */
	public Screen setSystemLogFont(IFont font) {
		LSystem.setSystemLogFont(font);
		return this;
	}

	/**
	 * 设置系统log使用的IFont
	 * 
	 * @return
	 */
	public IFont getSystemLogFont() {
		return LSystem.getSystemLogFont();
	}

	/**
	 * 当执行Screen转换时将调用此函数(如果返回的LTransition不为null，则渐变效果会被执行)
	 * 
	 * @return
	 */
	public LTransition onTransition() {
		return _transition;
	}

	/**
	 * 设置一个空操作的过渡效果
	 */
	public void noopTransition() {
		this._transition = LTransition.newEmpty();
	}

	/**
	 * 注入一个渐变特效，在引入Screen时将使用此特效进行渐变.
	 * 
	 * @param t
	 * @return
	 */
	public Screen setTransition(LTransition t) {
		this._transition = t;
		return this;
	}

	/**
	 * 注入一个渐变特效的名称，以及渐变使用的颜色，在引入Screen时将使用此特效进行渐变.
	 * 
	 * @param transName
	 * @param c
	 * @return
	 */
	public Screen setTransition(String transName, LColor c) {
		this._transition = LTransition.newTransition(transName, c);
		return this;
	}

	/**
	 * @see onTransition
	 * 
	 * @return
	 */
	public LTransition getTransition() {
		return this._transition;
	}

	/**
	 * 设定重力系统是否启动,并返回控制器
	 * 
	 * @param g
	 * @return
	 */
	public GravityHandler setGravity(boolean g) {
		return setGravity(EasingMode.Linear, g);
	}

	/**
	 * 设定重力系统是否启动,并返回控制器
	 * 
	 * @param ease
	 * @param g
	 * @return
	 */
	public GravityHandler setGravity(EasingMode ease, boolean g) {
		return setGravity(getWidth(), getHeight(), ease, 1f, g);
	}

	/**
	 * 设定重力系统是否启动,并返回控制器
	 * 
	 * @param ease
	 * @param d
	 * @param g
	 * @return
	 */
	public GravityHandler setGravity(EasingMode ease, float d, boolean g) {
		return setGravity(getWidth(), getHeight(), ease, d, g);
	}

	/**
	 * 设定重力系统是否启动,并返回控制器
	 * 
	 * @param w
	 * @param h
	 * @param ease
	 * @param g
	 * @return
	 */
	public GravityHandler setGravity(int w, int h, EasingMode ease, float d, boolean g) {
		if (g && (gravityHandler == null || gravityHandler.isClosed())) {
			gravityHandler = new GravityHandler(w, h, ease, d);
		}
		this.isGravity = g;
		return gravityHandler;
	}

	/**
	 * 返回重力系统是否启动
	 * 
	 * @param g
	 * @return
	 */
	public GravityHandler getGravity() {
		return gravityHandler;
	}

	/**
	 * 返回一个对象和当前其它重力对象的碰撞关系
	 * 
	 * @param g
	 * @return
	 */
	public GravityResult getCollisionBetweenObjects(Gravity g) {
		if (isGravity) {
			return gravityHandler.getCollisionBetweenObjects(g);
		}
		return null;
	}

	/**
	 * 判断重力系统是否启动
	 * 
	 * @return
	 */
	public boolean isGravity() {
		return this.isGravity;
	}

	/**
	 * 获得当前重力器句柄
	 * 
	 * @return
	 */
	public GravityHandler getGravityHandler() {
		return setGravity(true);
	}

	/**
	 * 获得当前游戏事务运算时间是否被锁定
	 * 
	 * @return
	 */
	public boolean isLock() {
		return isLock;
	}

	/**
	 * 锁定游戏事务运算时间
	 * 
	 * @param lock
	 */
	public Screen setLock(boolean lock) {
		this.isLock = lock;
		return this;
	}

	/**
	 * 关闭游戏
	 * 
	 * @param close
	 */
	public Screen setClose(boolean close) {
		this.isClose = close;
		return this;
	}

	/**
	 * 判断游戏是否被关闭
	 * 
	 * @return
	 */
	public boolean isClosed() {
		return isClose;
	}

	/**
	 * 设定当前帧
	 * 
	 * @param frame
	 */
	public Screen setFrame(int frame) {
		this.frame = frame;
		return this;
	}

	/**
	 * 返回当前帧
	 * 
	 * @return
	 */
	public int getFrame() {
		return frame;
	}

	/**
	 * 移动当前帧
	 * 
	 * @return
	 */
	public synchronized boolean next() {
		this.frame++;
		return isNext;
	}

	/**
	 * 初始化时加载的数据
	 */
	public abstract void onLoad();

	/**
	 * 初始化加载完毕
	 * 
	 */
	public void onLoaded() {

	}

	/**
	 * 改变资源加载状态
	 */
	public Screen setOnLoadState(boolean flag) {
		this.isLoad = flag;
		return this;
	}

	/**
	 * 是否处于过渡中
	 * 
	 * @return
	 */
	public boolean isTransitioning() {
		if (handler != null) {
			return handler.isTransitioning();
		}
		// 如果过渡效果不存在，则返回是否加载完毕
		return isLoad;
	}

	/**
	 * 过度是否完成
	 */
	public boolean isTransitionCompleted() {
		if (handler != null) {
			return handler.isTransitionCompleted();
		}
		// 如果过渡效果不存在，则返回是否加载完毕
		return isLoad;
	}

	/**
	 * 获得当前资源加载是否完成
	 */
	public boolean isOnLoadComplete() {
		return isLoad;
	}

	private ReplaceEvent revent;

	/**
	 * 设置一个Screen替换事件的默认布局
	 * 
	 * @param re
	 */
	public void setReplaceEvent(ReplaceEvent re) {
		this.revent = re;
	}

	/**
	 * 返回Screen替换事件
	 * 
	 * @return
	 */
	public ReplaceEvent getReplaceEvent() {
		return this.revent;
	}

	/**
	 * 返回指定Screen替换事件中的对应索引的Screen
	 * 
	 * @param idx
	 * @return
	 */
	public Screen getReplaceScreen(int idx) {
		if (revent == null) {
			return null;
		}
		return this.revent.getScreen(idx);
	}

	/**
	 * 判断指定名称的Screen是否存在
	 * 
	 * @param name
	 * @return
	 */
	public boolean containsScreen(CharSequence name) {
		return handler != null ? handler.containsScreen(name) : false;
	}

	/**
	 * 顺序运行并删除一个Screen
	 * 
	 * @return
	 */
	public Screen runPopScreen() {
		if (handler != null) {
			handler.runPopScreen();
		}
		return this;
	}

	/**
	 * 运行最后一个Screen
	 * 
	 * @return
	 */
	public Screen runPeekScreen() {
		if (handler != null) {
			handler.runPeekScreen();
		}
		return this;
	}

	/**
	 * 取出第一个Screen并执行
	 * 
	 */
	public Screen runFirstScreen() {
		if (handler != null) {
			handler.runFirstScreen();
		}
		return this;
	}

	/**
	 * 取出最后一个Screen并执行
	 */
	public Screen runLastScreen() {
		if (handler != null) {
			handler.runLastScreen();
		}
		return this;
	}

	/**
	 * 运行指定位置的Screen
	 * 
	 * @param index
	 */
	public Screen runIndexScreen(int index) {
		if (handler != null) {
			handler.runIndexScreen(index);
		}
		return this;
	}

	/**
	 * 运行自当前Screen起的上一个Screen
	 */
	public Screen runPreviousScreen() {
		if (handler != null) {
			handler.runPreviousScreen();
		}
		return this;
	}

	/**
	 * 运行自当前Screen起的下一个Screen
	 */
	public Screen runNextScreen() {
		if (handler != null) {
			handler.runNextScreen();
		}
		return this;
	}

	/**
	 * 添加指定名称的Screen到当前Screen，但不立刻执行
	 * 
	 * @param name
	 * @param screen
	 * @return
	 */
	public Screen addScreen(CharSequence name, Screen screen) {
		if (handler != null) {
			handler.addScreen(name, screen);
		}
		return this;
	}

	/**
	 * 获得指定名称的Screen
	 * 
	 * @param name
	 * @return
	 */
	public Screen getScreen(CharSequence name) {
		if (handler != null) {
			return handler.getScreen(name);
		}
		return this;
	}

	/**
	 * 执行指定替换事件索引的Screen
	 * 
	 * @param idx
	 * @return
	 */
	public Screen runEventScreen(int idx) {
		Screen screen = getReplaceScreen(idx);
		if (screen != null && handler != null) {
			handler.setScreen(screen);
		}
		return screen;
	}

	/**
	 * 执行指定名称的Screen
	 * 
	 * @param name
	 * @return
	 */
	public Screen runScreen(CharSequence name) {
		if (handler != null) {
			return handler.runScreen(name);
		}
		return this;
	}

	public Screen clearScreen() {
		if (handler != null) {
			handler.clearScreens();
		}
		return this;
	}

	/**
	 * 向缓存中添加Screen数据，但是不立即执行
	 * 
	 * @param screen
	 */
	public Screen addScreen(Screen screen) {
		if (handler != null) {
			handler.addScreen(screen);
		}
		return this;
	}

	/**
	 * 获得保存的Screen列表
	 * 
	 * @return
	 */
	public TArray<Screen> getScreens() {
		if (handler != null) {
			return handler.getScreens();
		}
		return null;
	}

	/**
	 * 获得缓存的Screen总数
	 */
	public int getScreenCount() {
		if (handler != null) {
			return handler.getScreenCount();
		}
		return 0;
	}

	/**
	 * 返回精灵监听
	 * 
	 * @return
	 */

	public SpriteListener getSprListerner() {
		if (sprites == null) {
			return null;
		}
		return sprites.getSprListerner();
	}

	/**
	 * 监听Screen中精灵
	 * 
	 * @param sprListerner
	 */

	public Screen setSprListerner(SpriteListener sprListerner) {
		if (sprites == null) {
			return this;
		}
		sprites.setSprListerner(sprListerner);
		return this;
	}

	/**
	 * 获得当前Screen类名
	 */
	public String getName() {
		return getClass().getSimpleName();
	}

	/**
	 * 设定模拟按钮监听器
	 */

	public Screen setEmulatorListener(EmulatorListener emulator) {
		if (LSystem.getProcess() != null) {
			LSystem.getProcess().setEmulatorListener(emulator);
		}
		return this;
	}

	/**
	 * 返回模拟按钮集合
	 * 
	 * @return
	 */

	public EmulatorButtons getEmulatorButtons() {
		if (LSystem.getProcess() != null) {
			return LSystem.getProcess().getEmulatorButtons();
		}
		return null;
	}

	/**
	 * 设定模拟按钮组是否显示
	 * 
	 * @param visible
	 */

	public Screen emulatorButtonsVisible(boolean visible) {
		if (LSystem.getProcess() != null) {
			try {
				EmulatorButtons es = LSystem.getProcess().getEmulatorButtons();
				es.setVisible(visible);
			} catch (Throwable e) {
			}
		}
		return this;
	}

	/**
	 * 设定背景图像
	 * 
	 * @param screen
	 */
	public Screen setBackground(final LTexture background) {
		if (background != null) {
			setRepaintMode(SCREEN_TEXTURE_REPAINT);
			currentScreenBackground = background;
		} else {
			setRepaintMode(SCREEN_NOT_REPAINT);
		}
		return this;
	}

	/**
	 * 设定背景图像
	 */
	public Screen setBackground(String fileName) {
		return this.setBackground(LSystem.loadTexture(fileName));
	}

	/**
	 * 设定背景颜色
	 * 
	 * @param c
	 */
	public Screen setBackground(LColor c) {
		setRepaintMode(SCREEN_COLOR_REPAINT);
		if (_backgroundColor == null) {
			_backgroundColor = new LColor(c);
		} else {
			_backgroundColor.setColor(c.r, c.g, c.b, c.a);
		}
		return this;
	}

	/**
	 * 设定背景颜色
	 * 
	 * @param colorString
	 * @return
	 */
	public Screen setBackgroundValue(String colorString) {
		return setBackground(LColor.decode(colorString));
	}

	/**
	 * 设定背景颜色
	 * 
	 * @param c
	 * @return
	 */
	public Screen setBackgroundString(String c) {
		return setBackground(new LColor(c));
	}

	public LColor getBackgroundColor() {
		return _backgroundColor;
	}

	/**
	 * 返回背景图像
	 * 
	 * @return
	 */
	public LTexture getBackground() {
		return currentScreenBackground;
	}

	/**
	 * 获得桌面组件（即UI）管理器
	 */
	public Desktop getDesktop() {
		return desktop;
	}

	/**
	 * @see getDesktop
	 * 
	 * @return
	 */
	public Desktop UI() {
		return getDesktop();
	}

	/**
	 * 获得精灵组件管理器
	 * 
	 * @return
	 */
	public Sprites getSprites() {
		return sprites;
	}

	/**
	 * @see getSprites
	 * 
	 * @return
	 */
	public Sprites SPRITE() {
		return getSprites();
	}

	/**
	 * 返回位于屏幕顶部的组件
	 * 
	 * @return
	 */

	public LComponent getTopComponent() {
		if (desktop != null) {
			return desktop.getTopComponent();
		}
		return null;
	}

	/**
	 * 返回位于屏幕底部的组件
	 * 
	 * @return
	 */

	public LComponent getBottomComponent() {
		if (desktop != null) {
			return desktop.getBottomComponent();
		}
		return null;
	}

	public LLayer getTopLayer() {
		if (desktop != null) {
			return desktop.getTopLayer();
		}
		return null;
	}

	public LLayer getBottomLayer() {
		if (desktop != null) {
			return desktop.getBottomLayer();
		}
		return null;
	}

	/**
	 * 返回位于数据顶部的精灵
	 * 
	 */

	public ISprite getTopSprite() {
		if (sprites != null) {
			return sprites.getTopSprite();
		}
		return null;
	}

	/**
	 * 返回位于数据底部的精灵
	 * 
	 */

	public ISprite getBottomSprite() {
		if (sprites != null) {
			return sprites.getBottomSprite();
		}
		return null;
	}

	public Screen add(Object... obj) {
		for (int i = 0; i < obj.length; i++) {
			add(obj[i]);
		}
		return this;
	}

	/**
	 * 添加游戏对象
	 * 
	 * @param obj
	 * @return
	 */
	public Screen add(Object obj) {
		if (obj instanceof ISprite) {
			add((ISprite) obj);
		} else if (obj instanceof LComponent) {
			add((LComponent) obj);
		} else if (obj instanceof LTexture) {
			addPaper((LTexture) obj, 0, 0);
		} else if (obj instanceof Updateable) {
			addLoad((Updateable) obj);
		} else if (obj instanceof GameProcess) {
			addProcess((GameProcess) obj);
		} else if (obj instanceof LRelease) {
			putRelease((LRelease) obj);
		}
		return this;
	}

	/**
	 * 按照上一个精灵的x,y位置,另起一行添加精灵,并偏移指定位置
	 * 
	 * @param spr
	 * @param offX
	 * @param offY
	 * @return
	 */
	public ISprite addPadding(ISprite spr, float offX, float offY) {
		if (isClose || sprites == null) {
			return spr;
		}
		return sprites.addPadding(spr, offX, offY);
	}

	/**
	 * 按照上一个精灵的y轴,另起一行添加精灵
	 * 
	 * @param spr
	 * @return
	 */
	public ISprite addCol(ISprite spr) {
		if (isClose || sprites == null) {
			return spr;
		}
		return sprites.addCol(spr);
	}

	/**
	 * 按照上一个精灵的y轴,另起一行添加精灵,并让y轴偏移指定位置
	 * 
	 * @param spr
	 * @param offY
	 * @return
	 */
	public ISprite addCol(ISprite spr, float offY) {
		if (isClose || sprites == null) {
			return spr;
		}
		return sprites.addCol(spr, offY);
	}

	/**
	 * 按照上一个精灵的x轴,另起一行添加精灵
	 * 
	 * @param spr
	 * @return
	 */
	public Screen addRow(ISprite spr) {
		if (isClose || sprites == null) {
			return this;
		}
		sprites.addRow(spr);
		return this;
	}

	/**
	 * 按照上一个精灵的x轴,另起一行添加精灵,并将x轴偏移指定位置
	 * 
	 * @param spr
	 * @param offX
	 * @return
	 */
	public Screen addRow(ISprite spr, float offX) {
		if (isClose || sprites == null) {
			return this;
		}
		sprites.addRow(spr, offX);
		return this;
	}

	/**
	 * 按照上一个组件的x,y位置,另起一行添加组件,并偏移指定位置
	 * 
	 * @param comp
	 * @param offX
	 * @param offY
	 * @return
	 */
	public Screen addPadding(LComponent comp, float offX, float offY) {
		if (isClose || desktop == null) {
			return this;
		}
		desktop.addPadding(comp, offX, offY);
		return this;
	}

	/**
	 * 按照上一个组件的y轴,另起一行添加组件
	 * 
	 * @param comp
	 * @return
	 */
	public Screen addCol(LComponent comp) {
		if (isClose || desktop == null) {
			return this;
		}
		desktop.addCol(comp);
		return this;
	}

	/**
	 * 按照上一个组件的y轴,另起一行添加组件,并偏移指定y轴坐标
	 * 
	 * @param comp
	 * @param offY
	 * @return
	 */
	public Screen addCol(LComponent comp, float offY) {
		if (isClose || desktop == null) {
			return this;
		}
		desktop.addCol(comp, offY);
		return this;
	}

	/**
	 * 按照上一个组件的x轴,在同一行添加组件
	 * 
	 * @param comp
	 * @return
	 */
	public Screen addRow(LComponent comp) {
		if (isClose || desktop == null) {
			return this;
		}
		desktop.addRow(comp);
		return this;
	}

	/**
	 * 按照上一个组件的x轴,在同一行添加组件,并偏移指定x轴
	 * 
	 * @param comp
	 * @param offX
	 * @return
	 */
	public Screen addRow(LComponent comp, float offX) {
		if (isClose || desktop == null) {
			return this;
		}
		desktop.addRow(comp, offX);
		return this;
	}

	/**
	 * 删除指定对象
	 * 
	 * @param obj
	 * @return
	 */
	public Screen remove(Object obj) {
		if (obj instanceof ISprite) {
			remove((ISprite) obj);
		} else if (obj instanceof LComponent) {
			remove((LComponent) obj);
		} else if (obj instanceof LTexture) {
			remove((LTexture) obj);
		} else if (obj instanceof Updateable) {
			removeLoad((Updateable) obj);
		} else if (obj instanceof GameProcess) {
			removeProcess((GameProcess) obj);
		} else if (obj instanceof LRelease) {
			removeRelease((LRelease) obj);
		}
		return this;
	}

	public Screen remove(Object... obj) {
		for (int i = 0; i < obj.length; i++) {
			remove(obj[i]);
		}
		return this;
	}

	/**
	 * 添加游戏精灵
	 * 
	 * @param sprite
	 */

	public Screen add(ISprite sprite) {
		if (sprites != null) {
			sprites.add(sprite);
			if (sprite instanceof LTouchArea) {
				registerTouchArea((LTouchArea) sprite);
			}
		}
		return this;
	}

	/**
	 * 添加游戏精灵
	 * 
	 * @param sprite
	 * @param x
	 * @param y
	 * @return
	 */
	public Screen addAt(ISprite sprite, float x, float y) {
		if (sprites != null) {
			sprites.addAt(sprite, x, y);
			if (sprite instanceof LTouchArea) {
				registerTouchArea((LTouchArea) sprite);
			}
		}
		return this;
	}

	/**
	 * 添加游戏组件
	 * 
	 * @param s
	 */

	public Screen addSpriteToUI(ISprite s) {
		if (desktop != null) {
			desktop.addSprite(s);
			if (s instanceof LTouchArea) {
				registerTouchArea((LTouchArea) s);
			}
		}
		return this;
	}

	/**
	 * 添加游戏组件
	 * 
	 * @param s
	 * @param x
	 * @param y
	 * @return
	 */
	public Screen addSpriteToUIAt(ISprite s, float x, float y) {
		if (desktop != null) {
			desktop.addSpriteAt(s, x, y);
			if (s instanceof LTouchArea) {
				registerTouchArea((LTouchArea) s);
			}
		}
		return this;
	}

	/**
	 * 添加游戏组件
	 * 
	 * @param comp
	 */

	public Screen add(LComponent comp) {
		if (desktop != null) {
			desktop.add(comp);
			if (comp instanceof LTouchArea) {
				registerTouchArea((LTouchArea) comp);
			}
		}
		return this;
	}

	/**
	 * 添加游戏组件
	 * 
	 * @param comp
	 * @param x
	 * @param y
	 * @return
	 */
	public Screen addAt(LComponent comp, float x, float y) {
		if (desktop != null) {
			desktop.addAt(comp, x, y);
			if (comp instanceof LTouchArea) {
				registerTouchArea((LTouchArea) comp);
			}
		}
		return this;
	}

	public LMenuSelect addMenu(String labels, int x, int y) {
		LMenuSelect menu = LMenuSelect.make(labels, x, y);
		add(menu);
		return menu;
	}

	public LMenuSelect addMenu(String[] labels, int x, int y) {
		LMenuSelect menu = LMenuSelect.make(labels, x, y);
		add(menu);
		return menu;
	}

	public LMenuSelect addMenu(IFont font, String[] labels, int x, int y) {
		LMenuSelect menu = LMenuSelect.make(font, labels, x, y);
		add(menu);
		return menu;
	}

	public LMenuSelect addMenu(IFont font, String[] labels, String path, int x, int y) {
		LMenuSelect menu = LMenuSelect.make(font, labels, path, x, y);
		add(menu);
		return menu;
	}

	public LMenuSelect addMenu(IFont font, String[] labels, LTexture bg, int x, int y) {
		LMenuSelect menu = LMenuSelect.make(font, labels, bg, x, y);
		add(menu);
		return menu;
	}

	public LClickButton addButton(String text, int x, int y, int width, int height) {
		LClickButton click = LClickButton.make(text, x, y, width, height);
		add(click);
		return click;
	}

	public LClickButton addButton(String text, int x, int y, int width, int height, Touched touched) {
		LClickButton click = LClickButton.make(text, x, y, width, height);
		add(click);
		return (LClickButton) click.up(touched);
	}

	public LLabel addLabel(String text) {
		return addLabel(text, 0, 0);
	}

	public LLabel addLabel(String text, float x, float y) {
		return addLabel(text, Vector2f.at(x, y));
	}

	public LLabel addLabel(String text, Vector2f pos) {
		LLabel label = LLabel.make(text, pos.x(), pos.y());
		add(label);
		return label;
	}

	public LLabel addLabel(IFont font, String text, float x, float y) {
		return addLabel(font, text, Vector2f.at(x, y));
	}

	public LLabel addLabel(IFont font, String text, Vector2f pos) {
		return addLabel(font, text, pos, LColor.white);
	}

	public LLabel addLabel(IFont font, String text, float x, float y, LColor color) {
		return addLabel(font, text, Vector2f.at(x, y), color);
	}

	public LLabel addLabel(IFont font, String text, Vector2f pos, LColor color) {
		LLabel label = LLabel.make(text, font, pos.x(), pos.y(), color);
		add(label);
		return label;
	}

	public LLabel addLabel(HorizontalAlign alignment, String text, float x, float y, LColor color) {
		return addLabel(alignment, LSystem.getSystemGameFont(), text, Vector2f.at(x, y), color);
	}

	public LLabel addLabel(HorizontalAlign alignment, IFont font, String text, float x, float y, LColor color) {
		return addLabel(alignment, font, text, Vector2f.at(x, y), color);
	}

	public LLabel addLabel(HorizontalAlign alignment, IFont font, String text, Vector2f pos, LColor color) {
		LLabel label = LLabel.make(alignment, text, font, pos.x(), pos.y(), color);
		add(label);
		return label;
	}

	public Sprite addSprite(LTexture tex) {
		return addSprite(tex, 0, 0);
	}

	public Sprite addSprite(LTexture tex, Vector2f pos) {
		return addSprite(tex, pos.x, pos.y);
	}

	public Sprite addSprite(LTexture tex, float x, float y) {
		Sprite sprite = new Sprite(tex, x, y);
		add(sprite);
		return sprite;
	}

	public LPaper addPaper(LTexture tex, Vector2f pos) {
		return addPaper(tex, pos.x, pos.y);
	}

	public LPaper addPaper(LTexture tex, float x, float y) {
		LPaper paper = new LPaper(tex, (int) x, (int) y);
		add(paper);
		return paper;
	}

	public SpriteLabel addSpriteLabel(String text, float x, float y) {
		return addSpriteLabel(text, Vector2f.at(x, y));
	}

	public SpriteLabel addSpriteLabel(String text, Vector2f pos) {
		SpriteLabel label = new SpriteLabel(text, pos.x(), pos.y());
		add(label);
		return label;
	}

	public SpriteLabel addSpriteLabel(String text, float x, float y, LColor color) {
		return addSpriteLabel(text, Vector2f.at(x, y), color);
	}

	public SpriteLabel addSpriteLabel(String text, Vector2f pos, LColor color) {
		SpriteLabel label = new SpriteLabel(text, pos.x(), pos.y());
		label.setColor(color);
		add(label);
		return label;
	}

	public SpriteLabel addSpriteLabel(IFont font, String text, float x, float y, LColor color) {
		return addSpriteLabel(font, text, Vector2f.at(x, y), color);
	}

	public SpriteLabel addSpriteLabel(IFont font, String text, Vector2f pos, LColor color) {
		SpriteLabel label = new SpriteLabel(font, text, pos.x(), pos.y());
		label.setColor(color);
		add(label);
		return label;
	}

	public SpriteLabel addSpriteLabel(String fontName, Style type, int size, int x, int y, Style style, String text,
			Vector2f pos, LColor color) {
		SpriteLabel label = new SpriteLabel(text, fontName, style, size, pos.x(), pos.y());
		label.setColor(color);
		add(label);
		return label;
	}

	public boolean contains(ISprite sprite) {
		return contains(sprite, false);
	}

	public boolean contains(ISprite sprite, boolean canView) {
		boolean can = false;
		if (sprites != null) {
			can = sprites.contains(sprite);
		}
		if (canView) {
			return can && contains(sprite.x(), sprite.y(), sprite.getWidth(), sprite.getHeight());
		} else {
			return can;
		}
	}

	public boolean intersects(ISprite sprite) {
		return intersects(sprite, false);
	}

	public boolean intersects(ISprite sprite, boolean canView) {
		boolean can = false;
		if (sprites != null) {
			can = sprites.contains(sprite);
		}
		if (canView) {
			return can && intersects(sprite.x(), sprite.y(), sprite.getWidth(), sprite.getHeight());
		} else {
			return can;
		}
	}

	public Screen remove(ISprite sprite) {
		if (sprites != null) {
			sprites.remove(sprite);
			if (sprite instanceof LTouchArea) {
				unregisterTouchArea((LTouchArea) sprite);
			}
		}
		return this;
	}

	public boolean contains(LComponent comp) {
		return contains(comp, false);
	}

	public boolean contains(LComponent comp, boolean canView) {
		boolean can = false;
		if (desktop != null) {
			can = desktop.contains(comp);
		}
		if (canView) {
			return can && getRectBox().contains(comp.getX(), comp.getY(), comp.getWidth(), comp.getHeight());
		} else {
			return can;
		}
	}

	public boolean intersects(LComponent comp) {
		return intersects(comp, false);
	}

	public boolean intersects(LComponent comp, boolean canView) {
		boolean can = false;
		if (desktop != null) {
			can = desktop.contains(comp);
		}
		if (canView) {
			return can && getRectBox().intersects(comp.getX(), comp.getY(), comp.getWidth(), comp.getHeight());
		} else {
			return can;
		}
	}

	public Screen remove(LTexture tex) {
		if (desktop != null) {
			LComponent textureObject = null;
			LComponent[] components = desktop.getComponents();
			for (int i = 0; i < components.length; i++) {
				LComponent comp = components[i];
				if (comp != null && comp.getBackground() != null && tex.equals(comp.getBackground())) {
					textureObject = comp;
					break;
				}
			}
			if (textureObject != null) {
				remove(textureObject);
				if (textureObject instanceof LTouchArea) {
					unregisterTouchArea((LTouchArea) textureObject);
				}
			}
		}
		return this;
	}

	public Screen remove(LComponent comp) {
		if (desktop != null) {
			desktop.remove(comp);
			if (comp instanceof LTouchArea) {
				unregisterTouchArea((LTouchArea) comp);
			}
		}
		return this;
	}

	public boolean containsView(ActionBind obj) {
		return getRectBox().contains(obj.getX(), obj.getY(), obj.getWidth(), obj.getHeight());
	}

	public boolean contains(Object obj) {
		if (obj instanceof ISprite) {
			return contains((ISprite) obj, false);
		} else if (obj instanceof LComponent) {
			return contains((LComponent) obj, false);
		} else if (obj instanceof Updateable) {
			Updateable update = (Updateable) obj;
			boolean can = containsLoad(update);
			if (!can) {
				can = containsUnLoad(update);
			}
			return can;
		} else if (obj instanceof GameProcess) {
			return containsProcess((GameProcess) obj);
		} else if (obj instanceof LRelease) {
			return containsRelease((LRelease) obj);
		}
		return false;
	}

	public Screen removeAll() {
		if (sprites != null) {
			sprites.removeAll();
		}
		if (desktop != null) {
			desktop.clear();
		}
		ActionControl.get().clear();
		removeAllLoad();
		removeAllUnLoad();
		clearTouchAreas();
		clearTouched();
		clearFrameLoop();
		return this;
	}

	/**
	 * 判断是否点中指定精灵
	 * 
	 * @param sprite
	 * @return
	 */

	public boolean onClick(ISprite sprite) {
		if (sprite == null) {
			return false;
		}
		if (sprite.isVisible()) {
			RectBox rect = sprite.getCollisionBox();
			if (rect.contains(SysTouch.getX(), SysTouch.getY()) || rect.intersects(SysTouch.getX(), SysTouch.getY())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 判断是否点中指定组件
	 * 
	 * @param component
	 * @return
	 */

	public boolean onClick(LComponent component) {
		if (component == null) {
			return false;
		}
		if (component.isVisible()) {
			RectBox rect = component.getCollisionBox();
			if (rect.contains(SysTouch.getX(), SysTouch.getY()) || rect.intersects(SysTouch.getX(), SysTouch.getY())) {
				return true;
			}
		}
		return false;
	}

	public Screen centerOn(final LObject<?> object) {
		LObject.centerOn(object, getWidth(), getHeight());
		return this;
	}

	public Screen topOn(final LObject<?> object) {
		LObject.topOn(object, getWidth(), getHeight());
		return this;
	}

	public Screen leftOn(final LObject<?> object) {
		LObject.leftOn(object, getWidth(), getHeight());
		return this;
	}

	public Screen rightOn(final LObject<?> object) {
		LObject.rightOn(object, getWidth(), getHeight());
		return this;
	}

	public Screen bottomOn(final LObject<?> object) {
		LObject.bottomOn(object, getWidth(), getHeight());
		return this;
	}

	/**
	 * 获得背景显示模式
	 */
	public int getRepaintMode() {
		return mode;
	}

	/**
	 * 设定背景刷新模式
	 * 
	 * @param mode
	 */
	public void setRepaintMode(int mode) {
		this.mode = mode;
	}

	public void setLocation(float x, float y) {
		pos(x, y);
	}

	public Screen pos(float x, float y) {
		this.tx = x;
		this.ty = y;
		this.isTranslate = (tx != 0 || ty != 0);
		return this;
	}

	public void setX(float x) {
		this.posX(x);
	}

	public Screen posX(float x) {
		setLocation(x, ty);
		return this;
	}

	public void setY(float y) {
		this.posY(y);
	}

	public Screen posY(float y) {
		setLocation(tx, y);
		return this;
	}

	@Override
	public float getX() {
		return this.tx;
	}

	@Override
	public float getY() {
		return this.ty;
	}

	protected LTextureImage createTextureImage(float width, float height) {
		if (LSystem.base() == null) {
			return null;
		}
		return new LTextureImage(LSystem.base().graphics(), LSystem.base().display().GL().batch(), width, height, true);
	}

	/**
	 * 重载此函数,可以自定义渲染Screen的最下层图像
	 * 
	 * @param g
	 */
	protected void afterUI(GLEx g) {}

	/**
	 * 重载此函数,可以自定义渲染Screen的最上层图像
	 * 
	 * @param g
	 */
	protected void beforeUI(GLEx g) {}

	private final void repaint(GLEx g) {
		if (!_visible) {
			return;
		}
		if (!isClose) {
			try {
				// 记录屏幕矩阵以及画笔
				g.save();
				if (_baseColor != null) {
					g.setColor(_baseColor);
				}
				if (_alpha != 1f) {
					g.setAlpha(_alpha);
				}
				if (_rotation != 0) {
					g.rotate(getX() + (_pivotX == -1 ? getHalfWidth() : _pivotX),
							getY() + (_pivotY == -1 ? getHalfHeight() : _pivotY), _rotation);
				}
				if (_flipX || _flipY) {
					g.flip(getX(), getY(), getWidth(), getHeight(), _flipX, _flipY);
				}
				if (_scaleX != 1f || _scaleY != 1f) {
					g.scale(_scaleX, _scaleY, getX() + (_pivotX == -1 ? getHalfWidth() : _pivotX),
							getY() + (_pivotY == -1 ? getHalfHeight() : _pivotY));
				}
				// 偏移屏幕
				if (isTranslate) {
					g.translate(tx, ty);
				}
				if (_isExistCamera) {
					g.setCamera(_baseCamera);
				}
				int repaintMode = getRepaintMode();
				switch (repaintMode) {
				case Screen.SCREEN_NOT_REPAINT:
					// 默认将background设置为和窗口一样大小
					if (getBackground() != null) {
						g.draw(getBackground(), 0, 0, getWidth(), getHeight());
					}
					break;
				case Screen.SCREEN_TEXTURE_REPAINT:
					g.draw(getBackground(), 0, 0, getWidth(), getHeight());
					break;
				case Screen.SCREEN_COLOR_REPAINT:
					if (getBackground() != null) {
						g.draw(getBackground(), 0, 0, getWidth(), getHeight());
					} else {
						LColor c = getBackgroundColor();
						if (c != null) {
							g.clear(c);
						}
					}
					break;
				default:
					g.draw(getBackground(), repaintMode / 2 - MathUtils.random(repaintMode),
							repaintMode / 2 - MathUtils.random(repaintMode), getWidth(), getHeight());
					break;
				}
				// 最下一层渲染，可重载
				afterUI(g);
				// PS:下列项允许用户调整顺序
				// 精灵
				if (fristPaintFlag) {
					fristOrder.paint(g);
				}
				// 其次，桌面
				if (secondPaintFlag) {
					secondOrder.paint(g);
				}
				// 最后，用户渲染
				if (lastPaintFlag) {
					lastOrder.paint(g);
				}
				// 最前一层渲染，可重载
				beforeUI(g);
			} finally {
				// 若存在摄影机,则还原camera坐标
				if (_isExistCamera) {
					g.restoreTx();
				}
				// 还原屏幕矩阵以及画笔
				g.restore();
			}
		}
	}

	public synchronized void createUI(GLEx g) {
		if (isClose) {
			return;
		}
		if (replaceLoading) {
			if (replaceDstScreen == null || !replaceDstScreen.isOnLoadComplete()) {
				repaint(g);
			} else if (screenSwitch != null) {
				replaceDstScreen.createUI(g);
				repaint(g);
			} else if (replaceDstScreen.isOnLoadComplete()) {
				if (isScreenFrom) {
					repaint(g);
					if (dstPos.x() != 0 || dstPos.y() != 0) {
						g.setClip(dstPos.x(), dstPos.y(), getWidth(), getHeight());
						g.translate(dstPos.x(), dstPos.y());
					}
					replaceDstScreen.createUI(g);
					if (dstPos.x() != 0 || dstPos.y() != 0) {
						g.translate(-dstPos.x(), -dstPos.y());
						g.clearClip();
					}
				} else {
					replaceDstScreen.createUI(g);
					if (dstPos.x() != 0 || dstPos.y() != 0) {
						g.setClip(dstPos.x(), dstPos.y(), getWidth(), getHeight());
						g.translate(dstPos.x(), dstPos.y());
					}
					repaint(g);
					if (dstPos.x() != 0 || dstPos.y() != 0) {
						g.translate(-dstPos.x(), -dstPos.y());
						g.clearClip();
					}
				}
			}
		} else {
			repaint(g);
		}
	}

	public void setScreenDelay(long delay) {
		this.delayTimer.setDelay(delay);
	}

	public long getScreenDelay() {
		return this.delayTimer.getDelay();
	}

	/**
	 * 暂停进程处理指定时间
	 * 
	 * @param delay
	 */
	public void processSleep(long delay) {
		pauseTimer.setDelay(delay);
		isTimerPaused = true;
	}

	private boolean initLoopEvents = false;

	private TArray<FrameLoopEvent> loopEvents;

	private void allocateLoopEvents() {
		if (loopEvents == null) {
			loopEvents = new TArray<FrameLoopEvent>();
		}
	}

	public void loop(FrameLoopEvent event) {
		addFrameLoop(event);
	}

	public void loop(float second, FrameLoopEvent event) {
		addFrameLoop(second, event);
	}

	public void addFrameLoop(TArray<FrameLoopEvent> events) {
		allocateLoopEvents();
		loopEvents.addAll(events);
		initLoopEvents = true;
	}

	public void addFrameLoop(float second, FrameLoopEvent event) {
		allocateLoopEvents();
		if (event != null) {
			event.setSecond(second);
		}
		loopEvents.add(event);
		initLoopEvents = true;
	}

	public void addFrameLoop(FrameLoopEvent event) {
		allocateLoopEvents();
		loopEvents.add(event);
		initLoopEvents = true;
	}

	public void removeFrameLoop(FrameLoopEvent event) {
		allocateLoopEvents();
		loopEvents.remove(event);
		initLoopEvents = (loopEvents.size <= 0);
	}

	public void clearFrameLoop() {
		if (loopEvents == null) {
			initLoopEvents = false;
			return;
		}
		loopEvents.clear();
		initLoopEvents = false;
	}

	private final void process(final LTimerContext timer) {
		for (int i = _keyActions.size() - 1; i >= 0; i--) {
			ActionKey act = (ActionKey) _keyActions.get(i);
			if (act.isPressed()) {
				act.act(elapsedTime);
				if (act.isReturn) {
					return;
				}
			}
		}
		this.elapsedTime = timer.timeSinceLastUpdate;
		// 如果Screen设置了计时器暂停
		if (isTimerPaused) {
			// 开始累加时间
			pauseTimer.addPercentage(timer);
			// 当还在暂停中，则不处理所有update事件，直接退出此进程
			if (!pauseTimer.isCompleted()) {
				return;
			} else {
				// 还原计时器暂停
				isTimerPaused = false;
				pauseTimer.refresh();
			}
		}
		if (delayTimer.action(elapsedTime)) {
			if (processing && !isClose) {
				if (isGravity) {
					gravityHandler.update(elapsedTime);
				}
				if (fristPaintFlag) {
					fristOrder.update(timer);
				}
				if (secondPaintFlag) {
					secondOrder.update(timer);
				}
				if (lastPaintFlag) {
					lastOrder.update(timer);
				}
			}
		}
		// 处理直接加入screen中的循环
		if (initLoopEvents) {
			if (loopEvents != null && loopEvents.size > 0) {
				final TArray<FrameLoopEvent> toUpdated;
				synchronized (this.loopEvents) {
					toUpdated = new TArray<FrameLoopEvent>(this.loopEvents);
				}
				final TArray<FrameLoopEvent> deadEvents = new TArray<FrameLoopEvent>();
				try {
					for (FrameLoopEvent eve : toUpdated) {
						eve.call(elapsedTime, this);
						if (eve.isDead()) {
							deadEvents.add(eve);
						}
					}
					if (deadEvents.size > 0) {
						for (FrameLoopEvent dead : deadEvents) {
							dead.completed();
						}
						synchronized (this.loopEvents) {
							this.loopEvents.removeAll(deadEvents);
						}
					}
				} catch (Throwable cause) {
					LSystem.error("FrameLoopEvent dispatch failure", cause);
				}
			}
		}
		this.touchDX = SysTouch.getX() - lastTouchX;
		this.touchDY = SysTouch.getY() - lastTouchY;
		this.lastTouchX = SysTouch.getX();
		this.lastTouchY = SysTouch.getY();
		this.touchButtonReleased = NO_BUTTON;
	}

	public void runTimer(final LTimerContext timer) {
		if (isClose) {
			return;
		}
		if (replaceLoading) {
			// 无替换对象
			if (replaceDstScreen == null || !replaceDstScreen.isOnLoadComplete()) {
				process(timer);
				// 渐进效果替换
			} else if (screenSwitch != null) {
				process(timer);
				if (replaceDelay.action(timer)) {
					screenSwitch.update(timer.timeSinceLastUpdate);
				}
				if (screenSwitch.isCompleted()) {
					submitReplaceScreen();
				}
				// 位移替换
			} else if (replaceDstScreen.isOnLoadComplete()) {
				process(timer);
				if (replaceDelay.action(timer)) {
					switch (replaceMethod) {
					case FROM_LEFT:
						dstPos.move_right(replaceScreenSpeed);
						if (dstPos.x() >= 0) {
							submitReplaceScreen();
							return;
						}
						break;
					case FROM_RIGHT:
						dstPos.move_left(replaceScreenSpeed);
						if (dstPos.x() <= 0) {
							submitReplaceScreen();
							return;
						}
						break;
					case FROM_UP:
						dstPos.move_down(replaceScreenSpeed);
						if (dstPos.y() >= 0) {
							submitReplaceScreen();
							return;
						}
						break;
					case FROM_DOWN:
						dstPos.move_up(replaceScreenSpeed);
						if (dstPos.y() <= 0) {
							submitReplaceScreen();
							return;
						}
						break;
					case OUT_LEFT:
						dstPos.move_left(replaceScreenSpeed);
						if (dstPos.x() < -getWidth()) {
							submitReplaceScreen();
							return;
						}
						break;
					case OUT_RIGHT:
						dstPos.move_right(replaceScreenSpeed);
						if (dstPos.x() > getWidth()) {
							submitReplaceScreen();
							return;
						}
						break;
					case OUT_UP:
						dstPos.move_up(replaceScreenSpeed);
						if (dstPos.y() < -getHeight()) {
							submitReplaceScreen();
							return;
						}
						break;
					case OUT_DOWN:
						dstPos.move_down(replaceScreenSpeed);
						if (dstPos.y() > getHeight()) {
							submitReplaceScreen();
							return;
						}
						break;
					case FROM_UPPER_LEFT:
						if (dstPos.y() < 0) {
							dstPos.move_45D_right(replaceScreenSpeed);
						} else {
							dstPos.move_right(replaceScreenSpeed);
						}
						if (dstPos.y() >= 0 && dstPos.x() >= 0) {
							submitReplaceScreen();
							return;
						}
						break;
					case FROM_UPPER_RIGHT:
						if (dstPos.y() < 0) {
							dstPos.move_45D_down(replaceScreenSpeed);
						} else {
							dstPos.move_left(replaceScreenSpeed);
						}
						if (dstPos.y() >= 0 && dstPos.x() <= 0) {
							submitReplaceScreen();
							return;
						}
						break;
					case FROM_LOWER_LEFT:
						if (dstPos.y() > 0) {
							dstPos.move_45D_up(replaceScreenSpeed);
						} else {
							dstPos.move_right(replaceScreenSpeed);
						}
						if (dstPos.y() <= 0 && dstPos.x() >= 0) {
							submitReplaceScreen();
							return;
						}
						break;
					case FROM_LOWER_RIGHT:
						if (dstPos.y() > 0) {
							dstPos.move_45D_left(replaceScreenSpeed);
						} else {
							dstPos.move_left(replaceScreenSpeed);
						}
						if (dstPos.y() <= 0 && dstPos.x() <= 0) {
							submitReplaceScreen();
							return;
						}
						break;
					case OUT_UPPER_LEFT:
						dstPos.move_45D_left(replaceScreenSpeed);
						if (dstPos.x() < -getWidth() || dstPos.y() <= -getHeight()) {
							submitReplaceScreen();
							return;
						}
						break;
					case OUT_UPPER_RIGHT:
						dstPos.move_45D_up(replaceScreenSpeed);
						if (dstPos.x() > getWidth() || dstPos.y() < -getHeight()) {
							submitReplaceScreen();
							return;
						}
						break;
					case OUT_LOWER_LEFT:
						dstPos.move_45D_down(replaceScreenSpeed);
						if (dstPos.x() < -getWidth() || dstPos.y() > getHeight()) {
							submitReplaceScreen();
							return;
						}
						break;
					case OUT_LOWER_RIGHT:
						dstPos.move_45D_right(replaceScreenSpeed);
						if (dstPos.x() > getWidth() || dstPos.y() > getHeight()) {
							submitReplaceScreen();
							return;
						}
						break;
					default:
						break;
					}
					replaceDstScreen.runTimer(timer);
				}
			}
		} else {
			process(timer);
		}
	}

	public SysInput getInput() {
		return this;
	}

	public Screen setNext(boolean next) {
		this.isNext = next;
		return this;
	}

	public abstract void alter(LTimerContext context);

	/**
	 * 设定游戏窗体
	 */
	public Screen setScreen(Screen screen) {
		if (handler != null) {
			this.handler.setScreen(screen);
		}
		return this;
	}

	public int getScreenWidth() {
		return width;
	}

	public int getScreenHeight() {
		return height;
	}

	@Override
	public int getWidth() {
		return width;
	}

	@Override
	public int getHeight() {
		return height;
	}

	/**
	 * 刷新基础设置
	 */
	@Override
	public void refresh() {
		touchType.clear();
		keyType.clear();
		touchDX = touchDY = 0;
	}

	public abstract void resize(int width, int height);

	@Override
	public PointI getTouch() {
		touch.set((int) SysTouch.getX(), (int) SysTouch.getY());
		return touch;
	}

	public boolean isPaused() {
		return LSystem.PAUSED;
	}

	@Override
	public int getTouchPressed() {
		return touchButtonPressed > SysInput.NO_BUTTON ? touchButtonPressed : SysInput.NO_BUTTON;
	}

	@Override
	public int getTouchReleased() {
		return touchButtonReleased > SysInput.NO_BUTTON ? touchButtonReleased : SysInput.NO_BUTTON;
	}

	@Override
	public boolean isTouchPressed(int button) {
		return touchButtonPressed == button;
	}

	@Override
	public boolean isTouchReleased(int button) {
		return touchButtonReleased == button;
	}

	@Override
	public int getTouchX() {
		return (int) SysTouch.getX();
	}

	@Override
	public int getTouchY() {
		return (int) SysTouch.getY();
	}

	@Override
	public int getTouchDX() {
		return (int) touchDX;
	}

	@Override
	public int getTouchDY() {
		return (int) touchDY;
	}

	@Override
	public boolean isTouchType(int type) {
		Boolean bn = touchType.get(type);
		if (bn == null) {
			return false;
		}
		return bn.booleanValue();
	}

	@Override
	public int getKeyPressed() {
		return keyButtonPressed > SysInput.NO_KEY ? keyButtonPressed : SysInput.NO_KEY;
	}

	@Override
	public boolean isKeyPressed(int keyCode) {
		return keyButtonPressed == keyCode;
	}

	@Override
	public int getKeyReleased() {
		return keyButtonReleased > SysInput.NO_KEY ? keyButtonReleased : SysInput.NO_KEY;
	}

	@Override
	public boolean isKeyReleased(int keyCode) {
		return keyButtonReleased == keyCode;
	}

	@Override
	public boolean isKeyType(int type) {
		Boolean bn = keyType.get(type);
		if (bn == null) {
			return false;
		}
		return bn.booleanValue();
	}

	public final void keyPressed(GameKey e) {
		if (isLock || isClose || !isLoad) {
			return;
		}
		int type = e.getType();
		int code = e.getKeyCode();
		try {
			int keySize = _keyActions.size();
			if (keySize > 0) {
				int keyCode = e.getKeyCode();
				for (int i = keySize - 1; i >= 0; i--) {
					Integer c = (Integer) _keyActions.getKey(i);
					if (c == keyCode) {
						ActionKey act = (ActionKey) _keyActions.getValue(c);
						act.press();
					}
				}
			}
			this.onKeyDown(e);
			if (desktop != null) {
				desktop.keyPressed(e);
			}
			keyType.put(type, Boolean.valueOf(true));
			keyButtonPressed = code;
			keyButtonReleased = SysInput.NO_KEY;
		} catch (Throwable ex) {
			keyButtonPressed = SysInput.NO_KEY;
			keyButtonReleased = SysInput.NO_KEY;
			error("Screen keyPressed() exception", ex);
		}
	}

	/**
	 * 设置键盘按下事件
	 * 
	 * @param code
	 */
	public void setKeyDown(int button) {
		keyButtonPressed = button;
		keyButtonReleased = SysInput.NO_KEY;
	}

	public final void keyReleased(GameKey e) {
		if (isLock || isClose || !isLoad) {
			return;
		}
		int type = e.getType();
		int code = e.getKeyCode();
		try {
			int keySize = _keyActions.size();
			if (keySize > 0) {
				int keyCode = e.getKeyCode();
				for (int i = 0; i < keySize; i++) {
					Integer c = (Integer) _keyActions.getKey(i);
					if (c == keyCode) {
						ActionKey act = (ActionKey) _keyActions.getValue(c);
						act.release();
					}
				}
			}
			this.onKeyUp(e);
			if (desktop != null) {
				desktop.keyReleased(e);
			}
			this.releaseActionKeys();
			keyType.put(type, Boolean.valueOf(false));
			keyButtonReleased = code;
			keyButtonPressed = SysInput.NO_KEY;
		} catch (Throwable ex) {
			keyButtonPressed = SysInput.NO_KEY;
			keyButtonReleased = SysInput.NO_KEY;
			error("Screen keyReleased() exception", ex);
		}
	}

	@Override
	public void setKeyUp(int button) {
		keyButtonReleased = button;
		keyButtonPressed = SysInput.NO_KEY;
	}

	public void keyTyped(GameKey e) {
		if (isLock || isClose || !isLoad) {
			return;
		}
		onKeyTyped(e);
	}

	public void onKeyDown(GameKey e) {

	}

	public void onKeyUp(GameKey e) {

	}

	public void onKeyTyped(GameKey e) {

	}

	public final void mousePressed(GameTouch e) {
		if (isLock || isClose || !isLoad) {
			return;
		}
		if (isTranslate) {
			e.offset(tx, ty);
		}
		int type = e.getType();
		int button = e.getButton();

		try {
			touchType.put(type, Boolean.valueOf(true));
			touchButtonPressed = button;
			touchButtonReleased = SysInput.NO_BUTTON;
			if (!isClickLimit(e)) {
				updateTouchArea(Event.DOWN, e.getX(), e.getY());
				touchDown(e);
				if (_clickListener != null && desktop != null) {
					_clickListener.DownClick(desktop.getSelectedComponent(), e.getX(), e.getY());
				}
			}
		} catch (Throwable ex) {
			touchButtonPressed = SysInput.NO_BUTTON;
			touchButtonReleased = SysInput.NO_BUTTON;
			error("Screen mousePressed() exception", ex);
		}
	}

	public abstract void touchDown(GameTouch e);

	public void mouseReleased(GameTouch e) {
		if (isLock || isClose || !isLoad) {
			return;
		}
		if (isTranslate) {
			e.offset(tx, ty);
		}
		int type = e.getType();
		int button = e.getButton();

		try {
			touchType.put(type, Boolean.valueOf(false));
			touchButtonReleased = button;
			touchButtonPressed = SysInput.NO_BUTTON;
			if (!isClickLimit(e)) {
				updateTouchArea(Event.UP, e.getX(), e.getY());
				touchUp(e);
				if (_clickListener != null && desktop != null) {
					_clickListener.UpClick(desktop.getSelectedComponent(), e.getX(), e.getY());
				}
			}
		} catch (Throwable ex) {
			touchButtonPressed = SysInput.NO_BUTTON;
			touchButtonReleased = SysInput.NO_BUTTON;
			error("Screen mouseReleased() exception", ex);
		}
	}

	public abstract void touchUp(GameTouch e);

	public void mouseMoved(GameTouch e) {
		if (isLock || isClose || !isLoad) {
			return;
		}
		if (isTranslate) {
			e.offset(tx, ty);
		}

		if (!isClickLimit(e)) {
			updateTouchArea(Event.MOVE, e.getX(), e.getY());
			touchMove(e);
		}
	}

	public abstract void touchMove(GameTouch e);

	public void mouseDragged(GameTouch e) {
		if (isLock || isClose || !isLoad) {
			return;
		}
		if (isTranslate) {
			e.offset(tx, ty);
		}
		if (!isClickLimit(e)) {
			updateTouchArea(Event.DRAG, e.getX(), e.getY());
			touchDrag(e);
			if (_clickListener != null && desktop != null) {
				_clickListener.DragClick(desktop.getSelectedComponent(), e.getX(), e.getY());
			}
		}
	}

	public abstract void touchDrag(GameTouch e);

	public LComponent getSelectedComponent() {
		return getDesktop().getSelectedComponent();
	}

	/**
	 * 判定是否点击了指定位置
	 * 
	 * @param event
	 * @param x
	 * @param y
	 * @param width
	 * @param height
	 * @return
	 */
	public boolean inBounds(GameTouch event, float x, float y, float width, float height) {
		return (event.x() > x && event.x() < x + width - 1 && event.y() > y && event.y() < y + height - 1);
	}

	/**
	 * 判定是否点击了指定位置
	 * 
	 * @param event
	 * @param x
	 * @param y
	 * @param width
	 * @param height
	 * @return
	 */
	public boolean inBounds(LTouchLocation event, float x, float y, float width, float height) {
		return (event.x() > x && event.x() < x + width - 1 && event.y() > y && event.y() < y + height - 1);
	}

	/**
	 * 判定是否点击了指定位置
	 * 
	 * @param event
	 * @param o
	 * @return
	 */
	public boolean inBounds(GameTouch event, LObject<?> o) {
		RectBox rect = o.getCollisionArea();
		if (rect != null) {
			return inBounds(event, rect);
		} else {
			return false;
		}
	}

	/**
	 * 判定是否点击了指定位置
	 * 
	 * @param event
	 * @param o
	 * @return
	 */
	public boolean inBounds(GameTouch event, ISprite o) {
		return inBounds(event, o.x(), o.y(), o.getWidth(), o.getHeight());
	}

	/**
	 * 判定是否点击了指定位置
	 * 
	 * @param event
	 * @param o
	 * @return
	 */
	public boolean inBounds(GameTouch event, LComponent o) {
		return inBounds(event, o.x(), o.y(), o.getWidth(), o.getHeight());
	}

	/**
	 * 判定是否点击了指定位置
	 * 
	 * @param event
	 * @param rect
	 * @return
	 */
	public boolean inBounds(GameTouch event, RectBox rect) {
		return inBounds(event, rect.x, rect.y, rect.width, rect.height);
	}

	@Override
	public boolean isMoving() {
		return SysTouch.isDrag();
	}

	public int getHalfWidth() {
		return halfWidth;
	}

	public int getHalfHeight() {
		return halfHeight;
	}

	public Accelerometer.SensorDirection setSensorDirection(Accelerometer.SensorDirection dir) {
		this.direction = dir;
		return direction;
	}

	public Accelerometer.SensorDirection getSensorDirection() {
		return direction;
	}

	public PaintOrder getFristOrder() {
		return fristOrder;
	}

	public Screen setFristOrder(PaintOrder fristOrder) {
		if (fristOrder == null) {
			this.fristPaintFlag = false;
		} else {
			this.fristPaintFlag = true;
			this.fristOrder = fristOrder;
		}
		return this;
	}

	public PaintOrder getSecondOrder() {
		return secondOrder;
	}

	public Screen setSecondOrder(PaintOrder secondOrder) {
		if (secondOrder == null) {
			this.secondPaintFlag = false;
		} else {
			this.secondPaintFlag = true;
			this.secondOrder = secondOrder;
		}
		return this;
	}

	public PaintOrder getLastOrder() {
		return lastOrder;
	}

	public Screen setLastOrder(PaintOrder lastOrder) {
		if (lastOrder == null) {
			this.lastPaintFlag = false;
		} else {
			this.lastPaintFlag = true;
			this.lastOrder = lastOrder;
		}
		return this;
	}

	public Display getDisplay() {
		return LSystem.base().display();
	}

	/**
	 * 设定是否允许自行释放桌面组件(UI)资源
	 * 
	 * @param a
	 * @return
	 */
	public Screen setAutoDestory(final boolean a) {
		if (desktop != null) {
			desktop.setAutoDestory(a);
		}
		return this;
	}

	public boolean isAutoDestory() {
		if (desktop != null) {
			return desktop.isAutoDestory();
		}
		return false;
	}

	/**
	 * 获得指定名称的资源管理器
	 */
	public ResourceLocal RES(String path) {
		return getResourceConfig(path);
	}

	/**
	 * 获得指定名称的资源管理器
	 * 
	 * @param path
	 * @return
	 */
	public ResourceLocal getResourceConfig(String path) {
		if (LSystem.base() == null) {
			return new ResourceLocal(path);
		}
		return LSystem.base().assets().getJsonResource(path);
	}

	/**
	 * 当前Screen是否旋转
	 * 
	 * @return
	 */
	public boolean isRotated() {
		return this._rotation != 0;
	}

	/**
	 * 设定Screen旋转角度
	 * 
	 * @param r
	 */
	public void setRotation(float r) {
		this._rotation = r;
		if (_rotation > 360f) {
			_rotation = 0f;
		}
	}

	public float getRotation() {
		return _rotation;
	}

	public void setPivotX(float pX) {
		_pivotX = pX;
	}

	public void setPivotY(float pY) {
		_pivotY = pY;
	}

	public float getPivotX() {
		return _pivotX;
	}

	public float getPivotY() {
		return _pivotY;
	}

	public void setPivot(float pX, float pY) {
		setPivotX(pX);
		setPivotY(pY);
	}

	public boolean isScaled() {
		return (this._scaleX != 1) || (this._scaleY != 1);
	}

	public float getScaleX() {
		return this._scaleX;
	}

	public float getScaleY() {
		return this._scaleY;
	}

	public void setScaleX(final float sx) {
		this._scaleX = sx;
	}

	public void setScaleY(final float sy) {
		this._scaleY = sy;
	}

	public void setScale(final float pScale) {
		this._scaleX = pScale;
		this._scaleY = pScale;
	}

	public void setScale(final float sx, final float sy) {
		this._scaleX = sx;
		this._scaleY = sy;
	}

	public boolean isVisible() {
		return this._visible;
	}

	public void setVisible(final boolean v) {
		this._visible = v;
	}

	public boolean isTxUpdate() {
		return _scaleX != 1f || _scaleY != 1f || _rotation != 0 || _flipX || _flipY || tx != 0 || ty != 0;
	}

	public void setAlpha(float a) {
		this._alpha = a;
	}

	public float getAlpha() {
		return this._alpha;
	}

	public void setColor(LColor color) {
		this._baseColor = color;
	}

	public LColor getColor() {
		return this._baseColor;
	}

	public Session getSession(String name) {
		return new Session(name);
	}

	public ConfigReader getConfigFile(String path) {
		return new ConfigReader(path);
	}

	public IFont getGameFont() {
		return LSystem.getSystemGameFont();
	}

	public Screen setGameFont(IFont font) {
		LSystem.setSystemGameFont(font);
		return this;
	}

	public IFont getLogFont() {
		return LSystem.getSystemLogFont();
	}

	public Screen setLogFont(IFont font) {
		LSystem.setSystemLogFont(font);
		return this;
	}

	public RectBox getBox() {
		return getRectBox();
	}

	public RectBox getRectBox() {
		if (_rectBox != null) {
			_rectBox.setBounds(MathUtils.getBounds(getX(), getY(), getWidth() * _scaleX, getHeight() * _scaleY,
					_rotation, _rectBox));
		} else {
			_rectBox = MathUtils.getBounds(getX(), getY(), getWidth() * _scaleX, getHeight() * _scaleY, _rotation,
					_rectBox);
		}
		return _rectBox;
	}

	public ScreenAction getScreenAction() {
		synchronized (this) {
			if (_screenAction == null) {
				_screenAction = new ScreenAction(this);
			} else {
				_screenAction.set(this);
			}
			return _screenAction;
		}
	}

	public boolean isFlipX() {
		return _flipX;
	}

	public Screen setFlipX(boolean flipX) {
		this._flipX = flipX;
		return this;
	}

	public boolean isFlipY() {
		return _flipY;
	}

	public Screen setFlipY(boolean flipY) {
		this._flipY = flipY;
		return this;
	}

	public Screen setFlipXY(boolean flipX, boolean flpiY) {
		setFlipX(flipX);
		setFlipY(flpiY);
		return this;
	}

	public boolean isActionCompleted() {
		return _screenAction == null || isActionCompleted(getScreenAction());
	}

	/**
	 * 返回Screen的动作事件
	 * 
	 * @return
	 */
	public ActionTween selfAction() {
		return set(getScreenAction(), false);
	}

	public abstract void resume();

	public abstract void pause();

	public void stop() {
	}// noop

	/**
	 * 释放函数内资源
	 * 
	 */
	public abstract void close();

	public UIControls createUIControls() {
		if (desktop != null) {
			return desktop.createUIControls();
		}
		return new UIControls();
	}

	public UIControls findUINames(String... uiName) {
		if (desktop != null) {
			return desktop.findUINamesToUIControls(uiName);
		}
		return new UIControls();
	}

	public UIControls findNotUINames(String... uiName) {
		if (desktop != null) {
			return desktop.findNotUINamesToUIControls(uiName);
		}
		return new UIControls();
	}

	public UIControls findNames(String... name) {
		if (desktop != null) {
			return desktop.findNamesToUIControls(name);
		}
		return new UIControls();
	}

	public UIControls findNotNames(String... name) {
		if (desktop != null) {
			return desktop.findNotNamesToUIControls(name);
		}
		return new UIControls();
	}

	public UIControls findTags(Object... o) {
		if (desktop != null) {
			return desktop.findTagsToUIControls(o);
		}
		return new UIControls();
	}

	public UIControls findNotTags(Object... o) {
		if (desktop != null) {
			return desktop.findNotTagsToUIControls(o);
		}
		return new UIControls();
	}

	public SpriteControls createSpriteControls() {
		if (sprites != null) {
			return sprites.createSpriteControls();
		}
		return new SpriteControls();
	}

	public SpriteControls findSpriteNames(String... names) {
		if (sprites != null) {
			return sprites.findNamesToSpriteControls(names);
		}
		return new SpriteControls();
	}

	public SpriteControls findSpriteNotNames(String... names) {
		if (sprites != null) {
			return sprites.findNotNamesToSpriteControls(names);
		}
		return new SpriteControls();
	}

	/**
	 * 查找所有【包含】指定tag的精灵
	 * 
	 * @param o
	 * @return
	 */
	public SpriteControls findSpriteTags(Object... o) {
		if (sprites != null) {
			return sprites.findTagsToSpriteControls(o);
		}
		return new SpriteControls();
	}

	/**
	 * 查找所有【不包含】指定tag的精灵
	 * 
	 * @param o
	 * @return
	 */
	public SpriteControls findSpriteNotTags(Object... o) {
		if (sprites != null) {
			return sprites.findNotTagsToSpriteControls(o);
		}
		return new SpriteControls();
	}

	/**
	 * 打开一个指定web页面
	 * 
	 * @param url
	 * @return
	 */
	public Screen openURL(final String url) {
		LSystem.base().openURL(url);
		return this;
	}

	/**
	 * 截屏并保存在texture
	 * 
	 * @return
	 */
	public LTexture screenshotToTexture() {
		return screenshotToImage().onHaveToClose(true).texture();
	}

	/**
	 * 返回全局通用的Bundle对象(作用类似Android中同名类,内部为key-value键值对形式的值,用来跨screen传递数据)
	 * 
	 * @return
	 */
	public ObjectBundle getBundle() {
		if (LSystem.getProcess() != null) {
			return LSystem.getProcess().getBundle();
		}
		return new ObjectBundle();
	}

	/**
	 * 添加全局通用的Bundle对象
	 * 
	 * @param key
	 * @param value
	 * @return
	 */
	public ObjectBundle addBundle(String key, Object value) {
		if (LSystem.getProcess() != null) {
			LSystem.getProcess().addBundle(key, value);
		}
		return new ObjectBundle();
	}

	/**
	 * 删除全局通用的Bundle对象
	 * 
	 * @param key
	 * @return
	 */
	public ObjectBundle removeBundle(String key) {
		if (LSystem.getProcess() != null) {
			LSystem.getProcess().removeBundle(key);
		}
		return new ObjectBundle();
	}

	/**
	 * 全局通用的Bundle中指定数据做加法
	 * 
	 * @param key
	 * @param value
	 * @return
	 */
	public ObjectBundle incBundle(String key, Object value) {
		return getBundle().inc(key, value);
	}

	/**
	 * 全局通用的Bundle中指定数据做减法
	 * 
	 * @param key
	 * @param value
	 * @return
	 */
	public ObjectBundle subBundle(String key, Object value) {
		return getBundle().sub(key, value);
	}

	/**
	 * 全局通用的Bundle中指定数据做乘法
	 * 
	 * @param key
	 * @param value
	 * @return
	 */
	public ObjectBundle mulBundle(String key, Object value) {
		return getBundle().mul(key, value);
	}

	/**
	 * 全局通用的Bundle中指定数据做除法
	 * 
	 * @param key
	 * @param value
	 * @return
	 */
	public ObjectBundle divBundle(String key, Object value) {
		return getBundle().div(key, value);
	}

	/**
	 * 获得一个针对全局通用的Bundle中指定数据的四则运算器
	 * 
	 * @param key
	 * @return
	 */
	public Calculator calcBundle(String key) {
		return getBundle().calc(key);
	}

	/**
	 * 截屏screen并转化为base64字符串
	 * 
	 * @return
	 */
	public String screenshotToBase64() {
		Image tmp = screenshotToImage();
		String base64 = tmp.getBase64();
		tmp.close();
		tmp = null;
		return base64;
	}

	/**
	 * 截屏screen并保存在image(image存在系统依赖,为系统本地image类组件的封装,所以效果可能存在差异)
	 * 
	 * @return
	 */
	public Image screenshotToImage() {
		Image tmp = GLUtils.getScreenshot();
		Image image = Image.getResize(tmp, getWidth(), getHeight());
		tmp.close();
		tmp = null;
		return image;
	}

	/**
	 * 截屏screen并保存在pixmap(pixmap本质上是一个无系统依赖的，仅存在于内存中的像素数组)
	 * 
	 * @return
	 */
	public Pixmap screenshotToPixmap() {
		Pixmap pixmap = GLUtils.getFrameBufferRGBPixmap();
		Pixmap image = Pixmap.getResize(pixmap, getWidth(), getHeight());
		pixmap.close();
		pixmap = null;
		return image;
	}

	/**
	 * 退出游戏
	 * 
	 * @return
	 */
	public Screen exitGame() {
		LSystem.exit();
		return this;
	}

	/**
	 * 返回video的缓存结果(不设置out对象时才会有效)
	 * 
	 * @return
	 */
	public ArrayByte getVideoCache() {
		if (LSystem.base() != null && LSystem.base().display() != null) {
			return LSystem.base().display().getVideoCache();
		}
		return new ArrayByte();
	}

	/**
	 * 开始录像(默认使用ArrayByte缓存录像结果到内存中)
	 * 
	 * @return
	 */
	public Screen startVideo() {
		if (LSystem.base() != null && LSystem.base().display() != null) {
			LSystem.base().display().startVideo();
		}
		return this;
	}

	/**
	 * 开始录像(指定一个OutputStream对象,比如FileOutputStream 输出录像结果到指定硬盘位置)
	 * 
	 * @param output
	 * @return
	 */
	public Screen startVideo(OutputStream output) {
		if (LSystem.base() != null && LSystem.base().display() != null) {
			LSystem.base().display().startVideo(output);
		}
		return this;
	}

	/**
	 * 开始录像(指定一个OutputStream对象,比如FileOutputStream 输出录像结果到指定硬盘位置)
	 * 
	 * @param output
	 * @param delay
	 * @return
	 */
	public Screen startVideo(OutputStream output, long delay) {
		if (LSystem.base() != null && LSystem.base().display() != null) {
			LSystem.base().display().startVideo(output, delay);
		}
		return this;
	}

	/**
	 * 结束录像
	 * 
	 * @return
	 */
	public Screen stopVideo() {
		if (LSystem.base() != null && LSystem.base().display() != null) {
			LSystem.base().display().startVideo();
		}
		return this;
	}

	/**
	 * 当前Screen名称
	 * 
	 * @return
	 */
	public String getScreenName() {
		return _screenName;
	}

	/**
	 * 全局延迟缓动动画指定时间
	 * 
	 * @param delay
	 */
	public void setTweenDelay(long delay) {
		ActionControl.setDelay(delay);
	}

	public Resolution getOriginResolution() {
		if (LSystem.getProcess() != null) {
			return LSystem.getProcess().getOriginResolution();
		}
		return new Resolution(LSystem.viewSize.getWidth(), LSystem.viewSize.getHeight());
	}

	public Resolution getDisplayResolution() {
		if (LSystem.getProcess() != null) {
			return LSystem.getProcess().getDisplayResolution();
		}
		return new Resolution(LSystem.viewSize.getWidth(), LSystem.viewSize.getHeight());
	}

	public String getOriginResolutionMode() {
		return getOriginResolution().matchMode();
	}

	public String getDisplayResolutionMode() {
		return getDisplayResolution().matchMode();
	}

	/**
	 * 判断当前Touch行为与上次是否在屏幕中指定间距内存在移动
	 * 
	 * @param distance
	 * @return
	 */
	public boolean isTouchMoved(float distance) {
		return CollisionHelper.isMoved(distance, lastTouchX, lastTouchY, getTouchX(), getTouchY());
	}

	/**
	 * 判断当前Touch行为与上次是否存在一定程度的移动(默认间距32个像素)
	 * 
	 * @return
	 */
	public boolean isTouchMoved() {
		return isTouchMoved(32);
	}

	/**
	 * 返回当前Touch行为的移动方向(返回int为map组件包中 @see Config 中设置的整型方向参数)
	 * 
	 * @param distance
	 * @return
	 */
	public int getTouchDirection(float distance) {
		if (isTouchMoved(distance)) {
			return Field2D.getDirection(Vector2f.at(lastTouchX, lastTouchY), Vector2f.at(getTouchX(), getTouchY()));
		}
		return Config.EMPTY;
	}

	/**
	 * 返回当前Touch行为的移动方向(返回map组件包中的Config整型方向)
	 * 
	 * @return
	 */
	public int getTouchDirection() {
		return getTouchDirection(32);
	}

	/**
	 * 获得碰撞器实例对象
	 * 
	 * @return
	 */
	public CollisionManager getCollisionManager() {
		if (_collisionClosed || _collisionManager == null) {
			_collisionManager = new CollisionManager();
			_collisionClosed = false;
		}
		return _collisionManager;
	}

	/**
	 * 初始碰撞器检测的地图瓦片范围(也就是实际像素/瓦片大小后缩放进行碰撞),瓦片数值越小,精确度越高,但是计算时间也越长
	 * 
	 * @param tileSize
	 */
	public void initializeCollision(int tileSize) {
		getCollisionManager().initialize(tileSize);
	}

	/**
	 * 初始碰撞器检测的地图瓦片范围(也就是实际像素/瓦片大小后缩放进行碰撞),瓦片数值越小,精确度越高,但是计算时间也越长
	 * 
	 * @param tileSizeX
	 * @param tileSizeY
	 */
	public void initializeCollision(int tileSizeX, int tileSizeY) {
		getCollisionManager().initialize(tileSizeX, tileSizeY);
	}

	/**
	 * 若此项为true(默认为false),则碰撞查询涉及具体碰撞对象的碰撞关系时,只会返回与查询对象同一层(layer值,z值)的对象
	 * 
	 * @param itlayer
	 */
	public void setCollisionInTheLayer(boolean itlayer) {
		if (_collisionClosed) {
			return;
		}
		_collisionManager.setInTheLayer(itlayer);
	}

	/**
	 * 是否设定了同层(layer值,z值)限制(默认为false)
	 * 
	 * @param itlayer
	 */
	public boolean getCollisionInTheLayer() {
		if (_collisionClosed) {
			return false;
		}
		return _collisionManager.getInTheLayer();
	}

	/**
	 * 让碰撞器偏移指定坐标后产生碰撞
	 * 
	 * @param x
	 * @param y
	 */
	public void setCollisionOffsetPos(float x, float y) {
		if (_collisionClosed) {
			return;
		}
		_collisionManager.setOffsetPos(x, y);
	}

	/**
	 * 让碰撞器偏移指定坐标后产生碰撞
	 * 
	 * @param x
	 */
	public void setCollisionOffsetX(float x) {
		if (_collisionClosed) {
			return;
		}
		_collisionManager.setOffsetX(x);
	}

	/**
	 * 让碰撞器偏移指定坐标后产生碰撞
	 * 
	 * @param y
	 */
	public void setCollisionOffsetY(float y) {
		if (_collisionClosed) {
			return;
		}
		_collisionManager.setOffsetY(y);
	}

	/**
	 * 获得碰撞器当前的偏移坐标
	 * 
	 * @return
	 */
	public Vector2f getCollisionOffsetPos() {
		if (_collisionClosed) {
			return Vector2f.ZERO();
		}
		return _collisionManager.getOffsetPos();
	}

	/**
	 * 注入一个碰撞对象
	 * 
	 * @param obj
	 */
	public void putCollision(CollisionObject obj) {
		if (_collisionClosed) {
			return;
		}
		_collisionManager.addObject(obj);
	}

	/**
	 * 删除一个碰撞对象
	 * 
	 * @param obj
	 */
	public void removeCollision(CollisionObject obj) {
		if (_collisionClosed) {
			return;
		}
		_collisionManager.removeObject(obj);
	}

	/**
	 * 删除一个指定对象标记的碰撞对象
	 * 
	 * @param objFlag
	 */
	public void removeCollision(String objFlag) {
		if (_collisionClosed) {
			return;
		}
		_collisionManager.removeObject(objFlag);
	}

	/**
	 * 获得当前存在的碰撞对象总数
	 * 
	 * @return
	 */
	public int getCollisionSize() {
		if (_collisionClosed) {
			return 0;
		}
		return _collisionManager.numberActors();
	}

	/**
	 * 返回当前碰撞管理器中存在的碰撞对象集合
	 * 
	 * @return
	 */
	public TArray<CollisionObject> getCollisionObjects() {
		if (_collisionClosed) {
			return null;
		}
		return _collisionManager.getActorsList();
	}

	/**
	 * 获得所有指定对象标记的碰撞对象
	 * 
	 * @param objFlag
	 * @return
	 */
	public TArray<CollisionObject> getCollisionObjects(String objFlag) {
		if (_collisionClosed) {
			return null;
		}
		return _collisionManager.getObjects(objFlag);
	}

	/**
	 * 获得与指定坐标碰撞并且有指定对象标记的对象
	 * 
	 * @param x
	 * @param y
	 * @param objFlag
	 * @return
	 */
	public TArray<CollisionObject> getCollisionObjectsAt(float x, float y, String objFlag) {
		if (_collisionClosed) {
			return null;
		}
		return _collisionManager.getObjectsAt(x, y, objFlag);
	}

	/**
	 * 获得有指定标记并与指定对象相交的集合
	 * 
	 * @param obj
	 * @param objFlag
	 * @return
	 */
	public TArray<CollisionObject> getIntersectingObjects(CollisionObject obj, String objFlag) {
		if (_collisionClosed) {
			return null;
		}
		return _collisionManager.getIntersectingObjects(obj, objFlag);
	}

	/**
	 * 获得一个有指定标记并与指定对象相交的单独对象
	 * 
	 * @param obj
	 * @param objFlag
	 * @return
	 */
	public CollisionObject getOnlyIntersectingObject(CollisionObject obj, String objFlag) {
		if (_collisionClosed) {
			return null;
		}
		return _collisionManager.getOnlyIntersectingObject(obj, objFlag);
	}

	/**
	 * 获得在指定位置指定大小圆轴内有指定标记的对象集合
	 * 
	 * @param x
	 * @param y
	 * @param r
	 * @param objFlag
	 * @return
	 */
	public TArray<CollisionObject> getObjectsInRange(float x, float y, float r, String objFlag) {
		if (_collisionClosed) {
			return null;
		}
		return _collisionManager.getObjectsInRange(x, y, r, objFlag);
	}

	/**
	 * 获得与指定对象相邻的全部对象
	 * 
	 * @param obj
	 * @param distance
	 * @param d
	 * @param objFlag
	 * @return
	 */
	public TArray<CollisionObject> getNeighbours(CollisionObject obj, float distance, boolean d, String objFlag) {
		if (_collisionClosed) {
			return null;
		}
		if (distance < 0) {
			throw new LSysException("distance < 0");
		} else {
			return _collisionManager.getNeighbours(obj, distance, d, objFlag);
		}
	}

	/**
	 * 注销碰撞器
	 * 
	 */
	public void disposeCollision() {
		_collisionClosed = true;
		if (_collisionManager != null) {
			_collisionManager.dispose();
			_collisionManager = null;
		}
	}

	/**
	 * 构建一个三角区域,让集合中的动作元素尽可能填充这一三角区域
	 * 
	 * @param objs
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 * @return
	 */
	public final Screen elementsTriangle(final TArray<ActionBind> objs, float x, float y, float w, float h) {
		LayoutManager.elementsTriangle(this, objs, x, y, w, h);
		return this;
	}

	/**
	 * 构建一个三角区域,让集合中的动作元素尽可能填充这一三角区域
	 * 
	 * @param objs
	 * @param triangle
	 * @param stepRate
	 * @return
	 */
	public final Screen elementsTriangle(final TArray<ActionBind> objs, Triangle2f triangle, int stepRate) {
		LayoutManager.elementsTriangle(this, objs, triangle, stepRate);
		return this;
	}

	/**
	 * 构建一个三角区域,让集合中的动作元素尽可能填充这一三角区域
	 * 
	 * @param objs
	 * @param triangle
	 * @param stepRate
	 * @param offsetX
	 * @param offsetY
	 */
	public final Screen elementsTriangle(final TArray<ActionBind> objs, Triangle2f triangle, int stepRate,
			float offsetX, float offsetY) {
		LayoutManager.elementsTriangle(this, objs, triangle, stepRate, offsetX, offsetY);
		return this;
	}

	/**
	 * 构建一个线性区域,让集合中的动作元素延续这一线性对象按照指定的初始坐标到完结坐标线性排序
	 * 
	 * @param objs
	 * @param x1
	 * @param y1
	 * @param x2
	 * @param y2
	 * @return
	 */
	public final Screen elementsLine(final TArray<ActionBind> objs, float x1, float y1, float x2, float y2) {
		LayoutManager.elementsLine(this, objs, x1, y1, x2, y2);
		return this;
	}

	/**
	 * 构建一个线性区域,让集合中的动作元素延续这一线性对象按照指定的初始坐标到完结坐标线性排序
	 * 
	 * @param objs
	 * @param x1
	 * @param y1
	 * @param x2
	 * @param y2
	 * @param offsetX
	 * @param offsetY
	 * @return
	 */
	public final Screen elementsLine(final TArray<ActionBind> objs, float x1, float y1, float x2, float y2,
			float offsetX, float offsetY) {
		LayoutManager.elementsLine(this, objs, x1, y1, x2, y2, offsetX, offsetY);
		return this;
	}

	/**
	 * 构建一个线性区域,让集合中的动作元素延续这一线性对象按照指定的初始坐标到完结坐标线性排序
	 * 
	 * @param objs
	 * @param line
	 * @param offsetX
	 * @param offsetY
	 */
	public final Screen elementsLine(final TArray<ActionBind> objs, Line line, float offsetX, float offsetY) {
		LayoutManager.elementsLine(this, objs, line, offsetX, offsetY);
		return this;
	}

	/**
	 * 以圆形环绕部署动作对象，构建一个圆形区域,让集合中的动作元素延续这一圆形对象按照指定的startAngle到endAngle范围环绕
	 * 
	 * @param objs
	 * @param x
	 * @param y
	 * @param radius
	 * @return
	 */
	public final Screen elementsCircle(final TArray<ActionBind> objs, float x, float y, float radius) {
		LayoutManager.elementsCircle(this, objs, x, y, radius);
		return this;
	}

	/**
	 * 以圆形环绕部署动作对象，构建一个圆形区域,让集合中的动作元素围绕这一圆形对象按照指定的startAngle到endAngle范围环绕
	 * 
	 * @param root
	 * @param objs
	 * @param circle
	 * @param startAngle
	 * @param endAngle
	 * @return
	 */
	public final Screen elementsCircle(final TArray<ActionBind> objs, Circle circle, float startAngle, float endAngle) {
		LayoutManager.elementsCircle(this, objs, circle, startAngle, endAngle);
		return this;
	}

	/**
	 * 以圆形环绕部署动作对象，构建一个圆形区域,让集合中的动作元素围绕这一圆形对象按照指定的startAngle到endAngle范围环绕
	 * 
	 * @param root
	 * @param objs
	 * @param circle
	 * @param startAngle
	 * @param endAngle
	 * @param offsetX
	 * @param offsetY
	 */
	public final Screen elementsCircle(final TArray<ActionBind> objs, Circle circle, float startAngle, float endAngle,
			float offsetX, float offsetY) {
		LayoutManager.elementsCircle(this, objs, circle, startAngle, endAngle, offsetX, offsetY);
		return this;
	}

	/**
	 * 把指定对象布局,在指定的RectBox范围内部署,并注入Screen
	 * 
	 * @param objs
	 * @param rectView
	 * @return
	 */
	public final Screen elements(final TArray<ActionBind> objs, BoxSize rectView) {
		LayoutManager.elements(this, objs, rectView);
		return this;
	}

	/**
	 * 把指定动作对象进行布局在指定的RectBox范围内部署,并注入Screen
	 * 
	 * @param objs
	 * @param rectView
	 * @param cellWidth
	 * @param cellHeight
	 * @return
	 */
	public final Screen elements(final TArray<ActionBind> objs, BoxSize rectView, float cellWidth, float cellHeight) {
		LayoutManager.elements(this, objs, rectView, cellWidth, cellHeight);
		return this;
	}

	/**
	 * 把指定对象布局,在指定的RectBox范围内部署,并注入Screen
	 * 
	 * @param objs
	 *            要布局的对象集合
	 * @param rectView
	 *            显示范围
	 * @param cellWidth
	 *            单独对象的默认width(如果对象有width,并且比cellWidth大,则以对象自己的为主)
	 * @param cellHeight
	 *            单独对象的默认height(如果对象有width,并且比cellWidth大,则以对象自己的为主)
	 * @param offsetX
	 *            显示坐标偏移x轴
	 * @param offsetY
	 *            显示坐标偏移y轴
	 */
	public final Screen elements(final TArray<ActionBind> objs, BoxSize rectView, float cellWidth, float cellHeight,
			float offsetX, float offsetY) {
		LayoutManager.elements(this, objs, rectView, cellWidth, cellHeight, offsetX, offsetY);
		return this;
	}

	/**
	 * 以指定名称指定坐标指定大小开始,在Screen中生成一组LClickButton,并返回Click对象集合
	 * 
	 * @param names
	 * @param sx
	 * @param sy
	 * @param cellWidth
	 * @param cellHeight
	 * @param offsetX
	 * @param offsetY
	 * @param listener
	 * @param maxHeight
	 * @return
	 */
	public final TArray<LClickButton> elementButtons(final String[] names, int sx, int sy, int cellWidth,
			int cellHeight, int offsetX, int offsetY, ClickListener listener, int maxHeight) {
		return LayoutManager.elementButtons(this, names, sx, sy, cellWidth, cellHeight, listener, maxHeight);
	}

	/**
	 * 删除符合指定条件的精灵并返回操作的集合
	 * 
	 * @param query
	 * @return
	 */
	public TArray<ISprite> removeSprite(QueryEvent<ISprite> query) {
		if (sprites != null) {
			return sprites.remove(query);
		}
		return new TArray<ISprite>();
	}

	/**
	 * 查找符合指定条件的精灵并返回操作的集合
	 * 
	 * @param query
	 * @return
	 */
	public TArray<ISprite> findSprite(QueryEvent<ISprite> query) {
		if (sprites != null) {
			return sprites.find(query);
		}
		return new TArray<ISprite>();
	}

	/**
	 * 删除指定条件的精灵并返回操作的集合
	 * 
	 * @param query
	 * @return
	 */
	public <T extends ISprite> TArray<T> deleteSprite(QueryEvent<T> query) {
		if (sprites != null) {
			return sprites.delete(query);
		}
		return new TArray<T>();
	}

	/**
	 * 查找符合指定条件的精灵并返回操作的集合
	 * 
	 * @param query
	 * @return
	 */
	public <T extends ISprite> TArray<T> selectSprite(QueryEvent<T> query) {
		if (sprites != null) {
			return sprites.select(query);
		}
		return new TArray<T>();
	}

	/**
	 * 删除符合指定条件的组件并返回操作的集合
	 * 
	 * @param query
	 * @return
	 */
	public TArray<LComponent> removeComponent(QueryEvent<LComponent> query) {
		if (desktop == null) {
			return new TArray<LComponent>();
		}
		return desktop.remove(query);
	}

	/**
	 * 查找符合指定条件的组件并返回操作的集合
	 * 
	 * @param query
	 * @return
	 */
	public TArray<LComponent> findComponent(QueryEvent<LComponent> query) {
		if (desktop == null) {
			return new TArray<LComponent>();
		}
		return desktop.find(query);
	}

	/**
	 * 删除指定条件的组件并返回操作的集合
	 * 
	 * @param query
	 * @return
	 */
	public <T extends LComponent> TArray<T> deleteComponent(QueryEvent<T> query) {
		if (desktop == null) {
			return new TArray<T>();
		}
		return desktop.delete(query);
	}

	/**
	 * 查找符合指定条件的组件并返回操作的集合
	 * 
	 * @param query
	 * @return
	 */
	public <T extends LComponent> TArray<T> selectComponent(QueryEvent<T> query) {
		if (desktop == null) {
			return new TArray<T>();
		}
		return desktop.select(query);
	}

	/**
	 * 返回当前Screen的渲染监听器
	 * 
	 * @return
	 */
	public DrawListener<Screen> getDrawListener() {
		return _drawListener;
	}

	/**
	 * 设定当前Screen的渲染监听器
	 * 
	 * @param drawListener
	 */
	public void setDrawListener(DrawListener<Screen> drawListener) {
		this._drawListener = drawListener;
	}
	
	@Override
	public String toString() {
		StringKeyValue sbr = new StringKeyValue(getClass().getName());
		sbr.newLine().kv("Sprites", sprites).newLine().kv("Desktop", desktop).newLine();
		return sbr.toString();
	}

	/**
	 * 注销Screen
	 */
	public final void destroy() {
		synchronized (Screen.class) {
			try {
				index = 0;
				_rotation = 0;
				_scaleX = _scaleY = _alpha = 1f;
				_baseColor = null;
				_visible = false;
				_drawListener = null;
				_clickListener = null;
				_touchListener = null;
				_limits.clear();
				_touchAreas.clear();
				touchButtonPressed = SysInput.NO_BUTTON;
				touchButtonReleased = SysInput.NO_BUTTON;
				keyButtonPressed = SysInput.NO_KEY;
				keyButtonReleased = SysInput.NO_KEY;
				replaceLoading = false;
				replaceDelay.setDelay(10);
				tx = ty = 0;
				isClose = true;
				isTranslate = false;
				isNext = false;
				isGravity = false;
				isLock = true;
				_isExistCamera = false;
				if (sprites != null) {
					sprites.close();
					sprites.clear();
					sprites = null;
				}
				if (desktop != null) {
					desktop.close();
					desktop.clear();
					desktop = null;
				}
				if (gravityHandler != null) {
					gravityHandler.close();
					gravityHandler = null;
				}
				clearTouched();
				clearFrameLoop();
				if (_screenAction != null) {
					removeAllActions(_screenAction);
				}
				if (releases != null) {
					for (LRelease r : releases) {
						if (r != null) {
							r.close();
						}
					}
					releases.clear();
				}
				_conns.close();
				release();
				_keyActions.clear();
				if (currentScreenBackground != null) {
					currentScreenBackground.close();
					currentScreenBackground = null;
				}
				if (_closeUpdate != null) {
					_closeUpdate.action(this);
				}
				disposeCollision();
				_closeUpdate = null;
				screenSwitch = null;
				DefUI.self().clearDefaultUI();
				if (LSystem.base() != null) {
					LSystem.base().closeFontTempTexture();
				}
			} catch (Throwable cause) {
				LSystem.error("Screen destroy() dispatch exception", cause);
			} finally {
				close();
			}
		}
	}


}
