/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.badlogic.gdx.backends.jglfw;

import static com.badlogic.gdx.utils.SharedLibraryLoader.*;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.LifecycleListener;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.backends.jglfw.audio.OpenALAudio;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Clipboard;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.GdxRuntimeException;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWFramebufferSizeCallback;
import org.lwjgl.glfw.GLFWMonitorCallback;
import org.lwjgl.glfw.GLFWWindowCloseCallback;
import org.lwjgl.glfw.GLFWWindowFocusCallback;
import org.lwjgl.glfw.GLFWWindowIconifyCallback;
import org.lwjgl.glfw.GLFWWindowPosCallback;
import org.lwjgl.glfw.GLFWWindowRefreshCallback;
import org.lwjgl.glfw.GLFWWindowSizeCallback;

import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/** An OpenGL surface fullscreen or in a lightweight window using GLFW.
 * @author mzechner
 * @author Nathan Sweet
 * @author Edu Garcia (arcnor) */
public class JglfwApplication implements Application {
	OpenALAudio audio;
	JglfwGraphics graphics;
	JglfwFiles files;
	JglfwInput input;
	JglfwNet net;
	final ApplicationListener listener;
	private final Array<Runnable> runnables = new Array();
	private final Array<Runnable> executedRunnables = new Array();
	private final Array<LifecycleListener> lifecycleListeners = new Array();
	private final Map<String, Preferences> preferences = new HashMap();
	private final JglfwClipboard clipboard = new JglfwClipboard(this);
	private int logLevel = LOG_INFO;
	volatile boolean running = true;
	boolean isPaused;
	protected String preferencesdir;
	private final IntBuffer intBuffer = BufferUtils.newIntBuffer(1);
	private final IntBuffer intBuffer2 = BufferUtils.newIntBuffer(1);

	private boolean forceExit;
	private int foregroundFPS, backgroundFPS, hiddenFPS;

	// Callbacks need to be fields to avoid GC, so don't inline them!
	private GLFWMonitorCallback monitorCallback;
	private GLFWFramebufferSizeCallback framebufferSizeCallback;
	private GLFWWindowSizeCallback windowSizeCallback;
	private GLFWWindowPosCallback windowPosCallback;
	private GLFWWindowRefreshCallback windowRefreshCallback;
	private GLFWWindowFocusCallback windowFocusCallback;
	private GLFWWindowIconifyCallback windowIconifyCallback;
	private GLFWWindowCloseCallback windowCloseCallback;
	private GLFWErrorCallback errorCallback;

	public JglfwApplication (ApplicationListener listener) {
		this(listener, listener.getClass().getSimpleName(), 640, 480);
	}

	public JglfwApplication (ApplicationListener listener, String title, int width, int height) {
		this(listener, createConfig(title, width, height));
	}

	static private JglfwApplicationConfiguration createConfig (String title, int width, int height) {
		JglfwApplicationConfiguration config = new JglfwApplicationConfiguration();
		config.title = title;
		config.width = width;
		config.height = height;
		return config;
	}

	public JglfwApplication (final ApplicationListener listener, final JglfwApplicationConfiguration config) {
		if (listener == null) throw new IllegalArgumentException("listener cannot be null.");
		if (config == null) throw new IllegalArgumentException("config cannot be null.");

		this.listener = listener;

		Runnable runnable = new Runnable() {
			public void run () {
				try {
					initialize(config);
				} catch (Throwable ex) {
					exception(ex);
				}
			}
		};
		if (isMac)
			runnable.run();
		else
			new Thread(runnable, "MainLoop").start();
	}

	/** Called when an uncaught exception happens in the game loop. Default implementation prints the exception and calls
	 * System.exit(0). */
	protected void exception (Throwable ex) {
		ex.printStackTrace();
		System.exit(0);
	}

	void initialize (JglfwApplicationConfiguration config) {
		forceExit = config.forceExit;
		foregroundFPS = config.foregroundFPS;
		backgroundFPS = config.backgroundFPS;
		hiddenFPS = config.hiddenFPS;
		preferencesdir = config.preferencesLocation;

		final Thread glThread = Thread.currentThread();

		GdxNativesLoader.load();
		JglfwNativesLoader.load(JglfwApplicationConfiguration.disableAudio);

		if (glfwInit() != GL_TRUE) throw new GdxRuntimeException("Unable to initialize GLFW.");

		Gdx.app = this;
		Gdx.graphics = graphics = new JglfwGraphics(config);
		Gdx.files = files = new JglfwFiles();
		Gdx.input = input = new JglfwInput(this);
		Gdx.net = net = new JglfwNet();
		if (!JglfwApplicationConfiguration.disableAudio) {
			try {
				audio = new OpenALAudio(
						config.audioDeviceSimultaneousSources,
						config.audioDeviceBufferCount,
						config.audioDeviceBufferSize
				);
			} catch (Throwable t) {
				log("JglfwApplication", "Couldn't initialize audio, disabling audio", t);
				JglfwApplicationConfiguration.disableAudio = true;
			}
		}
		Gdx.audio = audio;

		glfwSetMonitorCallback(monitorCallback = new GLFWMonitorCallback() {
			@Override
			public void invoke(long monitor, int event) {
				// TODO
//				graphics.monitorChanged();
			}
		});
		glfwSetFramebufferSizeCallback(graphics.window, framebufferSizeCallback = new GLFWFramebufferSizeCallback() {
			@Override
			public void invoke(long window, final int width, final int height) {
				intBuffer.clear();
				glfwGetWindowSize(window, intBuffer, null);
				final int winW = intBuffer.get();

				graphics.scale = (double)width / winW;

				Runnable runnable = new Runnable() {
					public void run () {
						graphics.sizeChanged(width, height);
					}
				};
				if (Thread.currentThread() != glThread)
					postRunnable(runnable);
				else
					runnable.run();
			}
		});
		glfwSetWindowSizeCallback(graphics.window, windowSizeCallback = new GLFWWindowSizeCallback() {
			@Override
			public void invoke(long window, final int width, final int height) {
				intBuffer.clear();
				intBuffer2.clear();
				glfwGetFramebufferSize(window, intBuffer, intBuffer2);
				final int newW = intBuffer.get();
				final int newH = intBuffer2.get();

				Runnable runnable = new Runnable() {
					public void run () {
						graphics.sizeChanged(newW, newH);
					}
				};
				if (Thread.currentThread() != glThread)
					postRunnable(runnable);
				else
					runnable.run();
			}
		});
		glfwSetWindowPosCallback(graphics.window, windowPosCallback = new GLFWWindowPosCallback() {
			@Override
			public void invoke(long window, final int x, final int y) {
				Runnable runnable = new Runnable() {
					public void run () {
						graphics.positionChanged(x, y);
					}
				};
				if (Thread.currentThread() != glThread)
					postRunnable(runnable);
				else
					runnable.run();
			}
		});
		glfwSetWindowRefreshCallback(graphics.window, windowRefreshCallback = new GLFWWindowRefreshCallback() {
			@Override
			public void invoke(long window) {
				if (Thread.currentThread() == glThread) render(System.nanoTime());
			}
		});
		glfwSetWindowFocusCallback(graphics.window, windowFocusCallback = new GLFWWindowFocusCallback() {
			@Override
			public void invoke(long window, int focused) {
				graphics.foreground = focused == GL_TRUE;
				graphics.requestRendering();
			}
		});
		glfwSetWindowIconifyCallback(graphics.window, windowIconifyCallback = new GLFWWindowIconifyCallback() {
			@Override
			public void invoke(long window, int iconified) {
				graphics.minimized = iconified == GL_TRUE;
			}
		});
		glfwSetWindowCloseCallback(graphics.window, windowCloseCallback = new GLFWWindowCloseCallback() {
			@Override
			public void invoke(long window) {
				if (shouldExit()) exit();
				glfwSetWindowShouldClose(window, GL_FALSE);
			}
		});
		glfwSetErrorCallback(errorCallback = new GLFWErrorCallback() {
			@Override
			public void invoke(int error, long description) {
				// FIXME
				System.err.println("GLFW ERROR: " + error + " " + description);
				throw new GdxRuntimeException("GLFW error " + error);
			}
		});

		start();
	}

	/** Starts the game loop after the application internals have been initialized. */
	protected void start () {
		listener.create();
		listener.resize(graphics.getWidth(), graphics.getHeight());

		while (running)
			frame();
		end();
	}

	/** Handles posted runnables, input, and rendering for each frame. */
	protected void frame () {
		if (!running) return;

		boolean shouldRender = false;

		if (executeRunnables()) shouldRender = true;

		if (!running) return;

		input.update();
		if (audio != null) {
			audio.update();
		}
		shouldRender |= graphics.shouldRender();

		long frameStartTime = System.nanoTime();
		int targetFPS = (graphics.isHidden() || graphics.isMinimized()) ? hiddenFPS : //
			(graphics.isForeground() ? foregroundFPS : backgroundFPS);

		if (targetFPS == -1) { // Rendering is paused.
			if (!isPaused) listener.pause();
			isPaused = true;
		} else {
			if (isPaused) listener.resume();
			isPaused = false;
			if (shouldRender)
				render(frameStartTime);
			else
				targetFPS = backgroundFPS;
		}

		if (targetFPS != 0) {
			if (targetFPS == -1)
				sleep(100);
			else
				Sync.sync(targetFPS);
		}
	}

	public boolean executeRunnables () {
		synchronized (runnables) {
			for (int i = runnables.size - 1; i >= 0; i--)
				executedRunnables.add(runnables.get(i));
			runnables.clear();
		}
		if (executedRunnables.size == 0) return false;
		do
			executedRunnables.pop().run();
		while (executedRunnables.size > 0);
		return true;
	}

	void sleep (int millis) {
		try {
			if (millis > 0) Thread.sleep(millis);
		} catch (InterruptedException ignored) {
		}
	}

	void render (long time) {
		graphics.frameStart(time);
		listener.render();
		glfwSwapBuffers(graphics.window);
	}

	/** Called when the game loop has exited. */
	protected void end () {
		synchronized (lifecycleListeners) {
			for (LifecycleListener listener : lifecycleListeners) {
				listener.pause();
				listener.dispose();
			}
		}
		listener.pause();
		listener.dispose();
		if (audio != null) {
			audio.dispose();
		}
		glfwReleaseCallbacks(graphics.window);
		glfwTerminate();
		if (forceExit) System.exit(-1);
	}

	public ApplicationListener getApplicationListener () {
		return listener;
	}

	public JglfwGraphics getGraphics () {
		return graphics;
	}

	public OpenALAudio getAudio () {
		return audio;
	}

	public JglfwInput getInput () {
		return input;
	}

	public JglfwFiles getFiles () {
		return files;
	}

	public JglfwNet getNet () {
		return net;
	}

	public ApplicationType getType () {
		return ApplicationType.Desktop;
	}

	public int getVersion () {
		return 0;
	}

	public long getJavaHeap () {
		return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
	}

	public long getNativeHeap () {
		return getJavaHeap();
	}

	public Preferences getPreferences (String name) {
		if (preferences.containsKey(name))
			return preferences.get(name);
		else {
			Preferences prefs = new JglfwPreferences(name, this.preferencesdir);
			preferences.put(name, prefs);
			return prefs;
		}
	}

	public Clipboard getClipboard () {
		return clipboard;
	}

	public void postRunnable (Runnable runnable) {
		synchronized (runnables) {
			runnables.add(runnable);
			graphics.requestRendering();
		}
	}

	public boolean isPaused () {
		return isPaused;
	}

	public void setForegroundFPS (int foregroundFPS) {
		this.foregroundFPS = foregroundFPS;
	}

	public void setBackgroundFPS (int backgroundFPS) {
		this.backgroundFPS = backgroundFPS;
	}

	public void setHiddenFPS (int hiddenFPS) {
		this.hiddenFPS = hiddenFPS;
	}

	protected boolean shouldExit () {
		return true;
	}

	public void exit () {
		running = false;
	}

	public void setLogLevel (int logLevel) {
		this.logLevel = logLevel;
	}

	@Override
	public int getLogLevel () {
		return logLevel;
	}

	public void debug (String tag, String message) {
		if (logLevel >= LOG_DEBUG) {
			System.out.println(tag + ": " + message);
		}
	}

	public void debug (String tag, String message, Throwable exception) {
		if (logLevel >= LOG_DEBUG) {
			System.out.println(tag + ": " + message);
			exception.printStackTrace(System.out);
		}
	}

	public void log (String tag, String message) {
		if (logLevel >= LOG_INFO) {
			System.out.println(tag + ": " + message);
		}
	}

	public void log (String tag, String message, Throwable exception) {
		if (logLevel >= LOG_INFO) {
			System.out.println(tag + ": " + message);
			exception.printStackTrace(System.out);
		}
	}

	public void error (String tag, String message) {
		if (logLevel >= LOG_ERROR) {
			System.err.println(tag + ": " + message);
		}
	}

	public void error (String tag, String message, Throwable exception) {
		if (logLevel >= LOG_ERROR) {
			System.err.println(tag + ": " + message);
			exception.printStackTrace(System.err);
		}
	}

	public void addLifecycleListener (LifecycleListener listener) {
		synchronized (lifecycleListeners) {
			lifecycleListeners.add(listener);
		}
	}

	public void removeLifecycleListener (LifecycleListener listener) {
		synchronized (lifecycleListeners) {
			lifecycleListeners.removeValue(listener, true);
		}
	}
}
