// =====================================================================
// SDGExplorerView - the sound explorer's list: Finder's list mode, drawn
//
// A UserView with a header of columns and rows under it, two levels deep:
// a bank (with a triangle that opens it) and its samples. A click on a
// column's header asks the owner to sort by it - a second click the other
// way - and the column sorted by shows an arrow. Right click on the header
// asks which columns to show. The owner (SDGWindow) keeps the model: it
// hands the view rows, and hears what is done to them.
//
//   v = SDGExplorerView();
//   v.columns = [(key: \name, label: "name"), (key: \duration, label: "dur", width: 48, align: \right)];
//   v.rows = [(level: 0, expandable: true, expanded: false, cells: ["bd (24)", "4.5"]), ...];
//   v.selectAction = { |row, i| ... };     // a row became current
//   v.toggleAction = { |row, i| ... };     // its triangle, a double click on a bank, left and right
//   v.activateAction = { |row, i| ... };   // a double click on a sample, enter
//   v.menuAction = { |row, i| ... };       // right click, after selecting
//   v.headerAction = { |key| ... };        // a click on a column's header
//   v.headerMenuAction = { |key| ... };    // a right click on it
//   v.keyAction = { |char, mod, key| ... };  // any other key: answer true when taken
//
// A cell is a String, or an Array of [string, colour] pairs drawn one after
// the other (the tags, by hand in the accent and by the analysis dimmed).
//
// Keys: up and down move, left closes a bank or goes up to it, right opens
// one, page up and down, home, end.
// =====================================================================

SDGExplorerView {
	classvar <>rowHeight = 17, <>headerHeight = 18, <>indent = 14;

	var <view, <rows, <columns, <sortKey, <ascending = true;
	var <selectedIndex, <top = 0;
	var <>selectAction, <>toggleAction, <>activateAction, <>menuAction;
	var <>headerAction, <>headerMenuAction, <>keyAction, <>hintAction;
	var font, boldFont, smallFont, dragBar;

	*new { |parent, bounds| ^super.new.init(parent, bounds) }

	init { |parent, bounds|
		rows = [];
		columns = [(key: \name, label: "name")];
		font = Font(Font.defaultSansFace, 11);
		boldFont = Font(Font.defaultSansFace, 11, true);
		smallFont = Font(Font.defaultSansFace, 9);
		view = UserView(parent, bounds ? Rect(0, 0, 400, 300));
		view.background = Color.grey(0.16);
		view.canFocus = true;
		view.drawFunc = { |v| this.prDraw(v) };
		view.mouseDownAction = { |v, x, y, mod, button, clickCount|
			this.prMouseDown(x, y, mod, button ? 0, clickCount ? 1);
		};
		view.mouseMoveAction = { |v, x, y| this.prMouseMove(x, y) };
		view.mouseUpAction = { dragBar = nil };
		view.mouseWheelAction = { |v, x, y, mod, dx, dy|
			this.scrollBy((dy ? 0).neg.sign.asInteger * 3);
			true
		};
		view.keyDownAction = { |v, char, mod, uni, keycode, key| this.prKeyDown(char, mod, key) };
		view.acceptsMouseOver = true;
		view.mouseOverAction = { |v, x, y| hintAction !? { |f| f.value(this.prColumnAt(x), y < headerHeight) } };
		view.onResize = { this.prClampTop; this.refresh };
		^this
	}

	refresh { if (view.isClosed.not) { view.refresh } }

	rows_ { |array|
		rows = array ? [];
		if (selectedIndex.notNil and: { selectedIndex >= rows.size }) { selectedIndex = nil };
		this.prClampTop;
		this.refresh;
	}

	columns_ { |array| columns = array; this.refresh }

	sortKey_ { |key| sortKey = key; this.refresh }
	ascending_ { |bool| ascending = bool; this.refresh }

	current { ^selectedIndex !? { rows[selectedIndex] } }

	// a row made current, scrolled into view, and told to the owner unless asked not to
	select { |index, notify = true|
		selectedIndex = index !? { index.clip(0, rows.size - 1) };
		if (rows.isEmpty) { selectedIndex = nil };
		selectedIndex !? { this.ensureVisible(selectedIndex) };
		this.refresh;
		if (notify) { selectAction.value(this.current, selectedIndex) };
	}

	// ------------------------------------------------------------ geometry

	numVisible { ^((view.bounds.height - headerHeight) div: rowHeight).asInteger.max(1) }

	prClampTop { top = top.asInteger.clip(0, (rows.size - this.numVisible).max(0)) }  // Qt sizes are Floats: keep row numbers Integers

	scrollBy { |n| top = top + n; this.prClampTop; this.refresh }

	ensureVisible { |i|
		if (i < top) { top = i };
		if (i >= (top + this.numVisible)) { top = i - this.numVisible + 1 };
		this.prClampTop;
	}

	scrollBarWidth { ^if (rows.size > this.numVisible) { 8 } { 0 } }

	// [x, width] of every column: the ones without a width share what is left
	columnSpans {
		var w = view.bounds.width - this.scrollBarWidth, fixed = 0, flex = 0, x = 0;
		columns.do { |c| if (c[\width].notNil) { fixed = fixed + c[\width] } { flex = flex + 1 } };
		^columns.collect { |c|
			var cw = c[\width] ?? { ((w - fixed) / flex.max(1)).max(40) };
			var span = [x, cw];
			x = x + cw;
			span
		}
	}

	prColumnAt { |x|
		var spans = this.columnSpans, i = spans.detectIndex { |s| x >= s[0] and: { x < (s[0] + s[1]) } };
		^i !? { columns[i][\key] }
	}

	prRowAt { |y|
		var i;
		if (y < headerHeight) { ^nil };
		i = top + ((y - headerHeight) div: rowHeight).asInteger;
		^if (i < rows.size) { i } { nil }
	}

	// --------------------------------------------------------------- mouse

	prMouseDown { |x, y, mod, button, count|
		var i, row, key;
		view.focus(true);
		if (this.scrollBarWidth > 0 and: { x >= (view.bounds.width - this.scrollBarWidth) } and: { y >= headerHeight }) {
			dragBar = true;
			^this.prMouseMove(x, y)
		};
		if (y < headerHeight) {
			key = this.prColumnAt(x);
			if (key.notNil) { if (button == 1) { headerMenuAction.value(key) } { headerAction.value(key) } };
			^this
		};
		i = this.prRowAt(y);
		if (i.isNil) { ^this };
		row = rows[i];
		// the triangle: open or close without choosing the row
		if (row[\expandable] == true and: { x < ((row[\level] * indent) + indent + 4) } and: { button != 1 }) {
			^toggleAction.value(row, i)
		};
		if (i != selectedIndex) { this.select(i) };
		if (button == 1) { ^{ menuAction.value(row, i) }.defer(0.05) };
		if (count == 2) {
			if (row[\expandable] == true) { toggleAction.value(row, i) } { activateAction.value(row, i) };
		};
	}

	prMouseMove { |x, y|
		var h, frac;
		if (dragBar.isNil) { ^this };
		h = view.bounds.height - headerHeight;
		frac = ((y - headerHeight) / h.max(1)).clip(0, 1);
		top = (frac * (rows.size - this.numVisible)).round.asInteger;
		this.prClampTop;
		this.refresh;
	}

	// answers true for a key it took, nil for the rest
	prKeyDown { |char, mod, key|
		var i = selectedIndex, row = this.current, parent;
		switch (key,
			16r1000013, { this.select(((i ? 0) - 1).max(0)); ^true },				// up
			16r1000015, { this.select(if (i.isNil) { 0 } { (i + 1).min(rows.size - 1) }); ^true },	// down
			16r1000016, { this.select(((i ? 0) - this.numVisible).max(0)); ^true },	// page up
			16r1000017, { this.select(((i ? 0) + this.numVisible).min(rows.size - 1)); ^true },	// page down
			16r1000010, { this.select(0); ^true },										// home
			16r1000011, { this.select(rows.size - 1); ^true },							// end
			16r1000012, {																// left
				if (row.isNil) { ^true };
				if (row[\expandable] == true and: { row[\expanded] == true }) { toggleAction.value(row, i); ^true };
				if (row[\level] > 0) {
					parent = (i..0).detect { |j| rows[j][\level] == 0 };
					parent !? { this.select(parent) };
				};
				^true
			},
			16r1000014, {																// right
				if (row.notNil and: { row[\expandable] == true } and: { row[\expanded] != true }) { toggleAction.value(row, i) };
				^true
			}
		);
		^keyAction.value(char, mod, key)
	}

	// ------------------------------------------------------------- drawing

	prDraw { |v|
		var w = v.bounds.width, h = v.bounds.height, spans = this.columnSpans, n = this.numVisible;
		var sb = this.scrollBarWidth;
		Pen.fillColor = Color.grey(0.16);
		Pen.fillRect(Rect(0, 0, w, h));
		// the rows
		n.do { |k|
			var i = top + k, row, y, sel;
			if (i < rows.size) {
				row = rows[i];
				y = headerHeight + (k * rowHeight);
				sel = (i == selectedIndex);
				if (sel) {
					Pen.fillColor = SDGWindow.colAccent;
					Pen.fillRect(Rect(0, y, w - sb, rowHeight));
				} {
					if (i.odd) { Pen.fillColor = Color.grey(0.18); Pen.fillRect(Rect(0, y, w - sb, rowHeight)) };
				};
				columns.do { |c, ci| this.prDrawCell(row, c, ci, spans[ci], y, sel) };
			};
		};
		if (rows.isEmpty) {
			Pen.stringCenteredIn("nothing here", Rect(0, headerHeight, w, h - headerHeight), font, SDGWindow.colDim);
		};
		// the header
		Pen.fillColor = Color.grey(0.24);
		Pen.fillRect(Rect(0, 0, w, headerHeight));
		columns.do { |c, ci|
			var x = spans[ci][0], cw = spans[ci][1], r = Rect(x + 4, 1, cw - 8, headerHeight - 2), label = c[\label] ? c[\key].asString;
			var on = (c[\key] == sortKey);
			if (on) { label = label ++ if (ascending) { " ▲" } { " ▼" } };
			if (c[\align] == \right) {
				Pen.stringRightJustIn(label, r, if (on) { boldFont } { font }, if (on) { SDGWindow.colText } { SDGWindow.colDim })
			} {
				Pen.stringLeftJustIn(label, r, if (on) { boldFont } { font }, if (on) { SDGWindow.colText } { SDGWindow.colDim })
			};
			if (ci > 0) {
				Pen.strokeColor = Color.grey(0.32);
				Pen.line(Point(x + 0.5, 3), Point(x + 0.5, headerHeight - 3));
				Pen.stroke;
			};
		};
		// the scroll bar
		if (sb > 0) {
			var trackH = h - headerHeight, frac = this.numVisible / rows.size;
			var thumbH = (trackH * frac).max(16), pos = top / (rows.size - this.numVisible).max(1);
			Pen.fillColor = Color.grey(0.2);
			Pen.fillRect(Rect(w - sb, headerHeight, sb, trackH));
			Pen.fillColor = Color.grey(0.42);
			Pen.fillRect(Rect(w - sb + 1, headerHeight + ((trackH - thumbH) * pos), sb - 2, thumbH));
		};
	}

	prDrawCell { |row, col, ci, span, y, sel|
		var x = span[0], cw = span[1], value = row[\cells] !? { |cells| cells[ci] }, pad = 4, r, colour;
		if (value.isNil) { ^this };
		colour = if (sel) { Color.black } { if (ci == 0) { row[\color] ? SDGWindow.colText } { SDGWindow.colDim } };
		if (ci == 0) {
			pad = (row[\level] ? 0) * indent + indent + 2;
			if (row[\expandable] == true) { this.prDrawTriangle(x + ((row[\level] ? 0) * indent) + 3, y, row[\expanded] == true, colour) };
		};
		r = Rect(x + pad, y, (cw - pad - 4).max(1), rowHeight);
		Pen.push;
		Pen.addRect(Rect(x, y, cw, rowHeight));
		Pen.clip;
		if (value.isString) {
			if (col[\align] == \right) {
				Pen.stringRightJustIn(value, r, if (ci == 0 and: { row[\level] == 0 }) { boldFont } { font }, colour)
			} {
				Pen.stringLeftJustIn(value, r, if (ci == 0 and: { row[\level] == 0 }) { boldFont } { font }, colour)
			};
		} {
			// [string, colour] pairs one after the other
			value.do { |pair|
				var str = pair[0], c = if (sel) { Color.black } { pair[1] ? colour };
				var sw = str.bounds(smallFont).width + 6;
				Pen.stringLeftJustIn(str, Rect(r.left, y, sw + 2, rowHeight), smallFont, c);
				r = Rect(r.left + sw, y, r.width - sw, rowHeight);
			};
		};
		Pen.pop;
	}

	prDrawTriangle { |x, y, open, colour|
		var cy = y + (rowHeight * 0.5);
		Pen.fillColor = colour.copy.alpha_(0.8);
		if (open) {
			Pen.moveTo(Point(x, cy - 3)); Pen.lineTo(Point(x + 8, cy - 3)); Pen.lineTo(Point(x + 4, cy + 3));
		} {
			Pen.moveTo(Point(x + 1, cy - 4)); Pen.lineTo(Point(x + 7, cy)); Pen.lineTo(Point(x + 1, cy + 4));
		};
		Pen.fill;
	}
}
