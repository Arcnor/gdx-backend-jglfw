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

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWCharCallback;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWScrollCallback;

import java.nio.DoubleBuffer;

import static org.lwjgl.glfw.GLFW.*;

/** An implementation of the {@link Input} interface hooking GLFW panel for input.
 * @author mzechner
 * @author Nathan Sweet
 * @author Edu Garcia (arcnor) */
public class JglfwInput implements Input {
	final JglfwApplication app;
	// We need to keep a reference to this so it never gets GC'd
	private final GlfwInputProcessor glfwInputProcessor;
	InputProcessor processor;
	int pressedKeys = 0;
	boolean keyJustPressed = false;
	boolean[] justPressedKeys = new boolean[256];
	boolean justTouched;
	int deltaX, deltaY;
	long currentEventTime;
	private final DoubleBuffer doubleBuf = BufferUtils.createDoubleBuffer(1);

	public JglfwInput (final JglfwApplication app) {
		this.app = app;

		InputProcessor inputProcessor = new InputProcessor() {
			private int mouseX, mouseY;

			public boolean keyDown (int keycode) {
				pressedKeys++;
				keyJustPressed = true;
				justPressedKeys[keycode] = true;
				app.graphics.requestRendering();
				return processor != null ? processor.keyDown(keycode) : false;
			}

			public boolean keyUp (int keycode) {
				pressedKeys--;
				app.graphics.requestRendering();
				return processor != null ? processor.keyUp(keycode) : false;
			}

			public boolean keyTyped (char character) {
				app.graphics.requestRendering();
				return processor != null ? processor.keyTyped(character) : false;
			}

			public boolean touchDown (int screenX, int screenY, int pointer, int button) {
				justTouched = true;
				app.graphics.requestRendering();
				return processor != null ? processor.touchDown(screenX, screenY, pointer, button) : false;
			}

			public boolean touchUp (int screenX, int screenY, int pointer, int button) {
				app.graphics.requestRendering();
				return processor != null ? processor.touchUp(screenX, screenY, pointer, button) : false;
			}

			public boolean touchDragged (int screenX, int screenY, int pointer) {
				deltaX = screenX - mouseX;
				deltaY = screenY - mouseY;
				mouseX = screenX;
				mouseY = screenY;
				app.graphics.requestRendering();
				return processor != null ? processor.touchDragged(mouseX, mouseY, 0) : false;
			}

			public boolean mouseMoved (int screenX, int screenY) {
				deltaX = screenX - mouseX;
				deltaY = screenY - mouseY;
				mouseX = screenX;
				mouseY = screenY;
				app.graphics.requestRendering();
				return processor != null ? processor.mouseMoved(mouseX, mouseY) : false;
			}

			public boolean scrolled (int amount) {
				app.graphics.requestRendering();
				return processor != null ? processor.scrolled(amount) : false;
			}
		};

		glfwInputProcessor = new GlfwInputProcessor(app.graphics.window, app.graphics, inputProcessor);
	}

	public void update () {
		deltaX = 0;
		deltaY = 0;
		justTouched = false;
		if (keyJustPressed) {
			keyJustPressed = false;
			for (int i = 0; i < justPressedKeys.length; i++) {
				justPressedKeys[i] = false;
			}
		}

		currentEventTime = System.nanoTime();
		glfwPollEvents(); // Use GLFW main loop to process events.
	}

	public float getAccelerometerX () {
		return 0;
	}

	public float getAccelerometerY () {
		return 0;
	}

	public float getAccelerometerZ () {
		return 0;
	}

	public int getX () {
		doubleBuf.clear();
		glfwGetCursorPos(app.graphics.window, doubleBuf, null);
		return (int) doubleBuf.get();
	}

	public int getX (int pointer) {
		return pointer > 0 ? 0 : getX();
	}

	public int getY () {
		doubleBuf.clear();
		glfwGetCursorPos(app.graphics.window, null, doubleBuf);
		return (int) doubleBuf.get();
	}

	public int getY (int pointer) {
		return pointer > 0 ? 0 : getY();
	}

	public int getDeltaX () {
		return deltaX;
	}

	public int getDeltaX (int pointer) {
		return pointer > 0 ? 0 : deltaX;
	}

	public int getDeltaY () {
		return deltaY;
	}

	public int getDeltaY (int pointer) {
		return pointer > 0 ? 0 : deltaY;
	}

	public boolean isTouched () {
		return glfwGetMouseButton(app.graphics.window, 0) == GLFW_PRESS || glfwGetMouseButton(app.graphics.window, 1) == GLFW_PRESS
				|| glfwGetMouseButton(app.graphics.window, 2) == GLFW_PRESS;
	}

	public boolean isTouched (int pointer) {
		return pointer > 0 ? false : isTouched();
	}

	public boolean justTouched () {
		return justTouched;
	}

	public boolean isButtonPressed (int button) {
		return glfwGetMouseButton(app.graphics.window, button) == GLFW_PRESS;
	}

	public boolean isKeyPressed (int key) {
		if (key == Input.Keys.ANY_KEY) return pressedKeys > 0;
		if (key == Input.Keys.SYM)
			return glfwGetKey(app.graphics.window, GLFW_KEY_LEFT_SUPER) == GLFW_PRESS || glfwGetKey(app.graphics.window, GLFW_KEY_RIGHT_SUPER) == GLFW_PRESS;
		return glfwGetKey(app.graphics.window, getJglfwKeyCode(key)) == GLFW_PRESS;
	}

	@Override
	public boolean isKeyJustPressed (int key) {
		if (key == Input.Keys.ANY_KEY) {
			return keyJustPressed;
		}
		if (key < 0 || key > 256) {
			return false;
		}
		return justPressedKeys[key];
	}

	public void setOnscreenKeyboardVisible (boolean visible) {
	}

	public void vibrate (int milliseconds) {
	}

	public void vibrate (long[] pattern, int repeat) {
	}

	public void cancelVibrate () {
	}

	public float getAzimuth () {
		return 0;
	}

	public float getPitch () {
		return 0;
	}

	public float getRoll () {
		return 0;
	}

	public void getRotationMatrix (float[] matrix) {
	}

	public long getCurrentEventTime () {
		return currentEventTime;
	}

	public void setCatchBackKey (boolean catchBack) {
	}

	public boolean isCatchBackKey () {
		return false;
	}

	public void setCatchMenuKey (boolean catchMenu) {
	}

	@Override
	public boolean isCatchMenuKey() {
		return false;
	}

	public void setInputProcessor (InputProcessor processor) {
		this.processor = processor;
	}

	public InputProcessor getInputProcessor () {
		return processor;
	}

	public boolean isPeripheralAvailable (Peripheral peripheral) {
		return peripheral == Peripheral.HardwareKeyboard;
	}

	public int getRotation () {
		return 0;
	}

	public Orientation getNativeOrientation () {
		return Orientation.Landscape;
	}

	public void setCursorCatched (boolean captured) {
		glfwSetInputMode(app.graphics.window, GLFW_CURSOR, captured ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
	}

	public boolean isCursorCatched () {
		return glfwGetInputMode(app.graphics.window, GLFW_CURSOR) == GLFW_CURSOR_DISABLED;
	}

	public void setCursorPosition (int x, int y) {
		glfwSetCursorPos(app.graphics.window, x, y);
	}

	public void getTextInput (final TextInputListener listener, final String title, final String text, final String hint) {
		// FIXME: Do this with JavaFX or native instead
	}

	static char characterForKeyCode (int key) {
		// Map certain key codes to character codes.
		switch (key) {
		case Keys.BACKSPACE:
			return 8;
		case Keys.TAB:
			return '\t';
		case Keys.FORWARD_DEL:
			return 127;
		}
		return 0;
	}

	static public int getGdxKeyCode (int lwjglKeyCode) {
		switch (lwjglKeyCode) {
		case GLFW_KEY_SPACE:
			return Input.Keys.SPACE;
		case GLFW_KEY_APOSTROPHE:
			return Input.Keys.APOSTROPHE;
		case GLFW_KEY_COMMA:
			return Input.Keys.COMMA;
		case GLFW_KEY_MINUS:
			return Input.Keys.MINUS;
		case GLFW_KEY_PERIOD:
			return Input.Keys.PERIOD;
		case GLFW_KEY_SLASH:
			return Input.Keys.SLASH;
		case GLFW_KEY_0:
			return Input.Keys.NUM_0;
		case GLFW_KEY_1:
			return Input.Keys.NUM_1;
		case GLFW_KEY_2:
			return Input.Keys.NUM_2;
		case GLFW_KEY_3:
			return Input.Keys.NUM_3;
		case GLFW_KEY_4:
			return Input.Keys.NUM_4;
		case GLFW_KEY_5:
			return Input.Keys.NUM_5;
		case GLFW_KEY_6:
			return Input.Keys.NUM_6;
		case GLFW_KEY_7:
			return Input.Keys.NUM_7;
		case GLFW_KEY_8:
			return Input.Keys.NUM_8;
		case GLFW_KEY_9:
			return Input.Keys.NUM_9;
		case GLFW_KEY_SEMICOLON:
			return Input.Keys.SEMICOLON;
		case GLFW_KEY_EQUAL:
			return Input.Keys.EQUALS;
		case GLFW_KEY_A:
			return Input.Keys.A;
		case GLFW_KEY_B:
			return Input.Keys.B;
		case GLFW_KEY_C:
			return Input.Keys.C;
		case GLFW_KEY_D:
			return Input.Keys.D;
		case GLFW_KEY_E:
			return Input.Keys.E;
		case GLFW_KEY_F:
			return Input.Keys.F;
		case GLFW_KEY_G:
			return Input.Keys.G;
		case GLFW_KEY_H:
			return Input.Keys.H;
		case GLFW_KEY_I:
			return Input.Keys.I;
		case GLFW_KEY_J:
			return Input.Keys.J;
		case GLFW_KEY_K:
			return Input.Keys.K;
		case GLFW_KEY_L:
			return Input.Keys.L;
		case GLFW_KEY_M:
			return Input.Keys.M;
		case GLFW_KEY_N:
			return Input.Keys.N;
		case GLFW_KEY_O:
			return Input.Keys.O;
		case GLFW_KEY_P:
			return Input.Keys.P;
		case GLFW_KEY_Q:
			return Input.Keys.Q;
		case GLFW_KEY_R:
			return Input.Keys.R;
		case GLFW_KEY_S:
			return Input.Keys.S;
		case GLFW_KEY_T:
			return Input.Keys.T;
		case GLFW_KEY_U:
			return Input.Keys.U;
		case GLFW_KEY_V:
			return Input.Keys.V;
		case GLFW_KEY_W:
			return Input.Keys.W;
		case GLFW_KEY_X:
			return Input.Keys.X;
		case GLFW_KEY_Y:
			return Input.Keys.Y;
		case GLFW_KEY_Z:
			return Input.Keys.Z;
		case GLFW_KEY_LEFT_BRACKET:
			return Input.Keys.LEFT_BRACKET;
		case GLFW_KEY_BACKSLASH:
			return Input.Keys.BACKSLASH;
		case GLFW_KEY_RIGHT_BRACKET:
			return Input.Keys.RIGHT_BRACKET;
		case GLFW_KEY_GRAVE_ACCENT:
			return Input.Keys.GRAVE;
		case GLFW_KEY_WORLD_1:
		case GLFW_KEY_WORLD_2:
			return Input.Keys.UNKNOWN;
		case GLFW_KEY_ESCAPE:
			return Input.Keys.ESCAPE;
		case GLFW_KEY_ENTER:
			return Input.Keys.ENTER;
		case GLFW_KEY_TAB:
			return Input.Keys.TAB;
		case GLFW_KEY_BACKSPACE:
			return Input.Keys.BACKSPACE;
		case GLFW_KEY_INSERT:
			return Input.Keys.INSERT;
		case GLFW_KEY_DELETE:
			return Input.Keys.FORWARD_DEL;
		case GLFW_KEY_RIGHT:
			return Input.Keys.RIGHT;
		case GLFW_KEY_LEFT:
			return Input.Keys.LEFT;
		case GLFW_KEY_DOWN:
			return Input.Keys.DOWN;
		case GLFW_KEY_UP:
			return Input.Keys.UP;
		case GLFW_KEY_PAGE_UP:
			return Input.Keys.PAGE_UP;
		case GLFW_KEY_PAGE_DOWN:
			return Input.Keys.PAGE_DOWN;
		case GLFW_KEY_HOME:
			return Input.Keys.HOME;
		case GLFW_KEY_END:
			return Input.Keys.END;
		case GLFW_KEY_CAPS_LOCK:
		case GLFW_KEY_SCROLL_LOCK:
		case GLFW_KEY_NUM_LOCK:
		case GLFW_KEY_PRINT_SCREEN:
		case GLFW_KEY_PAUSE:
			return Input.Keys.UNKNOWN;
		case GLFW_KEY_F1:
			return Input.Keys.F1;
		case GLFW_KEY_F2:
			return Input.Keys.F2;
		case GLFW_KEY_F3:
			return Input.Keys.F3;
		case GLFW_KEY_F4:
			return Input.Keys.F4;
		case GLFW_KEY_F5:
			return Input.Keys.F5;
		case GLFW_KEY_F6:
			return Input.Keys.F6;
		case GLFW_KEY_F7:
			return Input.Keys.F7;
		case GLFW_KEY_F8:
			return Input.Keys.F8;
		case GLFW_KEY_F9:
			return Input.Keys.F9;
		case GLFW_KEY_F10:
			return Input.Keys.F10;
		case GLFW_KEY_F11:
			return Input.Keys.F11;
		case GLFW_KEY_F12:
			return Input.Keys.F12;
		case GLFW_KEY_F13:
		case GLFW_KEY_F14:
		case GLFW_KEY_F15:
		case GLFW_KEY_F16:
		case GLFW_KEY_F17:
		case GLFW_KEY_F18:
		case GLFW_KEY_F19:
		case GLFW_KEY_F20:
		case GLFW_KEY_F21:
		case GLFW_KEY_F22:
		case GLFW_KEY_F23:
		case GLFW_KEY_F24:
		case GLFW_KEY_F25:
			return Input.Keys.UNKNOWN;
		case GLFW_KEY_KP_0:
			return Input.Keys.NUMPAD_0;
		case GLFW_KEY_KP_1:
			return Input.Keys.NUMPAD_1;
		case GLFW_KEY_KP_2:
			return Input.Keys.NUMPAD_2;
		case GLFW_KEY_KP_3:
			return Input.Keys.NUMPAD_3;
		case GLFW_KEY_KP_4:
			return Input.Keys.NUMPAD_4;
		case GLFW_KEY_KP_5:
			return Input.Keys.NUMPAD_5;
		case GLFW_KEY_KP_6:
			return Input.Keys.NUMPAD_6;
		case GLFW_KEY_KP_7:
			return Input.Keys.NUMPAD_7;
		case GLFW_KEY_KP_8:
			return Input.Keys.NUMPAD_8;
		case GLFW_KEY_KP_9:
			return Input.Keys.NUMPAD_9;
		case GLFW_KEY_KP_DECIMAL:
			return Input.Keys.PERIOD;
		case GLFW_KEY_KP_DIVIDE:
			return Input.Keys.SLASH;
		case GLFW_KEY_KP_MULTIPLY:
			return Input.Keys.STAR;
		case GLFW_KEY_KP_SUBTRACT:
			return Input.Keys.MINUS;
		case GLFW_KEY_KP_ADD:
			return Input.Keys.PLUS;
		case GLFW_KEY_KP_ENTER:
			return Input.Keys.ENTER;
		case GLFW_KEY_KP_EQUAL:
			return Input.Keys.EQUALS;
		case GLFW_KEY_LEFT_SHIFT:
			return Input.Keys.SHIFT_LEFT;
		case GLFW_KEY_LEFT_CONTROL:
			return Input.Keys.CONTROL_LEFT;
		case GLFW_KEY_LEFT_ALT:
			return Input.Keys.ALT_LEFT;
		case GLFW_KEY_LEFT_SUPER:
			return Input.Keys.SYM;
		case GLFW_KEY_RIGHT_SHIFT:
			return Input.Keys.SHIFT_RIGHT;
		case GLFW_KEY_RIGHT_CONTROL:
			return Input.Keys.CONTROL_RIGHT;
		case GLFW_KEY_RIGHT_ALT:
			return Input.Keys.ALT_RIGHT;
		case GLFW_KEY_RIGHT_SUPER:
			return Input.Keys.SYM;
		case GLFW_KEY_MENU:
			return Input.Keys.MENU;
		default:
			return Input.Keys.UNKNOWN;
		}
	}

	static public int getJglfwKeyCode (int gdxKeyCode) {
		switch (gdxKeyCode) {
		case Input.Keys.SPACE:
			return GLFW_KEY_SPACE;
		case Input.Keys.APOSTROPHE:
			return GLFW_KEY_APOSTROPHE;
		case Input.Keys.COMMA:
			return GLFW_KEY_COMMA;
		case Input.Keys.PERIOD:
			return GLFW_KEY_PERIOD;
		case Input.Keys.NUM_0:
			return GLFW_KEY_0;
		case Input.Keys.NUM_1:
			return GLFW_KEY_1;
		case Input.Keys.NUM_2:
			return GLFW_KEY_2;
		case Input.Keys.NUM_3:
			return GLFW_KEY_3;
		case Input.Keys.NUM_4:
			return GLFW_KEY_4;
		case Input.Keys.NUM_5:
			return GLFW_KEY_5;
		case Input.Keys.NUM_6:
			return GLFW_KEY_6;
		case Input.Keys.NUM_7:
			return GLFW_KEY_7;
		case Input.Keys.NUM_8:
			return GLFW_KEY_8;
		case Input.Keys.NUM_9:
			return GLFW_KEY_9;
		case Input.Keys.SEMICOLON:
			return GLFW_KEY_SEMICOLON;
		case Input.Keys.EQUALS:
			return GLFW_KEY_EQUAL;
		case Input.Keys.A:
			return GLFW_KEY_A;
		case Input.Keys.B:
			return GLFW_KEY_B;
		case Input.Keys.C:
			return GLFW_KEY_C;
		case Input.Keys.D:
			return GLFW_KEY_D;
		case Input.Keys.E:
			return GLFW_KEY_E;
		case Input.Keys.F:
			return GLFW_KEY_F;
		case Input.Keys.G:
			return GLFW_KEY_G;
		case Input.Keys.H:
			return GLFW_KEY_H;
		case Input.Keys.I:
			return GLFW_KEY_I;
		case Input.Keys.J:
			return GLFW_KEY_J;
		case Input.Keys.K:
			return GLFW_KEY_K;
		case Input.Keys.L:
			return GLFW_KEY_L;
		case Input.Keys.M:
			return GLFW_KEY_M;
		case Input.Keys.N:
			return GLFW_KEY_N;
		case Input.Keys.O:
			return GLFW_KEY_O;
		case Input.Keys.P:
			return GLFW_KEY_P;
		case Input.Keys.Q:
			return GLFW_KEY_Q;
		case Input.Keys.R:
			return GLFW_KEY_R;
		case Input.Keys.S:
			return GLFW_KEY_S;
		case Input.Keys.T:
			return GLFW_KEY_T;
		case Input.Keys.U:
			return GLFW_KEY_U;
		case Input.Keys.V:
			return GLFW_KEY_V;
		case Input.Keys.W:
			return GLFW_KEY_W;
		case Input.Keys.X:
			return GLFW_KEY_X;
		case Input.Keys.Y:
			return GLFW_KEY_Y;
		case Input.Keys.Z:
			return GLFW_KEY_Z;
		case Input.Keys.LEFT_BRACKET:
			return GLFW_KEY_LEFT_BRACKET;
		case Input.Keys.BACKSLASH:
			return GLFW_KEY_BACKSLASH;
		case Input.Keys.RIGHT_BRACKET:
			return GLFW_KEY_RIGHT_BRACKET;
		case Input.Keys.GRAVE:
			return GLFW_KEY_GRAVE_ACCENT;
		case Input.Keys.ESCAPE:
			return GLFW_KEY_ESCAPE;
		case Input.Keys.ENTER:
			return GLFW_KEY_ENTER;
		case Input.Keys.TAB:
			return GLFW_KEY_TAB;
		case Input.Keys.BACKSPACE:
			return GLFW_KEY_BACKSPACE;
		case Input.Keys.INSERT:
			return GLFW_KEY_INSERT;
		case Input.Keys.FORWARD_DEL:
			return GLFW_KEY_DELETE;
		case Input.Keys.RIGHT:
			return GLFW_KEY_RIGHT;
		case Input.Keys.LEFT:
			return GLFW_KEY_LEFT;
		case Input.Keys.DOWN:
			return GLFW_KEY_DOWN;
		case Input.Keys.UP:
			return GLFW_KEY_UP;
		case Input.Keys.PAGE_UP:
			return GLFW_KEY_PAGE_UP;
		case Input.Keys.PAGE_DOWN:
			return GLFW_KEY_PAGE_DOWN;
		case Input.Keys.HOME:
			return GLFW_KEY_HOME;
		case Input.Keys.END:
			return GLFW_KEY_END;
		case Input.Keys.F1:
			return GLFW_KEY_F1;
		case Input.Keys.F2:
			return GLFW_KEY_F2;
		case Input.Keys.F3:
			return GLFW_KEY_F3;
		case Input.Keys.F4:
			return GLFW_KEY_F4;
		case Input.Keys.F5:
			return GLFW_KEY_F5;
		case Input.Keys.F6:
			return GLFW_KEY_F6;
		case Input.Keys.F7:
			return GLFW_KEY_F7;
		case Input.Keys.F8:
			return GLFW_KEY_F8;
		case Input.Keys.F9:
			return GLFW_KEY_F9;
		case Input.Keys.F10:
			return GLFW_KEY_F10;
		case Input.Keys.F11:
			return GLFW_KEY_F11;
		case Input.Keys.F12:
			return GLFW_KEY_F12;
		case Input.Keys.NUMPAD_0:
			return GLFW_KEY_KP_0;
		case Input.Keys.NUMPAD_1:
			return GLFW_KEY_KP_1;
		case Input.Keys.NUMPAD_2:
			return GLFW_KEY_KP_2;
		case Input.Keys.NUMPAD_3:
			return GLFW_KEY_KP_3;
		case Input.Keys.NUMPAD_4:
			return GLFW_KEY_KP_4;
		case Input.Keys.NUMPAD_5:
			return GLFW_KEY_KP_5;
		case Input.Keys.NUMPAD_6:
			return GLFW_KEY_KP_6;
		case Input.Keys.NUMPAD_7:
			return GLFW_KEY_KP_7;
		case Input.Keys.NUMPAD_8:
			return GLFW_KEY_KP_8;
		case Input.Keys.NUMPAD_9:
			return GLFW_KEY_KP_9;
		case Input.Keys.SLASH:
			return GLFW_KEY_KP_DIVIDE;
		case Input.Keys.STAR:
			return GLFW_KEY_KP_MULTIPLY;
		case Input.Keys.MINUS:
			return GLFW_KEY_KP_SUBTRACT;
		case Input.Keys.PLUS:
			return GLFW_KEY_KP_ADD;
		case Input.Keys.SHIFT_LEFT:
			return GLFW_KEY_LEFT_SHIFT;
		case Input.Keys.CONTROL_LEFT:
			return GLFW_KEY_LEFT_CONTROL;
		case Input.Keys.ALT_LEFT:
			return GLFW_KEY_LEFT_ALT;
		case Input.Keys.SYM:
			return GLFW_KEY_LEFT_SUPER;
		case Input.Keys.SHIFT_RIGHT:
			return GLFW_KEY_RIGHT_SHIFT;
		case Input.Keys.CONTROL_RIGHT:
			return GLFW_KEY_RIGHT_CONTROL;
		case Input.Keys.ALT_RIGHT:
			return GLFW_KEY_RIGHT_ALT;
		case Input.Keys.MENU:
			return GLFW_KEY_MENU;
		default:
			return 0;
		}
	}

	/** Receives GLFW input and calls InputProcessor methods.
	 * @author Nathan Sweet */
	private static class GlfwInputProcessor {
		private final JglfwGraphics graphics;
		private int mouseX, mouseY, mousePressed;
		private char lastCharacter;
		private InputProcessor processor;

		private final GLFWCharCallback charCallback = new GLFWCharCallback() {
			@Override
			public void invoke(long window, int character) {
				if ((character & 0xff00) == 0xf700) return;
				lastCharacter = (char) character;
				processor.keyTyped(lastCharacter);
			}
		};

		private final GLFWKeyCallback keyCallback = new GLFWKeyCallback() {
			@Override
			public void invoke(long window, int key, int scancode, int action, int mods) {
				switch (action) {
					case GLFW_PRESS:

						key = getGdxKeyCode(key);
						processor.keyDown(key);

						lastCharacter = 0;
						char character = characterForKeyCode(key);
						if (character != 0) charCallback.invoke(window, character);
						break;

					case GLFW_RELEASE:
						processor.keyUp(getGdxKeyCode(key));
						break;

					case GLFW_REPEAT:
						if (lastCharacter != 0) processor.keyTyped(lastCharacter);
						break;
				}
			}
		};

		private final GLFWScrollCallback scrollCallback = new GLFWScrollCallback() {
			@Override
			public void invoke(long window, double xoffset, double yoffset) {
				processor.scrolled((int)-Math.signum(yoffset));
			}
		};

		private final GLFWMouseButtonCallback mouseButtonCallback = new GLFWMouseButtonCallback() {
			@Override
			public void invoke(long window, int button, int action, int mods) {
				int gdxButton = toGdxButton(button);
				if (button != -1 && gdxButton == -1) return; // Ignore unknown button.

				// FIXME: Missing GLFW_REPEAT...
				if (action == GLFW_PRESS) {
					mousePressed++;
					processor.touchDown(mouseX, mouseY, 0, gdxButton);
				} else if (action == GLFW_RELEASE) {
					mousePressed = Math.max(0, mousePressed - 1);
					processor.touchUp(mouseX, mouseY, 0, gdxButton);
				}
			}

			private int toGdxButton (int button) {
				if (button == 0) return Buttons.LEFT;
				if (button == 1) return Buttons.RIGHT;
				if (button == 2) return Buttons.MIDDLE;
				if (button == 3) return Buttons.BACK;
				if (button == 4) return Buttons.FORWARD;
				return -1;
			}
		};

		private final GLFWCursorPosCallback cursorPosCallback = new GLFWCursorPosCallback() {
			@Override
			public void invoke(long window, double xpos, double ypos) {
				mouseX = (int) (xpos * graphics.scale);
				mouseY = (int) (ypos * graphics.scale);
				if (mousePressed > 0)
					processor.touchDragged(mouseX, mouseY, 0);
				else
					processor.mouseMoved(mouseX, mouseY);
			}
		};

		public GlfwInputProcessor(long window, JglfwGraphics graphics, InputProcessor processor) {
			if (processor == null) throw new IllegalArgumentException("processor cannot be null.");
			this.graphics = graphics;
			this.processor = processor;

			glfwSetCharCallback(window, charCallback);
			glfwSetKeyCallback(window, keyCallback);
			glfwSetScrollCallback(window, scrollCallback);
			glfwSetMouseButtonCallback(window, mouseButtonCallback);
			glfwSetCursorPosCallback(window, cursorPosCallback);
		}
	}
}
