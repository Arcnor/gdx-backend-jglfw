/*******************************************************************************
 * Copyright 2015 See AUTHORS file.
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

import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.GdxRuntimeException;
import org.lwjgl.glfw.GLFWImage;

import static org.lwjgl.glfw.GLFW.glfwCreateCursor;
import static org.lwjgl.glfw.GLFW.glfwSetCursor;

/** @author mzechner
 * @author Edu Garcia (arcnor) */
public class JglfwCursor implements Cursor {
	private final JglfwGraphics graphics;

	private final long glfwCursor;

	public JglfwCursor(JglfwGraphics graphics, Pixmap pixmap, int xHotspot, int yHotspot) {
		this.graphics = graphics;

		if (pixmap == null) {
			glfwCursor = 0;
			return;
		}

		if (pixmap.getFormat() != Pixmap.Format.RGBA8888) {
			throw new GdxRuntimeException("Cursor image pixmap is not in RGBA8888 format.");
		}

		if ((pixmap.getWidth() & (pixmap.getWidth() - 1)) != 0) {
			throw new GdxRuntimeException("Cursor image pixmap width of " + pixmap.getWidth()
					+ " is not a power-of-two greater than zero.");
		}

		if ((pixmap.getHeight() & (pixmap.getHeight() - 1)) != 0) {
			throw new GdxRuntimeException("Cursor image pixmap height of " + pixmap.getHeight()
					+ " is not a power-of-two greater than zero.");
		}

		if (xHotspot < 0 || xHotspot >= pixmap.getWidth()) {
			throw new GdxRuntimeException("xHotspot coordinate of " + xHotspot + " is not within image width bounds: [0, "
					+ pixmap.getWidth() + ").");
		}

		if (yHotspot < 0 || yHotspot >= pixmap.getHeight()) {
			throw new GdxRuntimeException("yHotspot coordinate of " + yHotspot + " is not within image height bounds: [0, "
					+ pixmap.getHeight() + ").");
		}

		final GLFWImage img = GLFWImage.malloc();
		img.setWidth(pixmap.getWidth());
		img.setHeight(pixmap.getHeight());
		img.setPixels(pixmap.getPixels());

		glfwCursor = glfwCreateCursor(img, xHotspot, yHotspot);
		if (glfwCursor == 0) {
			throw new GdxRuntimeException("Could not create cursor image.");
		}
	}

	@Override
	public void setSystemCursor() {
		glfwSetCursor(graphics.window, glfwCursor);
	}
}
