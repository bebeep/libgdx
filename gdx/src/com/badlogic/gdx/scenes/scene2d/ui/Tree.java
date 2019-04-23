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

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Tree.Node;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.Layout;
import com.badlogic.gdx.scenes.scene2d.utils.Selection;
import com.badlogic.gdx.scenes.scene2d.utils.UIUtils;
import com.badlogic.gdx.utils.Array;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A tree widget where each node has an icon, actor, and child nodes.
 * <p>
 * The preferred size of the tree is determined by the preferred size of the actors for the expanded nodes.
 * <p>
 * {@link ChangeEvent} is fired when the selected node changes.
 * @param <N> The type of nodes in the tree.
 * @param <V> The type of values for each node.
 * @author Nathan Sweet */
public class Tree<N extends Node, V> extends WidgetGroup {
	static private final Vector2 tmp = new Vector2();

	TreeStyle style;
	final Array<N> rootNodes = new Array();
	@NotNull final Selection<N> selection;
	float ySpacing = 4, iconSpacingLeft = 2, iconSpacingRight = 2, paddingLeft, paddingRight, indentSpacing;
	private float prefWidth, prefHeight;
	private boolean sizeInvalid = true;
	private N foundNode, overNode;
	N rangeStart;
	private ClickListener clickListener;

	public Tree (@NotNull Skin skin) {
		this(skin.get(TreeStyle.class));
	}

	public Tree (@NotNull Skin skin, @NotNull String styleName) {
		this(skin.get(styleName, TreeStyle.class));
	}

	public Tree (@NotNull TreeStyle style) {
		selection = new Selection<N>() {
			protected void changed () {
				switch (size()) {
				case 0:
					rangeStart = null;
					break;
				case 1:
					rangeStart = first();
					break;
				}
			}
		};
		selection.setActor(this);
		selection.setMultiple(true);
		setStyle(style);
		initialize();
	}

	private void initialize () {
		addListener(clickListener = new ClickListener() {
			public void clicked (@NotNull InputEvent event, float x, float y) {
				N node = getNodeAt(y);
				if (node == null) return;
				if (node != getNodeAt(getTouchDownY())) return;
				if (selection.getMultiple() && selection.notEmpty() && UIUtils.shift()) {
					// Select range (shift).
					if (rangeStart == null) rangeStart = node;
					N rangeStart = Tree.this.rangeStart;
					if (!UIUtils.ctrl()) selection.clear();
					float start = rangeStart.actor.getY(), end = node.actor.getY();
					if (start > end)
						selectNodes(rootNodes, end, start);
					else {
						selectNodes(rootNodes, start, end);
						selection.items().orderedItems().reverse();
					}

					selection.fireChangeEvent();
					Tree.this.rangeStart = rangeStart;
					return;
				}
				if (node.children.size > 0 && (!selection.getMultiple() || !UIUtils.ctrl())) {
					// Toggle expanded if left of icon.
					float rowX = node.actor.getX();
					if (node.icon != null) rowX -= iconSpacingRight + node.icon.getMinWidth();
					if (x < rowX) {
						node.setExpanded(!node.expanded);
						return;
					}
				}
				if (!node.isSelectable()) return;
				selection.choose(node);
				if (!selection.isEmpty()) rangeStart = node;
			}

			public boolean mouseMoved (@NotNull InputEvent event, float x, float y) {
				setOverNode(getNodeAt(y));
				return false;
			}

			public void enter (InputEvent event, float x, float y, int pointer, Actor fromActor) {
				super.enter(event, x, y, pointer, fromActor);
				setOverNode(getNodeAt(y));
			}

			public void exit (@NotNull InputEvent event, float x, float y, int pointer, @Nullable Actor toActor) {
				super.exit(event, x, y, pointer, toActor);
				if (toActor == null || !toActor.isDescendantOf(Tree.this)) setOverNode(null);
			}
		});
	}

	public void setStyle (@NotNull TreeStyle style) {
		this.style = style;

		// Reasonable default.
		if (indentSpacing == 0) indentSpacing = plusMinusWidth();
	}

	public void add (@NotNull N node) {
		insert(rootNodes.size, node);
	}

	public void insert (int index, @NotNull N node) {
		int existingIndex = rootNodes.indexOf(node, true);
		if (existingIndex != -1 && existingIndex < index) index--;
		remove(node);
		node.parent = null;
		rootNodes.insert(index, node);
		node.addToTree(this);
		invalidateHierarchy();
	}

	public void remove (@NotNull N node) {
		if (node.parent != null) {
			node.parent.remove(node);
			return;
		}
		rootNodes.removeValue(node, true);
		node.removeFromTree(this);
		invalidateHierarchy();
	}

	/** Removes all tree nodes. */
	public void clearChildren () {
		super.clearChildren();
		setOverNode(null);
		rootNodes.clear();
		selection.clear();
	}

	@NotNull
	public Array<N> getNodes () {
		return rootNodes;
	}

	public void invalidate () {
		super.invalidate();
		sizeInvalid = true;
	}

	private float plusMinusWidth () {
		float width = Math.max(style.plus.getMinWidth(), style.minus.getMinWidth());
		if (style.plusOver != null) width = Math.max(width, style.plusOver.getMinWidth());
		if (style.minusOver != null) width = Math.max(width, style.minusOver.getMinWidth());
		return width;
	}

	private void computeSize () {
		sizeInvalid = false;
		prefWidth = plusMinusWidth();
		prefHeight = 0;
		computeSize(rootNodes, 0, prefWidth);
		prefWidth += paddingLeft + paddingRight;
	}

	private void computeSize (@NotNull Array<N> nodes, float indent, float plusMinusWidth) {
		float ySpacing = this.ySpacing;
		float spacing = iconSpacingLeft + iconSpacingRight;
		for (int i = 0, n = nodes.size; i < n; i++) {
			N node = nodes.get(i);
			float rowWidth = indent + plusMinusWidth;
			Actor actor = node.actor;
			if (actor instanceof Layout) {
				Layout layout = (Layout)actor;
				rowWidth += layout.getPrefWidth();
				node.height = layout.getPrefHeight();
			} else {
				rowWidth += actor.getWidth();
				node.height = actor.getHeight();
			}
			if (node.icon != null) {
				rowWidth += spacing + node.icon.getMinWidth();
				node.height = Math.max(node.height, node.icon.getMinHeight());
			}
			prefWidth = Math.max(prefWidth, rowWidth);
			prefHeight += node.height + ySpacing;
			if (node.expanded) computeSize(node.children, indent + indentSpacing, plusMinusWidth);
		}
	}

	public void layout () {
		if (sizeInvalid) computeSize();
		layout(rootNodes, paddingLeft, getHeight() - ySpacing / 2, plusMinusWidth());
	}

	private float layout (@NotNull Array<N> nodes, float indent, float y, float plusMinusWidth) {
		float ySpacing = this.ySpacing;
		float iconSpacingLeft = this.iconSpacingLeft;
		float spacing = iconSpacingLeft + iconSpacingRight;
		for (int i = 0, n = nodes.size; i < n; i++) {
			N node = nodes.get(i);
			float x = indent + plusMinusWidth;
			if (node.icon != null)
				x += spacing + node.icon.getMinWidth();
			else
				x += iconSpacingLeft;
			if (node.actor instanceof Layout) ((Layout)node.actor).pack();
			y -= node.getHeight();
			node.actor.setPosition(x, y);
			y -= ySpacing;
			if (node.expanded) y = layout(node.children, indent + indentSpacing, y, plusMinusWidth);
		}
		return y;
	}

	public void draw (@NotNull Batch batch, float parentAlpha) {
		drawBackground(batch, parentAlpha);
		Color color = getColor();
		batch.setColor(color.r, color.g, color.b, color.a * parentAlpha);
		draw(batch, rootNodes, paddingLeft, plusMinusWidth());
		super.draw(batch, parentAlpha); // Draw node actors.
	}

	/** Called to draw the background. Default implementation draws the style background drawable. */
	protected void drawBackground (@NotNull Batch batch, float parentAlpha) {
		if (style.background != null) {
			Color color = getColor();
			batch.setColor(color.r, color.g, color.b, color.a * parentAlpha);
			style.background.draw(batch, getX(), getY(), getWidth(), getHeight());
		}
	}

	/** Draws selection, icons, and expand icons. */
	private void draw (@NotNull Batch batch, @NotNull Array<N> nodes, float indent, float plusMinusWidth) {
		Rectangle cullingArea = getCullingArea();
		float cullBottom = 0, cullTop = 0;
		if (cullingArea != null) {
			cullBottom = cullingArea.y;
			cullTop = cullBottom + cullingArea.height;
		}
		TreeStyle style = this.style;
		float x = getX(), y = getY(), expandX = x + indent, iconX = expandX + plusMinusWidth + iconSpacingLeft;
		for (int i = 0, n = nodes.size; i < n; i++) {
			N node = nodes.get(i);
			Actor actor = node.actor;
			float actorY = actor.getY(), height = node.height;
			if (cullingArea == null || (actorY + height >= cullBottom && actorY <= cullTop)) {
				if (selection.contains(node) && style.selection != null) {
					drawSelection(node, style.selection, batch, x, y + actorY - ySpacing / 2, getWidth(), height + ySpacing);
				} else if (node == overNode && style.over != null) {
					drawOver(node, style.over, batch, x, y + actorY - ySpacing / 2, getWidth(), height + ySpacing);
				}

				if (node.icon != null) {
					float iconY = y + actorY + Math.round((height - node.icon.getMinHeight()) / 2);
					batch.setColor(actor.getColor());
					drawIcon(node, node.icon, batch, iconX, iconY);
					batch.setColor(1, 1, 1, 1);
				}

				if (node.children.size > 0) {
					Drawable expandIcon = getExpandIcon(node, iconX);
					float iconY = y + actorY + Math.round((height - expandIcon.getMinHeight()) / 2);
					drawExpandIcon(node, expandIcon, batch, expandX, iconY);
				}
			} else if (actorY < cullBottom) {
				return;
			}
			if (node.expanded && node.children.size > 0) draw(batch, node.children, indent + indentSpacing, plusMinusWidth);
		}
	}

	protected void drawSelection (N node, Drawable selection, Batch batch, float x, float y, float width, float height) {
		selection.draw(batch, x, y, width, height);
	}

	protected void drawOver (N node, Drawable over, Batch batch, float x, float y, float width, float height) {
		over.draw(batch, x, y, width, height);
	}

	protected void drawExpandIcon (N node, Drawable expandIcon, Batch batch, float x, float y) {
		expandIcon.draw(batch, x, y, expandIcon.getMinWidth(), expandIcon.getMinHeight());
	}

	protected void drawIcon (N node, Drawable icon, Batch batch, float x, float y) {
		icon.draw(batch, x, y, icon.getMinWidth(), icon.getMinHeight());
	}

	/** Returns the drawable for the expand icon. The default implementation returns {@link TreeStyle#plusOver} or
	 * {@link TreeStyle#minusOver} on the desktop if the node is the {@link #getOverNode() over node}, the mouse is left of
	 * <code>iconX</code>, and clicking would expand the node.
	 * @param iconX The X coordinate of the over node's icon. */
	protected Drawable getExpandIcon (N node, float iconX) {
		boolean over = false;
		if (node == overNode //
			&& Gdx.app.getType() == ApplicationType.Desktop //
			&& (!selection.getMultiple() || (!UIUtils.ctrl() && !UIUtils.shift())) //
		) {
			float mouseX = screenToLocalCoordinates(tmp.set(Gdx.input.getX(), 0)).x;
			if (mouseX >= 0 && mouseX < iconX) over = true;
		}
		if (over) {
			Drawable icon = node.expanded ? style.minusOver : style.plusOver;
			if (icon != null) return icon;
		}
		return node.expanded ? style.minus : style.plus;
	}

	/** @return May be null. */
	@Nullable
	public N getNodeAt (float y) {
		foundNode = null;
		getNodeAt(rootNodes, y, getHeight());
		return foundNode;
	}

	private float getNodeAt (@NotNull Array<N> nodes, float y, float rowY) {
		for (int i = 0, n = nodes.size; i < n; i++) {
			N node = nodes.get(i);
			float height = node.height;
			rowY -= node.getHeight() - height; // Node subclass may increase getHeight.
			if (y >= rowY - height - ySpacing && y < rowY) {
				foundNode = node;
				return -1;
			}
			rowY -= height + ySpacing;
			if (node.expanded) {
				rowY = getNodeAt(node.children, y, rowY);
				if (rowY == -1) return -1;
			}
		}
		return rowY;
	}

	void selectNodes (@NotNull Array<N> nodes, float low, float high) {
		for (int i = 0, n = nodes.size; i < n; i++) {
			N node = nodes.get(i);
			if (node.actor.getY() < low) break;
			if (!node.isSelectable()) continue;
			if (node.actor.getY() <= high) selection.add(node);
			if (node.expanded) selectNodes(node.children, low, high);
		}
	}

	@NotNull
	public Selection<N> getSelection () {
		return selection;
	}

	/** Returns the first selected node, or null. */
    @Nullable
	public N getSelectedNode () {
		return selection.first();
	}

	/** Returns the first selected value, or null. */
	@Nullable
	public V getSelectedValue () {
		N node = selection.first();
		return node == null ? null : (V)node.getValue();
	}

    @NotNull
    public TreeStyle getStyle () {
		return style;
	}

	@NotNull
	public Array<N> getRootNodes () {
		return rootNodes;
	}

	/** Removes the root node actors from the tree and adds them again. This is useful after changing the order of
	 * {@link #getRootNodes()}.
	 * @see Node#updateChildren() */
	public void updateRootNodes () {
		for (int i = rootNodes.size - 1; i >= 0; i--)
			rootNodes.get(i).removeFromTree(this);
		for (int i = 0, n = rootNodes.size; i < n; i++)
			rootNodes.get(i).addToTree(this);
	}

	/** @return May be null. */
	@Nullable
	public N getOverNode () {
		return overNode;
	}

	/** @return May be null. */
	@Nullable
	public V getOverValue () {
		if (overNode == null) return null;
		return (V)overNode.getValue();
	}

	/** @param overNode May be null. */
	public void setOverNode (@Nullable N overNode) {
		this.overNode = overNode;
	}

	/** Sets the amount of horizontal space between the nodes and the left/right edges of the tree. */
	public void setPadding (float padding) {
		paddingLeft = padding;
		paddingRight = padding;
	}

	/** Sets the amount of horizontal space between the nodes and the left/right edges of the tree. */
	public void setPadding (float left, float right) {
		this.paddingLeft = left;
		this.paddingRight = right;
	}

	public void setIndentSpacing (float indentSpacing) {
		this.indentSpacing = indentSpacing;
	}

	/** Returns the amount of horizontal space for indentation level. */
	public float getIndentSpacing () {
		return indentSpacing;
	}

	/** Sets the amount of vertical space between nodes. */
	public void setYSpacing (float ySpacing) {
		this.ySpacing = ySpacing;
	}

	public float getYSpacing () {
		return ySpacing;
	}

	/** Sets the amount of horizontal space left and right of the node's icon. If a node has no icon, the left spacing is used
	 * between the plus/minus drawable and the node's actor. */
	public void setIconSpacing (float left, float right) {
		this.iconSpacingLeft = left;
		this.iconSpacingRight = right;
	}

	public float getPrefWidth () {
		if (sizeInvalid) computeSize();
		return prefWidth;
	}

	public float getPrefHeight () {
		if (sizeInvalid) computeSize();
		return prefHeight;
	}

	public void findExpandedValues (@NotNull Array<V> values) {
		findExpandedValues(rootNodes, values);
	}

	public void restoreExpandedValues (@NotNull Array<V> values) {
		for (int i = 0, n = values.size; i < n; i++) {
			N node = findNode(values.get(i));
			if (node != null) {
				node.setExpanded(true);
				node.expandTo();
			}
		}
	}

	static boolean findExpandedValues (@NotNull Array<? extends Node> nodes, @NotNull Array values) {
		boolean expanded = false;
		for (int i = 0, n = nodes.size; i < n; i++) {
			Node node = nodes.get(i);
			if (node.expanded && !findExpandedValues(node.children, values)) values.add(node.value);
		}
		return expanded;
	}

	/** Returns the node with the specified value, or null. */
    @Nullable
	public N findNode (@NotNull V value) {
		if (value == null) throw new IllegalArgumentException("value cannot be null.");
		return (N)findNode(rootNodes, value);
	}

	static Node findNode (@NotNull Array<? extends Node> nodes, @NotNull Object value) {
		for (int i = 0, n = nodes.size; i < n; i++) {
			Node node = nodes.get(i);
			if (value.equals(node.value)) return node;
		}
		for (int i = 0, n = nodes.size; i < n; i++) {
			Node node = nodes.get(i);
			Node found = findNode(node.children, value);
			if (found != null) return found;
		}
		return null;
	}

	public void collapseAll () {
		collapseAll(rootNodes);
	}

	static void collapseAll (@NotNull Array<? extends Node> nodes) {
		for (int i = 0, n = nodes.size; i < n; i++) {
			Node node = nodes.get(i);
			node.setExpanded(false);
			collapseAll(node.children);
		}
	}

	public void expandAll () {
		expandAll(rootNodes);
	}

	static void expandAll (@NotNull Array<? extends Node> nodes) {
		for (int i = 0, n = nodes.size; i < n; i++)
			nodes.get(i).expandAll();
	}

	/** Returns the click listener the tree uses for clicking on nodes and the over node. */
	@NotNull
	public ClickListener getClickListener () {
		return clickListener;
	}

	/** A {@link Tree} node which has an actor and value.
	 * <p>
	 * A subclass can be used so the generic type parameters don't need to be specified repeatedly.
	 * @param <N> The type for the node's parent and child nodes.
	 * @param <V> The type for the node's value.
	 * @param <A> The type for the node's actor.
	 * @author Nathan Sweet */
	static abstract public class Node<N extends Node, V, A extends Actor> {
		A actor;
		N parent;
		final Array<N> children = new Array(0);
		boolean selectable = true;
		boolean expanded;
		Drawable icon;
		float height;
		V value;

		public Node (@NotNull A actor) {
			if (actor == null) throw new IllegalArgumentException("actor cannot be null.");
			this.actor = actor;
		}

		/** Creates a node without an actor. An actor must be set using {@link #setActor(Actor)} before this node can be used. */
		public Node () {
		}

		public void setExpanded (boolean expanded) {
			if (expanded == this.expanded) return;
			this.expanded = expanded;
			if (children.size == 0) return;
			Tree tree = getTree();
			if (tree == null) return;
			if (expanded) {
				for (int i = 0, n = children.size; i < n; i++)
					children.get(i).addToTree(tree);
			} else {
				for (int i = children.size - 1; i >= 0; i--)
					children.get(i).removeFromTree(tree);
			}
			tree.invalidateHierarchy();
		}

		/** Called to add the actor to the tree when the node's parent is expanded. */
		protected void addToTree (@NotNull Tree<N, V> tree) {
			tree.addActor(actor);
			if (!expanded) return;
			Object[] children = this.children.items;
			for (int i = this.children.size - 1; i >= 0; i--)
				((N)children[i]).addToTree(tree);
		}

		/** Called to remove the actor from the tree when the node's parent is collapsed. */
		protected void removeFromTree (@NotNull Tree<N, V> tree) {
			tree.removeActor(actor);
			if (!expanded) return;
			Object[] children = this.children.items;
			for (int i = this.children.size - 1; i >= 0; i--)
				((N)children[i]).removeFromTree(tree);
		}

		public void add (@NotNull N node) {
			insert(children.size, node);
		}

		public void addAll (@NotNull Array<N> nodes) {
			for (int i = 0, n = nodes.size; i < n; i++)
				insert(children.size, nodes.get(i));
		}

		public void insert (int index, @NotNull N node) {
			node.parent = this;
			children.insert(index, node);
			updateChildren();
		}

		public void remove () {
			Tree tree = getTree();
			if (tree != null)
				tree.remove(this);
			else if (parent != null) //
				parent.remove(this);
		}

		public void remove (@NotNull N node) {
			children.removeValue(node, true);
			if (!expanded) return;
			Tree tree = getTree();
			if (tree != null) node.removeFromTree(tree);
		}

		public void removeAll () {
			Tree tree = getTree();
			if (tree != null) {
				Object[] children = this.children.items;
				for (int i = this.children.size - 1; i >= 0; i--)
					((N)children[i]).removeFromTree(tree);
			}
			children.clear();
		}

		/** Returns the tree this node's actor is currently in, or null. The actor is only in the tree when all of its parent nodes
		 * are expanded. */
		@Nullable
		public Tree<N, V> getTree () {
			Group parent = actor.getParent();
			if (parent instanceof Tree) return (Tree)parent;
			return null;
		}

		public void setActor (A newActor) {
			if (actor != null) {
				Tree<N, V> tree = getTree();
				if (tree != null) {
					actor.remove();
					tree.addActor(newActor);
				}
			}
			actor = newActor;
		}

		@NotNull
		public A getActor () {
			return actor;
		}

		public boolean isExpanded () {
			return expanded;
		}

		/** If the children order is changed, {@link #updateChildren()} must be called. */
		@NotNull
		public Array<N> getChildren () {
			return children;
		}

		public boolean hasChildren () {
			return children.size > 0;
		}

		/** Removes the child node actors from the tree and adds them again. This is useful after changing the order of
		 * {@link #getChildren()}.
		 * @see Tree#updateRootNodes() */
		public void updateChildren () {
			if (!expanded) return;
			Tree tree = getTree();
			if (tree == null) return;
			for (int i = children.size - 1; i >= 0; i--)
				children.get(i).removeFromTree(tree);
			for (int i = 0, n = children.size; i < n; i++)
				children.get(i).addToTree(tree);
		}

		/** @return May be null. */
		@Nullable
		public N getParent () {
			return parent;
		}

		/** Sets an icon that will be drawn to the left of the actor. */
		public void setIcon (@Nullable Drawable icon) {
			this.icon = icon;
		}

		@Nullable
		public V getValue () {
			return value;
		}

		/** Sets an application specific value for this node. */
		public void setValue (@Nullable V value) {
			this.value = value;
		}

		@Nullable
		public Drawable getIcon () {
			return icon;
		}

		public int getLevel () {
			int level = 0;
			Node current = this;
			do {
				level++;
				current = current.getParent();
			} while (current != null);
			return level;
		}

		/** Returns this node or the child node with the specified value, or null. */
		@Nullable
		public N findNode (@NotNull V value) {
			if (value == null) throw new IllegalArgumentException("value cannot be null.");
			if (value.equals(this.value)) return (N)this;
			return (N)Tree.findNode(children, value);
		}

		/** Collapses all nodes under and including this node. */
		public void collapseAll () {
			setExpanded(false);
			Tree.collapseAll(children);
		}

		/** Expands all nodes under and including this node. */
		public void expandAll () {
			setExpanded(true);
			if (children.size > 0) Tree.expandAll(children);
		}

		/** Expands all parent nodes of this node. */
		public void expandTo () {
			Node node = parent;
			while (node != null) {
				node.setExpanded(true);
				node = node.parent;
			}
		}

		public boolean isSelectable () {
			return selectable;
		}

		public void setSelectable (boolean selectable) {
			this.selectable = selectable;
		}

		public void findExpandedValues (@NotNull Array<V> values) {
			if (expanded && !Tree.findExpandedValues(children, values)) values.add(value);
		}

		public void restoreExpandedValues (@NotNull Array<V> values) {
			for (int i = 0, n = values.size; i < n; i++) {
				N node = findNode(values.get(i));
				if (node != null) {
					node.setExpanded(true);
					node.expandTo();
				}
			}
		}

		/** Returns the height of the node as calculated for layout. A subclass may override and increase the returned height to
		 * create a blank space in the tree above the node, eg for a separator. */
		public float getHeight () {
			return height;
		}

		/** Returns true if the specified node is this node or an ascendant of this node. */
		public boolean isAscendantOf (N node) {
			if (node == null) throw new IllegalArgumentException("node cannot be null.");
			Node current = node;
			do {
				if (current == this) return true;
				current = current.parent;
			} while (current != null);
			return false;
		}

		/** Returns true if the specified node is this node or an descendant of this node. */
		public boolean isDescendantOf (N node) {
			if (node == null) throw new IllegalArgumentException("node cannot be null.");
			Node parent = this;
			do {
				if (parent == node) return true;
				parent = parent.parent;
			} while (parent != null);
			return false;
		}
	}

	/** The style for a {@link Tree}.
	 * @author Nathan Sweet */
	static public class TreeStyle {
		public Drawable plus, minus;
		/** Optional. */
		@Nullable public Drawable plusOver, minusOver;
        @Nullable public Drawable over, selection, background;

		public TreeStyle () {
		}

		public TreeStyle (@NotNull Drawable plus, @NotNull Drawable minus, @Nullable Drawable selection) {
			this.plus = plus;
			this.minus = minus;
			this.selection = selection;
		}

		public TreeStyle (@NotNull TreeStyle style) {
			this.plus = style.plus;
			this.minus = style.minus;
			this.plusOver = style.plusOver;
			this.minusOver = style.minusOver;
			this.over = style.over;
			this.selection = style.selection;
			this.background = style.background;
		}
	}
}
