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
package loon.opengl;

import loon.LTexture;
import loon.geom.Affine2f;

public abstract class BaseBatch extends LTextureBind {

	public void addQuad(LTexture tex, int tint, Affine2f xf, float x, float y,
			float w, float h) {
		if (tex == null || !tex.isLoaded() || tex.disposed()) {
			return;
		}
		setTexture(tex);
		float u2 = tex.getFormat().repeatX ? w / tex.width() : tex.widthRatio;
		float uv = tex.getFormat().repeatY ? h / tex.height() : tex.heightRatio;
		if (tex.isScale()) {
			addQuad(tint, xf, x, y, (x + w), (y + h), tex.xOff, tex.yOff, u2,
					uv);
		} else {
			addQuad(tint, xf, x, y, x + w, y + h, tex.xOff, tex.yOff, u2, uv);
		}
	}

	public void addQuad(LTexture tex, int tint, Affine2f xf, float dx,
			float dy, float dw, float dh, float sx, float sy, float sw, float sh) {
		if (tex == null || !tex.isLoaded() || tex.disposed()) {
			return;
		}
		setTexture(tex);
		float texWidth = tex.width(), texHeight = tex.height();
		if (tex.isScale()) {
			addQuad(tint, xf, dx, dy, dx + dw, dy + dh, tex.xOff
					* (sx / texWidth), tex.yOff * (sy / texHeight),
					((sx + sw) / texWidth), ((sy + sh) / texHeight));
		} else {
			addQuad(tint, xf, dx, dy, dx + dw, dy + dh, tex.xOff
					* (sx / texWidth), tex.yOff * (sy / texHeight),
					tex.widthRatio * ((sx + sw) / texWidth), tex.heightRatio
							* ((sy + sh) / texHeight));
		}
	}

	public void addQuad(int tint, Affine2f xf, float left, float top,
			float right, float bottom, float sl, float st, float sr, float sb) {
		addQuad(tint, xf.m00, xf.m01, xf.m10, xf.m11, xf.tx, xf.ty, left, top,
				right, bottom, sl, st, sr, sb);
	}

	public void addQuad(int tint, float m00, float m01, float m10, float m11,
			float tx, float ty, float left, float top, float right,
			float bottom, float sl, float st, float sr, float sb) {

		addQuad(tint, m00, m01, m10, m11, tx, ty, left, top, sl, st, right,
				top, sr, st, left, bottom, sl, sb, right, bottom, sr, sb);
	}

	public abstract void addQuad(int tint, float m00, float m01, float m10,
			float m11, float tx, float ty, float x1, float y1, float sx1,
			float sy1, float x2, float y2, float sx2, float sy2, float x3,
			float y3, float sx3, float sy3, float x4, float y4, float sx4,
			float sy4);

	protected BaseBatch(GL20 gl) {
		super(gl);
	}
}
