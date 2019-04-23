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

package com.badlogic.gdx.scenes.scene2d.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A button with a child {@link Image} and {@link Label}.
 * @see ImageButton
 * @see TextButton
 * @see Button
 * @author Nathan Sweet */
public class ImageTextButton extends Button {
	private final Image image;
	private Label label;
	private ImageTextButtonStyle style;

	public ImageTextButton (@Nullable String text, @NotNull Skin skin) {
		this(text, skin.get(ImageTextButtonStyle.class));
		setSkin(skin);
	}

	public ImageTextButton (@Nullable String text, @NotNull Skin skin, @NotNull String styleName) {
		this(text, skin.get(styleName, ImageTextButtonStyle.class));
		setSkin(skin);
	}

	public ImageTextButton (@Nullable String text, @NotNull ImageTextButtonStyle style) {
		super(style);
		this.style = style;

		defaults().space(3);

		image = new Image();
		image.setScaling(Scaling.fit);

		label = new Label(text, new LabelStyle(style.font, style.fontColor));
		label.setAlignment(Align.center);

		add(image);
		add(label);

		setStyle(style);

		setSize(getPrefWidth(), getPrefHeight());
	}

	public void setStyle (@NotNull ButtonStyle style) {
		if (!(style instanceof ImageTextButtonStyle)) throw new IllegalArgumentException("style must be a ImageTextButtonStyle.");
		super.setStyle(style);
		this.style = (ImageTextButtonStyle)style;
		if (image != null) updateImage();
		if (label != null) {
			ImageTextButtonStyle textButtonStyle = (ImageTextButtonStyle)style;
			LabelStyle labelStyle = label.getStyle();
			labelStyle.font = textButtonStyle.font;
			labelStyle.fontColor = textButtonStyle.fontColor;
			label.setStyle(labelStyle);
		}
	}

	@NotNull
	public ImageTextButtonStyle getStyle () {
		return style;
	}

	/** Updates the Image with the appropriate Drawable from the style before it is drawn. */
	protected void updateImage () {
		Drawable drawable = null;
		if (isDisabled() && style.imageDisabled != null)
			drawable = style.imageDisabled;
		else if (isPressed() && style.imageDown != null)
			drawable = style.imageDown;
		else if (isChecked && style.imageChecked != null)
			drawable = (style.imageCheckedOver != null && isOver()) ? style.imageCheckedOver : style.imageChecked;
		else if (isOver() && style.imageOver != null)
			drawable = style.imageOver;
		else if (style.imageUp != null) //
			drawable = style.imageUp;
		image.setDrawable(drawable);
	}

	public void draw (@NotNull Batch batch, float parentAlpha) {
		updateImage();
		Color fontColor;
		if (isDisabled() && style.disabledFontColor != null)
			fontColor = style.disabledFontColor;
		else if (isPressed() && style.downFontColor != null)
			fontColor = style.downFontColor;
		else if (isChecked && style.checkedFontColor != null)
			fontColor = (isOver() && style.checkedOverFontColor != null) ? style.checkedOverFontColor : style.checkedFontColor;
		else if (isOver() && style.overFontColor != null)
			fontColor = style.overFontColor;
		else
			fontColor = style.fontColor;
		if (fontColor != null) label.getStyle().fontColor = fontColor;
		super.draw(batch, parentAlpha);
	}

	@NotNull
	public Image getImage () {
		return image;
	}

	@NotNull
	public Cell getImageCell () {
		Cell cell = getCell(image);
		assert cell != null;
		return cell;
	}

	public void setLabel (@NotNull Label label) {
		getLabelCell().setActor(label);
		this.label = label;
	}

	@NotNull
	public Label getLabel () {
		return label;
	}

	@NotNull
	public Cell getLabelCell () {
		Cell cell = getCell(label);
		assert cell != null;
		return cell;
	}

	public void setText (@NotNull CharSequence text) {
		label.setText(text);
	}

	@NotNull
	public CharSequence getText () {
		return label.getText();
	}

	@NotNull
	public String toString () {
		String name = getName();
		if (name != null) return name;
		String className = getClass().getName();
		int dotIndex = className.lastIndexOf('.');
		if (dotIndex != -1) className = className.substring(dotIndex + 1);
		return (className.indexOf('$') != -1 ? "ImageTextButton " : "") + className + ": " + image.getDrawable() + " "
			+ label.getText();
	}

	/** The style for an image text button, see {@link ImageTextButton}.
	 * @author Nathan Sweet */
	static public class ImageTextButtonStyle extends TextButtonStyle {
		/** Optional. */
		@Nullable public Drawable imageUp, imageDown, imageOver, imageChecked, imageCheckedOver, imageDisabled;

		public ImageTextButtonStyle () {
		}

		public ImageTextButtonStyle (@Nullable Drawable up, @Nullable Drawable down, @Nullable Drawable checked, @NotNull BitmapFont font) {
			super(up, down, checked, font);
		}

		public ImageTextButtonStyle (@NotNull ImageTextButtonStyle style) {
			super(style);
			if (style.imageUp != null) this.imageUp = style.imageUp;
			if (style.imageDown != null) this.imageDown = style.imageDown;
			if (style.imageOver != null) this.imageOver = style.imageOver;
			if (style.imageChecked != null) this.imageChecked = style.imageChecked;
			if (style.imageCheckedOver != null) this.imageCheckedOver = style.imageCheckedOver;
			if (style.imageDisabled != null) this.imageDisabled = style.imageDisabled;
		}

		public ImageTextButtonStyle (@NotNull TextButtonStyle style) {
			super(style);
		}
	}
}
